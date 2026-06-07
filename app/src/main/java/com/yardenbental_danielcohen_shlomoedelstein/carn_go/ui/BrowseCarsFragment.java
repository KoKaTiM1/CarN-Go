package com.yardenbental_danielcohen_shlomoedelstein.carn_go.ui;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.AppPreferences;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.R;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.adapter.CarAdapter;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.model.Car;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.sync.BookingSyncScheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class BrowseCarsFragment extends Fragment {

    private static final long REFRESH_INTERVAL = 600000;

    private SwipeRefreshLayout swipeRefresh;
    private CarAdapter adapter;
    private final List<Car> cars = new ArrayList<>();
    private final List<Car> allCars = new ArrayList<>();
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = this::loadCars;
    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;
    private Location currentLocation;
    private String activeTypeFilter;
    private MaterialButton btnAllTypes;
    private MaterialButton btnEconomy;
    private MaterialButton btnCompact;
    private MaterialButton btnHybrid;
    private final BroadcastReceiver syncReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadCars();
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext());
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                permissions -> {
                    boolean granted = Boolean.TRUE.equals(permissions.get(Manifest.permission.ACCESS_FINE_LOCATION))
                            || Boolean.TRUE.equals(permissions.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                    if (granted) {
                        startLocationUpdates();
                    } else {
                        currentLocation = null;
                        showLocationSettingsDialog();
                        applyFiltersAndSort();
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_browse_cars, container, false);

        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        if (swipeRefresh != null) {
            swipeRefresh.setOnRefreshListener(this::loadCars);
        }

        RecyclerView rvCars = view.findViewById(R.id.rvCars);
        rvCars.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new CarAdapter(cars, car -> {
            Bundle bundle = new Bundle();
            bundle.putSerializable("car", car);
            Navigation.findNavController(view).navigate(R.id.action_browseCarsFragment_to_carDetailsFragment, bundle);
        });
        rvCars.setAdapter(adapter);

        btnAllTypes = view.findViewById(R.id.btnAllTypes);
        btnEconomy = view.findViewById(R.id.btnEconomy);
        btnCompact = view.findViewById(R.id.btnCompact);
        btnHybrid = view.findViewById(R.id.btnHybrid);

        btnAllTypes.setOnClickListener(v -> setTypeFilter(null));
        btnEconomy.setOnClickListener(v -> setTypeFilter(getString(R.string.economy)));
        btnCompact.setOnClickListener(v -> setTypeFilter(getString(R.string.compact)));
        btnHybrid.setOnClickListener(v -> setTypeFilter(getString(R.string.hybrid)));
        updateTypeButtons();

        loadCars();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        ContextCompat.registerReceiver(
                requireContext(),
                syncReceiver,
                new IntentFilter(BookingSyncScheduler.ACTION_SYNC_COMPLETED),
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
        loadCars();
        startPeriodicRefresh();
        ensureLocationReady();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopPeriodicRefresh();
        try {
            requireContext().unregisterReceiver(syncReceiver);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void setTypeFilter(@Nullable String typeFilter) {
        activeTypeFilter = typeFilter;
        updateTypeButtons();
        applyFiltersAndSort();
    }

    private void updateTypeButtons() {
        highlightButton(btnAllTypes, activeTypeFilter == null);
        highlightButton(btnEconomy, getString(R.string.economy).equalsIgnoreCase(activeTypeFilter));
        highlightButton(btnCompact, getString(R.string.compact).equalsIgnoreCase(activeTypeFilter));
        highlightButton(btnHybrid, getString(R.string.hybrid).equalsIgnoreCase(activeTypeFilter));
    }

    private void highlightButton(MaterialButton button, boolean selected) {
        button.setAlpha(selected ? 1f : 0.6f);
    }

    private void startPeriodicRefresh() {
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL);
    }

    private void stopPeriodicRefresh() {
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    private void ensureLocationReady() {
        if (hasLocationPermission()) {
            startLocationUpdates();
            return;
        }

        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
                || shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.location_permission_title)
                    .setMessage(R.string.location_permission_explore_message)
                    .setPositiveButton(R.string.allow_location, (dialog, which) -> requestLocationPermission())
                    .setNegativeButton(R.string.continue_without_location, null)
                    .show();
        } else {
            requestLocationPermission();
        }
    }

    private void requestLocationPermission() {
        locationPermissionLauncher.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void startLocationUpdates() {
        if (!hasLocationPermission()) {
            return;
        }
        requestCurrentLocationOnce();
    }

    private void loadCars() {
        if (swipeRefresh != null) {
            swipeRefresh.setRefreshing(true);
        }

        if (hasLocationPermission()) {
            requestCurrentLocationOnce();
        }

        long currentTime = System.currentTimeMillis();

        FirebaseFirestore.getInstance().collection("cars")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    allCars.clear();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        try {
                            Long availableTo = document.getLong("availableTo");
                            if (availableTo != null && availableTo < currentTime) {
                                continue;
                            }
                            Boolean isCurrentlyAvailable = document.getBoolean("isCurrentlyAvailable");
                            if (isCurrentlyAvailable != null && !isCurrentlyAvailable) {
                                continue;
                            }

                            String name = document.getString("name");
                            String type = document.getString("type");
                            String location = document.getString("location");
                            Double latitude = document.getDouble("latitude");
                            Double longitude = document.getDouble("longitude");
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

                            allCars.add(new Car(
                                    document.getId(),
                                    name != null ? name : getString(R.string.unknown),
                                    type != null ? type : getString(R.string.standard),
                                    location != null ? location : getString(R.string.location_unavailable_label),
                                    latitude,
                                    longitude,
                                    price != null ? price : 0.0,
                                    rating != null ? rating : 5.0,
                                    imageUrl,
                                    transmission != null ? transmission : getString(R.string.auto),
                                    seats,
                                    fuelType != null ? fuelType : getString(R.string.gas),
                                    tag != null ? tag : "",
                                    ownerId != null ? ownerId : "",
                                    availableFrom != null ? availableFrom : 0,
                                    availableTo != null ? availableTo : 0
                            ));
                        } catch (Exception e) {
                            Log.e("BrowseCarsFragment", "Error parsing car", e);
                        }
                    }
                    applyFiltersAndSort();
                    finishLoading();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), R.string.failed_to_load_cars, Toast.LENGTH_SHORT).show();
                    finishLoading();
                });
    }

    private void applyFiltersAndSort() {
        if (!isAdded()) {
            return;
        }

        int radiusKm = AppPreferences.getSearchRadiusKm(requireContext());
        cars.clear();

        for (Car car : allCars) {
            if (!matchesTypeFilter(car)) {
                continue;
            }

            Double distanceKm = calculateDistanceKm(car);
            car.setDistanceKm(distanceKm);

            if (currentLocation != null && radiusKm > 0) {
                if (distanceKm == null || distanceKm > radiusKm) {
                    continue;
                }
            }

            cars.add(car);
        }

        Collections.sort(cars, Comparator
                .comparing(Car::getDistanceKm, (left, right) -> {
                    if (left == null && right == null) return 0;
                    if (left == null) return 1;
                    if (right == null) return -1;
                    return Double.compare(left, right);
                })
                .thenComparing(Car::getName, String.CASE_INSENSITIVE_ORDER));

        adapter.notifyDataSetChanged();
    }

    private boolean matchesTypeFilter(Car car) {
        if (activeTypeFilter == null || activeTypeFilter.isEmpty()) {
            return true;
        }
        String carType = car.getType();
        return carType != null && carType.toLowerCase(Locale.ROOT).contains(activeTypeFilter.toLowerCase(Locale.ROOT));
    }

    private Double calculateDistanceKm(Car car) {
        if (currentLocation == null || car.getLatitude() == null || car.getLongitude() == null) {
            return null;
        }

        float[] results = new float[1];
        Location.distanceBetween(
                currentLocation.getLatitude(),
                currentLocation.getLongitude(),
                car.getLatitude(),
                car.getLongitude(),
                results
        );
        return results[0] / 1000d;
    }

    private void finishLoading() {
        if (swipeRefresh != null) {
            swipeRefresh.setRefreshing(false);
        }
        stopPeriodicRefresh();
        startPeriodicRefresh();
    }

    private void showLocationSettingsDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.location_permission_title)
                .setMessage(R.string.location_permission_settings_message)
                .setPositiveButton(R.string.open_settings, (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.fromParts("package", requireContext().getPackageName(), null));
                    startActivity(intent);
                })
                .setNegativeButton(R.string.continue_without_location, null)
                .show();
    }

    private void requestCurrentLocationOnce() {
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        currentLocation = location;
                        applyFiltersAndSort();
                    } else {
                        fusedLocationClient.getLastLocation()
                                .addOnSuccessListener(lastLocation -> {
                                    if (lastLocation != null) {
                                        currentLocation = lastLocation;
                                        applyFiltersAndSort();
                                    }
                                });
                    }
                });
    }
}
