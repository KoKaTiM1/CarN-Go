package com.yardenbental_danielcohen_shlomoedelstein.carn_go.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.R;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.adapter.CarAdapter;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.model.Car;

import java.util.ArrayList;
import java.util.List;
import android.util.Log;
import android.widget.Toast;

/**
 * Fragment that allows users to browse a list of available cars.
 */
public class BrowseCarsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_browse_cars, container, false);

        // Initialize RecyclerView and set its layout manager
        RecyclerView rvCars = view.findViewById(R.id.rvCars);
        rvCars.setLayoutManager(new LinearLayoutManager(getContext()));

        List<Car> cars = new ArrayList<>();
        CarAdapter adapter = new CarAdapter(cars, car -> {
            Bundle bundle = new Bundle();
            bundle.putSerializable("car", car);
            Navigation.findNavController(view).navigate(R.id.action_browseCarsFragment_to_carDetailsFragment, bundle);
        });
        rvCars.setAdapter(adapter);

        // Fetch cars from Firestore
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("cars")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    cars.clear();
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
                                    tag != null ? tag : ""
                            ));
                        } catch (Exception e) {
                            Log.e("BrowseCarsFragment", "Error parsing car", e);
                        }
                    }
                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Failed to load cars", Toast.LENGTH_SHORT).show();
                });

        return view;
    }
}