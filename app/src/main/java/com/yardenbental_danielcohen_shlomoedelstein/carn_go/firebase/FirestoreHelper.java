package com.yardenbental_danielcohen_shlomoedelstein.carn_go.firebase;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Helper class for managing Firestore operations related to user data and tokens.
 */
public class FirestoreHelper {
    private static final String PREFS_NAME = "carN-go_prefs";
    private static final String KEY_USER_ID = "user_id";

    /**
     * Updates the FCM registration token for the current user in Firestore.
     * If a unique user ID does not exist in SharedPreferences, one is generated and stored.
     *
     * @param context The application context.
     * @param token   The new FCM registration token.
     */
    public static void updateUserToken(Context context, String token) {
        // 1. Initialize SharedPreferences safely to retrieve or store the user ID
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // 2. Get or Create a Unique User ID to identify this device/installation
        String userID = prefs.getString(KEY_USER_ID, null);
        if (userID == null) {
            userID = UUID.randomUUID().toString();
            prefs.edit().putString(KEY_USER_ID, userID).apply();
        }

        // 3. Prepare the data to be saved to Firestore
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("lastUpdated", com.google.firebase.Timestamp.now());

        // 4. Save the token and timestamp to the "users" collection in Firestore
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(userID)
                .set(data);
    }
}