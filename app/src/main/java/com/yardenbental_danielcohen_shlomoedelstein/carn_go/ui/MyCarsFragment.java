package com.yardenbental_danielcohen_shlomoedelstein.carn_go.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
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

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.R;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.adapter.CarAdapter;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.firebase.FirestoreHelper;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.model.Car;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MyCarsFragment extends Fragment {

    private static class LocationDraft {
        String displayName;
        Double latitude;
        Double longitude;

        LocationDraft(String displayName, Double latitude, Double longitude) {
            this.displayName = displayName;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ActivityResultLauncher<String> pickImageLauncher;
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;
    private FusedLocationProviderClient fusedLocationClient;
    private Runnable pendingLocationAction;
    private RecyclerView rvMyCars;
    private CarAdapter adapter;
    private final List<Car> myCarsList = new ArrayList<>();
    private View layoutEmpty;
    private View addCarBtn;

    private long selectedStartTimestamp = 0;
    private long selectedEndTimestamp = 0;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext());

        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        showAddCarDialog(uri);
                    }
                }
        );

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null && result.getData().getExtras() != null) {
                        Bitmap photo = (Bitmap) result.getData().getExtras().get("data");
                        if (photo != null) {
                            showAddCarDialog(photo);
                        }
                    }
                }
        );

        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                permissions -> {
                    boolean granted = Boolean.TRUE.equals(permissions.get(Manifest.permission.ACCESS_FINE_LOCATION))
                            || Boolean.TRUE.equals(permissions.get(Manifest.permission.ACCESS_COARSE_LOCATION));

                    if (granted) {
                        if (pendingLocationAction != null) {
                            pendingLocationAction.run();
                        }
                    } else {
                        showLocationPermissionSettingsDialog();
                    }
                    pendingLocationAction = null;
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_my_cars, container, false);

        rvMyCars = view.findViewById(R.id.rvMyCars);
        layoutEmpty = view.findViewById(R.id.layoutEmpty);
        addCarBtn = view.findViewById(R.id.btnAddCar);

        rvMyCars.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new CarAdapter(myCarsList, new CarAdapter.OnCarClickListener() {
            @Override
            public void onCarClick(Car car) {
                Bundle bundle = new Bundle();
                bundle.putSerializable("car", car);
                Navigation.findNavController(view).navigate(R.id.action_myCarsFragment_to_carDetailsFragment, bundle);
            }

            @Override
            public void onEditClick(Car car) {
                showEditCarDialog(car);
            }

            @Override
            public void onDeleteClick(Car car) {
                showDeleteConfirmationDialog(car);
            }
        });
        adapter.setShowEditDeleteOptions(true);
        rvMyCars.setAdapter(adapter);

        if (addCarBtn != null) {
            addCarBtn.setOnClickListener(v -> {
                String[] options = {getString(R.string.take_photo), getString(R.string.choose_gallery)};
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.add_car_photo)
                        .setItems(options, (dialog, which) -> {
                            if (which == 0) {
                                Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                                cameraLauncher.launch(takePictureIntent);
                            } else {
                                pickImageLauncher.launch("image/*");
                            }
                        })
                        .show();
            });
        }
        fetchMyCars();
        return view;
    }

    private void fetchMyCars() {
        String currentUserId = FirestoreHelper.getCurrentUserId(getContext());
        if (currentUserId == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("cars")
                .whereEqualTo("ownerId", currentUserId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    myCarsList.clear();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        try {
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
                            String ownerId = document.getString("ownerId");
                            Long availableFrom = document.getLong("availableFrom");
                            Long availableTo = document.getLong("availableTo");

                            myCarsList.add(new Car(
                                    document.getId(),
                                    name != null ? name : getString(R.string.unknown),
                                    type != null ? type : getString(R.string.standard),
                                    location != null ? location : getString(R.string.unknown),
                                    latitude,
                                    longitude,
                                    price != null ? price : 0.0,
                                    rating != null ? rating : 5.0,
                                    imageUrl,
                                    transmission != null ? transmission : getString(R.string.auto),
                                    seats,
                                    fuelType != null ? fuelType : getString(R.string.gas),
                                    "",
                                    ownerId != null ? ownerId : "",
                                    availableFrom != null ? availableFrom : 0,
                                    availableTo != null ? availableTo : 0
                            ));
                        } catch (Exception e) {
                            Log.e("MyCarsFragment", "Error parsing car", e);
                        }
                    }

                    if (myCarsList.isEmpty()) {
                        layoutEmpty.setVisibility(View.VISIBLE);
                        rvMyCars.setVisibility(View.GONE);
                    } else {
                        layoutEmpty.setVisibility(View.GONE);
                        rvMyCars.setVisibility(View.VISIBLE);
                    }
                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> Toast.makeText(getContext(), R.string.failed_load_cars, Toast.LENGTH_SHORT).show());
    }

    private void showEditCarDialog(Car car) {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_car, null);
        TextInputEditText etName = dialogView.findViewById(R.id.etCarName);
        TextInputEditText etPrice = dialogView.findViewById(R.id.etPrice);
        TextInputEditText etLocation = dialogView.findViewById(R.id.etLocation);
        TextInputEditText etType = dialogView.findViewById(R.id.etType);
        TextInputEditText etTransmission = dialogView.findViewById(R.id.etTransmission);
        TextInputEditText etSeats = dialogView.findViewById(R.id.etSeats);
        TextInputEditText etFuelType = dialogView.findViewById(R.id.etFuelType);
        Button btnUseCurrentLocation = dialogView.findViewById(R.id.btnUseCurrentLocation);
        Button btnPickStart = dialogView.findViewById(R.id.btnPickStart);
        Button btnPickEnd = dialogView.findViewById(R.id.btnPickEnd);
        TextView tvAvailability = dialogView.findViewById(R.id.tvSelectedAvailability);
        LocationDraft locationDraft = new LocationDraft(car.getLocation(), car.getLatitude(), car.getLongitude());

        etName.setText(car.getName());
        etPrice.setText(String.valueOf(car.getPricePerHour()));
        etLocation.setText(car.getLocation());
        etType.setText(car.getType());
        etTransmission.setText(car.getTransmission());
        etSeats.setText(String.valueOf(car.getSeats()));
        etFuelType.setText(car.getFuelType());

        selectedStartTimestamp = car.getAvailableFrom();
        selectedEndTimestamp = car.getAvailableTo();
        normalizeAvailabilityForEditing();
        updateAvailabilityText(tvAvailability);

        btnUseCurrentLocation.setOnClickListener(v -> fillLocationFromGps(etLocation, locationDraft));
        btnPickStart.setOnClickListener(v -> pickDateTime(true, tvAvailability));
        btnPickEnd.setOnClickListener(v -> pickDateTime(false, tvAvailability));

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.edit_car_details)
                .setView(dialogView)
                .setPositiveButton(R.string.update, null)
                .setNegativeButton(R.string.cancel, null)
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            CarFormData formData = buildCarFormData(etName, etPrice, etLocation, etType, etTransmission, etSeats, etFuelType);
            if (formData == null || !validateAvailabilitySelection()) {
                return;
            }

            Double latitude = formData.location.equals(locationDraft.displayName) ? locationDraft.latitude : null;
            Double longitude = formData.location.equals(locationDraft.displayName) ? locationDraft.longitude : null;
            updateCarData(car.getId(), formData.name, formData.price, formData.location, latitude, longitude,
                    formData.type, formData.transmission, formData.seats, formData.fuelType,
                    selectedStartTimestamp, selectedEndTimestamp);
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void updateCarData(String carId, String name, double price, String location, Double latitude, Double longitude, String type, String transmission, int seats, String fuelType, long start, long end) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        updates.put("pricePerHour", price);
        updates.put("location", location);
        updates.put("latitude", latitude);
        updates.put("longitude", longitude);
        updates.put("type", type);
        updates.put("transmission", transmission);
        updates.put("seats", seats);
        updates.put("fuelType", fuelType);
        updates.put("availableFrom", start);
        updates.put("availableTo", end);

        db.collection("cars").document(carId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), R.string.car_updated, Toast.LENGTH_SHORT).show();
                    fetchMyCars();
                })
                .addOnFailureListener(e -> Toast.makeText(getContext(), getString(R.string.error_updating, e.getMessage()), Toast.LENGTH_SHORT).show());
    }

    private void showDeleteConfirmationDialog(Car car) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_car)
                .setMessage(R.string.delete_confirmation)
                .setPositiveButton(R.string.delete, (dialog, which) -> FirebaseFirestore.getInstance().collection("cars").document(car.getId())
                        .delete()
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(getContext(), R.string.car_deleted, Toast.LENGTH_SHORT).show();
                            fetchMyCars();
                        })
                        .addOnFailureListener(e -> Toast.makeText(getContext(), getString(R.string.error_deleting, e.getMessage()), Toast.LENGTH_SHORT).show()))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showAddCarDialog(Object imageSource) {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_car, null);
        TextInputEditText etName = dialogView.findViewById(R.id.etCarName);
        TextInputEditText etPrice = dialogView.findViewById(R.id.etPrice);
        TextInputEditText etLocation = dialogView.findViewById(R.id.etLocation);
        TextInputEditText etType = dialogView.findViewById(R.id.etType);
        TextInputEditText etTransmission = dialogView.findViewById(R.id.etTransmission);
        TextInputEditText etSeats = dialogView.findViewById(R.id.etSeats);
        TextInputEditText etFuelType = dialogView.findViewById(R.id.etFuelType);
        Button btnUseCurrentLocation = dialogView.findViewById(R.id.btnUseCurrentLocation);
        Button btnPickStart = dialogView.findViewById(R.id.btnPickStart);
        Button btnPickEnd = dialogView.findViewById(R.id.btnPickEnd);
        TextView tvAvailability = dialogView.findViewById(R.id.tvSelectedAvailability);
        LocationDraft locationDraft = new LocationDraft(null, null, null);

        selectedStartTimestamp = 0;
        selectedEndTimestamp = 0;

        btnUseCurrentLocation.setOnClickListener(v -> fillLocationFromGps(etLocation, locationDraft));
        btnPickStart.setOnClickListener(v -> pickDateTime(true, tvAvailability));
        btnPickEnd.setOnClickListener(v -> pickDateTime(false, tvAvailability));

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.add_new_car)
                .setView(dialogView)
                .setPositiveButton(R.string.add, null)
                .setNegativeButton(R.string.cancel, null)
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            CarFormData formData = buildCarFormData(etName, etPrice, etLocation, etType, etTransmission, etSeats, etFuelType);
            if (formData == null || !validateAvailabilitySelection()) {
                return;
            }

            Double latitude = formData.location.equals(locationDraft.displayName) ? locationDraft.latitude : null;
            Double longitude = formData.location.equals(locationDraft.displayName) ? locationDraft.longitude : null;
            uploadCarData(formData.name, formData.price, formData.location, latitude, longitude,
                    formData.type, formData.transmission, formData.seats, formData.fuelType,
                    imageSource, selectedStartTimestamp, selectedEndTimestamp);
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void pickDateTime(boolean isStart, TextView tvDisplay) {
        long minimumSelectableTimestamp = getMinimumSelectableTimestamp();
        Calendar todayUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        todayUtc.setTimeInMillis(minimumSelectableTimestamp);
        todayUtc.set(Calendar.HOUR_OF_DAY, 0);
        todayUtc.set(Calendar.MINUTE, 0);
        todayUtc.set(Calendar.SECOND, 0);
        todayUtc.set(Calendar.MILLISECOND, 0);
        long startOfTodayUtc = todayUtc.getTimeInMillis();

        CalendarConstraints.Builder constraintsBuilder = new CalendarConstraints.Builder();
        constraintsBuilder.setValidator(DateValidatorPointForward.from(startOfTodayUtc));

        long suggestedStartTimestamp = getSuggestedStartTimestamp();
        long suggestedEndTimestamp = getSuggestedEndTimestamp();

        long defaultSelection = isStart ? suggestedStartTimestamp : suggestedEndTimestamp;
        long selection = Math.max(defaultSelection, minimumSelectableTimestamp);

        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(isStart ? R.string.select_start_date : R.string.select_end_date)
                .setSelection(selection)
                .setCalendarConstraints(constraintsBuilder.build())
                .build();

        datePicker.addOnPositiveButtonClickListener(selectedDate -> {
            Calendar c = Calendar.getInstance();
            if (isStart) {
                c.setTimeInMillis(suggestedStartTimestamp);
            } else {
                c.setTimeInMillis(suggestedEndTimestamp);
            }

            MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_24H)
                    .setHour(c.get(Calendar.HOUR_OF_DAY))
                    .setMinute(c.get(Calendar.MINUTE))
                    .setTitleText(isStart ? R.string.select_start_time : R.string.select_end_time)
                    .build();

            timePicker.addOnPositiveButtonClickListener(v -> {
                Calendar calendar = Calendar.getInstance();
                calendar.setTimeInMillis(selectedDate);
                calendar.set(Calendar.HOUR_OF_DAY, timePicker.getHour());
                calendar.set(Calendar.MINUTE, timePicker.getMinute());
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);

                if (isStart) {
                    long chosenStart = calendar.getTimeInMillis();
                    if (chosenStart < minimumSelectableTimestamp) {
                        Toast.makeText(getContext(), R.string.error_start_in_past, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    selectedStartTimestamp = chosenStart;
                    if (selectedEndTimestamp != 0 && selectedEndTimestamp <= selectedStartTimestamp) {
                        selectedEndTimestamp = selectedStartTimestamp + TimeUnit.HOURS.toMillis(1);
                    }
                } else {
                    long chosenEnd = calendar.getTimeInMillis();
                    if (selectedStartTimestamp != 0 && chosenEnd <= selectedStartTimestamp) {
                        Toast.makeText(getContext(), R.string.error_end_before_start, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    selectedEndTimestamp = chosenEnd;
                }
                updateAvailabilityText(tvDisplay);
            });
            timePicker.show(getParentFragmentManager(), "TIME_PICKER");
        });
        datePicker.show(getParentFragmentManager(), "DATE_PICKER");
    }

    private void updateAvailabilityText(TextView tvDisplay) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
        String startStr = selectedStartTimestamp == 0 ? "..." : sdf.format(new Date(selectedStartTimestamp));
        String endStr = selectedEndTimestamp == 0 ? "..." : sdf.format(new Date(selectedEndTimestamp));
        tvDisplay.setText(getString(R.string.available_from_to, startStr, endStr));
    }

    private void normalizeAvailabilityForEditing() {
        long minimumSelectableTimestamp = getMinimumSelectableTimestamp();

        if (selectedEndTimestamp <= minimumSelectableTimestamp) {
            selectedStartTimestamp = minimumSelectableTimestamp;
            selectedEndTimestamp = selectedStartTimestamp + TimeUnit.HOURS.toMillis(1);
            return;
        }

        if (selectedStartTimestamp < minimumSelectableTimestamp) {
            selectedStartTimestamp = minimumSelectableTimestamp;
        }

        if (selectedEndTimestamp <= selectedStartTimestamp) {
            selectedEndTimestamp = selectedStartTimestamp + TimeUnit.HOURS.toMillis(1);
        }
    }

    private long getSuggestedStartTimestamp() {
        long minimumSelectableTimestamp = getMinimumSelectableTimestamp();
        return selectedStartTimestamp != 0 ? Math.max(selectedStartTimestamp, minimumSelectableTimestamp) : minimumSelectableTimestamp;
    }

    private long getSuggestedEndTimestamp() {
        long minimumSelectableTimestamp = getMinimumSelectableTimestamp();
        if (selectedEndTimestamp != 0 && selectedEndTimestamp > minimumSelectableTimestamp) {
            return selectedEndTimestamp;
        }
        long baseStart = getSuggestedStartTimestamp();
        return Math.max(baseStart + TimeUnit.HOURS.toMillis(1), minimumSelectableTimestamp + TimeUnit.HOURS.toMillis(1));
    }

    private long getMinimumSelectableTimestamp() {
        long now = System.currentTimeMillis();
        long minuteMs = TimeUnit.MINUTES.toMillis(1);
        return now + (minuteMs - (now % minuteMs));
    }

    @Nullable
    private CarFormData buildCarFormData(TextInputEditText etName,
                                         TextInputEditText etPrice,
                                         TextInputEditText etLocation,
                                         TextInputEditText etType,
                                         TextInputEditText etTransmission,
                                         TextInputEditText etSeats,
                                         TextInputEditText etFuelType) {
        String name = safeText(etName);
        String priceStr = safeText(etPrice);
        String location = safeText(etLocation);
        String type = safeText(etType);
        String transmission = safeText(etTransmission);
        String seatsStr = safeText(etSeats);
        String fuelType = safeText(etFuelType);

        if (name.isEmpty() || priceStr.isEmpty() || location.isEmpty()) {
            Toast.makeText(getContext(), R.string.error_required_fields, Toast.LENGTH_SHORT).show();
            return null;
        }

        try {
            double price = Double.parseDouble(priceStr);
            int seats = seatsStr.isEmpty() ? 5 : Integer.parseInt(seatsStr);
            return new CarFormData(name, price, location, type, transmission, seats, fuelType);
        } catch (NumberFormatException error) {
            Toast.makeText(getContext(), R.string.error_invalid_input, Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private boolean validateAvailabilitySelection() {
        if (selectedStartTimestamp == 0 || selectedEndTimestamp == 0) {
            Toast.makeText(getContext(), R.string.error_required_fields, Toast.LENGTH_SHORT).show();
            return false;
        }
        if (selectedStartTimestamp < getMinimumSelectableTimestamp()) {
            Toast.makeText(getContext(), R.string.error_start_in_past, Toast.LENGTH_SHORT).show();
            return false;
        }
        if (selectedEndTimestamp <= selectedStartTimestamp) {
            Toast.makeText(getContext(), R.string.error_end_before_start, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    @NonNull
    private String safeText(TextInputEditText field) {
        return field.getText() != null ? field.getText().toString().trim() : "";
    }

    private void uploadCarData(String carName, double price, String location, Double latitude, Double longitude, String type, String transmission, int seats, String fuelType, Object imageSource, long start, long end) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Toast.makeText(getContext(), R.string.processing_listing, Toast.LENGTH_SHORT).show();

        executorService.execute(() -> {
            try {
                Bitmap bitmap = null;
                if (imageSource instanceof Uri) {
                    InputStream inputStream = requireContext().getContentResolver().openInputStream((Uri) imageSource);
                    bitmap = BitmapFactory.decodeStream(inputStream);
                } else if (imageSource instanceof Bitmap) {
                    bitmap = (Bitmap) imageSource;
                }

                if (bitmap == null) {
                    mainHandler.post(() -> Toast.makeText(getContext(), R.string.error_image_processing, Toast.LENGTH_SHORT).show());
                    return;
                }

                Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, 640, 480, true);
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream);

                byte[] byteArray = outputStream.toByteArray();
                String encodedImage = Base64.encodeToString(byteArray, Base64.DEFAULT);

                Map<String, Object> carData = new HashMap<>();
                carData.put("name", carName);
                carData.put("pricePerHour", price);
                carData.put("location", location);
                carData.put("latitude", latitude);
                carData.put("longitude", longitude);
                carData.put("type", type);
                carData.put("imageUrl", encodedImage);
                carData.put("ownerId", FirestoreHelper.getCurrentUserId(getContext()));
                carData.put("rating", 5.0);
                carData.put("transmission", transmission);
                carData.put("seats", seats);
                carData.put("fuelType", fuelType);
                carData.put("availableFrom", start);
                carData.put("availableTo", end);

                db.collection("cars").add(carData)
                        .addOnSuccessListener(documentReference -> mainHandler.post(() -> {
                            Toast.makeText(getContext(), R.string.listing_added, Toast.LENGTH_SHORT).show();
                            fetchMyCars();
                        }))
                        .addOnFailureListener(e -> mainHandler.post(() -> Toast.makeText(getContext(), getString(R.string.firestore_error, e.getMessage()), Toast.LENGTH_SHORT).show()));

            } catch (Exception e) {
                Log.e("MyCarsFragment", "Upload failed", e);
                mainHandler.post(() -> Toast.makeText(getContext(), R.string.error_image_processing, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void fillLocationFromGps(EditText locationField, LocationDraft locationDraft) {
        ensureLocationPermission(() -> requestCurrentLocation(location -> reverseGeocode(location, resolvedLocation -> {
            locationDraft.displayName = resolvedLocation;
            locationDraft.latitude = location.getLatitude();
            locationDraft.longitude = location.getLongitude();
            locationField.setText(resolvedLocation);
            Toast.makeText(getContext(), R.string.location_detected, Toast.LENGTH_SHORT).show();
        })));
    }

    private void ensureLocationPermission(Runnable onGranted) {
        if (hasLocationPermission()) {
            onGranted.run();
            return;
        }

        pendingLocationAction = onGranted;
        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
                || shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.location_permission_title)
                    .setMessage(R.string.location_permission_owner_message)
                    .setPositiveButton(R.string.allow_location, (dialog, which) -> locationPermissionLauncher.launch(new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    }))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        } else {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCurrentLocation(LocationCallback callback) {
        if (!hasLocationPermission()) {
            return;
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        callback.onLocationResolved(location);
                    } else {
                        fusedLocationClient.getLastLocation()
                                .addOnSuccessListener(lastLocation -> {
                                    if (lastLocation != null) {
                                        callback.onLocationResolved(lastLocation);
                                    } else {
                                        Toast.makeText(getContext(), R.string.location_unavailable, Toast.LENGTH_SHORT).show();
                                    }
                                })
                                .addOnFailureListener(e -> Toast.makeText(getContext(), R.string.location_unavailable, Toast.LENGTH_SHORT).show());
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(getContext(), R.string.location_unavailable, Toast.LENGTH_SHORT).show());
    }

    private void reverseGeocode(Location location, AddressCallback callback) {
        executorService.execute(() -> {
            String resolvedLocation = getString(R.string.current_location_label);
            try {
                if (Geocoder.isPresent()) {
                    Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
                    List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                    if (addresses != null && !addresses.isEmpty()) {
                        resolvedLocation = formatAddress(addresses.get(0));
                    }
                }
            } catch (Exception e) {
                Log.w("MyCarsFragment", "Failed to reverse geocode location", e);
            }

            String finalResolvedLocation = resolvedLocation;
            mainHandler.post(() -> callback.onAddressResolved(finalResolvedLocation));
        });
    }

    private String formatAddress(Address address) {
        List<String> parts = new ArrayList<>();
        if (address.getThoroughfare() != null && !address.getThoroughfare().isEmpty()) {
            parts.add(address.getThoroughfare());
        }
        if (address.getSubLocality() != null && !address.getSubLocality().isEmpty()) {
            parts.add(address.getSubLocality());
        } else if (address.getLocality() != null && !address.getLocality().isEmpty()) {
            parts.add(address.getLocality());
        }
        if (parts.isEmpty() && address.getAdminArea() != null && !address.getAdminArea().isEmpty()) {
            parts.add(address.getAdminArea());
        }
        if (parts.isEmpty()) {
            return getString(R.string.current_location_label);
        }
        return String.join(", ", parts);
    }

    private void showLocationPermissionSettingsDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.location_permission_title)
                .setMessage(R.string.location_permission_settings_message)
                .setPositiveButton(R.string.open_settings, (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.fromParts("package", requireContext().getPackageName(), null));
                    startActivity(intent);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private interface LocationCallback {
        void onLocationResolved(Location location);
    }

    private interface AddressCallback {
        void onAddressResolved(String address);
    }

    private static class CarFormData {
        final String name;
        final double price;
        final String location;
        final String type;
        final String transmission;
        final int seats;
        final String fuelType;

        CarFormData(String name, double price, String location, String type, String transmission, int seats, String fuelType) {
            this.name = name;
            this.price = price;
            this.location = location;
            this.type = type;
            this.transmission = transmission;
            this.seats = seats;
            this.fuelType = fuelType;
        }
    }
}
