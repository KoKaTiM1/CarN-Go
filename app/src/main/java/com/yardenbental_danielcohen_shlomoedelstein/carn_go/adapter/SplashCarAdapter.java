package com.yardenbental_danielcohen_shlomoedelstein.carn_go.adapter;

import android.content.Context;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.R;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.model.Car;

import java.util.List;

public class SplashCarAdapter extends BaseAdapter {

    public interface OnSplashCarClickListener {
        void onCarClick(Car car);
    }

    private final List<Car> cars;
    private final OnSplashCarClickListener listener;

    public SplashCarAdapter(List<Car> cars, OnSplashCarClickListener listener) {
        this.cars = cars;
        this.listener = listener;
    }

    @Override
    public int getCount() {
        return cars.size();
    }

    @Override
    public Car getItem(int position) {
        return cars.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        SplashCarViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_splash_car, parent, false);
            holder = new SplashCarViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (SplashCarViewHolder) convertView.getTag();
        }

        Context context = convertView.getContext();
        Car car = getItem(position);
        holder.tvName.setText(car.getName());
        holder.tvPrice.setText(context.getString(R.string.price_per_hour, (int) car.getPricePerHour()));
        holder.tvType.setText(car.getType());

        if (car.getDistanceKm() != null) {
            holder.tvDistance.setText(context.getString(R.string.distance_away_only, car.getDistanceKm()));
        } else {
            holder.tvDistance.setText(car.getLocation());
        }

        String imagePath = car.getImageUrl();
        if (imagePath != null && !imagePath.isEmpty()) {
            if (imagePath.startsWith("http")) {
                Glide.with(context)
                        .load(imagePath)
                        .placeholder(R.drawable.ic_car_placeholder)
                        .centerCrop()
                        .into(holder.ivImage);
            } else {
                try {
                    byte[] decodedString = Base64.decode(imagePath, Base64.DEFAULT);
                    Glide.with(context)
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

        convertView.setOnClickListener(v -> listener.onCarClick(car));
        return convertView;
    }

    static class SplashCarViewHolder {
        private final ImageView ivImage;
        private final TextView tvName;
        private final TextView tvDistance;
        private final TextView tvPrice;
        private final TextView tvType;

        SplashCarViewHolder(View itemView) {
            ivImage = itemView.findViewById(R.id.ivSplashCarImage);
            tvName = itemView.findViewById(R.id.tvSplashCarName);
            tvDistance = itemView.findViewById(R.id.tvSplashCarDistance);
            tvPrice = itemView.findViewById(R.id.tvSplashCarPrice);
            tvType = itemView.findViewById(R.id.tvSplashCarType);
        }
    }
}
