package com.liskovsoft.smartyoutubetv2.common.zinou.update;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * ZINOU TOP update.json validation and SHA-256 integrity tests (pure JVM).
 * Run: ./gradlew :common:testZinouDebugUnitTest --tests "*.zinou.*"
 */
public class ZinouUpdateManifestTest {
    private static final Set<String> HOSTS = Collections.singleton("github.com");
    private static final String BASE = "https://github.com/OWNER/zinou-top/releases/download/v1.0.1/";
    private static final String UNIVERSAL = "ZinouTop-v1.0.1.apk";
    private static final String ARM64 = "ZinouTop-v1.0.1-arm64-v8a.apk";
    private static final String SHA_A = repeat("a", 64);
    private static final String SHA_B = repeat("b", 64);

    // ---------- valid manifests ----------

    @Test
    public void validManifest_1_0_0_to_1_0_1() throws Exception {
        ZinouUpdateManifest m = ZinouUpdateManifest.parse(validJson().toString(), HOSTS);
        assertEquals(10001, m.getLatestVersionCode());
        assertEquals("1.0.1", m.getLatestVersionName());
        assertEquals(UNIVERSAL, m.findFileBySha256(SHA_A));
        assertEquals(ARM64, m.findFileBySha256(SHA_B.toUpperCase()));
        assertEquals(100, m.getExpectedSize(UNIVERSAL));
        assertEquals(3, m.getDownloadUrls().size());
    }

    @Test
    public void latestVersionIsHighestVersionCode() throws Exception {
        JSONObject json = validJson();
        json.put("1.0.0", new JSONObject().put("versionCode", 10000).put("changelog", new JSONArray().put("first")));
        ZinouUpdateManifest m = ZinouUpdateManifest.parse(json.toString(), HOSTS);
        assertEquals(10001, m.getLatestVersionCode());
        assertEquals("1.0.1", m.getLatestVersionName());
    }

    @Test
    public void sizeIsOptional() throws Exception {
        JSONObject json = validJson();
        json.getJSONObject("package").remove("size");
        ZinouUpdateManifest m = ZinouUpdateManifest.parse(json.toString(), HOSTS);
        assertEquals(-1, m.getExpectedSize(UNIVERSAL));
    }

    // ---------- negative: manifest ----------

    @Test
    public void rejectsEmptyAndMalformed() {
        assertInvalid(null);
        assertInvalid("");
        assertInvalid("not json");
        assertInvalid("<html>404</html>");
        assertInvalid("{}");
    }

    @Test
    public void rejectsMissingPackage() throws Exception {
        JSONObject json = validJson();
        json.remove("package");
        assertInvalid(json.toString());
    }

    @Test
    public void rejectsNoVersions() throws Exception {
        JSONObject json = validJson();
        json.remove("1.0.1");
        assertInvalid(json.toString());
    }

    @Test
    public void rejectsBadVersionCode() throws Exception {
        JSONObject json = validJson();
        json.getJSONObject("1.0.1").put("versionCode", "10001");
        assertInvalid(json.toString());
        json.getJSONObject("1.0.1").put("versionCode", 0);
        assertInvalid(json.toString());
        json.put("1.0.1", "string");
        assertInvalid(json.toString());
    }

    @Test
    public void rejectsHttp() throws Exception {
        JSONObject json = validJson();
        json.getJSONObject("package").put("downloadUrlList", new JSONArray().put("http://github.com/OWNER/zinou-top/releases/download/v1.0.1/" + UNIVERSAL));
        assertInvalid(json.toString());
    }

    @Test
    public void rejectsForeignHost() throws Exception {
        JSONObject json = validJson();
        json.getJSONObject("package").put("downloadUrlList", new JSONArray().put("https://evil.example.com/" + UNIVERSAL));
        assertInvalid(json.toString());
        json.getJSONObject("package").put("downloadUrlList", new JSONArray().put("https://github.com.evil.example/" + UNIVERSAL));
        assertInvalid(json.toString());
    }

    @Test
    public void rejectsNonApkAndQueryUrls() throws Exception {
        JSONObject json = validJson();
        json.getJSONObject("package").put("downloadUrlList", new JSONArray().put(BASE + "update.json"));
        assertInvalid(json.toString());
        json.getJSONObject("package").put("downloadUrlList", new JSONArray().put(BASE + UNIVERSAL + "?x=1"));
        assertInvalid(json.toString());
    }

    @Test
    public void rejectsMissingOrBadSha256() throws Exception {
        JSONObject json = validJson();
        json.getJSONObject("package").remove("sha256");
        assertInvalid(json.toString());

        json = validJson();
        json.getJSONObject("package").getJSONObject("sha256").put(UNIVERSAL, "1234");
        assertInvalid(json.toString());

        json = validJson();
        json.getJSONObject("package").getJSONObject("sha256").remove(ARM64);
        assertInvalid(json.toString());
    }

    @Test
    public void rejectsEmptyUrlList() throws Exception {
        JSONObject json = validJson();
        json.getJSONObject("package").put("downloadUrlList_x86", new JSONArray());
        assertInvalid(json.toString());
    }

    @Test
    public void rejectsOversizedManifest() {
        assertInvalid("{\"x\":\"" + repeat("y", ZinouUpdateManifest.MAX_MANIFEST_CHARS) + "\"}");
    }

    // ---------- integrity (SHA-256) ----------

    @Test
    public void sha256OfKnownContent() throws Exception {
        File f = tempFile("abc".getBytes("UTF-8"));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", ZinouApkIntegrity.sha256Hex(f));
    }

    @Test
    public void integrityOkWhenHashAndSizeMatch() throws Exception {
        byte[] content = "fake apk content for test".getBytes("UTF-8");
        File apk = tempFile(content);
        ZinouUpdateManifest m = manifestFor(ZinouApkIntegrity.sha256Hex(apk), content.length);
        assertEquals(ZinouApkIntegrity.Result.OK, ZinouApkIntegrity.verify(m, apk));
    }

    @Test
    public void integrityFailsOnWrongHash() throws Exception {
        File apk = tempFile("tampered".getBytes("UTF-8"));
        ZinouUpdateManifest m = manifestFor(SHA_A, 8);
        assertEquals(ZinouApkIntegrity.Result.HASH_MISMATCH, ZinouApkIntegrity.verify(m, apk));
    }

    @Test
    public void integrityFailsOnWrongSize() throws Exception {
        byte[] content = "content".getBytes("UTF-8");
        File apk = tempFile(content);
        ZinouUpdateManifest m = manifestFor(ZinouApkIntegrity.sha256Hex(apk), content.length + 1);
        assertEquals(ZinouApkIntegrity.Result.SIZE_MISMATCH, ZinouApkIntegrity.verify(m, apk));
    }

    @Test
    public void integrityFailsOnMissingOrEmptyFile() throws Exception {
        ZinouUpdateManifest m = manifestFor(SHA_A, 1);
        assertEquals(ZinouApkIntegrity.Result.FILE_MISSING, ZinouApkIntegrity.verify(m, new File("/nonexistent/zinou.apk")));
        assertEquals(ZinouApkIntegrity.Result.FILE_MISSING, ZinouApkIntegrity.verify(m, tempFile(new byte[0])));
        assertEquals(ZinouApkIntegrity.Result.FILE_MISSING, ZinouApkIntegrity.verify(m, null));
    }

    @Test
    public void unknownHashFindsNothing() throws Exception {
        ZinouUpdateManifest m = ZinouUpdateManifest.parse(validJson().toString(), HOSTS);
        assertNull(m.findFileBySha256(repeat("c", 64)));
        assertNull(m.findFileBySha256(null));
    }

    // ---------- helpers ----------

    private static JSONObject validJson() throws Exception {
        JSONObject pkg = new JSONObject()
                .put("downloadUrlList", new JSONArray().put(BASE + UNIVERSAL))
                .put("downloadUrlList_arm64-v8a", new JSONArray().put(BASE + ARM64).put(BASE + UNIVERSAL))
                .put("sha256", new JSONObject().put(UNIVERSAL, SHA_A).put(ARM64, SHA_B))
                .put("size", new JSONObject().put(UNIVERSAL, 100).put(ARM64, 90));
        return new JSONObject()
                .put("package", pkg)
                .put("1.0.1", new JSONObject().put("versionCode", 10001)
                        .put("changelog", new JSONArray().put("Fixes")));
    }

    private static ZinouUpdateManifest manifestFor(String sha, long size) throws Exception {
        JSONObject pkg = new JSONObject()
                .put("downloadUrlList", new JSONArray().put(BASE + UNIVERSAL))
                .put("sha256", new JSONObject().put(UNIVERSAL, sha))
                .put("size", new JSONObject().put(UNIVERSAL, size));
        JSONObject json = new JSONObject().put("package", pkg)
                .put("1.0.1", new JSONObject().put("versionCode", 10001));
        return ZinouUpdateManifest.parse(json.toString(), HOSTS);
    }

    private static void assertInvalid(String json) {
        try {
            ZinouUpdateManifest.parse(json, HOSTS);
            fail("expected InvalidManifestException for: " + (json != null && json.length() > 80 ? json.substring(0, 80) : json));
        } catch (ZinouUpdateManifest.InvalidManifestException expected) {
            assertTrue(expected.getMessage() != null);
        }
    }

    private static File tempFile(byte[] content) throws IOException {
        File f = File.createTempFile("zinou", ".apk");
        f.deleteOnExit();
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(content);
        }
        return f;
    }

    private static String repeat(String s, int n) {
        StringBuilder b = new StringBuilder(s.length() * n);
        for (int i = 0; i < n; i++) {
            b.append(s);
        }
        return b.toString();
    }
}
