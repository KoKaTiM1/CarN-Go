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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.R;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.adapter.BookingAdapter;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.firebase.FirestoreHelper;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.model.Booking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    private void updateBookingStatus(Booking booking, String newStatus) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("bookings").document(booking.getId())
                .update("status", newStatus)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "Booking " + newStatus, Toast.LENGTH_SHORT).show();
                    booking.setStatus(newStatus);
                    adapter.notifyDataSetChanged();
                    
                    if ("APPROVED".equals(newStatus)) {
                        notifyRenter(booking);
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void notifyRenter(Booking booking) {
        String renterId = booking.getUserId();
        Context context = getContext();
        if (context == null) return;
        Context appContext = context.getApplicationContext();

        FirestoreHelper.getUserToken(renterId).addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                String token = documentSnapshot.getString("token");
                if (token != null) {
                    showLocalNotification(appContext, "Booking Approved!", 
                        "Your booking for " + booking.getCarName() + " has been approved by the owner.");
                }
            }
        });
    }

    private void showLocalNotification(Context context, String title, String body) {
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
