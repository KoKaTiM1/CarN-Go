package com.yardenbental_danielcohen_shlomoedelstein.carn_go.sync;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.util.concurrent.TimeUnit;

public final class BookingSyncScheduler {

    public static final String ACTION_SYNC_COMPLETED = "com.yardenbental_danielcohen_shlomoedelstein.carn_go.ACTION_SYNC_COMPLETED";
    public static final String EXTRA_SYNC_REASON = "sync_reason";
    public static final long SYNC_INTERVAL_MS = TimeUnit.MINUTES.toMillis(10);

    private static final int SYNC_REQUEST_CODE = 10701;

    private BookingSyncScheduler() {
    }

    public static void requestImmediateSync(Context context, String reason) {
        if (context == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        Intent intent = new Intent(appContext, BookingSyncService.class);
        intent.putExtra(EXTRA_SYNC_REASON, reason);
        try {
            ContextCompat.startForegroundService(appContext, intent);
        } catch (Exception e) {
            Log.e("BookingSyncScheduler", "Failed to start sync service", e);
        }
        scheduleNextSync(appContext);
    }

    public static void scheduleNextSync(Context context) {
        if (context == null) {
            return;
        }
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        long triggerAt = System.currentTimeMillis() + SYNC_INTERVAL_MS;
        alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                getSyncPendingIntent(context)
        );
    }

    public static void notifySyncCompleted(Context context) {
        if (context == null) {
            return;
        }
        context.getApplicationContext().sendBroadcast(new Intent(ACTION_SYNC_COMPLETED));
    }

    private static PendingIntent getSyncPendingIntent(Context context) {
        Intent intent = new Intent(context, BookingSyncReceiver.class);
        intent.setAction("com.yardenbental_danielcohen_shlomoedelstein.carn_go.ACTION_RUN_SYNC");
        return PendingIntent.getBroadcast(
                context,
                SYNC_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
