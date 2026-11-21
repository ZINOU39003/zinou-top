package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.appupdatechecker2.AppUpdateChecker;
import com.liskovsoft.sharedutils.helpers.AppInfoHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AppUpdatePresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.common.zinou.ZinouUpdateConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * ZINOU TOP About screen.<br/>
 * Used instead of the upstream About screens when {@code R.bool.zinou_branding} is true
 * (common/src/zinou/res/values/zinou_config.xml). Upstream flavors never reach this class.<br/>
 * Contains no links to upstream author resources (donations, Telegram, web site, bridge installers).
 */
public class ZinouAboutSettingsPresenter extends BasePresenter<Void> {
    private final AppUpdateChecker mUpdateChecker;

    public ZinouAboutSettingsPresenter(Context context) {
        super(context);
        mUpdateChecker = new AppUpdateChecker(getContext(), null);
    }

    public static ZinouAboutSettingsPresenter instance(Context context) {
        return new ZinouAboutSettingsPresenter(context);
    }

    public static boolean isEnabled(Context context) {
        return context != null && context.getResources().getBoolean(R.bool.zinou_branding);
    }

    public void show() {
        String versionName = AppInfoHelpers.getAppVersionName(getContext());
        String mainTitle = String.format("%s %s", getContext().getString(R.string.app_name), versionName);

        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        appendUpdateCheckButton(settingsPresenter);
        appendAutoUpdateSwitch(settingsPresenter);
        appendUpdateChangelog(settingsPresenter, versionName);
        appendInfo(settingsPresenter, versionName);
        appendGitHubLink(settingsPresenter);
        appendLicenses(settingsPresenter);

        settingsPresenter.showDialog(mainTitle);
    }

    private void appendUpdateCheckButton(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleButton(UiOptionItem.from(
                getContext().getString(R.string.check_for_updates),
                option -> AppUpdatePresenter.instance(getContext()).start(true)));
    }

    /**
     * Automatic update CHECK (off / sidebar notification / dialog). Installation always needs user confirmation
     * in the Android Package Installer.
     */
    private void appendAutoUpdateSwitch(AppDialogPresenter settingsPresenter) {
        GeneralData generalData = GeneralData.instance(getContext());
        List<OptionItem> items = new ArrayList<>();

        items.add(UiOptionItem.from(getContext().getString(R.string.option_disabled),
                optionItem -> mUpdateChecker.setUpdateCheckEnabled(false),
                !mUpdateChecker.isUpdateCheckEnabled()));

        items.add(UiOptionItem.from(getContext().getString(R.string.sidebar_notification), optionItem -> {
            mUpdateChecker.setUpdateCheckEnabled(true);
            generalData.setOldUpdateNotificationsEnabled(false);
        }, mUpdateChecker.isUpdateCheckEnabled() && !generalData.isOldUpdateNotificationsEnabled()));

        items.add(UiOptionItem.from(getContext().getString(R.string.dialog_notification), optionItem -> {
            mUpdateChecker.setUpdateCheckEnabled(true);
            generalData.setOldUpdateNotificationsEnabled(true);
        }, mUpdateChecker.isUpdateCheckEnabled() && generalData.isOldUpdateNotificationsEnabled()));

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.check_updates_auto), items);
    }

    private void appendUpdateChangelog(AppDialogPresenter settingsPresenter, String versionName) {
        List<String> changes = GeneralData.instance(getContext()).getChangelog();

        if (changes == null || changes.isEmpty()) {
            return;
        }

        List<OptionItem> changelog = new ArrayList<>();
        for (String change : changes) {
            changelog.add(UiOptionItem.from(change));
        }

        settingsPresenter.appendStringsCategory(String.format("%s %s",
                getContext().getString(R.string.update_changelog), versionName), changelog);
    }

    private void appendInfo(AppDialogPresenter settingsPresenter, String versionName) {
        List<OptionItem> info = new ArrayList<>();

        info.add(UiOptionItem.from(getContext().getString(R.string.zinou_about_description)));
        info.add(UiOptionItem.from(getContext().getString(R.string.zinou_about_version,
                versionName, AppInfoHelpers.getAppVersionCode(getContext()))));
        info.add(UiOptionItem.from(getContext().getString(R.string.zinou_about_powered_by)));

        if (!ZinouUpdateConfig.UPSTREAM_COMMIT.isEmpty()) {
            info.add(UiOptionItem.from(getContext().getString(R.string.zinou_about_upstream, ZinouUpdateConfig.UPSTREAM_COMMIT)));
        }

        settingsPresenter.appendStringsCategory(getContext().getString(R.string.zinou_about_info), info);
    }

    /**
     * Shown only when a real GitHub repository is configured in zinou.properties (no placeholder links).
     */
    private void appendGitHubLink(AppDialogPresenter settingsPresenter) {
        if (ZinouUpdateConfig.GITHUB_URL.isEmpty()) {
            return;
        }

        settingsPresenter.appendSingleButton(UiOptionItem.from(
                getContext().getString(R.string.zinou_github),
                option -> Utils.openLink(getContext(), Utils.toQrCodeLink(ZinouUpdateConfig.GITHUB_URL))));
    }

    private void appendLicenses(AppDialogPresenter settingsPresenter) {
        List<OptionItem> licenses = new ArrayList<>();

        for (String notice : getContext().getResources().getStringArray(R.array.zinou_license_notices)) {
            licenses.add(UiOptionItem.from(notice));
        }

        settingsPresenter.appendStringsCategory(getContext().getString(R.string.zinou_open_source_licenses), licenses);
    }
}
