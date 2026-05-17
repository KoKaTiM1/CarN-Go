package com.yardenbental_danielcohen_shlomoedelstein.carn_go.adapter;

import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import java.util.Locale;

public class BookingAdapter extends RecyclerView.Adapter<BookingAdapter.BookingViewHolder> {

    private List<Booking> bookingList;

    public BookingAdapter(List<Booking> bookingList) {
        this.bookingList = bookingList;
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
        holder.tvDuration.setText("Duration: " + booking.getHours() + (booking.getHours() == 1 ? " hour" : " hours"));
        holder.tvTotal.setText(String.format(Locale.getDefault(), "$%.2f", booking.getTotalCost()));

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
        holder.tvDate.setText(sdf.format(new Date(booking.getTimestamp())));

        String imagePath = booking.getCarImageUrl();
        if (imagePath != null && !imagePath.isEmpty()) {
            if (imagePath.startsWith("http")) {
                Glide.with(holder.itemView.getContext())
                        .load(imagePath)
                        .centerCrop()
                        .into(holder.ivCarImage);
            } else {
                try {
                    byte[] decodedString = Base64.decode(imagePath, Base64.DEFAULT);
                    Glide.with(holder.itemView.getContext())
                            .asBitmap()
                            .load(decodedString)
                            .centerCrop()
                            .into(holder.ivCarImage);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public int getItemCount() {
        return bookingList.size();
    }

    static class BookingViewHolder extends RecyclerView.ViewHolder {
        ImageView ivCarImage;
        TextView tvCarName, tvDuration, tvDate, tvTotal;

        public BookingViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCarImage = itemView.findViewById(R.id.ivBookingImage);
            tvCarName = itemView.findViewById(R.id.tvBookingCarName);
            tvDuration = itemView.findViewById(R.id.tvBookingDuration);
            tvDate = itemView.findViewById(R.id.tvBookingDate);
            tvTotal = itemView.findViewById(R.id.tvBookingTotal);
        }
    }
}