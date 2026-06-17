package com.yardenbental_danielcohen_shlomoedelstein.carn_go.ui;

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

import com.yardenbental_danielcohen_shlomoedelstein.carn_go.notifications.ReminderScheduler;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.R;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.data.BookingRepository;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.firebase.FirestoreHelper;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.model.Booking;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.model.Car;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.service.AppNotificationService;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.service.BookingService;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.sync.BookingStatus;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.sync.BookingSyncScheduler;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.util.NetworkUtils;
import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Activity that displays a summary of the booking for a selected car.
 */
public class BookingSummaryActivity extends BaseNavigationActivity {

    private final BookingRepository bookingRepository = new BookingRepository();
    private final BookingService bookingService = new BookingService();
    private final AppNotificationService notificationService = new AppNotificationService();
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
        String currentUserId = FirestoreHelper.getCurrentUserId(this);
        if (bookingService.isOwnerBookingOwnCar(currentUserId, car)) {
            Toast.makeText(this, "You cannot book your own car", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
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
                if (!bookingService.isWithinAvailability(car, selectedStartTimestamp, selectedEndTimestamp)) {
                    Toast.makeText(BookingSummaryActivity.this, "Booking period is outside car's availability", Toast.LENGTH_LONG).show();
                    return;
                }

                checkOverlapAndConfirm(car);
            });
        }

    }

    private void fetchBookingsAndSuggestSlot(Car car, TextView tvDisplay, TextView tvTotal, TextView tvTotalLabel) {
        if (!NetworkUtils.checkAndToast(this)) return;
        bookingRepository.fetchActiveBookingsForCar(car.getId(), new BookingRepository.BookingsCallback() {
            @Override
            public void onSuccess(List<Booking> bookings) {
                existingBookings.clear();
                existingBookings.addAll(bookings);
                suggestFirstAvailableSlot(car, tvDisplay, tvTotal, tvTotalLabel);
            }

            @Override
            public void onError(Exception error) {
                Toast.makeText(BookingSummaryActivity.this, "Error checking availability", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void suggestFirstAvailableSlot(Car car, TextView tvDisplay, TextView tvTotal, TextView tvTotalLabel) {
        BookingService.SuggestedSlot slot = bookingService.suggestFirstAvailableSlot(car, existingBookings, System.currentTimeMillis());
        selectedStartTimestamp = slot.startTime;
        selectedEndTimestamp = slot.endTime;
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

        if (bookingService.isValidBookingWindow(selectedStartTimestamp, selectedEndTimestamp)) {
            long diffInHours = bookingService.roundDurationHours(selectedStartTimestamp, selectedEndTimestamp);
            totalPrice = bookingService.calculateTotalPrice(car, selectedStartTimestamp, selectedEndTimestamp);
            tvTotal.setText("$" + String.format("%.2f", totalPrice));
            tvTotalLabel.setText("Total (" + diffInHours + (diffInHours == 1 ? " hour)" : " hours)"));
        } else {
            tvTotal.setText("$0.00");
            tvTotalLabel.setText("Total (0 hours)");
        }
    }

    private void checkOverlapAndConfirm(Car car) {
        if (!NetworkUtils.checkAndToast(this)) return;
        bookingRepository.hasBlockingOverlap(car.getId(), selectedStartTimestamp, selectedEndTimestamp, new BookingRepository.OverlapCheckCallback() {
            @Override
            public void onResult(boolean hasOverlap) {
                if (hasOverlap) {
                    handleOverlap(car);
                } else {
                    confirmBooking(car);
                }
            }

            @Override
            public void onError(Exception error) {
                Toast.makeText(BookingSummaryActivity.this, "Error checking availability: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
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
        if (bookingService.isOwnerBookingOwnCar(userId, car)) {
            Toast.makeText(BookingSummaryActivity.this, "You cannot book your own car", Toast.LENGTH_SHORT).show();
            return;
        }
        
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

        bookingRepository.createBooking(bookingData)
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
                    notificationService.showLocalNotification(getApplicationContext(), "Booking Submitted",
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
                    if (currentUserId != null && currentUserId.equals(ownerId)) {
                        notificationService.showLocalNotification(appContext, "New Booking Request", "Your car " + car.getName() + " has a new booking request!");
                    } else {
                        notificationService.sendRemoteNotification(appContext, token, "New Booking Request", "Your car " + car.getName() + " has a new booking request!");
                    }
                }
            } else {
                android.util.Log.w("BookingSummaryActivity", "No user document found for ownerId: " + ownerId);
            }
        }).addOnFailureListener(e -> {
            android.util.Log.e("BookingSummaryActivity", "Failed to fetch owner token", e);
        });
    }
}
