package com.yardenbental_danielcohen_shlomoedelstein.carn_go.notifications;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationCompat;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.R;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.App;

public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String carName = intent.getStringExtra("carName");
        String type = intent.getStringExtra("type"); // "START" or "END"

        String title, body;
        if ("END".equals(type)) {
            title = "Rental Ending Soon";
            body = "Your rental for " + carName + " ends in 30 minutes. Please remember to photograph the car upon return!";
        } else {
            title = "Rental Starting Soon";
            body = "Your rental for " + carName + " starts in 30 minutes. Don't forget to photograph the car when you pick it up!";
        }

        showNotification(context, title, body);
    }

    private void showNotification(Context context, String title, String body) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, App.CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        if (nm != null) {
            nm.notify((int) System.currentTimeMillis(), builder.build());
        }
    }
}
