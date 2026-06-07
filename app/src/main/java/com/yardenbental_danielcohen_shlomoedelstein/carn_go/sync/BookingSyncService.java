package com.yardenbental_danielcohen_shlomoedelstein.carn_go.sync;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.app.Service;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import com.yardenbental_danielcohen_shlomoedelstein.carn_go.App;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.MainActivity;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.R;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class BookingSyncService extends Service {

    private static final int NOTIFICATION_ID = 4207;
    private static final AtomicBoolean IS_RUNNING = new AtomicBoolean(false);

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        BookingSyncScheduler.scheduleNextSync(getApplicationContext());
        startForegroundForSync(intent != null ? intent.getStringExtra(BookingSyncScheduler.EXTRA_SYNC_REASON) : null);

        if (!IS_RUNNING.compareAndSet(false, true)) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        executorService.execute(() -> {
            try {
                BookingSyncCoordinator.runSync(getApplicationContext());
            } catch (Exception e) {
                Log.e("BookingSyncService", "Sync cycle failed", e);
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE);
                IS_RUNNING.set(false);
                stopSelf(startId);
            }
        });

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopForeground(STOP_FOREGROUND_REMOVE);
        executorService.shutdown();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startForegroundForSync(String reason) {
        Intent contentIntent = new Intent(this, MainActivity.class);
        contentIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String contentText = "Syncing bookings and car availability";
        if (reason != null && !reason.trim().isEmpty()) {
            contentText = String.format(Locale.getDefault(), "Syncing bookings and availability (%s)", reason.replace('_', ' '));
        }

        Notification notification = new NotificationCompat.Builder(this, App.SYNC_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("CarN-Go background sync")
                .setContentText(contentText)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent)
                .build();

        ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        );
    }
}
