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

import com.yardenbental_danielcohen_shlomoedelstein.carn_go.R;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.adapter.BookingAdapter;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.data.BookingRepository;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.firebase.FirestoreHelper;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.model.Booking;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.service.AppNotificationService;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.sync.BookingStatus;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.sync.BookingSyncScheduler;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.util.NetworkUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MyBookingsActivity extends BaseNavigationActivity implements BookingAdapter.OnBookingActionListener {

    private final BookingRepository bookingRepository = new BookingRepository();
    private final AppNotificationService notificationService = new AppNotificationService();
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
        if (!NetworkUtils.checkAndToast(this)) return;
        String userId = FirestoreHelper.getCurrentUserId(this);
        if (userId == null) return;

        bookingRepository.fetchBookingsForUserAndOwner(userId, new BookingRepository.BookingsCallback() {
            @Override
            public void onSuccess(List<Booking> bookings) {
                bookingList.clear();
                bookingList.addAll(bookings);
                updateUI();
            }

            @Override
            public void onError(Exception error) {
                Toast.makeText(MyBookingsActivity.this, "Error loading bookings", Toast.LENGTH_SHORT).show();
            }
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
        if (!NetworkUtils.checkAndToast(this)) return;
        bookingRepository.updateStatus(booking.getId(), newStatus)
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
                        notificationService.showLocalNotification(appContext, title, message);
                    } else {
                        notificationService.sendRemoteNotification(appContext, token, title, message);
                    }
                }
            }
        });
    }
}
