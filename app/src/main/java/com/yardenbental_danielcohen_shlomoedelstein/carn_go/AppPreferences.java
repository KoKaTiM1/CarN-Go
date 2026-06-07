package com.yardenbental_danielcohen_shlomoedelstein.carn_go;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppPreferences {

    private static final String PREFS_NAME = "car_n_go_prefs";
    private static final String KEY_SEARCH_RADIUS_KM = "search_radius_km";

    private AppPreferences() {
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static int getSearchRadiusKm(Context context) {
        return getPrefs(context).getInt(KEY_SEARCH_RADIUS_KM, 0);
    }

    public static void setSearchRadiusKm(Context context, int radiusKm) {
        getPrefs(context).edit().putInt(KEY_SEARCH_RADIUS_KM, radiusKm).apply();
    }
}
