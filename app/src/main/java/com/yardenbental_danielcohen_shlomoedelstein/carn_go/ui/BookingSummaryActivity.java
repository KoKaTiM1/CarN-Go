package com.yardenbental_danielcohen_shlomoedelstein.carn_go.ui;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import com.google.firebase.firestore.FirebaseFirestore;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.notifications.ReminderReceiver;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.notifications.ReminderScheduler;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.R;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.firebase.FirestoreHelper;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.model.Booking;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.model.Car;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.sync.BookingStatus;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.sync.BookingSyncScheduler;
import android.app.NotificationManager;
import android.content.Context;
import androidx.core.app.NotificationCompat;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import org.json.JSONObject;

/**
 * Activity that displays a summary of the booking for a selected car.
 */
public class BookingSummaryActivity extends BaseNavigationActivity {

    private long selectedStartTimestamp = 0;
    private long selectedEndTimestamp = 0;
    private double totalPrice = 0;
    private List<Booking> existingBookings = new ArrayList<>();
    private View contentView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.app_name);
        setScreenContent(R.layout.fragment_booking_summary, 0, false, true);
        View view = findViewById(android.R.id.content);
        contentView = view;

        Car car = (Car) getIntent().getSerializableExtra("car");
        if (car != null) {
            TextView tvName = view.findViewById(R.id.tvSummaryCarName);
            TextView tvPrice = view.findViewById(R.id.tvSummaryPrice);
            TextView tvAvailability = view.findViewById(R.id.tvCarAvailability);
            TextView tvTotal = view.findViewById(R.id.tvSummaryTotal);
            TextView tvTotalLabel = view.findViewById(R.id.tvTotalLabel);
            TextView tvSelectedPeriod = view.findViewById(R.id.tvSelectedBookingPeriod);
            Button btnPickStart = view.findViewById(R.id.btnBookPickStart);
            Button btnPickEnd = view.findViewById(R.id.btnBookPickEnd);

            // Populate the UI with car details
            tvName.setText(car.getName());
            tvPrice.setText("$" + (int)car.getPricePerHour() + " / hour");
            
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
            String availFrom = sdf.format(new Date(car.getAvailableFrom()));
            String availTo = sdf.format(new Date(car.getAvailableTo()));
            tvAvailability.setText("Available: " + availFrom + " - " + availTo);

            // Fetch existing bookings first to determine the first available slot
            fetchBookingsAndSuggestSlot(car, tvSelectedPeriod, tvTotal, tvTotalLabel);

            btnPickStart.setOnClickListener(v -> pickDateTime(true, tvSelectedPeriod, tvTotal, tvTotalLabel, car));
            btnPickEnd.setOnClickListener(v -> pickDateTime(false, tvSelectedPeriod, tvTotal, tvTotalLabel, car));

            // Handle back navigation from the toolbar
            Toolbar toolbar = view.findViewById(R.id.toolbarSummary);
            if (toolbar != null) {
                toolbar.setNavigationOnClickListener(v -> {
                    finish();
                });
            }

            // Handle the confirm booking button click
            view.findViewById(R.id.btnConfirmBooking).setOnClickListener(v -> {
                if (selectedStartTimestamp == 0 || selectedEndTimestamp == 0) {
                    Toast.makeText(BookingSummaryActivity.this, "Please select a booking period", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (selectedEndTimestamp <= selectedStartTimestamp) {
                    Toast.makeText(BookingSummaryActivity.this, "End time must be after start time", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // Check if within car's availability
                if (selectedStartTimestamp < car.getAvailableFrom() || selectedEndTimestamp > car.getAvailableTo()) {
                    Toast.makeText(BookingSummaryActivity.this, "Booking period is outside car's availability", Toast.LENGTH_LONG).show();
                    return;
                }

                checkOverlapAndConfirm(car);
            });
        }

    }

    private void fetchBookingsAndSuggestSlot(Car car, TextView tvDisplay, TextView tvTotal, TextView tvTotalLabel) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        // Optimized: only fetch bookings that end after 'now'
        db.collection("bookings")
                .whereEqualTo("carId", car.getId())
                .whereGreaterThan("endTime", System.currentTimeMillis())
                .orderBy("endTime")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    existingBookings.clear();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Booking b = doc.toObject(Booking.class);
                        // Ignore rejected bookings when calculating available slots
                        if (BookingStatus.blocksAvailability(b.getStatus())) {
                            existingBookings.add(b);
                        }
                    }
                    
                    // Sort bookings by start time (though Firestore might have helped if we had multiple orderBy)
                    Collections.sort(existingBookings, (b1, b2) -> Long.compare(b1.getStartTime(), b2.getStartTime()));
                    
                    suggestFirstAvailableSlot(car, tvDisplay, tvTotal, tvTotalLabel);
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("BookingSummary", "Error fetching bookings (check for missing index): " + e.getMessage());
                    // Fallback to non-optimized query if index is missing (optional, but good for UX)
                    db.collection("bookings")
                            .whereEqualTo("carId", car.getId())
                            .get()
                            .addOnSuccessListener(snapshots -> {
                                existingBookings.clear();
                                for (QueryDocumentSnapshot doc : snapshots) {
                                    Booking b = doc.toObject(Booking.class);
                                    if (BookingStatus.blocksAvailability(b.getStatus())) {
                                        existingBookings.add(b);
                                    }
                                }
                                Collections.sort(existingBookings, (b1, b2) -> Long.compare(b1.getStartTime(), b2.getStartTime()));
                                suggestFirstAvailableSlot(car, tvDisplay, tvTotal, tvTotalLabel);
                            });
                });
    }

    private void suggestFirstAvailableSlot(Car car, TextView tvDisplay, TextView tvTotal, TextView tvTotalLabel) {
        long now = System.currentTimeMillis();
        long currentPotentialStart = Math.max(car.getAvailableFrom(), now);
        
        // Find the first gap of at least 1 hour
        long suggestedStart = currentPotentialStart;
        for (Booking b : existingBookings) {
            if (suggestedStart + TimeUnit.HOURS.toMillis(1) <= b.getStartTime()) {
                // Found a gap before this booking
                break;
            }
            if (b.getEndTime() > suggestedStart) {
                suggestedStart = b.getEndTime();
            }
        }
        
        // Ensure suggested start is still within overall availability
        if (suggestedStart < car.getAvailableTo()) {
            selectedStartTimestamp = suggestedStart;
            // Suggest a 2-hour window or until the next booking
            long nextBookingStart = car.getAvailableTo();
            for (Booking b : existingBookings) {
                if (b.getStartTime() > selectedStartTimestamp) {
                    nextBookingStart = b.getStartTime();
                    break;
                }
            }
            selectedEndTimestamp = Math.min(nextBookingStart, selectedStartTimestamp + TimeUnit.HOURS.toMillis(2));
        } else {
            // No slots available
            selectedStartTimestamp = 0;
            selectedEndTimestamp = 0;
        }
        
        updateBookingSummary(tvDisplay, tvTotal, tvTotalLabel, car);
    }

    private void pickDateTime(boolean isStart, TextView tvDisplay, TextView tvTotal, TextView tvTotalLabel, Car car) {
        long now = System.currentTimeMillis();
        long minDate = Math.max(car.getAvailableFrom(), startOfDay(now));
        long maxDate = car.getAvailableTo();

        long currentSelection = isStart ? selectedStartTimestamp : selectedEndTimestamp;
        if (currentSelection == 0) {
            currentSelection = (isStart) ? minDate : Math.min(maxDate, minDate + TimeUnit.DAYS.toMillis(1));
        }
        currentSelection = Math.max(minDate, Math.min(currentSelection, maxDate));

        Calendar initialDate = Calendar.getInstance();
        initialDate.setTimeInMillis(currentSelection);
        DatePickerDialog datePicker = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    Calendar selectedDate = Calendar.getInstance();
                    selectedDate.set(year, month, dayOfMonth, 0, 0, 0);
                    selectedDate.set(Calendar.MILLISECOND, 0);
                    showTimePicker(selectedDate.getTimeInMillis(), isStart, tvDisplay, tvTotal, tvTotalLabel, car, now);
                },
                initialDate.get(Calendar.YEAR),
                initialDate.get(Calendar.MONTH),
                initialDate.get(Calendar.DAY_OF_MONTH)
        );
        datePicker.setTitle("Select " + (isStart ? "Start" : "End") + " Date");
        datePicker.getDatePicker().setMinDate(startOfDay(minDate));
        datePicker.getDatePicker().setMaxDate(startOfDay(maxDate));
        datePicker.show();
    }

    private void showTimePicker(long selectedDate, boolean isStart, TextView tvDisplay, TextView tvTotal, TextView tvTotalLabel, Car car, long now) {
        Calendar c = Calendar.getInstance();
        long baseTimestamp = isStart ? selectedStartTimestamp : selectedEndTimestamp;
        if (baseTimestamp == 0) baseTimestamp = selectedDate;
        c.setTimeInMillis(baseTimestamp);

        TimePickerDialog timePicker = new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            Calendar utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            utcCal.setTimeInMillis(selectedDate);

            Calendar localCal = Calendar.getInstance();
            localCal.set(utcCal.get(Calendar.YEAR), utcCal.get(Calendar.MONTH), utcCal.get(Calendar.DAY_OF_MONTH),
                    hourOfDay, minute, 0);
            localCal.set(Calendar.MILLISECOND, 0);

            long newTimestamp = localCal.getTimeInMillis();

            if (isStart) {
                long pickerAlignedNow = now - (now % TimeUnit.MINUTES.toMillis(1));
                if (newTimestamp < pickerAlignedNow) {
                    Toast.makeText(BookingSummaryActivity.this, "Start time cannot be in the past", Toast.LENGTH_SHORT).show();
                    return;
                }
                selectedStartTimestamp = newTimestamp;
                // Auto-adjust end time if it's now before the start
                if (selectedEndTimestamp != 0 && selectedEndTimestamp <= selectedStartTimestamp) {
                    selectedEndTimestamp = selectedStartTimestamp + TimeUnit.HOURS.toMillis(1);
                }
            } else {
                if (selectedStartTimestamp != 0 && newTimestamp <= selectedStartTimestamp) {
                    Toast.makeText(BookingSummaryActivity.this, "End time must be after start time", Toast.LENGTH_SHORT).show();
                    return;
                }
                selectedEndTimestamp = newTimestamp;
            }
            updateBookingSummary(tvDisplay, tvTotal, tvTotalLabel, car);
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true);
        timePicker.setTitle("Select " + (isStart ? "Start" : "End") + " Time");
        timePicker.show();
    }

    private long startOfDay(long timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private void updateBookingSummary(TextView tvDisplay, TextView tvTotal, TextView tvTotalLabel, Car car) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
        String startStr = selectedStartTimestamp == 0 ? "..." : sdf.format(new Date(selectedStartTimestamp));
        String endStr = selectedEndTimestamp == 0 ? "..." : sdf.format(new Date(selectedEndTimestamp));
        tvDisplay.setText("Booking: " + startStr + " to " + endStr);

        if (selectedStartTimestamp != 0 && selectedEndTimestamp != 0 && selectedEndTimestamp > selectedStartTimestamp) {
            long diffInMillis = selectedEndTimestamp - selectedStartTimestamp;
            long diffInHours = TimeUnit.MILLISECONDS.toHours(diffInMillis);
            if (diffInMillis % TimeUnit.HOURS.toMillis(1) > 0) {
                diffInHours++; // Round up to nearest hour
            }
            
            totalPrice = diffInHours * car.getPricePerHour();
            tvTotal.setText("$" + String.format("%.2f", totalPrice));
            tvTotalLabel.setText("Total (" + diffInHours + (diffInHours == 1 ? " hour)" : " hours)"));
        } else {
            tvTotal.setText("$0.00");
            tvTotalLabel.setText("Total (0 hours)");
        }
    }

    private void checkOverlapAndConfirm(Car car) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        // Optimized query: only check bookings that end after our selected start time
        db.collection("bookings")
                .whereEqualTo("carId", car.getId())
                .whereGreaterThan("endTime", selectedStartTimestamp)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    boolean hasOverlap = false;
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String status = doc.getString("status");
                        if (!BookingStatus.blocksAvailability(status)) continue;

                        Long existingStart = doc.getLong("startTime");
                        if (existingStart != null) {
                            if (selectedEndTimestamp > existingStart) {
                                hasOverlap = true;
                                break;
                            }
                        }
                    }
                    
                    if (hasOverlap) {
                        handleOverlap(car);
                    } else {
                        confirmBooking(car);
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("BookingSummary", "Check overlap failed (index may be building): " + e.getMessage());
                    // Fallback to simple query if index is missing
                    db.collection("bookings")
                            .whereEqualTo("carId", car.getId())
                            .get()
                            .addOnSuccessListener(snapshots -> {
                                boolean hasOverlap = false;
                                for (com.google.firebase.firestore.QueryDocumentSnapshot doc : snapshots) {
                                    String status = doc.getString("status");
                                    if (!BookingStatus.blocksAvailability(status)) continue;

                                    Long existingStart = doc.getLong("startTime");
                                    Long existingEnd = doc.getLong("endTime");
                                    if (existingStart != null && existingEnd != null) {
                                        if (selectedStartTimestamp < existingEnd && selectedEndTimestamp > existingStart) {
                                            hasOverlap = true;
                                            break;
                                        }
                                    }
                                }
                                if (hasOverlap) {
                                    handleOverlap(car);
                                } else {
                                    confirmBooking(car);
                                }
                            })
                            .addOnFailureListener(e2 -> {
                                Toast.makeText(BookingSummaryActivity.this, "Error checking availability: " + e2.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                });
    }

    private void handleOverlap(Car car) {
        Toast.makeText(BookingSummaryActivity.this, "Overlaps with another booking. Auto-adjusting...", Toast.LENGTH_LONG).show();
        suggestFirstAvailableSlot(car, contentView.findViewById(R.id.tvSelectedBookingPeriod), 
                                 contentView.findViewById(R.id.tvSummaryTotal), 
                                 contentView.findViewById(R.id.tvTotalLabel));
    }

    private void confirmBooking(Car car) {
        String userId = FirestoreHelper.getCurrentUserId(BookingSummaryActivity.this);
        if (userId == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        Map<String, Object> bookingData = new HashMap<>();
        bookingData.put("carId", car.getId());
        bookingData.put("userId", userId);
        bookingData.put("ownerId", car.getOwnerId());
        bookingData.put("carName", car.getName());
        bookingData.put("carImageUrl", car.getImageUrl());
        bookingData.put("startTime", selectedStartTimestamp);
        bookingData.put("endTime", selectedEndTimestamp);
        bookingData.put("totalCost", totalPrice);
        bookingData.put("timestamp", System.currentTimeMillis());
        bookingData.put("status", BookingStatus.PENDING);

        db.collection("bookings").add(bookingData)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(BookingSummaryActivity.this, "Booking Confirmed!", Toast.LENGTH_SHORT).show();

                    Booking createdBooking = new Booking(
                            documentReference.getId(),
                            car.getId(),
                            userId,
                            car.getOwnerId(),
                            car.getName(),
                            car.getImageUrl(),
                            selectedStartTimestamp,
                            selectedEndTimestamp,
                            totalPrice,
                            System.currentTimeMillis(),
                            BookingStatus.PENDING
                    );
                    ReminderScheduler.cancelRentalReminders(BookingSummaryActivity.this, createdBooking.getId());

                    // 2. Trigger local notification for the Renter (Added to bookings)
                    showLocalNotification(getApplicationContext(), "Booking Submitted", 
                            "Your request for " + car.getName() + " has been added to 'My Bookings'. Wait for owner approval!");

                    // 3. Trigger notification to the owner
                    notifyOwner(car);
                    BookingSyncScheduler.requestImmediateSync(BookingSummaryActivity.this, "booking_created");
                    
                    Intent intent = new Intent(BookingSummaryActivity.this, MyBookingsActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(BookingSummaryActivity.this, "Booking failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void notifyOwner(Car car) {
        String ownerId = car.getOwnerId();
        if (ownerId == null || ownerId.isEmpty()) return;

        // Get current user ID to see if we should show the local simulation
        String currentUserId = FirestoreHelper.getCurrentUserId(BookingSummaryActivity.this);

        // Capture application context to avoid null context when fragment is detached
        Context appContext = getApplicationContext();

        FirestoreHelper.getUserToken(ownerId).addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                String token = documentSnapshot.getString("token");
                if (token != null) {
                    android.util.Log.d("BookingSummaryActivity", "Owner FCM Token found: " + token);

                    // Simulation logic: Only show the "Owner Alert" if the current user IS the owner
                    if (currentUserId != null && currentUserId.equals(ownerId)) {
                        showLocalNotification(appContext, "New Booking Request", "Your car " + car.getName() + " has a new booking request!");
                    } else {
                        sendFCMNotification(appContext, token, "New Booking Request", "Your car " + car.getName() + " has a new booking request!");
                    }
                }
            } else {
                android.util.Log.w("BookingSummaryActivity", "No user document found for ownerId: " + ownerId);
            }
        }).addOnFailureListener(e -> {
            android.util.Log.e("BookingSummaryActivity", "Failed to fetch owner token", e);
        });
    }

    private void sendFCMNotification(Context context, String targetToken, String title, String body) {
        new Thread(() -> {
            try {
                // 1. Get Access Token using the Service Account JSON
                InputStream inputStream = context.getResources().openRawResource(R.raw.service_account);
                com.google.auth.oauth2.GoogleCredentials credentials = 
                    com.google.auth.oauth2.GoogleCredentials.fromStream(inputStream)
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

                // Replace 'carn-go' with your actual Project ID if different
                String url = "https://fcm.googleapis.com/v1/projects/carn-go/messages:send";

                Request request = new Request.Builder()
                        .url(url)
                        .post(requestBody)
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .build();

                try (okhttp3.Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        android.util.Log.d("FCM_DIAGNOSTIC", "V1 Success! Response: " + response.body().string());
                    } else {
                        android.util.Log.e("FCM_DIAGNOSTIC", "V1 Failed! Code: " + response.code() + " Body: " + response.body().string());
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("FCM_DIAGNOSTIC", "V1 Error: " + e.getMessage(), e);
            }
        }).start();
    }

    private void showLocalNotification(Context context, String title, String body) {
        if (context == null) {
            android.util.Log.e("BookingSummaryActivity", "Cannot show notification: context is null");
            return;
        }

        android.util.Log.d("BookingSummaryActivity", "Attempting to show notification: " + title);

        NotificationManager notificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, com.yardenbental_danielcohen_shlomoedelstein.carn_go.App.CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_MAX)   // Max priority for heads-up
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setDefaults(NotificationCompat.DEFAULT_ALL)   // Sound, Vibration, Lights
                .setAutoCancel(true);

        if (notificationManager != null) {
            int notificationId = (int) System.currentTimeMillis();
            notificationManager.notify(notificationId, builder.build());
            android.util.Log.d("BookingSummaryActivity", "Notification ID " + notificationId + " sent to system");
        } else {
            android.util.Log.e("BookingSummaryActivity", "NotificationManager is null");
        }
    }
}
