package com.yardenbental_danielcohen_shlomoedelstein.carn_go.ui;

import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.R;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.model.Car;

/**
 * Fragment that displays detailed information about a specific car.
 */
public class CarDetailsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_car_details, container, false);

        // Retrieve the Car object passed from the previous fragment
        Car car = (Car) getArguments().getSerializable("car");
        if (car != null) {
            ImageView ivCarImage = view.findViewById(R.id.ivCarDetailImage);
            TextView tvName = view.findViewById(R.id.tvDetailName);
            TextView tvRating = view.findViewById(R.id.tvDetailRating);
            TextView tvSeats = view.findViewById(R.id.tvDetailSeats);
            TextView tvTransmission = view.findViewById(R.id.tvDetailTransmission);
            TextView tvTag = view.findViewById(R.id.tvDetailTag);
            TextView tvFuelType = view.findViewById(R.id.tvDetailFuelType);
            View fuelTypeDivider = view.findViewById(R.id.tvDetailFuelTypeDivider);

            // Populate the UI with car details
            tvName.setText(car.getName());
            tvRating.setText(String.valueOf(car.getRating()));
            tvSeats.setText(car.getSeats() + " Seats");
            tvTransmission.setText(car.getTransmission());

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

            // Load the car image using Glide (handles both URL and Base64)
            String imageUrl = car.getImageUrl();
            if (imageUrl != null && !imageUrl.isEmpty()) {
                if (imageUrl.startsWith("http")) {
                    // It's a URL
                    Glide.with(this)
                            .load(imageUrl)
                            .into(ivCarImage);
                } else {
                    // It's likely Base64 data
                    try {
                        byte[] decodedString = Base64.decode(imageUrl, Base64.DEFAULT);
                        Glide.with(this)
                                .asBitmap()
                                .load(decodedString)
                                .into(ivCarImage);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        // Navigate to booking summary when "Book Now" is clicked
        view.findViewById(R.id.btnBookNow).setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putSerializable("car", car);
            Navigation.findNavController(view).navigate(R.id.action_carDetailsFragment_to_bookingSummaryFragment, bundle);
        });

        // Handle back navigation from the toolbar
        view.findViewById(R.id.toolbar).setOnClickListener(v -> {
            Navigation.findNavController(view).navigateUp();
        });

        return view;
    }
}