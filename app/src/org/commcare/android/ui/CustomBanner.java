package org.commcare.android.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.util.DisplayMetrics;
import android.widget.ImageView;

import org.commcare.android.util.MediaUtil;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.CommCarePreferences;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class CustomBanner {
    public static boolean useCustomBanner(Activity activity, @NonNull ImageView topBannerImageView) {
        DisplayMetrics displaymetrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        int screenHeight = displaymetrics.heightPixels;
        int screenWidth = displaymetrics.widthPixels;

        return useCustomBanner(activity, screenHeight, screenWidth, topBannerImageView);
    }

    public static boolean useCustomBanner(Context context, int screenHeight, int screenWidth, @NonNull ImageView topBannerImageView) {
        CommCareApp app = CommCareApplication._().getCurrentApp();
        if (app == null) {
            return false;
        }

        String customBannerURI = app.getAppPreferences().getString(CommCarePreferences.BRAND_BANNER_HOME, "");
        if (!"".equals(customBannerURI)) {
            int maxBannerHeight = screenHeight / 4;

            Bitmap bitmap = MediaUtil.inflateDisplayImage(context, customBannerURI, screenWidth, maxBannerHeight);
            if (bitmap != null) {
                topBannerImageView.setMaxHeight(maxBannerHeight);
                topBannerImageView.setImageBitmap(bitmap);
                return true;
            }
        }
        return false;
    }
}
