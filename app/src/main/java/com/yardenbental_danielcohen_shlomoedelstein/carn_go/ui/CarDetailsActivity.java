package com.yardenbental_danielcohen_shlomoedelstein.carn_go.ui;

import android.os.Bundle;
import android.util.Base64;
import android.content.Intent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import androidx.core.content.ContextCompat;
import androidx.appcompat.widget.Toolbar;

import com.bumptech.glide.Glide;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.R;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.firebase.FirestoreHelper;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.model.Booking;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.model.Car;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Activity that displays detailed information about a specific car.
 */
public class CarDetailsActivity extends BaseNavigationActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.app_name);
        setScreenContent(R.layout.fragment_car_details, 0, false, true);
        View view = findViewById(android.R.id.content);

        Car car = (Car) getIntent().getSerializableExtra("car");
        if (car != null) {
            ImageView ivCarImage = view.findViewById(R.id.ivCarDetailImage);
            TextView tvName = view.findViewById(R.id.tvDetailName);
            TextView tvRating = view.findViewById(R.id.tvDetailRating);
            TextView tvSeats = view.findViewById(R.id.tvDetailSeats);
            TextView tvTransmission = view.findViewById(R.id.tvDetailTransmission);
            TextView tvTag = view.findViewById(R.id.tvDetailTag);
            TextView tvFuelType = view.findViewById(R.id.tvDetailFuelType);
            View fuelTypeDivider = view.findViewById(R.id.tvDetailFuelTypeDivider);
            TextView tvAvailableFrom = view.findViewById(R.id.tvDetailAvailableFrom);
            TextView tvAvailableTo = view.findViewById(R.id.tvDetailAvailableTo);
            TextView tvBusySlotsLabel = view.findViewById(R.id.tvBusySlotsLabel);
            TextView tvBusySlots = view.findViewById(R.id.tvBusySlots);
            TextView tvDescription = view.findViewById(R.id.tvDetailDescription);

            // Populate the UI with car details
            tvName.setText(car.getName());
            tvRating.setText(String.valueOf(car.getRating()));
            tvSeats.setText(car.getSeats() + " Seats");
            tvTransmission.setText(car.getTransmission());

            // Format and show availability
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault());
            tvAvailableFrom.setText("From: " + sdf.format(new Date(car.getAvailableFrom())));
            tvAvailableTo.setText("To: " + sdf.format(new Date(car.getAvailableTo())));

            String description = car.getDescription() == null ? "" : car.getDescription().trim();
            if (description.isEmpty()) {
                tvDescription.setText(R.string.no_car_description);
                tvDescription.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant));
            } else {
                tvDescription.setText(description);
                tvDescription.setTextColor(ContextCompat.getColor(this, R.color.on_surface));
            }

            // Show or hide Fuel Type based on availability
            if (car.getFuelType() != null && !car.getFuelType().isEmpty()) {
                tvFuelType.setText(car.getFuelType());
                tvFuelType.setVisibility(View.VISIBLE);
                fuelTypeDivider.setVisibility(View.VISIBLE);
            } else {
                tvFuelType.setVisibility(View.GONE);
                fuelTypeDivider.setVisibility(View.GONE);
            }
            
            // Show or hide the tag based on availability
            if (car.getTag() != null && !car.getTag().isEmpty()) {
                tvTag.setText(car.getTag());
                tvTag.setVisibility(View.VISIBLE);
            } else {
                tvTag.setVisibility(View.GONE);
            }

            // Fetch and show busy slots
            fetchAndShowBusySlots(car.getId(), tvBusySlotsLabel, tvBusySlots);

            // Load the car image using Glide (handles both URL and Base64)
            String imageUrl = car.getImageUrl();
            if (imageUrl != null && !imageUrl.isEmpty()) {
                if (imageUrl.startsWith("http")) {
                    // It's a URL
                    Glide.with(this)
                            .load(imageUrl)
                            .placeholder(R.drawable.ic_car_placeholder)
                            .into(ivCarImage);
                } else {
                    // It's likely Base64 data
                    try {
                        byte[] decodedString = Base64.decode(imageUrl, Base64.DEFAULT);
                        Glide.with(this)
                                .asBitmap()
                                .load(decodedString)
                                .placeholder(R.drawable.ic_car_placeholder)
                                .into(ivCarImage);
                    } catch (Exception e) {
                        ivCarImage.setImageResource(R.drawable.ic_car_placeholder);
                    }
                }
            } else {
                ivCarImage.setImageResource(R.drawable.ic_car_placeholder);
            }
        }

        // Navigate to booking summary when "Book Now" is clicked
        View bookNowButton = view.findViewById(R.id.btnBookNow);
        String currentUserId = FirestoreHelper.getCurrentUserId(this);
        boolean isOwnCar = car != null
                && currentUserId != null
                && currentUserId.equals(car.getOwnerId());
        if (isOwnCar) {
            bookNowButton.setVisibility(View.GONE);
        } else {
            bookNowButton.setOnClickListener(v -> {
                Intent intent = new Intent(this, BookingSummaryActivity.class);
                intent.putExtra("car", car);
                startActivity(intent);
            });
        }

        // Handle back navigation from the toolbar navigation icon
        Toolbar toolbar = view.findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }
    }

    private void fetchAndShowBusySlots(String carId, TextView label, TextView content) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("bookings")
                .whereEqualTo("carId", carId)
                .whereGreaterThan("endTime", System.currentTimeMillis())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Booking> bookings = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Booking b = doc.toObject(Booking.class);
                        // Ignore rejected bookings in the "Busy Slots" list
                        if (!"REJECTED".equals(b.getStatus())) {
                            bookings.add(b);
                        }
                    }

                    if (bookings.isEmpty()) {
                        label.setVisibility(View.GONE);
                        content.setVisibility(View.GONE);
                        return;
                    }

                    Collections.sort(bookings, (b1, b2) -> Long.compare(b1.getStartTime(), b2.getStartTime()));

                    StringBuilder sb = new StringBuilder();
                    SimpleDateFormat sdf = new SimpleDateFormat("MMM d, HH:mm", Locale.getDefault());
                    for (Booking b : bookings) {
                        if (sb.length() > 0) sb.append("\n");
                        sb.append(sdf.format(new Date(b.getStartTime())))
                          .append(" - ")
                          .append(sdf.format(new Date(b.getEndTime())));
                    }

                    label.setVisibility(View.VISIBLE);
                    content.setVisibility(View.VISIBLE);
                    content.setText(sb.toString());
                });
    }
}
