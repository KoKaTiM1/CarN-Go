package com.yardenbental_danielcohen_shlomoedelstein.carn_go.ui;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;
import android.widget.ListView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessaging;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.AppPreferences;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.R;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.adapter.SplashCarAdapter;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.discovery.CarDiscoveryHelper;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.firebase.FirestoreHelper;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.model.Car;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.sync.BookingSyncScheduler;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.util.NetworkUtils;

import java.util.ArrayList;
import java.util.List;

public class SplashActivity extends BaseNavigationActivity {

    private static final int MAX_RESULTS = 3;
    private static final String STATE_NEAREST_CARS = "state_nearest_cars";
    private static final String STATE_STATUS_TEXT = "state_status_text";
    private static final String STATE_RESULTS_TITLE = "state_results_title";
    private static final String STATE_RESULTS_VISIBLE = "state_results_visible";
    private static final String STATE_HAS_SEARCHED = "state_has_searched";

    private final List<Car> nearestCars = new ArrayList<>();
    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private SplashCarAdapter adapter;
    private MaterialButton btnSearchNearby;
    private View layoutContent;
    private View layoutCircle;
    private View pulseOuter;
    private View pulseInner;
    private View layoutResults;
    private TextView tvBadge;
    private TextView tvTitle;
    private TextView tvSubtitle;
    private TextView tvStatus;
    private TextView tvResultsTitle;
    private TextView tvResultsSubtitle;
    private AnimatorSet idlePulseAnimator;
    private AnimatorSet activePulseAnimator;
    private boolean isSearching = false;
    private boolean hasSearched = false;
    private boolean shouldShowResults = false;
    private String currentStatusText;
    private String currentResultsTitleText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            ArrayList<Car> savedCars = (ArrayList<Car>) savedInstanceState.getSerializable(STATE_NEAREST_CARS);
            if (savedCars != null) {
                nearestCars.clear();
                nearestCars.addAll(savedCars);
            }
            currentStatusText = savedInstanceState.getString(STATE_STATUS_TEXT);
            currentResultsTitleText = savedInstanceState.getString(STATE_RESULTS_TITLE);
            shouldShowResults = savedInstanceState.getBoolean(STATE_RESULTS_VISIBLE, false);
            hasSearched = savedInstanceState.getBoolean(STATE_HAS_SEARCHED, false);
        }
        setTitle(R.string.app_name);
        setScreenContent(R.layout.fragment_splash, 0, false, true);
        View view = findViewById(android.R.id.content);

        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> Log.d("Permission", "Notification permission granted=" + isGranted)
        );
        askNotificationPermission();
        signInAnonymously();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                permissions -> {
                    boolean granted = Boolean.TRUE.equals(permissions.get(Manifest.permission.ACCESS_FINE_LOCATION))
                            || Boolean.TRUE.equals(permissions.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                    if (granted) {
                        runNearbySearch();
                    } else {
                        showLocationDeniedState();
                    }
                }
        );

        btnSearchNearby = view.findViewById(R.id.btnSearchNearby);
        layoutContent = view.findViewById(R.id.layoutSplashContent);
        layoutCircle = view.findViewById(R.id.layoutSplashCircle);
        pulseOuter = view.findViewById(R.id.viewPulseOuter);
        pulseInner = view.findViewById(R.id.viewPulseInner);
        layoutResults = view.findViewById(R.id.layoutSplashResults);
        tvBadge = view.findViewById(R.id.tvSplashBadge);
        tvTitle = view.findViewById(R.id.tvSplashTitle);
        tvSubtitle = view.findViewById(R.id.tvSplashSubtitle);
        tvStatus = view.findViewById(R.id.tvSplashStatus);
        tvResultsTitle = view.findViewById(R.id.tvSplashResultsTitle);
        tvResultsSubtitle = view.findViewById(R.id.tvSplashResultsSubtitle);
        ListView rvCars = view.findViewById(R.id.rvSplashCars);

        adapter = new SplashCarAdapter(nearestCars, this::openCarDetails);
        rvCars.setAdapter(adapter);

        view.findViewById(R.id.btnSkipSplash).setOnClickListener(v -> openExplore());
        btnSearchNearby.setOnClickListener(v -> startSearchFlow());
        renderCurrentState();
    }

    private void signInAnonymously() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            auth.signInAnonymously().addOnCompleteListener(this, task -> {
                initializeFCM();
                BookingSyncScheduler.requestImmediateSync(this, task.isSuccessful() ? "app_launch" : "app_launch_fallback");
            });
        } else {
            initializeFCM();
            BookingSyncScheduler.requestImmediateSync(this, "app_launch_existing_user");
        }
    }

    private void initializeFCM() {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w("FCM", "Fetching FCM registration token failed", task.getException());
                        return;
                    }
                    FirestoreHelper.updateUserToken(this, task.getResult());
                });
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAnimator(idlePulseAnimator);
        stopAnimator(activePulseAnimator);
        idlePulseAnimator = null;
        activePulseAnimator = null;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(STATE_NEAREST_CARS, new ArrayList<>(nearestCars));
        outState.putString(STATE_STATUS_TEXT, currentStatusText);
        outState.putString(STATE_RESULTS_TITLE, currentResultsTitleText);
        outState.putBoolean(STATE_RESULTS_VISIBLE, shouldShowResults);
        outState.putBoolean(STATE_HAS_SEARCHED, hasSearched);
    }

    private void startSearchFlow() {
        if (isSearching) {
            return;
        }
        if (!NetworkUtils.checkAndToast(this)) {
            return;
        }

        if (hasLocationPermission()) {
            runNearbySearch();
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
                || shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            new AlertDialog.Builder(SplashActivity.this)
                    .setTitle(R.string.location_permission_title)
                    .setMessage(R.string.location_permission_explore_message)
                    .setPositiveButton(R.string.allow_location, (dialog, which) -> requestLocationPermission())
                    .setNegativeButton(R.string.continue_without_location, (dialog, which) -> showLocationDeniedState())
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

    private void runNearbySearch() {
        boolean keepCompactLayout = hasSearched;
        isSearching = true;
        shouldShowResults = false;
        applyStatusText(getString(R.string.splash_status_searching));
        btnSearchNearby.setEnabled(false);
        btnSearchNearby.setText(R.string.splash_searching_button);
        layoutResults.setVisibility(View.GONE);
        applyCompactLayout(keepCompactLayout);
        startActivePulse();

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        loadNearbyCars(location);
                    } else {
                        fusedLocationClient.getLastLocation()
                                .addOnSuccessListener(lastLocation -> {
                                    if (lastLocation != null) {
                                        loadNearbyCars(lastLocation);
                                    } else {
                                        showLocationUnavailableState();
                                    }
                                })
                                .addOnFailureListener(error -> showLocationUnavailableState());
                    }
                })
                .addOnFailureListener(error -> showLocationUnavailableState());
    }

    private void loadNearbyCars(@NonNull Location location) {
        String currentUserId = FirestoreHelper.getCurrentUserId(SplashActivity.this);
        CarDiscoveryHelper.loadAvailableCars(SplashActivity.this, System.currentTimeMillis(), currentUserId, new CarDiscoveryHelper.CarsResultCallback() {
            @Override
            public void onSuccess(List<Car> cars) {
                if (!!isFinishing()) {
                    return;
                }
                List<Car> sortedCars = CarDiscoveryHelper.filterAndSortCars(
                        cars,
                        location,
                        AppPreferences.getSearchRadiusKm(SplashActivity.this),
                        null
                );
                showSearchResults(sortedCars);
            }

            @Override
            public void onError(Exception error) {
                if (!!isFinishing()) {
                    return;
                }
                showSearchErrorState();
            }
        });
    }

    private void showSearchResults(List<Car> cars) {
        nearestCars.clear();
        nearestCars.addAll(cars.subList(0, Math.min(MAX_RESULTS, cars.size())));
        adapter.notifyDataSetChanged();

        isSearching = false;
        hasSearched = true;
        shouldShowResults = true;
        btnSearchNearby.setEnabled(true);
        btnSearchNearby.setText(R.string.splash_search_again_button);
        stopAnimator(activePulseAnimator);
        startIdlePulse();
        applyCompactLayout(true);
        layoutResults.setVisibility(View.VISIBLE);

        if (nearestCars.isEmpty()) {
            applyStatusText(getString(R.string.splash_status_no_results));
            applyResultsTitle(getString(R.string.splash_results_empty_title));
        } else {
            applyStatusText(getString(R.string.splash_status_found, nearestCars.size()));
            applyResultsTitle(getString(R.string.splash_results_title));
        }
    }

    private void showLocationDeniedState() {
        isSearching = false;
        hasSearched = true;
        shouldShowResults = false;
        btnSearchNearby.setEnabled(true);
        btnSearchNearby.setText(R.string.splash_retry_button);
        stopAnimator(activePulseAnimator);
        startIdlePulse();
        applyCompactLayout(true);
        layoutResults.setVisibility(View.GONE);
        applyStatusText(getString(R.string.splash_status_location_denied));

        new AlertDialog.Builder(SplashActivity.this)
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

    private void showLocationUnavailableState() {
        isSearching = false;
        hasSearched = true;
        shouldShowResults = false;
        btnSearchNearby.setEnabled(true);
        btnSearchNearby.setText(R.string.splash_retry_button);
        stopAnimator(activePulseAnimator);
        startIdlePulse();
        applyCompactLayout(true);
        layoutResults.setVisibility(View.GONE);
        applyStatusText(getString(R.string.splash_status_location_unavailable));
    }

    private void showSearchErrorState() {
        isSearching = false;
        hasSearched = true;
        shouldShowResults = false;
        btnSearchNearby.setEnabled(true);
        btnSearchNearby.setText(R.string.splash_retry_button);
        stopAnimator(activePulseAnimator);
        startIdlePulse();
        applyCompactLayout(true);
        layoutResults.setVisibility(View.GONE);
        applyStatusText(getString(R.string.splash_status_error));
    }

    private void openExplore() {
        startActivity(new Intent(this, BrowseCarsActivity.class));
    }

    private void openCarDetails(Car car) {
        Intent intent = new Intent(this, CarDetailsActivity.class);
        intent.putExtra("car", car);
        startActivity(intent);
    }

    private void startIdlePulse() {
        stopAnimator(idlePulseAnimator);
        pulseOuter.setAlpha(0.18f);
        pulseInner.setAlpha(0.28f);
        pulseOuter.setScaleX(1f);
        pulseOuter.setScaleY(1f);
        pulseInner.setScaleX(1f);
        pulseInner.setScaleY(1f);
        idlePulseAnimator = buildPulseAnimator(1.18f, 2600L, false);
        idlePulseAnimator.start();
    }

    private void startActivePulse() {
        stopAnimator(idlePulseAnimator);
        stopAnimator(activePulseAnimator);
        activePulseAnimator = buildPulseAnimator(1.45f, 1100L, true);
        activePulseAnimator.start();
    }

    private AnimatorSet buildPulseAnimator(float scaleTarget, long durationMs, boolean includeButtonBounce) {
        ObjectAnimator outerScaleX = ObjectAnimator.ofFloat(pulseOuter, View.SCALE_X, 1f, scaleTarget);
        ObjectAnimator outerScaleY = ObjectAnimator.ofFloat(pulseOuter, View.SCALE_Y, 1f, scaleTarget);
        ObjectAnimator outerAlpha = ObjectAnimator.ofFloat(pulseOuter, View.ALPHA, 0.28f, 0f);

        ObjectAnimator innerScaleX = ObjectAnimator.ofFloat(pulseInner, View.SCALE_X, 1f, scaleTarget - 0.12f);
        ObjectAnimator innerScaleY = ObjectAnimator.ofFloat(pulseInner, View.SCALE_Y, 1f, scaleTarget - 0.12f);
        ObjectAnimator innerAlpha = ObjectAnimator.ofFloat(pulseInner, View.ALPHA, 0.4f, 0f);

        outerScaleX.setRepeatCount(ObjectAnimator.INFINITE);
        outerScaleY.setRepeatCount(ObjectAnimator.INFINITE);
        outerAlpha.setRepeatCount(ObjectAnimator.INFINITE);
        innerScaleX.setRepeatCount(ObjectAnimator.INFINITE);
        innerScaleY.setRepeatCount(ObjectAnimator.INFINITE);
        innerAlpha.setRepeatCount(ObjectAnimator.INFINITE);

        outerScaleX.setRepeatMode(ObjectAnimator.RESTART);
        outerScaleY.setRepeatMode(ObjectAnimator.RESTART);
        outerAlpha.setRepeatMode(ObjectAnimator.RESTART);
        innerScaleX.setRepeatMode(ObjectAnimator.RESTART);
        innerScaleY.setRepeatMode(ObjectAnimator.RESTART);
        innerAlpha.setRepeatMode(ObjectAnimator.RESTART);

        outerScaleX.setDuration(durationMs);
        outerScaleY.setDuration(durationMs);
        outerAlpha.setDuration(durationMs);
        innerScaleX.setDuration(durationMs);
        innerScaleY.setDuration(durationMs);
        innerAlpha.setDuration(durationMs);

        outerScaleX.setInterpolator(new AccelerateDecelerateInterpolator());
        outerScaleY.setInterpolator(new AccelerateDecelerateInterpolator());
        outerAlpha.setInterpolator(new AccelerateDecelerateInterpolator());
        innerScaleX.setInterpolator(new AccelerateDecelerateInterpolator());
        innerScaleY.setInterpolator(new AccelerateDecelerateInterpolator());
        innerAlpha.setInterpolator(new AccelerateDecelerateInterpolator());

        AnimatorSet animatorSet = new AnimatorSet();
        if (includeButtonBounce) {
            ObjectAnimator buttonScaleX = ObjectAnimator.ofFloat(btnSearchNearby, View.SCALE_X, 1f, 1.06f, 1f);
            ObjectAnimator buttonScaleY = ObjectAnimator.ofFloat(btnSearchNearby, View.SCALE_Y, 1f, 1.06f, 1f);
            buttonScaleX.setRepeatCount(ObjectAnimator.INFINITE);
            buttonScaleY.setRepeatCount(ObjectAnimator.INFINITE);
            buttonScaleX.setDuration(durationMs);
            buttonScaleY.setDuration(durationMs);
            buttonScaleX.setInterpolator(new AccelerateDecelerateInterpolator());
            buttonScaleY.setInterpolator(new AccelerateDecelerateInterpolator());
            animatorSet.playTogether(
                    outerScaleX, outerScaleY, outerAlpha,
                    innerScaleX, innerScaleY, innerAlpha,
                    buttonScaleX, buttonScaleY
            );
        } else {
            animatorSet.playTogether(
                    outerScaleX, outerScaleY, outerAlpha,
                    innerScaleX, innerScaleY, innerAlpha
            );
        }
        return animatorSet;
    }

    private void stopAnimator(@Nullable AnimatorSet animatorSet) {
        if (animatorSet != null) {
            animatorSet.cancel();
        }
    }

    private void renderCurrentState() {
        if (isSearching) {
            btnSearchNearby.setEnabled(false);
            btnSearchNearby.setText(R.string.splash_searching_button);
            layoutResults.setVisibility(View.GONE);
            if (currentStatusText == null) {
                applyStatusText(getString(R.string.splash_status_searching));
            } else {
                tvStatus.setText(currentStatusText);
            }
            startActivePulse();
            return;
        }

        btnSearchNearby.setEnabled(true);
        btnSearchNearby.setText(hasSearched ? R.string.splash_search_again_button : R.string.splash_search_button);
        layoutResults.setVisibility(shouldShowResults ? View.VISIBLE : View.GONE);
        applyCompactLayout(hasSearched);

        if (currentStatusText != null) {
            tvStatus.setText(currentStatusText);
        } else {
            applyStatusText(getString(R.string.splash_status_idle));
        }

        if (currentResultsTitleText != null) {
            tvResultsTitle.setText(currentResultsTitleText);
        }

        adapter.notifyDataSetChanged();
        startIdlePulse();
    }

    private void applyStatusText(@NonNull String text) {
        currentStatusText = text;
        if (tvStatus != null) {
            tvStatus.setText(text);
        }
    }

    private void applyResultsTitle(@NonNull String text) {
        currentResultsTitleText = text;
        if (tvResultsTitle != null) {
            tvResultsTitle.setText(text);
        }
    }

    private void applyCompactLayout(boolean compact) {
        if (tvBadge == null || tvTitle == null || tvSubtitle == null || tvResultsSubtitle == null
                || layoutContent == null || layoutCircle == null) {
            return;
        }

        tvBadge.setVisibility(compact ? View.GONE : View.VISIBLE);
        tvTitle.setVisibility(compact ? View.GONE : View.VISIBLE);
        tvSubtitle.setVisibility(compact ? View.GONE : View.VISIBLE);
        tvResultsSubtitle.setVisibility(compact ? View.GONE : View.VISIBLE);
        tvTitle.setText(compact ? R.string.splash_title_compact : R.string.splash_title);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f);

        ViewGroup.MarginLayoutParams titleParams = (ViewGroup.MarginLayoutParams) tvTitle.getLayoutParams();
        titleParams.topMargin = dpToPx(12);
        tvTitle.setLayoutParams(titleParams);

        ViewGroup.MarginLayoutParams circleParams = (ViewGroup.MarginLayoutParams) layoutCircle.getLayoutParams();
        circleParams.topMargin = dpToPx(compact ? 4 : 18);
        layoutCircle.setLayoutParams(circleParams);
        updateCircleSizing(compact);

        ViewGroup.MarginLayoutParams statusParams = (ViewGroup.MarginLayoutParams) tvStatus.getLayoutParams();
        statusParams.topMargin = dpToPx(compact ? 0 : 8);
        tvStatus.setLayoutParams(statusParams);
        tvStatus.setMaxLines(compact ? 2 : 3);

        ViewGroup.MarginLayoutParams resultsParams = (ViewGroup.MarginLayoutParams) layoutResults.getLayoutParams();
        resultsParams.topMargin = dpToPx(compact ? 4 : 14);
        layoutResults.setLayoutParams(resultsParams);
        layoutResults.setTranslationY(compact ? -dpToPx(26) : 0f);
        tvStatus.setTranslationY(compact ? -dpToPx(28) : 0f);
        layoutCircle.setTranslationY(compact ? -dpToPx(10) : 0f);

        layoutContent.setPadding(
                layoutContent.getPaddingLeft(),
                dpToPx(compact ? 12 : 20),
                layoutContent.getPaddingRight(),
                dpToPx(compact ? 16 : 24)
        );
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void updateCircleSizing(boolean compact) {
        int outerSize = dpToPx(compact ? 180 : 280);
        int innerSize = dpToPx(compact ? 144 : 220);
        int buttonSize = dpToPx(compact ? 112 : 168);

        ViewGroup.LayoutParams circleLayoutParams = layoutCircle.getLayoutParams();
        circleLayoutParams.width = outerSize;
        circleLayoutParams.height = outerSize;
        layoutCircle.setLayoutParams(circleLayoutParams);

        ViewGroup.LayoutParams outerLayoutParams = pulseOuter.getLayoutParams();
        outerLayoutParams.width = outerSize;
        outerLayoutParams.height = outerSize;
        pulseOuter.setLayoutParams(outerLayoutParams);

        ViewGroup.LayoutParams innerLayoutParams = pulseInner.getLayoutParams();
        innerLayoutParams.width = innerSize;
        innerLayoutParams.height = innerSize;
        pulseInner.setLayoutParams(innerLayoutParams);

        ViewGroup.LayoutParams buttonLayoutParams = btnSearchNearby.getLayoutParams();
        buttonLayoutParams.width = buttonSize;
        buttonLayoutParams.height = buttonSize;
        btnSearchNearby.setLayoutParams(buttonLayoutParams);

        btnSearchNearby.setTextSize(TypedValue.COMPLEX_UNIT_SP, compact ? 16f : 20f);
        btnSearchNearby.setIconPadding(dpToPx(compact ? 8 : 12));
    }
}
