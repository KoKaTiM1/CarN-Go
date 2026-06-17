package com.yardenbental_danielcohen_shlomoedelstein.carn_go.data;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.model.Booking;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.sync.BookingStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BookingRepository {

    public interface BookingsCallback {
        void onSuccess(List<Booking> bookings);

        void onError(Exception error);
    }

    public interface OverlapCheckCallback {
        void onResult(boolean hasOverlap);

        void onError(Exception error);
    }

    private final FirebaseFirestore firestore;

    public BookingRepository() {
        this(FirebaseFirestore.getInstance());
    }

    BookingRepository(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    public void fetchBookingsForUserAndOwner(@NonNull String userId, @NonNull BookingsCallback callback) {
        firestore.collection("bookings")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(rentals -> {
                    List<Booking> results = new ArrayList<>();
                    Set<String> seenIds = new HashSet<>();
                    for (QueryDocumentSnapshot doc : rentals) {
                        Booking booking = doc.toObject(Booking.class);
                        booking.setId(doc.getId());
                        results.add(booking);
                        seenIds.add(doc.getId());
                    }

                    firestore.collection("bookings")
                            .whereEqualTo("ownerId", userId)
                            .get()
                            .addOnSuccessListener(requests -> {
                                for (QueryDocumentSnapshot doc : requests) {
                                    if (seenIds.contains(doc.getId())) {
                                        continue;
                                    }
                                    Booking booking = doc.toObject(Booking.class);
                                    booking.setId(doc.getId());
                                    results.add(booking);
                                }
                                Collections.sort(results, (left, right) -> Long.compare(right.getTimestamp(), left.getTimestamp()));
                                callback.onSuccess(results);
                            })
                            .addOnFailureListener(callback::onError);
                })
                .addOnFailureListener(callback::onError);
    }

    public Task<Void> updateStatus(@NonNull String bookingId, @NonNull String newStatus) {
        return firestore.collection("bookings").document(bookingId).update("status", newStatus);
    }

    public Task<Void> submitPickupPhoto(@NonNull String bookingId, @NonNull String base64Image) {
        return firestore.collection("bookings")
                .document(bookingId)
                .update("startPhotoUrl", base64Image, "status", BookingStatus.ACTIVE);
    }

    public Task<Void> completeBooking(@NonNull String bookingId, @NonNull String base64Image) {
        return firestore.collection("bookings")
                .document(bookingId)
                .update("status", BookingStatus.COMPLETED, "endPhotoUrl", base64Image);
    }

    public Task<DocumentReference> createBooking(@NonNull Map<String, Object> bookingData) {
        return firestore.collection("bookings").add(bookingData);
    }

    public void fetchActiveBookingsForCar(@NonNull String carId, @NonNull BookingsCallback callback) {
        firestore.collection("bookings")
                .whereEqualTo("carId", carId)
                .whereGreaterThan("endTime", System.currentTimeMillis())
                .orderBy("endTime")
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
                .addOnFailureListener(error -> fallbackFetchActiveBookingsForCar(carId, callback, error));
    }

    public void hasBlockingOverlap(@NonNull String carId,
                                   long selectedStart,
                                   long selectedEnd,
                                   @NonNull OverlapCheckCallback callback) {
        firestore.collection("bookings")
                .whereEqualTo("carId", carId)
                .whereGreaterThan("endTime", selectedStart)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    boolean hasOverlap = false;
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String status = doc.getString("status");
                        if (!BookingStatus.blocksAvailability(status)) {
                            continue;
                        }
                        Long existingStart = doc.getLong("startTime");
                        if (existingStart != null && selectedEnd > existingStart) {
                            hasOverlap = true;
                            break;
                        }
                    }
                    callback.onResult(hasOverlap);
                })
                .addOnFailureListener(error -> fallbackOverlapCheck(carId, selectedStart, selectedEnd, callback, error));
    }

    private void fallbackFetchActiveBookingsForCar(@NonNull String carId,
                                                   @NonNull BookingsCallback callback,
                                                   @NonNull Exception originalError) {
        firestore.collection("bookings")
                .whereEqualTo("carId", carId)
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

    private void fallbackOverlapCheck(@NonNull String carId,
                                      long selectedStart,
                                      long selectedEnd,
                                      @NonNull OverlapCheckCallback callback,
                                      @NonNull Exception originalError) {
        firestore.collection("bookings")
                .whereEqualTo("carId", carId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    boolean hasOverlap = false;
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String status = doc.getString("status");
                        if (!BookingStatus.blocksAvailability(status)) {
                            continue;
                        }
                        Long existingStart = doc.getLong("startTime");
                        Long existingEnd = doc.getLong("endTime");
                        if (existingStart != null && existingEnd != null
                                && selectedStart < existingEnd
                                && selectedEnd > existingStart) {
                            hasOverlap = true;
                            break;
                        }
                    }
                    callback.onResult(hasOverlap);
                })
                .addOnFailureListener(callback::onError);
    }
}
