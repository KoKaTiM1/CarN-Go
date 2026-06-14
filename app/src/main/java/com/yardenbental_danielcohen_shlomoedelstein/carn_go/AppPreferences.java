package com.yardenbental_danielcohen_shlomoedelstein.carn_go;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

public final class AppPreferences {

    private static final String PREFS_NAME = "car_n_go_prefs";
    private static final String KEY_SEARCH_RADIUS_KM = "search_radius_km";
    private static final String KEY_BOOKING_REMINDERS_ENABLED = "booking_reminders_enabled";
    private static final String KEY_DARK_MODE_ENABLED = "dark_mode_enabled";

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

    public static boolean areBookingRemindersEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_BOOKING_REMINDERS_ENABLED, true);
    }

    public static void setBookingRemindersEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_BOOKING_REMINDERS_ENABLED, enabled).apply();
    }

    public static boolean isDarkModeEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_DARK_MODE_ENABLED, false);
    }

    public static void setDarkModeEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_DARK_MODE_ENABLED, enabled).apply();
    }

    public static void applyThemeMode(Context context) {
        AppCompatDelegate.setDefaultNightMode(
                isDarkModeEnabled(context)
                        ? AppCompatDelegate.MODE_NIGHT_YES
                        : AppCompatDelegate.MODE_NIGHT_NO
        );
    }
}
