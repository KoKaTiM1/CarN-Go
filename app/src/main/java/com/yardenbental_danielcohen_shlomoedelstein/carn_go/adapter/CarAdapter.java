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
        default void onEditClick(Car car) {}
        default void onDeleteClick(Car car) {}
    }

    private boolean showEditDeleteOptions = false;

    public CarAdapter(List<Car> carList, OnCarClickListener listener) {
        this.carList = carList;
        this.listener = listener;
    }

    public void setShowEditDeleteOptions(boolean show) {
        this.showEditDeleteOptions = show;
        notifyDataSetChanged();
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
        if (car.getDistanceKm() != null) {
            String locationText = car.getLocation() == null || car.getLocation().isEmpty()
                    ? holder.itemView.getContext().getString(R.string.distance_away_only, car.getDistanceKm())
                    : holder.itemView.getContext().getString(R.string.location_with_distance, car.getLocation(), car.getDistanceKm());
            holder.tvLocation.setText(locationText);
        } else {
            holder.tvLocation.setText(car.getLocation());
        }

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

        // Load the car image using Glide (handles both URL and Base64)
        String imagePath = car.getImageUrl();
        if (imagePath != null && !imagePath.isEmpty()) {
            if (imagePath.startsWith("http")) {
                // It's a URL
                Glide.with(holder.itemView.getContext())
                        .load(imagePath)
                        .placeholder(R.drawable.ic_car_placeholder)
                        .centerCrop()
                        .into(holder.ivCarImage);
            } else {
                // It's likely Base64 data
                try {
                    byte[] decodedString = Base64.decode(imagePath, Base64.DEFAULT);
                    Glide.with(holder.itemView.getContext())
                            .asBitmap()
                            .load(decodedString)
                            .placeholder(R.drawable.ic_car_placeholder)
                            .centerCrop() // Optimization: crop before rendering
                            .into(holder.ivCarImage);
                } catch (Exception e) {
                    holder.ivCarImage.setImageResource(R.drawable.ic_car_placeholder);
                }
            }
        } else {
            holder.ivCarImage.setImageResource(R.drawable.ic_car_placeholder);
        }

        // Set click listeners for the item and the details button
        holder.itemView.setOnClickListener(v -> listener.onCarClick(car));
        holder.btnDetails.setOnClickListener(v -> listener.onCarClick(car));

        if (showEditDeleteOptions) {
            holder.btnDetails.setVisibility(View.GONE);
            holder.layoutEditDelete.setVisibility(View.VISIBLE);
            holder.btnEdit.setOnClickListener(v -> listener.onEditClick(car));
            holder.btnDelete.setOnClickListener(v -> listener.onDeleteClick(car));
        } else {
            holder.btnDetails.setVisibility(View.VISIBLE);
            holder.layoutEditDelete.setVisibility(View.GONE);
        }
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
        View tagBackground, btnDetails, layoutEditDelete, btnEdit, btnDelete;

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
            layoutEditDelete = itemView.findViewById(R.id.layoutEditDelete);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
