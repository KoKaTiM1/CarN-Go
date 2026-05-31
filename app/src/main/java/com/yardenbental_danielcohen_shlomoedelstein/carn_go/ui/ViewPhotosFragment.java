package com.yardenbental_danielcohen_shlomoedelstein.carn_go.ui;

import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.R;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.model.Booking;

public class ViewPhotosFragment extends Fragment {

    private ImageView ivPickup, ivFinish;
    private Booking booking;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            booking = (Booking) getArguments().getSerializable("booking");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_view_photos, container, false);

        ivPickup = view.findViewById(R.id.ivViewPickupPhoto);
        ivFinish = view.findViewById(R.id.ivViewFinishPhoto);

        if (booking != null) {
            loadPhoto(booking.getStartPhotoUrl(), ivPickup);
            loadPhoto(booking.getEndPhotoUrl(), ivFinish);
        }

        view.findViewById(R.id.btnClosePhotos).setOnClickListener(v -> 
            Navigation.findNavController(v).navigateUp()
        );

        return view;
    }

    private void loadPhoto(String photoUrl, ImageView imageView) {
        if (photoUrl != null && !photoUrl.isEmpty()) {
            if (photoUrl.startsWith("http")) {
                Glide.with(this).load(photoUrl).into(imageView);
            } else {
                try {
                    byte[] decoded = Base64.decode(photoUrl, Base64.DEFAULT);
                    Glide.with(this).asBitmap().load(decoded).into(imageView);
                } catch (Exception e) {
                    imageView.setImageResource(R.drawable.ic_car_placeholder);
                }
            }
        }
    }
}