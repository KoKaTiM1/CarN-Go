package com.yardenbental_danielcohen_shlomoedelstein.carn_go.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.R;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.model.Car;

import java.util.List;

/**
 * Adapter for displaying a list of Car objects in a RecyclerView.
 */
public class CarAdapter extends RecyclerView.Adapter<CarAdapter.CarViewHolder> {

    private List<Car> carList;
    private OnCarClickListener listener;

    /**
     * Interface to handle clicks on car items in the list.
     */
    public interface OnCarClickListener {
        void onCarClick(Car car);
    }

    public CarAdapter(List<Car> carList, OnCarClickListener listener) {
        this.carList = carList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CarViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate the car item layout
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_car, parent, false);
        return new CarViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CarViewHolder holder, int position) {
        Car car = carList.get(position);
        
        // Bind car data to UI elements
        holder.tvCarName.setText(car.getName());
        holder.tvLocation.setText(car.getLocation());
        
        holder.tvPrice.setText(holder.itemView.getContext().getString(R.string.price_per_hour, (int)car.getPricePerHour()));
        holder.tvRating.setText(String.valueOf(car.getRating()));
        holder.tvTransmission.setText(car.getTransmission());
        holder.tvSeats.setText(holder.itemView.getContext().getString(R.string.seats_count, car.getSeats()));
        holder.tvTag.setText(car.getTag());
        
        // Show or hide the tag based on whether it's available
        if (car.getTag() == null || car.getTag().isEmpty()) {
            holder.tagBackground.setVisibility(View.GONE);
            holder.tvTag.setVisibility(View.GONE);
        } else {
            holder.tagBackground.setVisibility(View.VISIBLE);
            holder.tvTag.setVisibility(View.VISIBLE);
        }

        // Load the car image using Glide
        Glide.with(holder.itemView.getContext())
                .load(car.getImageUrl())
                .into(holder.ivCarImage);

        // Set click listeners for the item and the details button
        holder.itemView.setOnClickListener(v -> listener.onCarClick(car));
        holder.btnDetails.setOnClickListener(v -> listener.onCarClick(car));
    }

    @Override
    public int getItemCount() {
        return carList.size();
    }

    /**
     * ViewHolder class for holding references to car item views.
     */
    static class CarViewHolder extends RecyclerView.ViewHolder {
        ImageView ivCarImage;
        TextView tvCarName, tvLocation, tvPrice, tvRating, tvTransmission, tvSeats, tvTag;
        View tagBackground, btnDetails;

        public CarViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCarImage = itemView.findViewById(R.id.ivCarImage);
            tvCarName = itemView.findViewById(R.id.tvCarName);
            tvLocation = itemView.findViewById(R.id.tvLocation);
            tvPrice = itemView.findViewById(R.id.tvPrice);
            tvRating = itemView.findViewById(R.id.tvRating);
            tvTransmission = itemView.findViewById(R.id.tvTransmission);
            tvSeats = itemView.findViewById(R.id.tvSeats);
            tvTag = itemView.findViewById(R.id.tvTag);
            tagBackground = itemView.findViewById(R.id.tagBackground);
            btnDetails = itemView.findViewById(R.id.btnDetails);
        }
    }
}