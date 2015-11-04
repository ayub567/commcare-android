package org.commcare.android.session;

import android.content.SharedPreferences;
import android.util.Pair;

import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.commcare.dalvik.preferences.DeveloperPreferences;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class DevSessionRestorer {

    public static Pair<String, String> getAutoLoginCreds() {
        if (autoLoginEnabled()) {
            SharedPreferences prefs =
                    CommCareApplication._().getCurrentApp().getAppPreferences();
            String lastUser = prefs.getString(CommCarePreferences.LAST_LOGGED_IN_USER, "");
            String lastPass = prefs.getString(CommCarePreferences.LAST_PASSWORD, "");

            if (!"".equals(lastPass)) {
                return new Pair<>(lastUser, lastPass);
            }
        }

        return null;
    }

    public static void tryAutoLoginPasswordSave(String password) {
        if (autoLoginEnabled()) {
            SharedPreferences prefs =
                    CommCareApplication._().getCurrentApp().getAppPreferences();
            prefs.edit().putString(CommCarePreferences.LAST_PASSWORD, password).commit();
        }
    }

    private static boolean autoLoginEnabled() {
        return BuildConfig.DEBUG && DeveloperPreferences.isAutoLoginEnabled();
    }
}
