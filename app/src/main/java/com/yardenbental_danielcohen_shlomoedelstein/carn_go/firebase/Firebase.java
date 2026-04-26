package com.yardenbental_danielcohen_shlomoedelstein.carn_go.firebase;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import android.util.Log;

public class Firebase extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        // Handle incoming messages here
        Log.d("FCM", "From: " + remoteMessage.getFrom());
    }

    @Override
    public void onNewToken(String token) {
        Log.d("FCM", "Refreshed token: " + token);
        sendTokenToServer(token);

    }

    public void sendTokenToServer(String token) {

    }

}