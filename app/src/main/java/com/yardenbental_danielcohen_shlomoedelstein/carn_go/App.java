package com.yardenbental_danielcohen_shlomoedelstein.carn_go;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

/**
 * Custom Application class for global initialization.
 */
public class App extends Application {

    public static final String CHANNEL_ID = "fcm_pop_up_channel";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    /**
     * Creates the notification channel for the application.
     * Since minSdk is 26 (Android 8.0), the check for SDK version is not required.
     */
    private void createNotificationChannel() {
        CharSequence name = "General Notifications";
        String description = "Used for general app updates and news";
        int importance = NotificationManager.IMPORTANCE_HIGH;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
        channel.setDescription(description);

        // Register the channel with the system
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }
}
