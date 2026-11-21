package com.liskovsoft.smartyoutubetv2.common.zinou.update;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Strict parser/validator for ZINOU TOP <code>update.json</code>. Pure Java (org.json only), unit-tested.<br/>
 * The format is the one read by the upstream AppUpdateChecker engine, plus integrity data:
 * <pre>
 * {
 *   "package": {
 *     "downloadUrlList": ["https://github.com/OWNER/REPO/releases/download/v1.0.1/ZinouTop-v1.0.1.apk"],
 *     "downloadUrlList_arm64-v8a": ["https://github.com/.../ZinouTop-v1.0.1-arm64-v8a.apk", "...universal..."],
 *     "sha256": { "ZinouTop-v1.0.1.apk": "&lt;64 hex&gt;", "ZinouTop-v1.0.1-arm64-v8a.apk": "&lt;64 hex&gt;" },
 *     "size":   { "ZinouTop-v1.0.1.apk": 12345678, ... }
 *   },
 *   "1.0.1": { "versionCode": 10001, "changelog": ["..."], "changelog_ar": ["..."] }
 * }
 * </pre>
 * Validation runs BEFORE the engine sees the file, so a malformed manifest can never reach the
 * engine (which would otherwise hang the loading indicator or crash on an empty version list).
 */
public final class ZinouUpdateManifest {
    public static final int MAX_MANIFEST_CHARS = 256 * 1024;
    private static final String PACKAGE = "package";
    private static final String DOWNLOAD_URL = "downloadUrl";
    private static final String DOWNLOAD_URL_LIST = "downloadUrlList";
    private static final String SHA256 = "sha256";
    private static final String SIZE = "size";
    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-f]{64}$");

    private final int mLatestVersionCode;
    private final String mLatestVersionName;
    private final Map<String, String> mSha256ByFile;
    private final Map<String, Long> mSizeByFile;
    private final List<String> mDownloadUrls;

    public static final class InvalidManifestException extends Exception {
        public InvalidManifestException(String message) {
            super(message);
        }
    }

    private ZinouUpdateManifest(int latestVersionCode, String latestVersionName, Map<String, String> sha256ByFile,
                                Map<String, Long> sizeByFile, List<String> downloadUrls) {
        mLatestVersionCode = latestVersionCode;
        mLatestVersionName = latestVersionName;
        mSha256ByFile = Collections.unmodifiableMap(sha256ByFile);
        mSizeByFile = Collections.unmodifiableMap(sizeByFile);
        mDownloadUrls = Collections.unmodifiableList(downloadUrls);
    }

    public static ZinouUpdateManifest parse(String json, Collection<String> allowedHosts) throws InvalidManifestException {
        if (json == null || json.trim().isEmpty()) {
            throw new InvalidManifestException("empty manifest");
        }
        if (json.length() > MAX_MANIFEST_CHARS) {
            throw new InvalidManifestException("manifest too large");
        }

        try {
            JSONObject root = new JSONObject(json);
            JSONObject pkg = root.optJSONObject(PACKAGE);
            if (pkg == null) {
                throw new InvalidManifestException("missing 'package' object");
            }

            // Versions: every other top-level key must be {"versionCode": int > 0, ...}
            int latestCode = -1;
            String latestName = null;
            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (PACKAGE.equals(key)) {
                    continue;
                }
                JSONObject version = root.optJSONObject(key);
                if (version == null) {
                    throw new InvalidManifestException("version entry '" + key + "' is not an object");
                }
                Object code = version.opt("versionCode");
                if (!(code instanceof Integer) || (Integer) code <= 0) {
                    throw new InvalidManifestException("invalid versionCode for '" + key + "'");
                }
                if ((Integer) code > latestCode) {
                    latestCode = (Integer) code;
                    latestName = key;
                }
            }
            if (latestCode <= 0) {
                throw new InvalidManifestException("no version entries");
            }

            // Download URLs: HTTPS only, allowed hosts only, *.apk only
            List<String> urls = new ArrayList<>();
            Iterator<String> pkgKeys = pkg.keys();
            while (pkgKeys.hasNext()) {
                String key = pkgKeys.next();
                if (DOWNLOAD_URL.equals(key)) {
                    urls.add(pkg.getString(key));
                } else if (key.startsWith(DOWNLOAD_URL_LIST)) {
                    JSONArray list = pkg.optJSONArray(key);
                    if (list == null || list.length() == 0) {
                        throw new InvalidManifestException("'" + key + "' must be a non-empty array");
                    }
                    for (int i = 0; i < list.length(); i++) {
                        urls.add(list.getString(i));
                    }
                }
            }
            if (urls.isEmpty()) {
                throw new InvalidManifestException("no download URLs");
            }

            JSONObject shaJson = pkg.optJSONObject(SHA256);
            if (shaJson == null) {
                throw new InvalidManifestException("missing 'sha256' object");
            }
            JSONObject sizeJson = pkg.optJSONObject(SIZE);

            Map<String, String> sha256ByFile = new HashMap<>();
            Map<String, Long> sizeByFile = new HashMap<>();
            for (String url : urls) {
                String fileName = validateUrl(url, allowedHosts);
                String sha = shaJson.optString(fileName, "").toLowerCase(Locale.ROOT);
                if (!SHA256_HEX.matcher(sha).matches()) {
                    throw new InvalidManifestException("missing/invalid sha256 for " + fileName);
                }
                sha256ByFile.put(fileName, sha);
                if (sizeJson != null) {
                    long size = sizeJson.optLong(fileName, -1);
                    if (size <= 0) {
                        throw new InvalidManifestException("missing/invalid size for " + fileName);
                    }
                    sizeByFile.put(fileName, size);
                }
            }

            return new ZinouUpdateManifest(latestCode, latestName, sha256ByFile, sizeByFile, urls);
        } catch (JSONException e) {
            throw new InvalidManifestException("malformed JSON: " + e.getMessage());
        }
    }

    /**
     * @return APK file name (last path segment) of a valid URL
     */
    static String validateUrl(String url, Collection<String> allowedHosts) throws InvalidManifestException {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException | NullPointerException e) {
            throw new InvalidManifestException("invalid URL: " + url);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new InvalidManifestException("non-HTTPS URL rejected: " + url);
        }
        String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : null;
        if (host == null || allowedHosts == null || !allowedHosts.contains(host)) {
            throw new InvalidManifestException("host not allowed: " + url);
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getUserInfo() != null) {
            throw new InvalidManifestException("unexpected URL parts: " + url);
        }
        String path = uri.getPath();
        int slash = path != null ? path.lastIndexOf('/') : -1;
        String fileName = slash >= 0 ? path.substring(slash + 1) : null;
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".apk") || fileName.length() <= 4) {
            throw new InvalidManifestException("URL is not an .apk file: " + url);
        }
        return fileName;
    }

    public int getLatestVersionCode() {
        return mLatestVersionCode;
    }

    public String getLatestVersionName() {
        return mLatestVersionName;
    }

    public List<String> getDownloadUrls() {
        return mDownloadUrls;
    }

    /**
     * @return the published file name whose SHA-256 equals {@code sha256Hex}, or null
     */
    public String findFileBySha256(String sha256Hex) {
        if (sha256Hex == null) {
            return null;
        }
        String needle = sha256Hex.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : mSha256ByFile.entrySet()) {
            if (entry.getValue().equals(needle)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * @return expected size in bytes, or -1 when the manifest has no size information
     */
    public long getExpectedSize(String fileName) {
        Long size = mSizeByFile.get(fileName);
        return size != null ? size : -1;
    }
}
