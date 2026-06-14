package com.yardenbental_danielcohen_shlomoedelstein.carn_go.discovery;

import android.content.Context;
import android.location.Location;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.R;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.model.Car;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class CarDiscoveryHelper {

    private CarDiscoveryHelper() {
    }

    public interface CarsResultCallback {
        void onSuccess(List<Car> cars);

        void onError(Exception error);
    }

    public interface CarMatcher {
        boolean matches(Car car);
    }

    public static void loadAvailableCars(@NonNull Context context, long currentTime, @NonNull CarsResultCallback callback) {
        FirebaseFirestore.getInstance().collection("cars")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Car> cars = new ArrayList<>();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        try {
                            Long availableTo = document.getLong("availableTo");
                            if (availableTo != null && availableTo < currentTime) {
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

                            cars.add(new Car(
                                    document.getId(),
                                    name != null ? name : context.getString(R.string.unknown),
                                    type != null ? type : context.getString(R.string.standard),
                                    location != null ? location : context.getString(R.string.location_unavailable_label),
                                    latitude,
                                    longitude,
                                    price != null ? price : 0.0,
                                    rating != null ? rating : 5.0,
                                    imageUrl,
                                    transmission != null ? transmission : context.getString(R.string.auto),
                                    seats,
                                    fuelType != null ? fuelType : context.getString(R.string.gas),
                                    tag != null ? tag : "",
                                    ownerId != null ? ownerId : "",
                                    availableFrom != null ? availableFrom : 0L,
                                    availableTo != null ? availableTo : 0L
                            ));
                        } catch (Exception ignored) {
                        }
                    }
                    callback.onSuccess(cars);
                })
                .addOnFailureListener(callback::onError);
    }

    public static List<Car> filterAndSortCars(@NonNull List<Car> source,
                                              @Nullable Location currentLocation,
                                              int radiusKm,
                                              @Nullable CarMatcher matcher) {
        List<Car> filteredCars = new ArrayList<>();

        for (Car car : source) {
            if (matcher != null && !matcher.matches(car)) {
                continue;
            }

            Double distanceKm = calculateDistanceKm(currentLocation, car);
            car.setDistanceKm(distanceKm);

            if (currentLocation != null && radiusKm > 0) {
                if (distanceKm == null || distanceKm > radiusKm) {
                    continue;
                }
            }

            filteredCars.add(car);
        }

        Collections.sort(filteredCars, Comparator
                .comparing(Car::getDistanceKm, (left, right) -> {
                    if (left == null && right == null) return 0;
                    if (left == null) return 1;
                    if (right == null) return -1;
                    return Double.compare(left, right);
                })
                .thenComparing(Car::getName, String.CASE_INSENSITIVE_ORDER));

        return filteredCars;
    }

    @Nullable
    public static Double calculateDistanceKm(@Nullable Location currentLocation, @NonNull Car car) {
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

    public static boolean matchesTypeFilter(@NonNull Car car, @Nullable String activeTypeFilter) {
        if (activeTypeFilter == null || activeTypeFilter.isEmpty()) {
            return true;
        }
        String carType = car.getType();
        return carType != null && carType.toLowerCase(Locale.ROOT).contains(activeTypeFilter.toLowerCase(Locale.ROOT));
    }
}
