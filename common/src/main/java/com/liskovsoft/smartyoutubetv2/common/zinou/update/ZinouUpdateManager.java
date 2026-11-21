package com.liskovsoft.smartyoutubetv2.common.zinou.update;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import com.liskovsoft.appupdatechecker2.AppUpdateCheckerListener;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.okhttp.OkHttpManager;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.utils.LoadingManager;
import com.liskovsoft.smartyoutubetv2.common.zinou.ZinouUpdateConfig;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * ZINOU TOP update flow (zinou flavor only). Wraps the upstream AppUpdateChecker engine:
 * <pre>
 * GitHub Release -> update.json (preflight: fetch over HTTPS + strict validation)
 *   -> engine: compare versionCode, pick APK for the device ABI, download over HTTPS
 *   -> verifyAndInstall(): SHA-256 + size + package name + newer versionCode
 *   -> Android Package Installer (user confirmation, signature check by Android)
 * </pre>
 * Never installs silently, never bypasses the system installer.
 */
public final class ZinouUpdateManager {
    private static final String TAG = ZinouUpdateManager.class.getSimpleName();
    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());
    private static volatile ZinouUpdateManifest sLastManifest;

    enum Failure {
        NOT_CONFIGURED,
        NETWORK,
        NO_RELEASE,
        UNAVAILABLE,
        INVALID
    }

    static final class FetchException extends Exception {
        final Failure failure;

        FetchException(Failure failure, String message, Throwable cause) {
            super(message, cause);
            this.failure = failure;
        }
    }

    private ZinouUpdateManager() {
    }

    public static boolean isEnabled(Context context) {
        return context != null && context.getResources().getBoolean(R.bool.zinou_branding);
    }

    /**
     * Fetches and validates update.json before the engine runs.
     * @param onValid runs on the main thread when update.json is valid (then the engine continues)
     */
    public static void preflight(Context context, boolean forceCheck, Runnable onValid) {
        if (!ZinouUpdateConfig.isConfigured()) {
            if (forceCheck) {
                MessageHelpers.showLongMessage(context, R.string.zinou_update_not_configured);
            }
            return;
        }

        if (forceCheck) {
            LoadingManager.showLoading(context, true);
            MessageHelpers.showMessage(context, R.string.zinou_update_checking);
        }

        runInBackground(() -> {
            try {
                ZinouUpdateManifest manifest = fetchManifest();
                sLastManifest = manifest;
                sMainHandler.post(onValid);
            } catch (FetchException e) {
                Log.e(TAG, "Update check failed: %s", e.getMessage());
                if (forceCheck) {
                    sMainHandler.post(() -> {
                        LoadingManager.showLoading(context, false);
                        MessageHelpers.showLongMessage(context, messageFor(e.failure));
                    });
                }
            }
        });
    }

    /**
     * Friendly handling of engine errors (download failures etc.).
     * @return true if handled; false lets the caller keep its default behaviour ("latest version" message)
     */
    public static boolean handleEngineError(Context context, Exception error, boolean forceCheck) {
        if (error != null && AppUpdateCheckerListener.LATEST_VERSION.equals(error.getMessage())) {
            return false;
        }

        Log.e(TAG, "Update engine error: %s", error != null ? error.getMessage() : null);

        if (forceCheck) {
            LoadingManager.showLoading(context, false);
            MessageHelpers.showLongMessage(context, R.string.zinou_update_download_failed);
        }

        return true;
    }

    /**
     * Verifies the downloaded APK, then runs {@code install} (which opens the Android Package Installer).
     * On any failure the file is deleted and nothing is installed.
     */
    public static void verifyAndInstall(Context context, String apkPath, Runnable install) {
        LoadingManager.showLoading(context, true);
        MessageHelpers.showMessage(context, R.string.zinou_update_verifying);
        Context appContext = context.getApplicationContext();

        runInBackground(() -> {
            int messageResId;
            try {
                messageResId = verify(appContext, apkPath);
            } catch (FetchException e) {
                Log.e(TAG, "Cannot verify update: %s", e.getMessage());
                messageResId = R.string.zinou_update_unavailable;
            } catch (IOException | RuntimeException e) {
                Log.e(TAG, "Verification error: %s", e.getMessage());
                messageResId = R.string.zinou_update_verify_failed;
            }

            final int result = messageResId;
            if (result != 0) {
                deleteQuietly(apkPath);
            }

            sMainHandler.post(() -> {
                LoadingManager.showLoading(context, false);
                if (result == 0) {
                    install.run();
                } else {
                    MessageHelpers.showLongMessage(context, result);
                }
            });
        });
    }

    /**
     * @return 0 when the APK may be installed, otherwise the message resource explaining why not
     */
    private static int verify(Context context, String apkPath) throws FetchException, IOException {
        File apk = apkPath != null ? new File(apkPath) : null;
        if (apk == null || !apk.isFile()) {
            return R.string.zinou_update_download_failed;
        }

        PackageManager pm = context.getPackageManager();
        PackageInfo archive = pm.getPackageArchiveInfo(apk.getAbsolutePath(), 0);
        if (archive == null || !context.getPackageName().equals(archive.packageName)) {
            return R.string.zinou_update_verify_failed;
        }

        int installedVersion;
        try {
            installedVersion = pm.getPackageInfo(context.getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return R.string.zinou_update_verify_failed;
        }
        if (archive.versionCode <= installedVersion) {
            return R.string.zinou_update_not_newer;
        }

        ZinouUpdateManifest manifest = sLastManifest;
        if (manifest == null || manifest.getLatestVersionCode() != archive.versionCode) {
            manifest = fetchManifest();
            sLastManifest = manifest;
        }
        if (manifest.getLatestVersionCode() != archive.versionCode) {
            return R.string.zinou_update_verify_failed;
        }

        ZinouApkIntegrity.Result integrity = ZinouApkIntegrity.verify(manifest, apk);
        Log.d(TAG, "Integrity check: %s", integrity);
        return integrity == ZinouApkIntegrity.Result.OK ? 0 : R.string.zinou_update_verify_failed;
    }

    static ZinouUpdateManifest fetchManifest() throws FetchException {
        String json = download(ZinouUpdateConfig.UPDATE_MANIFEST_URL);
        try {
            return ZinouUpdateManifest.parse(json, ZinouUpdateConfig.ALLOWED_DOWNLOAD_HOSTS);
        } catch (ZinouUpdateManifest.InvalidManifestException e) {
            throw new FetchException(Failure.INVALID, e.getMessage(), e);
        }
    }

    private static String download(String url) throws FetchException {
        if (url == null || !url.startsWith("https://")) {
            throw new FetchException(Failure.NOT_CONFIGURED, "update URL is not configured", null);
        }

        OkHttpClient client = OkHttpManager.instance().getClient().newBuilder()
                .connectTimeout(ZinouUpdateConfig.CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .readTimeout(ZinouUpdateConfig.READ_TIMEOUT_SEC, TimeUnit.SECONDS)
                .followSslRedirects(false) // never follow a redirect from HTTPS to HTTP
                .build();
        Request request = new Request.Builder().url(url).header("Accept", "application/json").get().build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.request().url().isHttps()) {
                throw new FetchException(Failure.INVALID, "insecure redirect", null);
            }
            if (response.code() == 404) {
                throw new FetchException(Failure.NO_RELEASE, "update.json not found (no published release?)", null);
            }
            if (!response.isSuccessful()) {
                throw new FetchException(Failure.UNAVAILABLE, "HTTP " + response.code(), null);
            }
            ResponseBody body = response.body();
            if (body == null || body.contentLength() > ZinouUpdateManifest.MAX_MANIFEST_CHARS) {
                throw new FetchException(Failure.INVALID, "empty or oversized response", null);
            }
            return body.string();
        } catch (IOException | IllegalStateException e) {
            // no internet, DNS failure, timeout, TLS error, interrupted connection
            throw new FetchException(Failure.NETWORK, e.getMessage(), e);
        }
    }

    static int messageFor(Failure failure) {
        switch (failure) {
            case NOT_CONFIGURED:
                return R.string.zinou_update_not_configured;
            case NO_RELEASE:
                return R.string.zinou_update_no_release;
            case INVALID:
                return R.string.zinou_update_invalid;
            case NETWORK:
            case UNAVAILABLE:
            default:
                return R.string.zinou_update_unavailable;
        }
    }

    private static void deleteQuietly(String path) {
        if (path != null) {
            //noinspection ResultOfMethodCallIgnored
            new File(path).delete();
        }
    }

    private static void runInBackground(Runnable task) {
        Thread thread = new Thread(task, "ZinouUpdate");
        thread.setDaemon(true);
        thread.start();
    }
}
