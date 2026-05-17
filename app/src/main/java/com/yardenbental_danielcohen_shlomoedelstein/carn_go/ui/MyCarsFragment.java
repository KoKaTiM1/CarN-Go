package com.yardenbental_danielcohen_shlomoedelstein.carn_go.ui;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
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

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.R;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.adapter.CarAdapter;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.firebase.FirestoreHelper;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.model.Car;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Fragment that displays the cars owned or rented by the current user.
 */
public class MyCarsFragment extends Fragment {

    private ActivityResultLauncher<String> pickImageLauncher;
    private RecyclerView rvMyCars;
    private CarAdapter adapter;
    private List<Car> myCarsList = new ArrayList<>();
    private View layoutEmpty;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize the image picker launcher
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        // Image selected! Now show a dialog to get car details.
                        showAddCarDialog(uri);
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_my_cars, container, false);

        rvMyCars = view.findViewById(R.id.rvMyCars);
        layoutEmpty = view.findViewById(R.id.layoutEmpty);

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

        // Find the "Add a Car" button and set its click listener
        View addCarBtn = view.findViewById(R.id.btnAddCar);
        if (addCarBtn != null) {
            addCarBtn.setOnClickListener(v -> {
                // Open the local photo gallery
                pickImageLauncher.launch("image/*");
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
                            String tag = document.getString("tag");

                            myCarsList.add(new Car(
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
                                    tag != null ? tag : ""
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
                    Toast.makeText(getContext(), "Failed to load your cars", Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Shows a dialog to edit car details.
     */
    private void showEditCarDialog(Car car) {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_car, null);
        EditText etName = dialogView.findViewById(R.id.etCarName);
        EditText etPrice = dialogView.findViewById(R.id.etPrice);
        EditText etLocation = dialogView.findViewById(R.id.etLocation);
        EditText etType = dialogView.findViewById(R.id.etType);
        EditText etTransmission = dialogView.findViewById(R.id.etTransmission);
        EditText etSeats = dialogView.findViewById(R.id.etSeats);
        EditText etFuelType = dialogView.findViewById(R.id.etFuelType);

        // Pre-fill with current data
        etName.setText(car.getName());
        etPrice.setText(String.valueOf(car.getPricePerHour()));
        etLocation.setText(car.getLocation());
        etType.setText(car.getType());
        etTransmission.setText(car.getTransmission());
        etSeats.setText(String.valueOf(car.getSeats()));
        etFuelType.setText(car.getFuelType());

        new AlertDialog.Builder(requireContext())
                .setTitle("Edit Car Details")
                .setView(dialogView)
                .setPositiveButton("Update", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String priceStr = etPrice.getText().toString().trim();
                    String location = etLocation.getText().toString().trim();
                    String type = etType.getText().toString().trim();
                    String transmission = etTransmission.getText().toString().trim();
                    String seatsStr = etSeats.getText().toString().trim();
                    String fuelType = etFuelType.getText().toString().trim();

                    if (!name.isEmpty() && !priceStr.isEmpty()) {
                        double price = Double.parseDouble(priceStr);
                        int seats = seatsStr.isEmpty() ? 5 : Integer.parseInt(seatsStr);
                        updateCarData(car.getId(), name, price, location, type, transmission, seats, fuelType);
                    } else {
                        Toast.makeText(getContext(), "Name and price are required", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateCarData(String carId, String name, double price, String location, String type, String transmission, int seats, String fuelType) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        updates.put("pricePerHour", price);
        updates.put("location", location);
        updates.put("type", type);
        updates.put("transmission", transmission);
        updates.put("seats", seats);
        updates.put("fuelType", fuelType);

        db.collection("cars").document(carId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "Car updated!", Toast.LENGTH_SHORT).show();
                    fetchMyCars();
                })
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Error updating: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void showDeleteConfirmationDialog(Car car) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Car")
                .setMessage("Are you sure you want to delete this car?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    FirebaseFirestore.getInstance().collection("cars").document(car.getId())
                            .delete()
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(getContext(), "Car deleted", Toast.LENGTH_SHORT).show();
                                fetchMyCars();
                            })
                            .addOnFailureListener(e -> Toast.makeText(getContext(), "Error deleting: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Shows a dialog to input car details before uploading.
     */
    private void showAddCarDialog(Uri imageUri) {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_car, null);
        EditText etName = dialogView.findViewById(R.id.etCarName);
        EditText etPrice = dialogView.findViewById(R.id.etPrice);
        EditText etLocation = dialogView.findViewById(R.id.etLocation);
        EditText etType = dialogView.findViewById(R.id.etType);
        EditText etTransmission = dialogView.findViewById(R.id.etTransmission);
        EditText etSeats = dialogView.findViewById(R.id.etSeats);
        EditText etFuelType = dialogView.findViewById(R.id.etFuelType);

        new AlertDialog.Builder(requireContext())
                .setTitle("Car Details")
                .setView(dialogView)
                .setPositiveButton("Add", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String priceStr = etPrice.getText().toString().trim();
                    String location = etLocation.getText().toString().trim();
                    String type = etType.getText().toString().trim();
                    String transmission = etTransmission.getText().toString().trim();
                    String seatsStr = etSeats.getText().toString().trim();
                    String fuelType = etFuelType.getText().toString().trim();

                    if (!name.isEmpty() && !priceStr.isEmpty()) {
                        double price = Double.parseDouble(priceStr);
                        int seats = seatsStr.isEmpty() ? 5 : Integer.parseInt(seatsStr);
                        uploadCarData(name, price, location, type, transmission, seats, fuelType, imageUri);
                    } else {
                        Toast.makeText(getContext(), "Name and price are required", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Converts image to Base64 and saves everything directly to Firestore.
     * No Firebase Storage bucket required.
     */
    private void uploadCarData(String carName, double price, String location, String type, String transmission, int seats, String fuelType, Uri imageUri) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Toast.makeText(getContext(), "Processing listing...", Toast.LENGTH_SHORT).show();

        try {
            // 1. Convert Image Uri to Bitmap
            InputStream inputStream = requireContext().getContentResolver().openInputStream(imageUri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

            // 2. IMPORTANT: Resize & Compress for Firestore (Firestore has a 1MB per document limit)
            // We scale down the image and compress quality to 70% to keep it small.
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, 640, 480, true);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream);

            // 3. Convert to Base64 String
            byte[] byteArray = outputStream.toByteArray();
            String encodedImage = Base64.encodeToString(byteArray, Base64.DEFAULT);

            // 4. Save to Firestore
            Map<String, Object> carData = new HashMap<>();
            carData.put("name", carName);
            carData.put("pricePerHour", price);
            carData.put("location", location);
            carData.put("type", type);
            carData.put("imageUrl", encodedImage); // This now stores the bit data
            carData.put("ownerId", FirestoreHelper.getCurrentUserId(getContext()));
            carData.put("rating", 5.0);
            carData.put("transmission", transmission);
            carData.put("seats", seats);
            carData.put("fuelType", fuelType);

            db.collection("cars").add(carData)
                    .addOnSuccessListener(documentReference -> {
                        Toast.makeText(getContext(), "Listing added successfully!", Toast.LENGTH_SHORT).show();
                        fetchMyCars(); // Refresh the list
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(getContext(), "Firestore Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Image processing failed.", Toast.LENGTH_SHORT).show();
        }
    }
}
