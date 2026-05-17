package com.yardenbental_danielcohen_shlomoedelstein.carn_go.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

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

import java.util.HashMap;
import java.util.Map;

/**
 * Fragment that displays a summary of the booking for a selected car.
 */
public class BookingSummaryFragment extends Fragment {

    private int selectedHours = 1;
    private double totalPrice = 0;

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
            TextView tvTotalLabel = view.findViewById(R.id.tvTotalLabel);
            EditText etHours = view.findViewById(R.id.etBookingHours);

            // Populate the UI with car details
            tvName.setText(car.getName());
            tvPrice.setText("$" + (int)car.getPricePerHour() + " / hour");
            
            totalPrice = car.getPricePerHour();
            tvTotal.setText("$" + String.format("%.2f", totalPrice));

            // Handle back navigation from the toolbar
            Toolbar toolbar = view.findViewById(R.id.toolbarSummary);
            if (toolbar != null) {
                toolbar.setNavigationOnClickListener(v -> {
                    Navigation.findNavController(view).navigateUp();
                });
            }

            etHours.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    try {
                        String input = s.toString();
                        if (input.isEmpty()) {
                            selectedHours = 0;
                        } else {
                            selectedHours = Integer.parseInt(input);
                        }
                        totalPrice = car.getPricePerHour() * selectedHours;
                        tvTotal.setText("$" + String.format("%.2f", totalPrice));
                        tvTotalLabel.setText("Total (" + selectedHours + (selectedHours == 1 ? " hour)" : " hours)"));
                    } catch (NumberFormatException e) {
                        selectedHours = 0;
                    }
                }
            });

            // Handle the confirm booking button click
            view.findViewById(R.id.btnConfirmBooking).setOnClickListener(v -> {
                if (selectedHours <= 0) {
                    Toast.makeText(getContext(), "Please enter a valid duration", Toast.LENGTH_SHORT).show();
                    return;
                }
                confirmBooking(car);
            });
        }

        return view;
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
        bookingData.put("hours", selectedHours);
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