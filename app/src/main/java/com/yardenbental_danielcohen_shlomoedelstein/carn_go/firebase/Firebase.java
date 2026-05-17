package com.yardenbental_danielcohen_shlomoedelstein.carn_go.firebase;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.App;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.MainActivity;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.R;

public class Firebase extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        // 1. Get the message content
        if (remoteMessage.getNotification() != null) {
            String title = remoteMessage.getNotification().getTitle();
            String body = remoteMessage.getNotification().getBody();
            
            // 2. Manually show it (this makes it pop up in-app)
            sendNotification(title, body);
        }
    }

    private void sendNotification(String title, String messageBody) {
        // Prepare the action when the notification is clicked
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        // Build the notification
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, App.CHANNEL_ID)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(title)
                        .setContentText(messageBody)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH) // Required for pop-up
                        .setContentIntent(pendingIntent);

        // Show the notification
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify((int) System.currentTimeMillis(), notificationBuilder.build());
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        Log.d("FCM", "Token: " + token);
            sendTokenToServer(token);
    }

    /**
     * Updates the user's token in the remote Firestore database.
     */
    public void sendTokenToServer(String token) {
        FirestoreHelper.updateUserToken(this, token);
    }
}
