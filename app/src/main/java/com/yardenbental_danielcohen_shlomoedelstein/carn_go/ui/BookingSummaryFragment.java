package com.yardenbental_danielcohen_shlomoedelstein.carn_go.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.yardenbental_danielcohen_shlomoedelstein.carn_go.R;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.model.Car;

/**
 * Fragment that displays a summary of the booking for a selected car.
 */
public class BookingSummaryFragment extends Fragment {

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
            TextView tvTotal = view.findViewById(R.id.tvSummaryTotal);

            // Populate the UI with car details
            tvName.setText(car.getName());
            tvPrice.setText("$" + (int)car.getPricePerHour() + " / day");
            // Example calculation for total price (assuming 3 days)
            tvTotal.setText("$" + ((int)car.getPricePerHour() * 3) + ".00");
        }

        // Handle the confirm booking button click
        view.findViewById(R.id.btnConfirmBooking).setOnClickListener(v -> {
            Toast.makeText(getContext(), "Booking Confirmed!", Toast.LENGTH_SHORT).show();
        });

        return view;
    }
}