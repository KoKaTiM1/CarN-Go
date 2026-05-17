package com.yardenbental_danielcohen_shlomoedelstein.carn_go.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.R;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.adapter.BookingAdapter;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.firebase.FirestoreHelper;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.model.Booking;

import java.util.ArrayList;
import java.util.List;

public class MyBookingsFragment extends Fragment {

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
        
        rvBookings.setLayoutManager(new LinearLayoutManager(getContext()));
        bookingList = new ArrayList<>();
        adapter = new BookingAdapter(bookingList);
        rvBookings.setAdapter(adapter);

        loadBookings();

        return view;
    }

    private void loadBookings() {
        String userId = FirestoreHelper.getCurrentUserId(getContext());
        if (userId == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("bookings")
                .whereEqualTo("userId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    bookingList.clear();
                    bookingList.addAll(queryDocumentSnapshots.toObjects(Booking.class));
                    
                    if (bookingList.isEmpty()) {
                        layoutNoBookings.setVisibility(View.VISIBLE);
                        rvBookings.setVisibility(View.GONE);
                    } else {
                        layoutNoBookings.setVisibility(View.GONE);
                        rvBookings.setVisibility(View.VISIBLE);
                        adapter.notifyDataSetChanged();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Error loading bookings: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}