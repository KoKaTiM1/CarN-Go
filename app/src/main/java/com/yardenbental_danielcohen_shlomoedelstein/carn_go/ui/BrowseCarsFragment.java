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

import com.yardenbental_danielcohen_shlomoedelstein.carn_go.R;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.adapter.CarAdapter;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.model.Car;

import java.util.ArrayList;
import java.util.List;

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

        // Create a list of mock Car data
        List<Car> cars = new ArrayList<>();
        cars.add(new Car("Toyota Corolla", "Hybrid", "0.8 miles away", 8.0, 4.8, 
                "https://lh3.googleusercontent.com/aida-public/AB6AXuABbXyVzhNIMg_suayVWkmIzkuWfy5J7iJqYreB-NWmFkWs_09s9FqNcmGlR4u7CZKmHK5tlZ_nosuOquPE6YqBQtL04vytfwQF1hEjMcSwa9CqHikT3RYQ2b2Rpg8AkImi79Ep6B7CgXQaYQJvkdLoib7VGjghIQYaJnH_oDc38VgcXG0p1CyYyai25S2WOp7FPbItX1f_ppNYjwQc84LO2zsXKXRGl9wB-UFH6vEUeAjfFIiKmGEg1Bap2r9crFRuatp7Rcq0-vE", 
                "Auto", 5, "Hybrid", "TOP VALUE"));
        
        cars.add(new Car("Honda Civic Sport", "Compact", "1.5 miles away", 10.0, 4.9, 
                "https://lh3.googleusercontent.com/aida-public/AB6AXuDjeWQM4ZfeOoZK_fbqcVnL6nbGm9bm3avP98CN4x-FQVJF8gicogCpzBME4bj6ccarhQt1iTvYEJioIKT1BlNTl9r65eIBgojoP1IFK6YChsKTR04I4LHYYiLsPjqgUi_cpk-3EJc2jV6A4vxK7qLC_I901bmjYWY0ktKTmW6RdHMoyoA7dT0lGNWlBiy5TKWv2A4g7nqKAKOlDNLhtFAV3Jo2htQSVbZIXaXOd0qzBI_gvrSeYOQNKSe30Czar83EpNLsDKsTUeY", 
                "Auto", 5, "Gas", ""));

        cars.add(new Car("Volkswagen Golf", "Hatchback", "2.2 miles away", 7.0, 4.7, 
                "https://lh3.googleusercontent.com/aida-public/AB6AXuAhaKb2aUT3Cb3cTcABPtNxVRADDOWLw92UUDHe2U6q0YwIjNPsg5hhR6CZpLaiF017feK0rHCf3ea2kipyam9EwSAaF1g2d2aPyzMNtKUGwp4k2cg1GOvcMHLw4MyOk9WGACNILz8xu3deVJauO-6QAe85qStSs2Jp1nEJVSl-jl5AryO64mZq7uFq4p2U98jZDEscD2DozXbo84vUJSulY96HHLwx2r38-bUYHUCr1jIheWZ-jSBwaT4oubKEbH7DDH33Y0ml6i8", 
                "Manual", 5, "Gas", ""));

        // Set up the adapter with a click listener to navigate to car details
        CarAdapter adapter = new CarAdapter(cars, car -> {
            Bundle bundle = new Bundle();
            bundle.putSerializable("car", car);
            Navigation.findNavController(view).navigate(R.id.action_browseCarsFragment_to_carDetailsFragment, bundle);
        });
        rvCars.setAdapter(adapter);

        return view;
    }
}