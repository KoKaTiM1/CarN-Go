package com.yardenbental_danielcohen_shlomoedelstein.carn_go.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.R;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.model.Booking;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.model.Car;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.sync.BookingStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CarRepository {

    public interface CarsCallback {
        void onSuccess(List<Car> cars);

        void onError(Exception error);
    }

    public interface BookingsCallback {
        void onSuccess(List<Booking> bookings);

        void onError(Exception error);
    }

    private final FirebaseFirestore firestore;

    public CarRepository() {
        this(FirebaseFirestore.getInstance());
    }

    CarRepository(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    public void fetchCarsOwnedBy(@NonNull Context context, @NonNull String ownerId, @NonNull CarsCallback callback) {
        firestore.collection("cars")
                .whereEqualTo("ownerId", ownerId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Car> cars = new ArrayList<>();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        cars.add(fromDocument(context, document));
                    }
                    callback.onSuccess(cars);
                })
                .addOnFailureListener(callback::onError);
    }

    public void fetchAllAvailableCars(@NonNull Context context,
                                      long currentTime,
                                      @Nullable String excludedOwnerId,
                                      @NonNull CarsCallback callback) {
        firestore.collection("cars")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Car> cars = new ArrayList<>();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Long availableTo = document.getLong("availableTo");
                        String ownerId = document.getString("ownerId");
                        if (availableTo != null && availableTo < currentTime) {
                            continue;
                        }
                        if (excludedOwnerId != null && excludedOwnerId.equals(ownerId)) {
                            continue;
                        }
                        cars.add(fromDocument(context, document));
                    }
                    callback.onSuccess(cars);
                })
                .addOnFailureListener(callback::onError);
    }

    public Task<DocumentReference> addCar(@NonNull Map<String, Object> carData) {
        return firestore.collection("cars").add(carData);
    }

    public Task<Void> updateCar(@NonNull String carId, @NonNull Map<String, Object> updates) {
        return firestore.collection("cars").document(carId).update(updates);
    }

    public Task<Void> deleteCar(@NonNull String carId) {
        return firestore.collection("cars").document(carId).delete();
    }

    public void fetchFutureBookingsForCar(@NonNull String carId, @NonNull BookingsCallback callback) {
        firestore.collection("bookings")
                .whereEqualTo("carId", carId)
                .whereGreaterThan("endTime", System.currentTimeMillis())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Booking> bookings = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Booking booking = doc.toObject(Booking.class);
                        booking.setId(doc.getId());
                        if (BookingStatus.blocksAvailability(booking.getStatus())) {
                            bookings.add(booking);
                        }
                    }
                    Collections.sort(bookings, (left, right) -> Long.compare(left.getStartTime(), right.getStartTime()));
                    callback.onSuccess(bookings);
                })
                .addOnFailureListener(callback::onError);
    }

    @NonNull
    private Car fromDocument(@NonNull Context context, @NonNull QueryDocumentSnapshot document) {
        String name = document.getString("name");
        String description = document.getString("description");
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
        Long availableTo = document.getLong("availableTo");

        return new Car(
                document.getId(),
                name != null ? name : context.getString(R.string.unknown),
                description,
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
        );
    }
}
