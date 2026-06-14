package com.yardenbental_danielcohen_shlomoedelstein.carn_go.adapter;

import android.content.Context;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.R;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.model.Booking;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.sync.BookingStatus;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class BookingAdapter extends BaseAdapter {

    public interface OnBookingActionListener {
        void onApprove(Booking booking);
        void onReject(Booking booking);
        void onPickupPhoto(Booking booking);
        void onFinish(Booking booking);
        void onViewPhotos(Booking booking);
    }

    private final List<Booking> bookingList;
    private final String currentUserId;
    private final OnBookingActionListener listener;

    public BookingAdapter(List<Booking> bookingList, String currentUserId, OnBookingActionListener listener) {
        this.bookingList = bookingList;
        this.currentUserId = currentUserId;
        this.listener = listener;
    }

    @Override
    public int getCount() {
        return bookingList.size();
    }

    @Override
    public Booking getItem(int position) {
        return bookingList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        BookingViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_booking, parent, false);
            holder = new BookingViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (BookingViewHolder) convertView.getTag();
        }

        Context context = convertView.getContext();
        Booking booking = getItem(position);
        holder.tvCarName.setText(booking.getCarName());

        String status = BookingStatus.normalize(booking.getStatus());
        holder.tvStatus.setText(status);
        if (BookingStatus.APPROVED.equals(status) || BookingStatus.ACTIVE.equals(status)) {
            holder.tvStatus.setTextColor(context.getColor(R.color.primary));
        } else if (BookingStatus.RETURN_PENDING.equals(status) || BookingStatus.REJECTED.equals(status)) {
            holder.tvStatus.setTextColor(context.getColor(R.color.error));
        } else {
            holder.tvStatus.setTextColor(context.getColor(R.color.secondary));
        }

        if (currentUserId != null && currentUserId.equals(booking.getOwnerId()) && BookingStatus.PENDING.equals(status)) {
            holder.layoutActions.setVisibility(View.VISIBLE);
            holder.btnApprove.setVisibility(View.VISIBLE);
            holder.btnReject.setVisibility(View.VISIBLE);
            holder.btnPickupPhoto.setVisibility(View.GONE);
            holder.btnFinish.setVisibility(View.GONE);
            holder.btnViewPhotos.setVisibility(View.GONE);
        } else if (currentUserId != null
                && currentUserId.equals(booking.getUserId())
                && (BookingStatus.ACTIVE.equals(status) || BookingStatus.RETURN_PENDING.equals(status))) {
            holder.layoutActions.setVisibility(View.VISIBLE);
            holder.btnApprove.setVisibility(View.GONE);
            holder.btnReject.setVisibility(View.GONE);

            if (BookingStatus.RETURN_PENDING.equals(status)) {
                holder.btnPickupPhoto.setVisibility(View.GONE);
                holder.btnFinish.setVisibility(View.VISIBLE);
            } else if (booking.getStartPhotoUrl() == null || booking.getStartPhotoUrl().isEmpty()) {
                holder.btnPickupPhoto.setVisibility(View.VISIBLE);
                holder.btnFinish.setVisibility(View.GONE);
            } else {
                holder.btnPickupPhoto.setVisibility(View.GONE);
                holder.btnFinish.setVisibility(View.VISIBLE);
            }
            holder.btnViewPhotos.setVisibility(View.GONE);
        } else if (BookingStatus.COMPLETED.equals(status)) {
            holder.layoutActions.setVisibility(View.VISIBLE);
            holder.btnApprove.setVisibility(View.GONE);
            holder.btnReject.setVisibility(View.GONE);
            holder.btnPickupPhoto.setVisibility(View.GONE);
            holder.btnFinish.setVisibility(View.GONE);
            holder.btnViewPhotos.setVisibility(View.VISIBLE);
        } else {
            holder.layoutActions.setVisibility(View.GONE);
        }

        if (BookingStatus.COMPLETED.equals(status)) {
            convertView.setOnClickListener(v -> {
                if (listener != null) listener.onViewPhotos(booking);
            });
        } else {
            convertView.setOnClickListener(null);
        }

        holder.btnApprove.setOnClickListener(v -> {
            if (listener != null) listener.onApprove(booking);
        });
        holder.btnReject.setOnClickListener(v -> {
            if (listener != null) listener.onReject(booking);
        });
        holder.btnPickupPhoto.setOnClickListener(v -> {
            if (listener != null) listener.onPickupPhoto(booking);
        });
        holder.btnFinish.setOnClickListener(v -> {
            if (listener != null) listener.onFinish(booking);
        });
        holder.btnViewPhotos.setOnClickListener(v -> {
            if (listener != null) listener.onViewPhotos(booking);
        });

        long durationMillis = booking.getEndTime() - booking.getStartTime();
        long hours = TimeUnit.MILLISECONDS.toHours(durationMillis);
        if (durationMillis % TimeUnit.HOURS.toMillis(1) > 0) {
            hours++;
        }

        holder.tvDuration.setText("Duration: " + hours + (hours == 1 ? " hour" : " hours"));
        holder.tvTotal.setText(String.format(Locale.getDefault(), "$%.2f", booking.getTotalCost()));

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
        holder.tvDate.setText(sdf.format(new Date(booking.getStartTime())) + " - " + sdf.format(new Date(booking.getEndTime())));

        String imagePath = booking.getCarImageUrl();
        if (imagePath != null && !imagePath.isEmpty()) {
            if (imagePath.startsWith("http")) {
                Glide.with(context)
                        .load(imagePath)
                        .centerCrop()
                        .into(holder.ivCarImage);
            } else {
                try {
                    byte[] decodedString = Base64.decode(imagePath, Base64.DEFAULT);
                    Glide.with(context)
                            .asBitmap()
                            .load(decodedString)
                            .placeholder(R.drawable.ic_car_placeholder)
                            .centerCrop()
                            .into(holder.ivCarImage);
                } catch (Exception e) {
                    holder.ivCarImage.setImageResource(R.drawable.ic_car_placeholder);
                }
            }
        } else {
            holder.ivCarImage.setImageResource(R.drawable.ic_car_placeholder);
        }

        return convertView;
    }

    static class BookingViewHolder {
        ImageView ivCarImage;
        TextView tvCarName, tvDuration, tvDate, tvTotal, tvStatus;
        View layoutActions;
        Button btnApprove, btnReject, btnPickupPhoto, btnFinish, btnViewPhotos;

        BookingViewHolder(View itemView) {
            ivCarImage = itemView.findViewById(R.id.ivBookingImage);
            tvCarName = itemView.findViewById(R.id.tvBookingCarName);
            tvDuration = itemView.findViewById(R.id.tvBookingDuration);
            tvDate = itemView.findViewById(R.id.tvBookingDate);
            tvTotal = itemView.findViewById(R.id.tvBookingTotal);
            tvStatus = itemView.findViewById(R.id.tvBookingStatus);
            layoutActions = itemView.findViewById(R.id.layoutBookingActions);
            btnApprove = itemView.findViewById(R.id.btnApproveBooking);
            btnReject = itemView.findViewById(R.id.btnRejectBooking);
            btnPickupPhoto = itemView.findViewById(R.id.btnPickupPhoto);
            btnFinish = itemView.findViewById(R.id.btnFinishRental);
            btnViewPhotos = itemView.findViewById(R.id.btnViewPhotos);
        }
    }
}
