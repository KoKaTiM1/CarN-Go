package com.yardenbental_danielcohen_shlomoedelstein.carn_go.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
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
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
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

/**
 * Fragment that displays the cars owned or rented by the current user.
 */
public class MyCarsFragment extends Fragment {

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ActivityResultLauncher<String> pickImageLauncher;
    private ActivityResultLauncher<Intent> cameraLauncher;
    private RecyclerView rvMyCars;
    private CarAdapter adapter;
    private List<Car> myCarsList = new ArrayList<>();
    private View layoutEmpty;
    private View addCarBtn;

    private long selectedStartTimestamp = 0;
    private long selectedEndTimestamp = 0;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize the image picker launcher
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        showAddCarDialog(uri);
                    }
                }
        );

        // Initialize the camera launcher
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Bitmap photo = (Bitmap) result.getData().getExtras().get("data");
                        if (photo != null) {
                            showAddCarDialog(photo);
                        }
                    }
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
                                // Camera
                                Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                                cameraLauncher.launch(takePictureIntent);
                            } else {
                                // Gallery
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
                                    price != null ? price : 0.0,
                                    rating != null ? rating : 5.0,
                                    imageUrl,
                                    transmission != null ? transmission : getString(R.string.auto),
                                    seats,
                                    fuelType != null ? fuelType : getString(R.string.gas),
                                    "", // tag
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
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), R.string.failed_load_cars, Toast.LENGTH_SHORT).show();
                });
    }

    private void showEditCarDialog(Car car) {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_car, null);
        EditText etName = dialogView.findViewById(R.id.etCarName);
        EditText etPrice = dialogView.findViewById(R.id.etPrice);
        EditText etLocation = dialogView.findViewById(R.id.etLocation);
        EditText etType = dialogView.findViewById(R.id.etType);
        EditText etTransmission = dialogView.findViewById(R.id.etTransmission);
        EditText etSeats = dialogView.findViewById(R.id.etSeats);
        EditText etFuelType = dialogView.findViewById(R.id.etFuelType);
        Button btnPickStart = dialogView.findViewById(R.id.btnPickStart);
        Button btnPickEnd = dialogView.findViewById(R.id.btnPickEnd);
        TextView tvAvailability = dialogView.findViewById(R.id.tvSelectedAvailability);

        etName.setText(car.getName());
        etPrice.setText(String.valueOf(car.getPricePerHour()));
        etLocation.setText(car.getLocation());
        etType.setText(car.getType());
        etTransmission.setText(car.getTransmission());
        etSeats.setText(String.valueOf(car.getSeats()));
        etFuelType.setText(car.getFuelType());

        selectedStartTimestamp = car.getAvailableFrom();
        selectedEndTimestamp = car.getAvailableTo();
        updateAvailabilityText(tvAvailability);

        btnPickStart.setOnClickListener(v -> pickDateTime(true, tvAvailability));
        btnPickEnd.setOnClickListener(v -> pickDateTime(false, tvAvailability));

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.edit_car_details)
                .setView(dialogView)
                .setPositiveButton(R.string.update, (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String priceStr = etPrice.getText().toString().trim();
                    String location = etLocation.getText().toString().trim();
                    String type = etType.getText().toString().trim();
                    String transmission = etTransmission.getText().toString().trim();
                    String seatsStr = etSeats.getText().toString().trim();
                    String fuelType = etFuelType.getText().toString().trim();

                    if (!name.isEmpty() && !priceStr.isEmpty() && selectedStartTimestamp != 0 && selectedEndTimestamp != 0) {
                        double price = Double.parseDouble(priceStr);
                        int seats = seatsStr.isEmpty() ? 5 : Integer.parseInt(seatsStr);
                        updateCarData(car.getId(), name, price, location, type, transmission, seats, fuelType, selectedStartTimestamp, selectedEndTimestamp);
                    } else {
                        Toast.makeText(getContext(), R.string.error_required_fields, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void updateCarData(String carId, String name, double price, String location, String type, String transmission, int seats, String fuelType, long start, long end) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        updates.put("pricePerHour", price);
        updates.put("location", location);
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
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    FirebaseFirestore.getInstance().collection("cars").document(car.getId())
                            .delete()
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(getContext(), R.string.car_deleted, Toast.LENGTH_SHORT).show();
                                fetchMyCars();
                            })
                            .addOnFailureListener(e -> Toast.makeText(getContext(), getString(R.string.error_deleting, e.getMessage()), Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showAddCarDialog(Object imageSource) {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_car, null);
        EditText etName = dialogView.findViewById(R.id.etCarName);
        EditText etPrice = dialogView.findViewById(R.id.etPrice);
        EditText etLocation = dialogView.findViewById(R.id.etLocation);
        EditText etType = dialogView.findViewById(R.id.etType);
        EditText etTransmission = dialogView.findViewById(R.id.etTransmission);
        EditText etSeats = dialogView.findViewById(R.id.etSeats);
        EditText etFuelType = dialogView.findViewById(R.id.etFuelType);
        Button btnPickStart = dialogView.findViewById(R.id.btnPickStart);
        Button btnPickEnd = dialogView.findViewById(R.id.btnPickEnd);
        TextView tvAvailability = dialogView.findViewById(R.id.tvSelectedAvailability);

        selectedStartTimestamp = 0;
        selectedEndTimestamp = 0;

        btnPickStart.setOnClickListener(v -> pickDateTime(true, tvAvailability));
        btnPickEnd.setOnClickListener(v -> pickDateTime(false, tvAvailability));

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.add_new_car)
                .setView(dialogView)
                .setPositiveButton(R.string.add, (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String priceStr = etPrice.getText().toString().trim();
                    String location = etLocation.getText().toString().trim();
                    String type = etType.getText().toString().trim();
                    String transmission = etTransmission.getText().toString().trim();
                    String seatsStr = etSeats.getText().toString().trim();
                    String fuelType = etFuelType.getText().toString().trim();

                    if (!name.isEmpty() && !priceStr.isEmpty() && selectedStartTimestamp != 0 && selectedEndTimestamp != 0) {
                        try {
                            double price = Double.parseDouble(priceStr);
                            int seats = seatsStr.isEmpty() ? 5 : Integer.parseInt(seatsStr);
                            uploadCarData(name, price, location, type, transmission, seats, fuelType, imageSource, selectedStartTimestamp, selectedEndTimestamp);
                        } catch (NumberFormatException e) {
                            Toast.makeText(getContext(), R.string.error_invalid_input, Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(getContext(), R.string.error_required_fields, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void pickDateTime(boolean isStart, TextView tvDisplay) {
        Calendar todayUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        todayUtc.set(Calendar.HOUR_OF_DAY, 0);
        todayUtc.set(Calendar.MINUTE, 0);
        todayUtc.set(Calendar.SECOND, 0);
        todayUtc.set(Calendar.MILLISECOND, 0);
        long startOfTodayUtc = todayUtc.getTimeInMillis();

        CalendarConstraints.Builder constraintsBuilder = new CalendarConstraints.Builder();
        constraintsBuilder.setValidator(DateValidatorPointForward.from(startOfTodayUtc));

        long selection = isStart ? (selectedStartTimestamp != 0 ? selectedStartTimestamp : System.currentTimeMillis())
                                : (selectedEndTimestamp != 0 ? selectedEndTimestamp : (selectedStartTimestamp != 0 ? selectedStartTimestamp + TimeUnit.HOURS.toMillis(1) : System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)));

        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(isStart ? R.string.select_start_date : R.string.select_end_date)
                .setSelection(selection)
                .setCalendarConstraints(constraintsBuilder.build())
                .build();

        datePicker.addOnPositiveButtonClickListener(selectedDate -> {
            Calendar c = Calendar.getInstance();
            if (isStart) {
                if (selectedStartTimestamp != 0) {
                    c.setTimeInMillis(selectedStartTimestamp);
                } else {
                    c.setTimeInMillis(System.currentTimeMillis());
                }
            } else {
                if (selectedEndTimestamp != 0) {
                    c.setTimeInMillis(selectedEndTimestamp);
                } else if (selectedStartTimestamp != 0) {
                    c.setTimeInMillis(selectedStartTimestamp + TimeUnit.HOURS.toMillis(1));
                } else {
                    c.setTimeInMillis(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(24));
                }
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
                    selectedStartTimestamp = calendar.getTimeInMillis();
                    if (selectedEndTimestamp != 0 && selectedEndTimestamp <= selectedStartTimestamp) {
                        selectedEndTimestamp = selectedStartTimestamp + TimeUnit.HOURS.toMillis(24);
                    }
                } else {
                    selectedEndTimestamp = calendar.getTimeInMillis();
                    if (selectedStartTimestamp != 0 && selectedStartTimestamp >= selectedEndTimestamp) {
                        selectedStartTimestamp = selectedEndTimestamp - TimeUnit.HOURS.toMillis(24);
                    }
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

    private void uploadCarData(String carName, double price, String location, String type, String transmission, int seats, String fuelType, Object imageSource, long start, long end) {
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

                // Resize & Compress
                Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, 640, 480, true);
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream);

                // Convert to Base64 String
                byte[] byteArray = outputStream.toByteArray();
                String encodedImage = Base64.encodeToString(byteArray, Base64.DEFAULT);

                // Save to Firestore
                Map<String, Object> carData = new HashMap<>();
                carData.put("name", carName);
                carData.put("pricePerHour", price);
                carData.put("location", location);
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
                        .addOnSuccessListener(documentReference -> {
                            mainHandler.post(() -> {
                                Toast.makeText(getContext(), R.string.listing_added, Toast.LENGTH_SHORT).show();
                                fetchMyCars();
                            });
                        })
                        .addOnFailureListener(e -> {
                            mainHandler.post(() -> Toast.makeText(getContext(), getString(R.string.firestore_error, e.getMessage()), Toast.LENGTH_SHORT).show());
                        });

            } catch (Exception e) {
                Log.e("MyCarsFragment", "Upload failed", e);
                mainHandler.post(() -> Toast.makeText(getContext(), R.string.error_image_processing, Toast.LENGTH_SHORT).show());
            }
        });
    }
}
