package com.yardenbental_danielcohen_shlomoedelstein.carn_go.ui;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.firestore.FirebaseFirestore;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.R;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.adapter.BookingAdapter;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.firebase.FirestoreHelper;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.model.Booking;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.sync.BookingStatus;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.sync.BookingSyncScheduler;

import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class MyBookingsActivity extends BaseNavigationActivity implements BookingAdapter.OnBookingActionListener {

    private ListView rvBookings;
    private BookingAdapter adapter;
    private List<Booking> bookingList;
    private LinearLayout layoutNoBookings;

    private final BroadcastReceiver syncReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadBookings();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.app_name);
        setScreenContent(R.layout.fragment_my_bookings, R.id.nav_my_bookings, true, true);
        View view = findViewById(android.R.id.content);

        rvBookings = view.findViewById(R.id.rvMyBookings);
        layoutNoBookings = view.findViewById(R.id.layoutNoBookings);

        String userId = FirestoreHelper.getCurrentUserId(this);
        bookingList = new ArrayList<>();
        adapter = new BookingAdapter(bookingList, userId, this);
        rvBookings.setAdapter(adapter);

        loadBookings();
    }

    @Override
    public void onResume() {
        super.onResume();
        ContextCompat.registerReceiver(
                this,
                syncReceiver,
                new IntentFilter(BookingSyncScheduler.ACTION_SYNC_COMPLETED),
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
        loadBookings();
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            unregisterReceiver(syncReceiver);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void loadBookings() {
        String userId = FirestoreHelper.getCurrentUserId(this);
        if (userId == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("bookings")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(rentals -> {
                    bookingList.clear();
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : rentals) {
                        Booking b = doc.toObject(Booking.class);
                        b.setId(doc.getId());
                        bookingList.add(b);
                    }

                    db.collection("bookings")
                            .whereEqualTo("ownerId", userId)
                            .get()
                            .addOnSuccessListener(requests -> {
                                for (com.google.firebase.firestore.QueryDocumentSnapshot doc : requests) {
                                    Booking b = doc.toObject(Booking.class);
                                    b.setId(doc.getId());
                                    boolean exists = false;
                                    for (Booking existing : bookingList) {
                                        if (existing.getId().equals(b.getId())) {
                                            exists = true;
                                            break;
                                        }
                                    }
                                    if (!exists) bookingList.add(b);
                                }

                                Collections.sort(bookingList, (b1, b2) -> Long.compare(b2.getTimestamp(), b1.getTimestamp()));
                                updateUI();
                            });
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error loading bookings", Toast.LENGTH_SHORT).show());
    }

    private void updateUI() {
        if (bookingList.isEmpty()) {
            layoutNoBookings.setVisibility(View.VISIBLE);
            rvBookings.setVisibility(View.GONE);
        } else {
            layoutNoBookings.setVisibility(View.GONE);
            rvBookings.setVisibility(View.VISIBLE);
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onApprove(Booking booking) {
        updateBookingStatus(booking, "APPROVED");
    }

    @Override
    public void onReject(Booking booking) {
        updateBookingStatus(booking, "REJECTED");
    }

    @Override
    public void onPickupPhoto(Booking booking) {
        Intent intent = new Intent(this, RentalPickupActivity.class);
        intent.putExtra("booking", booking);
        startActivity(intent);
    }

    @Override
    public void onFinish(Booking booking) {
        Intent intent = new Intent(this, RentalCompletionActivity.class);
        intent.putExtra("booking", booking);
        startActivity(intent);
    }

    @Override
    public void onViewPhotos(Booking booking) {
        Intent intent = new Intent(this, ViewPhotosActivity.class);
        intent.putExtra("booking", booking);
        startActivity(intent);
    }

    private void updateBookingStatus(Booking booking, String newStatus) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("bookings").document(booking.getId())
                .update("status", newStatus)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Booking " + newStatus, Toast.LENGTH_SHORT).show();
                    booking.setStatus(newStatus);
                    adapter.notifyDataSetChanged();

                    notifyRenter(booking, newStatus);
                    BookingSyncScheduler.requestImmediateSync(this, "booking_status_" + newStatus.toLowerCase());
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void notifyRenter(Booking booking, String status) {
        String renterId = booking.getUserId();
        String currentUserId = FirestoreHelper.getCurrentUserId(this);
        Context appContext = getApplicationContext();

        String title = BookingStatus.APPROVED.equals(status) ? "Booking Approved" : "Booking Rejected";
        String message = "Your booking for " + booking.getCarName() + " has been " + status.toLowerCase() + " by the owner.";

        FirestoreHelper.getUserToken(renterId).addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                String token = documentSnapshot.getString("token");
                if (token != null) {
                    if (currentUserId != null && currentUserId.equals(renterId)) {
                        showLocalNotification(appContext, title, message);
                    } else {
                        sendFCMNotification(appContext, token, title, message);
                    }
                }
            }
        });
    }

    private void sendFCMNotification(Context context, String targetToken, String title, String body) {
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

                OkHttpClient client = new OkHttpClient();
                RequestBody requestBody = RequestBody.create(
                        root.toString(),
                        MediaType.parse("application/json; charset=utf-8")
                );

                Request request = new Request.Builder()
                        .url("https://fcm.googleapis.com/v1/projects/carn-go/messages:send")
                        .post(requestBody)
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .build();

                try (okhttp3.Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        android.util.Log.d("FCM_DIAGNOSTIC", "MyBookings V1 - Success! Response: " + response.body().string());
                    } else {
                        android.util.Log.e("FCM_DIAGNOSTIC", "MyBookings V1 - Failed! Code: " + response.code() + " Body: " + response.body().string());
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("FCM_DIAGNOSTIC", "MyBookings V1 - Error: " + e.getMessage(), e);
            }
        }).start();
    }

    public void showLocalNotification(Context context, String title, String body) {
        NotificationManager notificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, com.yardenbental_danielcohen_shlomoedelstein.carn_go.App.CHANNEL_ID)
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
}
