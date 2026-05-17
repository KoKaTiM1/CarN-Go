package com.yardenbental_danielcohen_shlomoedelstein.carn_go.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.R;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.adapter.CarAdapter;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.model.Car;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment that allows users to browse a list of available cars.
 * Includes time-based filtering, manual refresh, and periodic auto-refresh.
 */
public class BrowseCarsFragment extends Fragment {

    private SwipeRefreshLayout swipeRefresh;
    private CarAdapter adapter;
    private final List<Car> cars = new ArrayList<>();
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = this::loadCars;
    private static final long REFRESH_INTERVAL = 600000; // 1 minute

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_browse_cars, container, false);

        // Initialize SwipeRefreshLayout
        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        if (swipeRefresh != null) {
            swipeRefresh.setOnRefreshListener(this::loadCars);
        }

        // Initialize RecyclerView
        RecyclerView rvCars = view.findViewById(R.id.rvCars);
        rvCars.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new CarAdapter(cars, car -> {
            Bundle bundle = new Bundle();
            bundle.putSerializable("car", car);
            Navigation.findNavController(view).navigate(R.id.action_browseCarsFragment_to_carDetailsFragment, bundle);
        });
        rvCars.setAdapter(adapter);

        loadCars();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Automatically refresh when returning to this tab to ensure expired cars are removed
        loadCars();
        // Start periodic refresh
        startPeriodicRefresh();
    }

    @Override
    public void onPause() {
        super.onPause();
        // Stop periodic refresh to save resources when app is in background
        stopPeriodicRefresh();
    }

    private void startPeriodicRefresh() {
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL);
    }

    private void stopPeriodicRefresh() {
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    private void loadCars() {
        if (swipeRefresh != null) {
            swipeRefresh.setRefreshing(true);
        }
        
        long currentTime = System.currentTimeMillis();

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("cars")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    cars.clear();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        try {
                            Long availableTo = document.getLong("availableTo");
                            
                            // FILTER: Hide cars that are no longer available (end time passed)
                            if (availableTo != null && availableTo < currentTime) {
                                continue;
                            }

                            String name = document.getString("name");
                            String type = document.getString("type");
                            String location = document.getString("location");
                            Double price = document.getDouble("pricePerHour");
                            Double rating = document.getDouble("rating");
                            String imageUrl = document.getString("imageUrl");
                            String transmission = document.getString("transmission");
                            Long seatsLong = document.getLong("seats");
                            int seats = seatsLong != null ? seatsLong.intValue() : 5;
                            String fuelType = document.getString("fuelType");
                            String tag = document.getString("tag");
                            String ownerId = document.getString("ownerId");
                            Long availableFrom = document.getLong("availableFrom");

                            cars.add(new Car(
                                    document.getId(),
                                    name != null ? name : "Unknown",
                                    type != null ? type : "Standard",
                                    location != null ? location : "Unknown",
                                    price != null ? price : 0.0,
                                    rating != null ? rating : 5.0,
                                    imageUrl,
                                    transmission != null ? transmission : "Auto",
                                    seats,
                                    fuelType != null ? fuelType : "Gas",
                                    tag != null ? tag : "",
                                    ownerId != null ? ownerId : "",
                                    availableFrom != null ? availableFrom : 0,
                                    availableTo != null ? availableTo : 0
                            ));
                        } catch (Exception e) {
                            Log.e("BrowseCarsFragment", "Error parsing car", e);
                        }
                    }
                    adapter.notifyDataSetChanged();
                    if (swipeRefresh != null) {
                        swipeRefresh.setRefreshing(false);
                    }
                    
                    // Reschedule next refresh
                    stopPeriodicRefresh();
                    startPeriodicRefresh();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Failed to load cars", Toast.LENGTH_SHORT).show();
                    if (swipeRefresh != null) {
                        swipeRefresh.setRefreshing(false);
                    }
                    // Reschedule even on failure
                    stopPeriodicRefresh();
                    startPeriodicRefresh();
                });
    }
}