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
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.discovery.CarDiscoveryHelper;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.model.Car;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.sync.BookingSyncScheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class BrowseCarsFragment extends Fragment {

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
    private MaterialButton btnAllTypes;
    private MaterialButton btnEconomy;
    private MaterialButton btnCompact;
    private MaterialButton btnHybrid;
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
            swipeRefresh.setOnRefreshListener(() -> requestCarsReload(true));
            swipeRefresh.setRefreshing(false);
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
        startPeriodicRefresh();
        refreshOnResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopPeriodicRefresh();
        setRefreshingState(false);
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
        refreshHandler.removeCallbacks(refreshRunnable);
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL);
    }

    private void stopPeriodicRefresh() {
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    private void requestCarsReload(boolean force) {
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
        loadCars();
    }

    private void refreshOnResume() {
        int radiusKm = AppPreferences.getSearchRadiusKm(requireContext());
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
        maybeRefreshLocation();
    }

    private void loadCars() {
        isLoadingCars = true;
        lastLoadStartedAt = System.currentTimeMillis();
        setRefreshingState(true);

        maybeRefreshLocation();

        long currentTime = System.currentTimeMillis();

        CarDiscoveryHelper.loadAvailableCars(requireContext(), currentTime, new CarDiscoveryHelper.CarsResultCallback() {
            @Override
            public void onSuccess(List<Car> loadedCars) {
                allCars.clear();
                allCars.addAll(loadedCars);
                applyFiltersAndSort();
                finishLoading();
            }

            @Override
            public void onError(Exception error) {
                Log.e("BrowseCarsFragment", "Error loading cars", error);
                Toast.makeText(getContext(), R.string.failed_to_load_cars, Toast.LENGTH_SHORT).show();
                finishLoading();
            }
        });
    }

    private void applyFiltersAndSort() {
        if (!isAdded()) {
            return;
        }

        int radiusKm = AppPreferences.getSearchRadiusKm(requireContext());
        lastAppliedRadiusKm = radiusKm;
        cars.clear();
        cars.addAll(CarDiscoveryHelper.filterAndSortCars(
                allCars,
                currentLocation,
                radiusKm,
                this::matchesTypeFilter
        ));
        adapter.notifyDataSetChanged();
    }

    private boolean matchesTypeFilter(Car car) {
        return CarDiscoveryHelper.matchesTypeFilter(car, activeTypeFilter);
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
                    lastLocationRefreshAt = System.currentTimeMillis();
                    if (location != null) {
                        currentLocation = location;
                        applyFiltersAndSort();
                    } else {
                        fusedLocationClient.getLastLocation()
                                .addOnSuccessListener(lastLocation -> {
                                    lastLocationRefreshAt = System.currentTimeMillis();
                                    if (lastLocation != null) {
                                        currentLocation = lastLocation;
                                        applyFiltersAndSort();
                                    }
                                });
                    }
                });
    }

    private void maybeRefreshLocation() {
        if (!hasLocationPermission()) {
            return;
        }
        if (currentLocation != null
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
