package com.yardenbental_danielcohen_shlomoedelstein.carn_go.firebase;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
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
     *
     * @param context The application context.
     * @param token   The new FCM registration token.
     */
    public static void updateUserToken(Context context, String token) {
        String userID = getCurrentUserId(context);

        // Prepare the data
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("lastUpdated", com.google.firebase.Timestamp.now());

        // Save to Firestore
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(userID)
                .set(data, com.google.firebase.firestore.SetOptions.merge());
    }

    /**
     * Fetches the FCM token for a specific user.
     *
     * @param userId The ID of the user whose token to fetch.
     * @return A Task that resolves to the token string.
     */
    public static Task<DocumentSnapshot> getUserToken(String userId) {
        return FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .get();
    }

    /**
     * Checks if the current user is authenticated with Firebase.
     * @return true if the user is signed in (Anonymously or via Email/Google).
     */
    public static boolean isUserAuthenticated() {
        return FirebaseAuth.getInstance().getCurrentUser() != null;
    }

    /**
     * Returns the current unique identifier for the user.
     * Priorities: Firebase Auth UID > SharedPreferences ID (prefixed with "local_").
     *
     * @param context The application context.
     * @return A unique identifier for the user.
     */
    public static String getCurrentUserId(Context context) {
        // 1. Try to get the Firebase Auth UID (Preferred)
        String userID = FirebaseAuth.getInstance().getUid();

        // 2. Fallback to SharedPreferences only if not signed in
        if (userID == null && context != null) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            userID = prefs.getString(KEY_USER_ID, null);

            if (userID == null) {
                // We add "local_" so you can easily identify unregistered users in your database
                userID = "local_" + UUID.randomUUID().toString();
                prefs.edit().putString(KEY_USER_ID, userID).apply();
            }
        }
        return userID;
    }
}