package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.sharedutils.helpers.AppInfoHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;

import java.util.ArrayList;
import java.util.List;

/**
 * ZINOU TOP About screen.<br/>
 * Used instead of the upstream About screens when {@code R.bool.zinou_branding} is true
 * (set in common/src/zinou/res/values/zinou_config.xml). Upstream flavors never reach this class.<br/>
 * Contains no links to upstream author resources (donations, Telegram, web site, bridge installers).
 * Update check items are added in the update-system phase.
 */
public class ZinouAboutSettingsPresenter extends BasePresenter<Void> {
    public ZinouAboutSettingsPresenter(Context context) {
        super(context);
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

        appendInfo(settingsPresenter, versionName);
        appendLicenses(settingsPresenter);

        settingsPresenter.showDialog(mainTitle);
    }

    private void appendInfo(AppDialogPresenter settingsPresenter, String versionName) {
        List<OptionItem> info = new ArrayList<>();

        info.add(UiOptionItem.from(getContext().getString(R.string.zinou_about_description)));
        info.add(UiOptionItem.from(getContext().getString(R.string.zinou_about_version,
                versionName, AppInfoHelpers.getAppVersionCode(getContext()))));
        info.add(UiOptionItem.from(getContext().getString(R.string.zinou_about_powered_by)));

        String upstreamCommit = getContext().getString(R.string.zinou_upstream_commit);
        if (!upstreamCommit.isEmpty()) {
            info.add(UiOptionItem.from(getContext().getString(R.string.zinou_about_upstream, upstreamCommit)));
        }

        settingsPresenter.appendStringsCategory(getContext().getString(R.string.zinou_about_info), info);
    }

    private void appendLicenses(AppDialogPresenter settingsPresenter) {
        List<OptionItem> licenses = new ArrayList<>();

        for (String notice : getContext().getResources().getStringArray(R.array.zinou_license_notices)) {
            licenses.add(UiOptionItem.from(notice));
        }

        settingsPresenter.appendStringsCategory(getContext().getString(R.string.zinou_open_source_licenses), licenses);
    }
}
