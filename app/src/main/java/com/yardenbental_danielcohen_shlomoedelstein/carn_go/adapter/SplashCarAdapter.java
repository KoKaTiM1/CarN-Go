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

public class SplashCarAdapter extends RecyclerView.Adapter<SplashCarAdapter.SplashCarViewHolder> {

    public interface OnSplashCarClickListener {
        void onCarClick(Car car);
    }

    private final List<Car> cars;
    private final OnSplashCarClickListener listener;

    public SplashCarAdapter(List<Car> cars, OnSplashCarClickListener listener) {
        this.cars = cars;
        this.listener = listener;
    }

    @NonNull
    @Override
    public SplashCarViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_splash_car, parent, false);
        return new SplashCarViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SplashCarViewHolder holder, int position) {
        Car car = cars.get(position);
        holder.tvName.setText(car.getName());
        holder.tvPrice.setText(holder.itemView.getContext().getString(R.string.price_per_hour, (int) car.getPricePerHour()));
        holder.tvType.setText(car.getType());

        if (car.getDistanceKm() != null) {
            holder.tvDistance.setText(holder.itemView.getContext().getString(R.string.distance_away_only, car.getDistanceKm()));
        } else {
            holder.tvDistance.setText(car.getLocation());
        }

        String imagePath = car.getImageUrl();
        if (imagePath != null && !imagePath.isEmpty()) {
            if (imagePath.startsWith("http")) {
                Glide.with(holder.itemView.getContext())
                        .load(imagePath)
                        .placeholder(R.drawable.ic_car_placeholder)
                        .centerCrop()
                        .into(holder.ivImage);
            } else {
                try {
                    byte[] decodedString = Base64.decode(imagePath, Base64.DEFAULT);
                    Glide.with(holder.itemView.getContext())
                            .asBitmap()
                            .load(decodedString)
                            .placeholder(R.drawable.ic_car_placeholder)
                            .centerCrop()
                            .into(holder.ivImage);
                } catch (Exception error) {
                    holder.ivImage.setImageResource(R.drawable.ic_car_placeholder);
                }
            }
        } else {
            holder.ivImage.setImageResource(R.drawable.ic_car_placeholder);
        }

        holder.itemView.setOnClickListener(v -> listener.onCarClick(car));
    }

    @Override
    public int getItemCount() {
        return cars.size();
    }

    static class SplashCarViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivImage;
        private final TextView tvName;
        private final TextView tvDistance;
        private final TextView tvPrice;
        private final TextView tvType;

        public SplashCarViewHolder(@NonNull View itemView) {
            super(itemView);
            ivImage = itemView.findViewById(R.id.ivSplashCarImage);
            tvName = itemView.findViewById(R.id.tvSplashCarName);
            tvDistance = itemView.findViewById(R.id.tvSplashCarDistance);
            tvPrice = itemView.findViewById(R.id.tvSplashCarPrice);
            tvType = itemView.findViewById(R.id.tvSplashCarType);
        }
    }
}
