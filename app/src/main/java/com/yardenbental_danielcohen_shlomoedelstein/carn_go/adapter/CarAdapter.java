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
import java.util.Locale;

public class CarAdapter extends BaseAdapter {

    public interface OnCarClickListener {
        void onCarClick(Car car);
        default void onEditClick(Car car) {}
        default void onDeleteClick(Car car) {}
    }

    private final List<Car> carList;
    private final OnCarClickListener listener;
    private boolean showEditDeleteOptions = false;

    public CarAdapter(List<Car> carList, OnCarClickListener listener) {
        this.carList = carList;
        this.listener = listener;
    }

    public void setShowEditDeleteOptions(boolean show) {
        this.showEditDeleteOptions = show;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return carList.size();
    }

    @Override
    public Car getItem(int position) {
        return carList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        CarViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_car, parent, false);
            holder = new CarViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (CarViewHolder) convertView.getTag();
        }

        Context context = convertView.getContext();
        Car car = getItem(position);

        holder.tvCarName.setText(car.getName());
        if (car.getDistanceKm() != null) {
            String locationText = car.getLocation() == null || car.getLocation().isEmpty()
                    ? context.getString(R.string.distance_away_only, car.getDistanceKm())
                    : context.getString(R.string.location_with_distance, car.getLocation(), car.getDistanceKm());
            holder.tvLocation.setText(locationText);
        } else {
            holder.tvLocation.setText(car.getLocation());
        }

        holder.tvPrice.setText(context.getString(R.string.price_per_hour, (int) car.getPricePerHour()));
        holder.tvTransmission.setText(car.getTransmission());
        holder.tvSeats.setText(context.getString(R.string.seats_count, car.getSeats()));
        holder.tvTag.setText(car.getTag());

        if (car.hasRealRating()) {
            holder.ratingDivider.setVisibility(View.VISIBLE);
            holder.tvRating.setVisibility(View.VISIBLE);
            holder.tvRating.setText(String.format(Locale.getDefault(), "%.1f", car.getRating()));
        } else {
            holder.ratingDivider.setVisibility(View.GONE);
            holder.tvRating.setVisibility(View.GONE);
        }

        if (car.getTag() == null || car.getTag().isEmpty()) {
            holder.tagBackground.setVisibility(View.GONE);
            holder.tvTag.setVisibility(View.GONE);
        } else {
            holder.tagBackground.setVisibility(View.VISIBLE);
            holder.tvTag.setVisibility(View.VISIBLE);
        }

        String imagePath = car.getImageUrl();
        if (imagePath != null && !imagePath.isEmpty()) {
            if (imagePath.startsWith("http")) {
                Glide.with(context)
                        .load(imagePath)
                        .placeholder(R.drawable.ic_car_placeholder)
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

        convertView.setOnClickListener(v -> listener.onCarClick(car));
        holder.btnDetails.setOnClickListener(v -> listener.onCarClick(car));

        if (showEditDeleteOptions) {
            holder.btnDetails.setVisibility(View.GONE);
            holder.layoutEditDelete.setVisibility(View.VISIBLE);
            holder.btnEdit.setOnClickListener(v -> listener.onEditClick(car));
            holder.btnDelete.setOnClickListener(v -> listener.onDeleteClick(car));
        } else {
            holder.btnDetails.setVisibility(View.VISIBLE);
            holder.layoutEditDelete.setVisibility(View.GONE);
            holder.btnEdit.setOnClickListener(null);
            holder.btnDelete.setOnClickListener(null);
        }

        return convertView;
    }

    static class CarViewHolder {
        ImageView ivCarImage;
        TextView tvCarName, tvLocation, tvPrice, tvRating, tvTransmission, tvSeats, tvTag;
        View tagBackground, ratingDivider, btnDetails, layoutEditDelete, btnEdit, btnDelete;

        CarViewHolder(View itemView) {
            ivCarImage = itemView.findViewById(R.id.ivCarImage);
            tvCarName = itemView.findViewById(R.id.tvCarName);
            tvLocation = itemView.findViewById(R.id.tvLocation);
            tvPrice = itemView.findViewById(R.id.tvPrice);
            tvRating = itemView.findViewById(R.id.tvRating);
            tvTransmission = itemView.findViewById(R.id.tvTransmission);
            tvSeats = itemView.findViewById(R.id.tvSeats);
            tvTag = itemView.findViewById(R.id.tvTag);
            tagBackground = itemView.findViewById(R.id.tagBackground);
            ratingDivider = itemView.findViewById(R.id.tvRatingDivider);
            btnDetails = itemView.findViewById(R.id.btnDetails);
            layoutEditDelete = itemView.findViewById(R.id.layoutEditDelete);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
