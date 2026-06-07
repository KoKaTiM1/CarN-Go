package com.yardenbental_danielcohen_shlomoedelstein.carn_go.sync;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class BookingSyncService extends Service {

    private static final AtomicBoolean IS_RUNNING = new AtomicBoolean(false);

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        BookingSyncScheduler.scheduleNextSync(getApplicationContext());

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
                IS_RUNNING.set(false);
                stopSelf(startId);
            }
        });

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
