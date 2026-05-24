package com.yardenbental_danielcohen_shlomoedelstein.carn_go.ui;

import android.app.NotificationManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.R;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.adapter.BookingAdapter;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.firebase.FirestoreHelper;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.model.Booking;

import org.json.JSONObject;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class MyBookingsFragment extends Fragment implements BookingAdapter.OnBookingActionListener {

    private RecyclerView rvBookings;
    private BookingAdapter adapter;
    private List<Booking> bookingList;
    private LinearLayout layoutNoBookings;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_my_bookings, container, false);

        rvBookings = view.findViewById(R.id.rvMyBookings);
        layoutNoBookings = view.findViewById(R.id.layoutNoBookings);
        
        String userId = FirestoreHelper.getCurrentUserId(getContext());
        rvBookings.setLayoutManager(new LinearLayoutManager(getContext()));
        bookingList = new ArrayList<>();
        adapter = new BookingAdapter(bookingList, userId, this);
        rvBookings.setAdapter(adapter);

        loadBookings();

        return view;
    }

    private void loadBookings() {
        String userId = FirestoreHelper.getCurrentUserId(getContext());
        if (userId == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        // Load bookings where I am the Renter
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
                    
                    // Also load bookings where I am the Owner (Requests for my cars)
                    db.collection("bookings")
                            .whereEqualTo("ownerId", userId)
                            .get()
                            .addOnSuccessListener(requests -> {
                                for (com.google.firebase.firestore.QueryDocumentSnapshot doc : requests) {
                                    Booking b = doc.toObject(Booking.class);
                                    b.setId(doc.getId());
                                    // Avoid duplicates
                                    boolean exists = false;
                                    for (Booking existing : bookingList) {
                                        if (existing.getId().equals(b.getId())) { exists = true; break; }
                                    }
                                    if (!exists) bookingList.add(b);
                                }
                                
                                Collections.sort(bookingList, (b1, b2) -> Long.compare(b2.getTimestamp(), b1.getTimestamp()));
                                updateUI();
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Error loading bookings", Toast.LENGTH_SHORT).show();
                });
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
        Bundle bundle = new Bundle();
        bundle.putSerializable("booking", booking);
        if (getView() != null) {
            Navigation.findNavController(getView())
                    .navigate(R.id.action_myBookingsFragment_to_rentalPickupFragment, bundle);
        }
    }

    @Override
    public void onFinish(Booking booking) {
        Bundle bundle = new Bundle();
        bundle.putSerializable("booking", booking);
        if (getView() != null) {
            Navigation.findNavController(getView())
                    .navigate(R.id.action_myBookingsFragment_to_rentalCompletionFragment, bundle);
        }
    }

    @Override
    public void onViewPhotos(Booking booking) {
        Bundle bundle = new Bundle();
        bundle.putSerializable("booking", booking);
        if (getView() != null) {
            Navigation.findNavController(getView())
                    .navigate(R.id.action_myBookingsFragment_to_viewPhotosFragment, bundle);
        }
    }

    private void updateBookingStatus(Booking booking, String newStatus) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("bookings").document(booking.getId())
                .update("status", newStatus)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "Booking " + newStatus, Toast.LENGTH_SHORT).show();
                    booking.setStatus(newStatus);
                    adapter.notifyDataSetChanged();
                    
                    notifyRenter(booking, newStatus);
                })
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void notifyRenter(Booking booking, String status) {
        String renterId = booking.getUserId();
        String currentUserId = FirestoreHelper.getCurrentUserId(getContext());
        Context context = getContext();
        if (context == null) return;
        Context appContext = context.getApplicationContext();

        String title = status.equals("APPROVED") ? "[TEST] Alert for Renter: Approved" : "[TEST] Alert for Renter: Rejected";
        String message = "Your booking for " + booking.getCarName() + " has been " + status.toLowerCase() + " by the owner.";

        FirestoreHelper.getUserToken(renterId).addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                String token = documentSnapshot.getString("token");
                if (token != null) {
                    // Only show alert if I am the Renter (for single-device testing)
                    if (currentUserId != null && currentUserId.equals(renterId)) {
                        showLocalNotification(appContext, title, message);
                    } else {
                        sendFCMNotification(appContext, token, title, message);                    }
                }
            }
        });
    }

    private void sendFCMNotification(Context context, String targetToken, String title, String body) {
        new Thread(() -> {
            try {
                // 1. Get Access Token using the Service Account JSON
                InputStream inputStream = context.getResources().openRawResource(R.raw.service_account);
                GoogleCredentials credentials = GoogleCredentials.fromStream(inputStream)
                        .createScoped(Collections.singletonList("https://www.googleapis.com/auth/cloud-platform"));
                credentials.refreshIfExpired();
                String accessToken = credentials.getAccessToken().getTokenValue();

                // 2. Build the V1 JSON Structure
                JSONObject message = new JSONObject();
                JSONObject notification = new JSONObject();
                notification.put("title", title);
                notification.put("body", body);

                message.put("token", targetToken);
                message.put("notification", notification);

                JSONObject root = new JSONObject();
                root.put("message", message);

                // 3. Send the Request
                OkHttpClient client = new OkHttpClient();
                RequestBody requestBody = RequestBody.create(
                        root.toString(),
                        MediaType.parse("application/json; charset=utf-8")
                );

                // Using project ID 'carn-go'
                String url = "https://fcm.googleapis.com/v1/projects/carn-go/messages:send";

                Request request = new Request.Builder()
                        .url(url)
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
