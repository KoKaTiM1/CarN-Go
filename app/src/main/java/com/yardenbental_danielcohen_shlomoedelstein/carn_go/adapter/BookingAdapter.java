package com.yardenbental_danielcohen_shlomoedelstein.carn_go.adapter;

import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.R;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.model.Booking;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.Locale;

public class BookingAdapter extends RecyclerView.Adapter<BookingAdapter.BookingViewHolder> {

    public interface OnBookingActionListener {
        void onApprove(Booking booking);
        void onReject(Booking booking);
        void onPickupPhoto(Booking booking);
        void onFinish(Booking booking);
        void onViewPhotos(Booking booking);
    }

    private List<Booking> bookingList;
    private String currentUserId;
    private OnBookingActionListener listener;

    public BookingAdapter(List<Booking> bookingList, String currentUserId, OnBookingActionListener listener) {
        this.bookingList = bookingList;
        this.currentUserId = currentUserId;
        this.listener = listener;
    }

    @NonNull
    @Override
    public BookingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_booking, parent, false);
        return new BookingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BookingViewHolder holder, int position) {
        Booking booking = bookingList.get(position);

        holder.tvCarName.setText(booking.getCarName());
        
        // Status display
        String status = booking.getStatus() != null ? booking.getStatus() : "PENDING";
        holder.tvStatus.setText(status);
        if ("APPROVED".equals(status)) {
            holder.tvStatus.setTextColor(holder.itemView.getContext().getColor(R.color.primary));
        } else if ("REJECTED".equals(status)) {
            holder.tvStatus.setTextColor(holder.itemView.getContext().getColor(R.color.error));
        } else {
            holder.tvStatus.setTextColor(holder.itemView.getContext().getColor(R.color.secondary));
        }

        // Action buttons visibility: Only show if I am the owner and it is PENDING
        if (currentUserId != null && currentUserId.equals(booking.getOwnerId()) && "PENDING".equals(status)) {
            holder.layoutActions.setVisibility(View.VISIBLE);
            holder.btnApprove.setVisibility(View.VISIBLE);
            holder.btnReject.setVisibility(View.VISIBLE);
            holder.btnPickupPhoto.setVisibility(View.GONE);
            holder.btnFinish.setVisibility(View.GONE);
            holder.btnViewPhotos.setVisibility(View.GONE);
        } else if (currentUserId != null && currentUserId.equals(booking.getUserId()) && "APPROVED".equals(status)) {
            holder.layoutActions.setVisibility(View.VISIBLE);
            holder.btnApprove.setVisibility(View.GONE);
            holder.btnReject.setVisibility(View.GONE);
            
            // Logic: If no pickup photo, show pickup button. If pickup photo exists, show finish button.
            if (booking.getStartPhotoUrl() == null || booking.getStartPhotoUrl().isEmpty()) {
                holder.btnPickupPhoto.setVisibility(View.VISIBLE);
                holder.btnFinish.setVisibility(View.GONE);
            } else {
                holder.btnPickupPhoto.setVisibility(View.GONE);
                holder.btnFinish.setVisibility(View.VISIBLE);
            }
            holder.btnViewPhotos.setVisibility(View.GONE);
        } else if ("COMPLETED".equals(status)) {
            holder.layoutActions.setVisibility(View.VISIBLE);
            holder.btnApprove.setVisibility(View.GONE);
            holder.btnReject.setVisibility(View.GONE);
            holder.btnPickupPhoto.setVisibility(View.GONE);
            holder.btnFinish.setVisibility(View.GONE);
            holder.btnViewPhotos.setVisibility(View.VISIBLE);
        } else {
            holder.layoutActions.setVisibility(View.GONE);
        }

        // Make the entire card clickable to view photos if completed
        if ("COMPLETED".equals(status)) {
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onViewPhotos(booking);
            });
        } else {
            holder.itemView.setOnClickListener(null);
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
        String startStr = sdf.format(new Date(booking.getStartTime()));
        String endStr = sdf.format(new Date(booking.getEndTime()));
        holder.tvDate.setText(startStr + " - " + endStr);

        String imagePath = booking.getCarImageUrl();
        if (imagePath != null && !imagePath.isEmpty()) {
            if (imagePath.startsWith("http")) {
                Glide.with(holder.itemView.getContext())
                        .load(imagePath)
                        .centerCrop()
                        .into(holder.ivCarImage);
            } else {
                // For Base64 strings, we can pass them directly if they have the data:image prefix,
                // but since these are raw Base64, we decode. 
                // To keep onBindViewHolder fast, we let Glide's background threads handle it by passing the string if supported,
                // or we can use a small optimization. Glide's load(byte[]) is good, but decode is still on UI.
                // For now, we'll keep it as is but ensure it's wrapped safely.
                try {
                    byte[] decodedString = Base64.decode(imagePath, Base64.DEFAULT);
                    Glide.with(holder.itemView.getContext())
                            .asBitmap()
                            .load(decodedString)
                            .placeholder(R.drawable.ic_car_placeholder) // Add a placeholder
                            .centerCrop()
                            .into(holder.ivCarImage);
                } catch (Exception e) {
                    holder.ivCarImage.setImageResource(R.drawable.ic_car_placeholder);
                }
            }
        } else {
            holder.ivCarImage.setImageResource(R.drawable.ic_car_placeholder);
        }
    }

    @Override
    public int getItemCount() {
        return bookingList.size();
    }

    static class BookingViewHolder extends RecyclerView.ViewHolder {
        ImageView ivCarImage;
        TextView tvCarName, tvDuration, tvDate, tvTotal, tvStatus;
        View layoutActions;
        Button btnApprove, btnReject, btnPickupPhoto, btnFinish, btnViewPhotos;

        public BookingViewHolder(@NonNull View itemView) {
            super(itemView);
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