package com.dirtyfly.carn_go.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.dirtyfly.carn_go.R;
import com.dirtyfly.carn_go.model.Car;

public class BookingSummaryFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_booking_summary, container, false);

        Car car = (Car) getArguments().getSerializable("car");
        if (car != null) {
            TextView tvName = view.findViewById(R.id.tvSummaryCarName);
            TextView tvPrice = view.findViewById(R.id.tvSummaryPrice);
            TextView tvTotal = view.findViewById(R.id.tvSummaryTotal);

            tvName.setText(car.getName());
            tvPrice.setText("$" + (int)car.getPricePerHour() + " / day");
            tvTotal.setText("$" + ((int)car.getPricePerHour() * 3) + ".00");
        }

        view.findViewById(R.id.btnConfirmBooking).setOnClickListener(v -> {
            Toast.makeText(getContext(), "Booking Confirmed!", Toast.LENGTH_SHORT).show();
        });

        return view;
    }
}