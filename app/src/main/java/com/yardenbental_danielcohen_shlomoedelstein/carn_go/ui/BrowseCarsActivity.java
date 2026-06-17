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
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.AppPreferences;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.R;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.adapter.CarAdapter;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.discovery.CarDiscoveryHelper;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.model.Car;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.sync.BookingSyncScheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class BrowseCarsActivity extends BaseNavigationActivity {

    private static final long REFRESH_INTERVAL = 600000;
    private static final long MIN_RELOAD_GAP_MS = 1500;
    private static final long LOCATION_REFRESH_INTERVAL_MS = TimeUnit.MINUTES.toMillis(10);

    private SwipeRefreshLayout swipeRefresh;
    private CarAdapter adapter;
    private final List<Car> cars = new ArrayList<>();
    private final List<Car> allCars = new ArrayList<>();
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = () -> requestCarsReload(true);
    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;
    private Location currentLocation;
    private String activeTypeFilter;
    private String activeTransmissionFilter;
    private String activeFuelTypeFilter;
    private String activeSearchQuery = "";
    private ListView rvCars;
    private Spinner dropdownTypeFilter;
    private Spinner dropdownTransmissionFilter;
    private Spinner dropdownFuelFilter;
    private boolean isLoadingCars = false;
    private boolean pendingReload = false;
    private long lastLoadStartedAt = 0L;
    private long lastLoadCompletedAt = 0L;
    private long lastLocationRefreshAt = 0L;
    private int lastAppliedRadiusKm = Integer.MIN_VALUE;

    private final BroadcastReceiver syncReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            requestCarsReload(true);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.app_name);
        setScreenContent(R.layout.fragment_browse_cars, R.id.nav_browse_cars, true, true);
        View view = findViewById(android.R.id.content);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
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

        swipeRefresh = view.findViewById(R.id.swipeRefresh);

        rvCars = view.findViewById(R.id.rvCars);
        adapter = new CarAdapter(cars, car -> {
            Intent intent = new Intent(this, CarDetailsActivity.class);
            intent.putExtra("car", car);
            startActivity(intent);
        });
        rvCars.setAdapter(adapter);

        if (swipeRefresh != null) {
            swipeRefresh.setOnRefreshListener(() -> requestCarsReload(true, true));
            swipeRefresh.setOnChildScrollUpCallback((parent, child) -> rvCars != null && rvCars.canScrollVertically(-1));
            swipeRefresh.setRefreshing(false);
        }

        EditText searchField = view.findViewById(R.id.etCarSearch);
        searchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                activeSearchQuery = s == null ? "" : s.toString().trim();
                applyFiltersAndSort();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        dropdownTypeFilter = view.findViewById(R.id.dropdownTypeFilter);
        dropdownTransmissionFilter = view.findViewById(R.id.dropdownTransmissionFilter);
        dropdownFuelFilter = view.findViewById(R.id.dropdownFuelFilter);
        setupFilterDropdowns();
    }

    @Override
    public void onResume() {
        super.onResume();
        ContextCompat.registerReceiver(
                this,
                syncReceiver,
                new IntentFilter(BookingSyncScheduler.ACTION_SYNC_COMPLETED),
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
        startPeriodicRefresh();
        refreshOnResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopPeriodicRefresh();
        setRefreshingState(false);
        try {
            unregisterReceiver(syncReceiver);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void setTypeFilter(@Nullable String typeFilter) {
        activeTypeFilter = typeFilter;
        applyFiltersAndSort();
    }

    private void setTransmissionFilter(@Nullable String transmissionFilter) {
        activeTransmissionFilter = transmissionFilter;
        applyFiltersAndSort();
    }

    private void setFuelTypeFilter(@Nullable String fuelTypeFilter) {
        activeFuelTypeFilter = fuelTypeFilter;
        applyFiltersAndSort();
    }

    private void startPeriodicRefresh() {
        refreshHandler.removeCallbacks(refreshRunnable);
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL);
    }

    private void stopPeriodicRefresh() {
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    private void requestCarsReload(boolean force) {
        requestCarsReload(force, false);
    }

    private void requestCarsReload(boolean force, boolean forceLocationRefresh) {
        long now = System.currentTimeMillis();
        if (!force && now - lastLoadStartedAt < MIN_RELOAD_GAP_MS) {
            setRefreshingState(false);
            return;
        }
        if (isLoadingCars) {
            pendingReload = true;
            setRefreshingState(false);
            return;
        }
        loadCars(forceLocationRefresh);
    }

    private void refreshOnResume() {
        int radiusKm = AppPreferences.getSearchRadiusKm(this);
        boolean radiusChanged = radiusKm != lastAppliedRadiusKm;
        boolean needsInitialLoad = allCars.isEmpty();
        boolean staleData = System.currentTimeMillis() - lastLoadCompletedAt >= REFRESH_INTERVAL;

        if (needsInitialLoad || staleData) {
            requestCarsReload(needsInitialLoad);
        } else {
            setRefreshingState(false);
            if (radiusChanged) {
                lastAppliedRadiusKm = radiusKm;
                applyFiltersAndSort();
            }
            maybeRefreshLocation();
        }

        if (!hasLocationPermission()) {
            ensureLocationReady();
        }
    }

    private void ensureLocationReady() {
        if (hasLocationPermission()) {
            startLocationUpdates();
            return;
        }

        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
                || shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            new AlertDialog.Builder(this)
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
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void startLocationUpdates() {
        if (!hasLocationPermission()) {
            return;
        }
        maybeRefreshLocation();
    }

    private void loadCars(boolean forceLocationRefresh) {
        isLoadingCars = true;
        lastLoadStartedAt = System.currentTimeMillis();
        setRefreshingState(true);

        if (needsLocationRefresh(forceLocationRefresh)) {
            requestCurrentLocationOnce(this::loadCarsFromFirestore);
        } else {
            loadCarsFromFirestore();
        }
    }

    private void loadCarsFromFirestore() {
        CarDiscoveryHelper.loadAvailableCars(this, System.currentTimeMillis(), new CarDiscoveryHelper.CarsResultCallback() {
            @Override
            public void onSuccess(List<Car> loadedCars) {
                allCars.clear();
                allCars.addAll(loadedCars);
                applyFiltersAndSort();
                finishLoading();
            }

            @Override
            public void onError(Exception error) {
                Log.e("BrowseCarsActivity", "Error loading cars", error);
                Toast.makeText(BrowseCarsActivity.this, R.string.failed_to_load_cars, Toast.LENGTH_SHORT).show();
                finishLoading();
            }
        });
    }

    private boolean needsLocationRefresh(boolean forceRefresh) {
        return hasLocationPermission()
                && (forceRefresh
                || currentLocation == null
                || System.currentTimeMillis() - lastLocationRefreshAt >= LOCATION_REFRESH_INTERVAL_MS);
    }

    private void applyFiltersAndSort() {
        int radiusKm = AppPreferences.getSearchRadiusKm(this);
        lastAppliedRadiusKm = radiusKm;
        cars.clear();
        cars.addAll(CarDiscoveryHelper.filterAndSortCars(
                allCars,
                currentLocation,
                radiusKm,
                this::matchesTypeFilter
        ));
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private boolean matchesTypeFilter(Car car) {
        return CarDiscoveryHelper.matchesTypeFilter(car, activeTypeFilter)
                && CarDiscoveryHelper.matchesTransmissionFilter(car, activeTransmissionFilter)
                && CarDiscoveryHelper.matchesFuelTypeFilter(car, activeFuelTypeFilter)
                && matchesSearchQuery(car);
    }

    private boolean matchesSearchQuery(Car car) {
        if (activeSearchQuery == null || activeSearchQuery.isEmpty()) {
            return true;
        }
        String query = activeSearchQuery.toLowerCase(java.util.Locale.ROOT);
        return containsIgnoreCase(car.getName(), query)
                || containsIgnoreCase(car.getType(), query)
                || containsIgnoreCase(car.getLocation(), query)
                || containsIgnoreCase(car.getTransmission(), query)
                || containsIgnoreCase(car.getFuelType(), query)
                || containsIgnoreCase(car.getTag(), query);
    }

    private boolean containsIgnoreCase(@Nullable String value, String query) {
        return value != null && value.toLowerCase(java.util.Locale.ROOT).contains(query);
    }

    private void setupFilterDropdowns() {
        setupSpinner(dropdownTypeFilter, R.array.filter_all_type_options, getString(R.string.filter_all_types), selected ->
                setTypeFilter(isAllSelection(selected, getString(R.string.filter_all_types)) ? null : selected));
        setupSpinner(dropdownTransmissionFilter, R.array.filter_all_transmission_options, getString(R.string.filter_all_transmissions), selected ->
                setTransmissionFilter(isAllSelection(selected, getString(R.string.filter_all_transmissions)) ? null : selected));
        setupSpinner(dropdownFuelFilter, R.array.filter_all_fuel_options, getString(R.string.filter_all_fuel_types), selected ->
                setFuelTypeFilter(isAllSelection(selected, getString(R.string.filter_all_fuel_types)) ? null : selected));
    }

    private boolean isAllSelection(String value, String allLabel) {
        return value.equalsIgnoreCase(allLabel);
    }

    private void setupSpinner(Spinner spinner, int optionsArrayId, String defaultValue, SpinnerSelectionListener listener) {
        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(
                this,
                optionsArrayId,
                android.R.layout.simple_spinner_item
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinnerAdapter);
        spinner.setSelection(indexOf(defaultValue, spinnerAdapter));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                listener.onSelected(parent.getItemAtPosition(position).toString());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private int indexOf(String value, ArrayAdapter<CharSequence> adapter) {
        for (int i = 0; i < adapter.getCount(); i++) {
            if (value.equalsIgnoreCase(adapter.getItem(i).toString())) {
                return i;
            }
        }
        return 0;
    }

    private interface SpinnerSelectionListener {
        void onSelected(String value);
    }

    private void finishLoading() {
        isLoadingCars = false;
        lastLoadCompletedAt = System.currentTimeMillis();
        setRefreshingState(false);
        stopPeriodicRefresh();
        startPeriodicRefresh();
        if (pendingReload) {
            pendingReload = false;
            requestCarsReload(false);
        }
    }

    private void showLocationSettingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.location_permission_title)
                .setMessage(R.string.location_permission_settings_message)
                .setPositiveButton(R.string.open_settings, (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.fromParts("package", getPackageName(), null));
                    startActivity(intent);
                })
                .setNegativeButton(R.string.continue_without_location, null)
                .show();
    }

    private void requestCurrentLocationOnce() {
        requestCurrentLocationOnce(null);
    }

    private void requestCurrentLocationOnce(@Nullable Runnable onComplete) {
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    lastLocationRefreshAt = System.currentTimeMillis();
                    if (location != null) {
                        currentLocation = location;
                        applyFiltersAfterLocationChange();
                        runLocationComplete(onComplete);
                    } else {
                        fusedLocationClient.getLastLocation()
                                .addOnSuccessListener(lastLocation -> {
                                    lastLocationRefreshAt = System.currentTimeMillis();
                                    if (lastLocation != null) {
                                        currentLocation = lastLocation;
                                        applyFiltersAfterLocationChange();
                                    }
                                    runLocationComplete(onComplete);
                                })
                                .addOnFailureListener(error -> runLocationComplete(onComplete));
                    }
                })
                .addOnFailureListener(error -> runLocationComplete(onComplete));
    }

    private void applyFiltersAfterLocationChange() {
        if (!allCars.isEmpty()) {
            applyFiltersAndSort();
        }
    }

    private void runLocationComplete(@Nullable Runnable onComplete) {
        if (onComplete != null) {
            onComplete.run();
        }
    }

    private void maybeRefreshLocation() {
        maybeRefreshLocation(false);
    }

    private void maybeRefreshLocation(boolean forceRefresh) {
        if (!hasLocationPermission()) {
            return;
        }
        if (!forceRefresh
                && currentLocation != null
                && System.currentTimeMillis() - lastLocationRefreshAt < LOCATION_REFRESH_INTERVAL_MS) {
            return;
        }
        requestCurrentLocationOnce();
    }

    private void setRefreshingState(boolean refreshing) {
        if (swipeRefresh != null) {
            swipeRefresh.setRefreshing(refreshing);
        }
    }
}
