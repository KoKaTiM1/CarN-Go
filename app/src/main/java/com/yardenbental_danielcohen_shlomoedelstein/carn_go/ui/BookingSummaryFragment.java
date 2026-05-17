package com.yardenbental_danielcohen_shlomoedelstein.carn_go.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.CompositeDateValidator;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.R;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.firebase.FirestoreHelper;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.model.Booking;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.model.Car;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import com.google.firebase.firestore.QueryDocumentSnapshot;

/**
 * Fragment that displays a summary of the booking for a selected car.
 */
public class BookingSummaryFragment extends Fragment {

    private long selectedStartTimestamp = 0;
    private long selectedEndTimestamp = 0;
    private double totalPrice = 0;
    private List<Booking> existingBookings = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_booking_summary, container, false);

        // Retrieve the Car object passed from the previous fragment
        Car car = (Car) getArguments().getSerializable("car");
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
                    Navigation.findNavController(view).navigateUp();
                });
            }

            // Handle the confirm booking button click
            view.findViewById(R.id.btnConfirmBooking).setOnClickListener(v -> {
                if (selectedStartTimestamp == 0 || selectedEndTimestamp == 0) {
                    Toast.makeText(getContext(), "Please select a booking period", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (selectedEndTimestamp <= selectedStartTimestamp) {
                    Toast.makeText(getContext(), "End time must be after start time", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // Check if within car's availability
                if (selectedStartTimestamp < car.getAvailableFrom() || selectedEndTimestamp > car.getAvailableTo()) {
                    Toast.makeText(getContext(), "Booking period is outside car's availability", Toast.LENGTH_LONG).show();
                    return;
                }

                checkOverlapAndConfirm(car);
            });
        }

        return view;
    }

    private void fetchBookingsAndSuggestSlot(Car car, TextView tvDisplay, TextView tvTotal, TextView tvTotalLabel) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("bookings")
                .whereEqualTo("carId", car.getId())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    existingBookings.clear();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Booking b = doc.toObject(Booking.class);
                        existingBookings.add(b);
                    }
                    
                    // Sort bookings by start time
                    Collections.sort(existingBookings, (b1, b2) -> Long.compare(b1.getStartTime(), b2.getStartTime()));
                    
                    suggestFirstAvailableSlot(car, tvDisplay, tvTotal, tvTotalLabel);
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

        // MaterialDatePicker works exclusively with UTC for its internal logic.
        Calendar calUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calUtc.set(Calendar.HOUR_OF_DAY, 0);
        calUtc.set(Calendar.MINUTE, 0);
        calUtc.set(Calendar.SECOND, 0);
        calUtc.set(Calendar.MILLISECOND, 0);
        long startOfTodayUtc = calUtc.getTimeInMillis();

        // 1. SET ABSOLUTE BOUNDARIES (The Car's Availability)
        // We want the user to see the full range the car is available,
        // regardless of whether they are picking start or end.
        long minDate = Math.max(car.getAvailableFrom(), startOfTodayUtc);
        long maxDate = car.getAvailableTo();

        // 2. ALIGN VALIDATORS TO MIDNIGHT UTC
        calUtc.setTimeInMillis(minDate);
        calUtc.set(Calendar.HOUR_OF_DAY, 0);
        calUtc.set(Calendar.MINUTE, 0);
        calUtc.set(Calendar.SECOND, 0);
        calUtc.set(Calendar.MILLISECOND, 0);
        long validatorMin = calUtc.getTimeInMillis();

        calUtc.setTimeInMillis(maxDate);
        calUtc.set(Calendar.HOUR_OF_DAY, 0);
        calUtc.set(Calendar.MINUTE, 0);
        calUtc.set(Calendar.SECOND, 0);
        calUtc.set(Calendar.MILLISECOND, 0);
        long validatorMax = calUtc.getTimeInMillis();

        // 3. CONFIGURE CALENDAR CONSTRAINTS
        CalendarConstraints.Builder constraintsBuilder = new CalendarConstraints.Builder();
        constraintsBuilder.setStart(validatorMin);
        constraintsBuilder.setEnd(validatorMax);

        List<CalendarConstraints.DateValidator> validators = new ArrayList<>();
        validators.add(DateValidatorPointForward.from(validatorMin));
        validators.add(DateValidatorPointBackward.before(validatorMax));

        constraintsBuilder.setValidator(CompositeDateValidator.allOf(validators));

        // 4. SET INITIAL SELECTION
        long currentSelection = isStart ? selectedStartTimestamp : selectedEndTimestamp;
        if (currentSelection == 0) {
            currentSelection = (isStart) ? minDate : Math.min(maxDate, minDate + TimeUnit.DAYS.toMillis(1));
        }

        // Ensure selection is within bounds
        if (currentSelection < validatorMin) currentSelection = validatorMin;
        if (currentSelection > validatorMax) currentSelection = validatorMax;

        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select " + (isStart ? "Start" : "End") + " Date")
                .setSelection(currentSelection)
                .setCalendarConstraints(constraintsBuilder.build())
                .build();

        datePicker.addOnPositiveButtonClickListener(selectedDate -> {
            // Proceed to Time Picker
            showTimePicker(selectedDate, isStart, tvDisplay, tvTotal, tvTotalLabel, car, now);
        });
        datePicker.show(getParentFragmentManager(), "DATE_PICKER");
    }

    // Extracted Time Picker logic for clarity
    private void showTimePicker(long selectedDate, boolean isStart, TextView tvDisplay, TextView tvTotal, TextView tvTotalLabel, Car car, long now) {
        Calendar c = Calendar.getInstance();
        long baseTimestamp = isStart ? selectedStartTimestamp : selectedEndTimestamp;
        if (baseTimestamp == 0) baseTimestamp = selectedDate;
        c.setTimeInMillis(baseTimestamp);

        MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(c.get(Calendar.HOUR_OF_DAY))
                .setMinute(c.get(Calendar.MINUTE))
                .setTitleText("Select " + (isStart ? "Start" : "End") + " Time")
                .build();

        timePicker.addOnPositiveButtonClickListener(v -> {
            Calendar utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            utcCal.setTimeInMillis(selectedDate);

            Calendar localCal = Calendar.getInstance();
            localCal.set(utcCal.get(Calendar.YEAR), utcCal.get(Calendar.MONTH), utcCal.get(Calendar.DAY_OF_MONTH),
                    timePicker.getHour(), timePicker.getMinute(), 0);
            localCal.set(Calendar.MILLISECOND, 0);

            long newTimestamp = localCal.getTimeInMillis();

            if (isStart) {
                if (newTimestamp < now) {
                    Toast.makeText(getContext(), "Start time cannot be in the past", Toast.LENGTH_SHORT).show();
                    return;
                }
                selectedStartTimestamp = newTimestamp;
                // Auto-adjust end time if it's now before the start
                if (selectedEndTimestamp != 0 && selectedEndTimestamp <= selectedStartTimestamp) {
                    selectedEndTimestamp = selectedStartTimestamp + TimeUnit.HOURS.toMillis(1);
                }
            } else {
                if (selectedStartTimestamp != 0 && newTimestamp <= selectedStartTimestamp) {
                    Toast.makeText(getContext(), "End time must be after start time", Toast.LENGTH_SHORT).show();
                    return;
                }
                selectedEndTimestamp = newTimestamp;
            }
            updateBookingSummary(tvDisplay, tvTotal, tvTotalLabel, car);
        });
        timePicker.show(getParentFragmentManager(), "TIME_PICKER");
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
        
        // We need to check if any existing booking for this car overlaps with our selected window.
        // Overlap condition: (StartA < EndB) AND (EndA > StartB)
        db.collection("bookings")
                .whereEqualTo("carId", car.getId())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    boolean hasOverlap = false;
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : queryDocumentSnapshots) {
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
                        Toast.makeText(getContext(), "Overlaps with another booking. Auto-adjusting to next available slot...", Toast.LENGTH_LONG).show();
                        suggestFirstAvailableSlot(car, getView().findViewById(R.id.tvSelectedBookingPeriod), 
                                                 getView().findViewById(R.id.tvSummaryTotal), 
                                                 getView().findViewById(R.id.tvTotalLabel));
                    } else {
                        confirmBooking(car);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Error checking availability: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void confirmBooking(Car car) {
        String userId = FirestoreHelper.getCurrentUserId(getContext());
        if (userId == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        Map<String, Object> bookingData = new HashMap<>();
        bookingData.put("carId", car.getId());
        bookingData.put("userId", userId);
        bookingData.put("carName", car.getName());
        bookingData.put("carImageUrl", car.getImageUrl());
        bookingData.put("startTime", selectedStartTimestamp);
        bookingData.put("endTime", selectedEndTimestamp);
        bookingData.put("totalCost", totalPrice);
        bookingData.put("timestamp", System.currentTimeMillis());

        db.collection("bookings").add(bookingData)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(getContext(), "Booking Confirmed!", Toast.LENGTH_SHORT).show();
                    
                    NavController navController = Navigation.findNavController(requireView());
                    // Pop everything in the Explore tab back to the car list
                    navController.popBackStack(R.id.browseCarsFragment, false);
                    
                    // Switch the active tab to "My Bookings"
                    if (getActivity() != null) {
                        BottomNavigationView bottomNav = getActivity().findViewById(R.id.bottom_navigation);
                        if (bottomNav != null) {
                            bottomNav.setSelectedItemId(R.id.myBookingsFragment);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Booking failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}