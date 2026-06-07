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
    public static final String SYNC_CHANNEL_ID = "booking_sync_channel";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
    }

    /**
     * Creates the notification channel for the application.
     * Since minSdk is 26 (Android 8.0), the check for SDK version is not required.
     */
    private void createNotificationChannels() {
        NotificationChannel generalChannel = new NotificationChannel(
                CHANNEL_ID,
                "General Notifications",
                NotificationManager.IMPORTANCE_HIGH
        );
        generalChannel.setDescription("Used for general app updates and news");

        NotificationChannel syncChannel = new NotificationChannel(
                SYNC_CHANNEL_ID,
                "Booking Sync Service",
                NotificationManager.IMPORTANCE_LOW
        );
        syncChannel.setDescription("Used while background booking and availability sync is running");

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(generalChannel);
            notificationManager.createNotificationChannel(syncChannel);
        }
    }
}
