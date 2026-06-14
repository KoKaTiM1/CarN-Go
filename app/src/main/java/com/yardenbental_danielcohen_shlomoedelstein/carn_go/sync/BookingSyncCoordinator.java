package com.yardenbental_danielcohen_shlomoedelstein.carn_go.sync;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.firebase.FirestoreHelper;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.model.Booking;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.notifications.ReminderScheduler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BookingSyncCoordinator {

    private BookingSyncCoordinator() {
    }

    public static void runSync(Context context) throws Exception {
        if (context == null) {
            return;
        }

        String currentUserId = FirestoreHelper.getCurrentUserId(context);
        if (currentUserId == null || currentUserId.isEmpty()) {
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        long now = System.currentTimeMillis();

        QuerySnapshot allBookingsSnapshot = Tasks.await(
                db.collection("bookings").get()
        );
        QuerySnapshot allCarsSnapshot = Tasks.await(
                db.collection("cars").get()
        );

        Map<String, Booking> relevantBookings = new HashMap<>();
        for (QueryDocumentSnapshot snapshot : allBookingsSnapshot) {
            Booking booking = snapshot.toObject(Booking.class);
            booking.setId(snapshot.getId());
            relevantBookings.put(snapshot.getId(), booking);
        }

        Set<String> affectedCarIds = new HashSet<>();
        for (QueryDocumentSnapshot snapshot : allCarsSnapshot) {
            affectedCarIds.add(snapshot.getId());
        }

        for (Booking booking : relevantBookings.values()) {
            if (booking.getCarId() != null) {
                affectedCarIds.add(booking.getCarId());
            }

            String resolvedStatus = BookingStatus.resolveNextStatus(
                    booking.getStatus(),
                    booking.getStartTime(),
                    booking.getEndTime(),
                    booking.getStartPhotoUrl(),
                    booking.getEndPhotoUrl(),
                    now
            );

            if (!resolvedStatus.equals(BookingStatus.normalize(booking.getStatus()))) {
                Tasks.await(
                        db.collection("bookings")
                                .document(booking.getId())
                                .update("status", resolvedStatus)
                );
                booking.setStatus(resolvedStatus);
            }

            if (currentUserId.equals(booking.getUserId())) {
                ReminderScheduler.scheduleRentalReminders(context, booking);
                if (BookingStatus.isTerminal(booking.getStatus())) {
                    ReminderScheduler.cancelRentalReminders(context, booking.getId());
                }
            }
        }

        for (String carId : affectedCarIds) {
            syncCarAvailability(db, carId, now);
        }

        BookingSyncScheduler.notifySyncCompleted(context);
    }

    private static void syncCarAvailability(FirebaseFirestore db, String carId, long now) throws Exception {
        if (carId == null || carId.isEmpty()) {
            return;
        }

        DocumentReference carRef = db.collection("cars").document(carId);
        DocumentSnapshot carSnapshot = Tasks.await(carRef.get());
        if (!carSnapshot.exists()) {
            return;
        }

        Long availableFrom = carSnapshot.getLong("availableFrom");
        Long availableTo = carSnapshot.getLong("availableTo");
        boolean insideBaseWindow = (availableFrom == null || now >= availableFrom) && (availableTo == null || now < availableTo);

        QuerySnapshot bookingsSnapshot = Tasks.await(
                db.collection("bookings")
                        .whereEqualTo("carId", carId)
                        .get()
        );

        boolean blockedNow = false;
        for (QueryDocumentSnapshot bookingSnapshot : bookingsSnapshot) {
            Booking booking = bookingSnapshot.toObject(Booking.class);
            String resolvedStatus = BookingStatus.resolveNextStatus(
                    booking.getStatus(),
                    booking.getStartTime(),
                    booking.getEndTime(),
                    booking.getStartPhotoUrl(),
                    booking.getEndPhotoUrl(),
                    now
            );

            if (!BookingStatus.blocksAvailability(resolvedStatus)) {
                continue;
            }
            if (booking.getStartTime() <= now && now < booking.getEndTime()) {
                blockedNow = true;
                break;
            }
        }

        Boolean currentAvailability = carSnapshot.getBoolean("isCurrentlyAvailable");
        boolean nextAvailability = insideBaseWindow && !blockedNow;

        if (currentAvailability == null || currentAvailability != nextAvailability) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("isCurrentlyAvailable", nextAvailability);
            updates.put("lastAvailabilitySyncAt", now);
            Tasks.await(carRef.update(updates));
            Log.d("BookingSyncCoordinator", "Updated availability for car " + carId + " to " + nextAvailability);
        }
    }
}
