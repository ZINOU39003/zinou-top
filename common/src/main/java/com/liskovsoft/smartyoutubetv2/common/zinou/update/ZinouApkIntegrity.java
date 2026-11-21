package com.liskovsoft.smartyoutubetv2.common.zinou.update;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 verification of a downloaded update APK against update.json. Pure Java, unit-tested.<br/>
 * A downloaded file is accepted only if its SHA-256 equals one of the hashes published for the
 * latest release (and its size matches, when sizes are published).
 */
public final class ZinouApkIntegrity {
    public enum Result {
        OK,
        FILE_MISSING,
        HASH_MISMATCH,
        SIZE_MISMATCH
    }

    private ZinouApkIntegrity() {
    }

    public static Result verify(ZinouUpdateManifest manifest, File apk) throws IOException {
        if (apk == null || !apk.isFile() || apk.length() == 0) {
            return Result.FILE_MISSING;
        }

        String fileName = manifest.findFileBySha256(sha256Hex(apk));
        if (fileName == null) {
            return Result.HASH_MISMATCH;
        }

        long expectedSize = manifest.getExpectedSize(fileName);
        if (expectedSize > 0 && expectedSize != apk.length()) {
            return Result.SIZE_MISMATCH;
        }

        return Result.OK;
    }

    public static String sha256Hex(File file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 not available", e);
        }

        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }

        byte[] hash = digest.digest();
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }
}
