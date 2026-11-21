package com.liskovsoft.smartyoutubetv2.common.zinou;

import com.liskovsoft.smartyoutubetv2.common.BuildConfig;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * ZINOU TOP update configuration.<br/>
 * All values are generated at build time from <code>zinou.properties</code> (repository root)
 * by <code>common/zinou-config.gradle</code>. Do not hardcode repository names anywhere else.
 * Values are empty in upstream flavors and while the GitHub owner is not configured.
 */
public final class ZinouUpdateConfig {
    public static final String GITHUB_OWNER = BuildConfig.ZINOU_GITHUB_OWNER;
    public static final String GITHUB_REPOSITORY = BuildConfig.ZINOU_GITHUB_REPOSITORY;
    public static final String GITHUB_URL = BuildConfig.ZINOU_GITHUB_URL;
    /** GitHub Releases API endpoint of the latest release (informational / tooling). */
    public static final String RELEASE_API_URL = BuildConfig.ZINOU_RELEASE_API_URL;
    /** update.json attached to the latest published GitHub Release. */
    public static final String UPDATE_MANIFEST_URL = BuildConfig.ZINOU_UPDATE_MANIFEST_URL;
    public static final String UPSTREAM_COMMIT = BuildConfig.ZINOU_UPSTREAM_COMMIT;

    /** APKs may only be downloaded from these hosts (HTTPS only). */
    public static final Set<String> ALLOWED_DOWNLOAD_HOSTS =
            Collections.unmodifiableSet(new HashSet<>(Collections.singletonList("github.com")));

    /** Network timeouts for update.json. */
    public static final int CONNECT_TIMEOUT_SEC = 15;
    public static final int READ_TIMEOUT_SEC = 20;

    private ZinouUpdateConfig() {
    }

    public static boolean isConfigured() {
        return UPDATE_MANIFEST_URL != null && UPDATE_MANIFEST_URL.startsWith("https://");
    }
}
