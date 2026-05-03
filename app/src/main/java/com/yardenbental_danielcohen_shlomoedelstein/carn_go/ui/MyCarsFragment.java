package com.yardenbental_danielcohen_shlomoedelstein.carn_go.ui;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.R;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.firebase.FirestoreHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fragment that displays the cars owned or rented by the current user.
 */
public class MyCarsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_my_cars, container, false);
    }

    private void uploadCarData(String carName, double price, Uri imageUri) {
        FirebaseStorage storage = FirebaseStorage.getInstance();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // 1. Upload Image to Storage (using standard java.util.UUID)
        StorageReference storageRef = storage.getReference().child("car_images/" + UUID.randomUUID().toString());

        storageRef.putFile(imageUri).addOnSuccessListener(taskSnapshot -> {
            // 2. Get the Download URL
            storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                String downloadUrl = uri.toString();

                // 3. Save to Firestore using the consistent ID helper
                Map<String, Object> carData = new HashMap<>();
                carData.put("name", carName);
                carData.put("pricePerHour", price);
                carData.put("imageUrl", downloadUrl);
                carData.put("ownerId", FirestoreHelper.getCurrentUserId(getContext()));

                db.collection("cars").add(carData)
                        .addOnSuccessListener(documentReference -> {
                            Toast.makeText(getContext(), "Listing added!", Toast.LENGTH_SHORT).show();
                        });
            });
        });
    }
}
