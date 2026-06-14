package com.yardenbental_danielcohen_shlomoedelstein.carn_go.ui;

import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.R;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.model.Booking;

public class ViewPhotosActivity extends BaseNavigationActivity {

    private ImageView ivPickup, ivFinish;
    private Booking booking;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Rental Photos");
        setScreenContent(R.layout.fragment_view_photos, 0, false, true);
        View view = findViewById(android.R.id.content);
        booking = (Booking) getIntent().getSerializableExtra("booking");

        ivPickup = view.findViewById(R.id.ivViewPickupPhoto);
        ivFinish = view.findViewById(R.id.ivViewFinishPhoto);

        if (booking != null) {
            loadPhoto(booking.getStartPhotoUrl(), ivPickup);
            loadPhoto(booking.getEndPhotoUrl(), ivFinish);
        }

        view.findViewById(R.id.btnClosePhotos).setOnClickListener(v -> finish());
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
