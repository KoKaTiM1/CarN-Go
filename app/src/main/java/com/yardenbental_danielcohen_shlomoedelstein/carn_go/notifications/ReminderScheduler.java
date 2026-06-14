package com.yardenbental_danielcohen_shlomoedelstein.carn_go.notifications;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.yardenbental_danielcohen_shlomoedelstein.carn_go.AppPreferences;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.model.Booking;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.sync.BookingStatus;

public final class ReminderScheduler {

    private static final long REMINDER_OFFSET_MS = 30L * 60L * 1000L;

    private ReminderScheduler() {
    }

    public static void scheduleRentalReminders(Context context, Booking booking) {
        if (context == null || booking == null || booking.getId() == null) {
            return;
        }

        if (!AppPreferences.areBookingRemindersEnabled(context)) {
            cancelRentalReminders(context, booking.getId());
            return;
        }

        String status = BookingStatus.normalize(booking.getStatus());
        if (!BookingStatus.APPROVED.equals(status)
                && !BookingStatus.ACTIVE.equals(status)
                && !BookingStatus.RETURN_PENDING.equals(status)) {
            cancelRentalReminders(context, booking.getId());
            return;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long startReminderTime = booking.getStartTime() - REMINDER_OFFSET_MS;
        long endReminderTime = booking.getEndTime() - REMINDER_OFFSET_MS;

        if (startReminderTime > now) {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    startReminderTime,
                    buildReminderPendingIntent(context, booking, "START")
            );
        } else {
            cancelReminder(alarmManager, context, booking, "START");
        }

        if (endReminderTime > now) {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    endReminderTime,
                    buildReminderPendingIntent(context, booking, "END")
            );
        } else {
            cancelReminder(alarmManager, context, booking, "END");
        }
    }

    public static void cancelRentalReminders(Context context, String bookingId) {
        if (context == null || bookingId == null) {
            return;
        }
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        cancelReminder(alarmManager, context, bookingId, "START");
        cancelReminder(alarmManager, context, bookingId, "END");
    }

    private static void cancelReminder(AlarmManager alarmManager, Context context, Booking booking, String type) {
        cancelReminder(alarmManager, context, booking.getId(), type);
    }

    private static void cancelReminder(AlarmManager alarmManager, Context context, String bookingId, String type) {
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                buildReminderRequestCode(bookingId, type),
                buildReminderIntent(context, bookingId, null, type),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        alarmManager.cancel(pendingIntent);
    }

    private static PendingIntent buildReminderPendingIntent(Context context, Booking booking, String type) {
        return PendingIntent.getBroadcast(
                context,
                buildReminderRequestCode(booking.getId(), type),
                buildReminderIntent(context, booking.getId(), booking.getCarName(), type),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static Intent buildReminderIntent(Context context, String bookingId, String carName, String type) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("bookingId", bookingId);
        intent.putExtra("carName", carName);
        intent.putExtra("type", type);
        return intent;
    }

    private static int buildReminderRequestCode(String bookingId, String type) {
        return (bookingId + ":" + type).hashCode();
    }
}
