package com.yardenbental_danielcohen_shlomoedelstein.carn_go.sync;

public final class BookingStatus {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String ACTIVE = "ACTIVE";
    public static final String RETURN_PENDING = "RETURN_PENDING";
    public static final String REJECTED = "REJECTED";
    public static final String COMPLETED = "COMPLETED";

    private BookingStatus() {
    }

    public static String normalize(String status) {
        if (status == null || status.trim().isEmpty()) {
            return PENDING;
        }
        return status;
    }

    public static boolean isTerminal(String status) {
        String normalized = normalize(status);
        return REJECTED.equals(normalized) || COMPLETED.equals(normalized);
    }

    public static boolean blocksAvailability(String status) {
        String normalized = normalize(status);
        return !REJECTED.equals(normalized) && !COMPLETED.equals(normalized);
    }

    public static String resolveNextStatus(String currentStatus, long startTime, long endTime, String startPhotoUrl, String endPhotoUrl, long now) {
        String normalized = normalize(currentStatus);

        if (REJECTED.equals(normalized) || COMPLETED.equals(normalized)) {
            return normalized;
        }
        if (endPhotoUrl != null && !endPhotoUrl.isEmpty()) {
            return COMPLETED;
        }
        if (PENDING.equals(normalized)) {
            return PENDING;
        }
        if (APPROVED.equals(normalized)) {
            if (now >= startTime) {
                return now >= endTime ? RETURN_PENDING : ACTIVE;
            }
            return APPROVED;
        }
        if (ACTIVE.equals(normalized)) {
            return now >= endTime ? RETURN_PENDING : ACTIVE;
        }
        if (RETURN_PENDING.equals(normalized)) {
            return RETURN_PENDING;
        }
        if (startPhotoUrl != null && !startPhotoUrl.isEmpty()) {
            return now >= endTime ? RETURN_PENDING : ACTIVE;
        }
        return normalized;
    }
}
