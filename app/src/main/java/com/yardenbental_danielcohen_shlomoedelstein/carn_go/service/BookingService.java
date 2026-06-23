package com.yardenbental_danielcohen_shlomoedelstein.carn_go.service;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yardenbental_danielcohen_shlomoedelstein.carn_go.model.Booking;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.model.Car;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class BookingService {

    public static final class SuggestedSlot {
        public final long startTime;
        public final long endTime;

        public SuggestedSlot(long startTime, long endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public boolean isAvailable() {
            return startTime > 0 && endTime > startTime;
        }
    }

    public boolean isOwnerBookingOwnCar(@Nullable String userId, @Nullable Car car) {
        return userId != null && car != null && userId.equals(car.getOwnerId());
    }

    public boolean isWithinAvailability(@NonNull Car car, long startTime, long endTime) {
        return startTime >= car.getAvailableFrom() && endTime <= car.getAvailableTo();
    }

    public boolean isValidBookingWindow(long startTime, long endTime) {
        return startTime > 0 && endTime > startTime;
    }

    public long roundDurationHours(long startTime, long endTime) {
        long diffInMillis = endTime - startTime;
        long hours = TimeUnit.MILLISECONDS.toHours(diffInMillis);
        if (diffInMillis % TimeUnit.HOURS.toMillis(1) > 0) {
            hours++;
        }
        return Math.max(hours, 0);
    }

    public double calculateTotalPrice(@NonNull Car car, long startTime, long endTime) {
        return roundDurationHours(startTime, endTime) * car.getPricePerHour();
    }

    @NonNull
    public SuggestedSlot suggestFirstAvailableSlot(@NonNull Car car, @NonNull List<Booking> existingBookings, long now) {
        long suggestedStart = Math.max(car.getAvailableFrom(), now);
        for (Booking booking : existingBookings) {
            if (suggestedStart + TimeUnit.HOURS.toMillis(1) <= booking.getStartTime()) {
                break;
            }
            if (booking.getEndTime() > suggestedStart) {
                suggestedStart = booking.getEndTime();
            }
        }

        if (suggestedStart >= car.getAvailableTo()) {
            return new SuggestedSlot(0, 0);
        }

        long nextBookingStart = car.getAvailableTo();
        for (Booking booking : existingBookings) {
            if (booking.getStartTime() > suggestedStart) {
                nextBookingStart = booking.getStartTime();
                break;
            }
        }

        long suggestedEnd = Math.min(nextBookingStart, suggestedStart + TimeUnit.HOURS.toMillis(2));
        return new SuggestedSlot(suggestedStart, suggestedEnd);
    }
}
