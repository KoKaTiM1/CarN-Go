package com.yardenbental_danielcohen_shlomoedelstein.carn_go.service;

import android.app.NotificationManager;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.auth.oauth2.GoogleCredentials;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.App;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.R;

import org.json.JSONObject;

import java.io.InputStream;
import java.util.Collections;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class AppNotificationService {

    private final OkHttpClient httpClient = new OkHttpClient();

    public void showLocalNotification(@NonNull Context context, @NonNull String title, @NonNull String body) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, App.CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true);

        if (notificationManager != null) {
            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }

    public void sendRemoteNotification(@NonNull Context context,
                                       @NonNull String targetToken,
                                       @NonNull String title,
                                       @NonNull String body) {
        new Thread(() -> {
            try {
                InputStream inputStream = context.getResources().openRawResource(R.raw.service_account);
                GoogleCredentials credentials = GoogleCredentials.fromStream(inputStream)
                        .createScoped(Collections.singletonList("https://www.googleapis.com/auth/cloud-platform"));
                credentials.refreshIfExpired();
                String accessToken = credentials.getAccessToken().getTokenValue();

                JSONObject message = new JSONObject();
                JSONObject notification = new JSONObject();
                notification.put("title", title);
                notification.put("body", body);
                message.put("token", targetToken);
                message.put("notification", notification);

                JSONObject root = new JSONObject();
                root.put("message", message);

                RequestBody requestBody = RequestBody.create(
                        root.toString(),
                        MediaType.parse("application/json; charset=utf-8")
                );

                Request request = new Request.Builder()
                        .url("https://fcm.googleapis.com/v1/projects/carn-go/messages:send")
                        .post(requestBody)
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .build();

                try (okhttp3.Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String bodyText = response.body() != null ? response.body().string() : "";
                        Log.e("AppNotificationSvc", "FCM request failed: " + response.code() + " " + bodyText);
                    }
                }
            } catch (Exception error) {
                Log.e("AppNotificationSvc", "Failed to send remote notification", error);
            }
        }).start();
    }
}
