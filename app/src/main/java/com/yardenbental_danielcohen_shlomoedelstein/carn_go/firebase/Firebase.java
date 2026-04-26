package com.yardenbental_danielcohen_shlomoedelstein.carn_go.firebase;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import android.util.Log;

/**
 * Service to handle Firebase Cloud Messaging (FCM) events.
 * This service receives messages from FCM and handles registration token updates.
 */
public class Firebase extends FirebaseMessagingService {

    /**
     * Called when a message is received from FCM.
     *
     * @param remoteMessage Object representing the message received from Firebase Cloud Messaging.
     */
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.d("FCM", "From: " + remoteMessage.getFrom());
        // Handle notification data or payload here if needed
    }

    /**
     * Called when a new FCM registration token is generated.
     * This occurs on the first app launch or if the token is refreshed by the system.
     *
     * @param token The new registration token.
     */
    @Override
    public void onNewToken(String token) {
        Log.d("FCM", "Refreshed token: " + token);
        // Update the token in Firestore so the server can send notifications to this specific device
        sendTokenToServer(token);
    }

    /**
     * Updates the user's token in the remote Firestore database.
     *
     * @param token The registration token to be saved.
     */
    public void sendTokenToServer(String token) {
        // Use the FirestoreHelper to persist the token
        FirestoreHelper.updateUserToken(this, token);
    }
}