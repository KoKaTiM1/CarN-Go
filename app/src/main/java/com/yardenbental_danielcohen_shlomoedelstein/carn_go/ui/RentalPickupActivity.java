package com.yardenbental_danielcohen_shlomoedelstein.carn_go.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import android.util.Log;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.R;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.data.BookingRepository;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.model.Booking;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.sync.BookingSyncScheduler;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.util.ImageCodec;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.util.NetworkUtils;

public class RentalPickupActivity extends BaseNavigationActivity {

    private final BookingRepository bookingRepository = new BookingRepository();
    private ImageView ivPhoto;
    private Button btnSubmit;
    private View layoutOverlay;
    private String base64Image = null;
    private Booking booking;

    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Bitmap photo = (Bitmap) result.getData().getExtras().get("data");
                    if (photo != null) {
                        ivPhoto.setImageBitmap(photo);
                        layoutOverlay.setVisibility(View.GONE);
                        base64Image = encodeImage(photo);
                        btnSubmit.setEnabled(true);
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.app_name);
        setScreenContent(R.layout.fragment_rental_pickup, 0, false, true);
        View view = findViewById(android.R.id.content);
        booking = (Booking) getIntent().getSerializableExtra("booking");

        ivPhoto = view.findViewById(R.id.ivPickupPhoto);
        btnSubmit = view.findViewById(R.id.btnSubmitPickup);
        layoutOverlay = view.findViewById(R.id.layoutPickupPhotoOverlay);

        view.findViewById(R.id.cardPickupPhoto).setOnClickListener(v -> {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cameraLauncher.launch(takePictureIntent);
        });

        btnSubmit.setOnClickListener(v -> submitPickupPhoto());
    }

    private String encodeImage(Bitmap bitmap) {
        return ImageCodec.encodeJpegBase64(bitmap, 70);
    }

    private void submitPickupPhoto() {
        if (base64Image == null || booking == null) return;
        if (!NetworkUtils.checkAndToast(this)) return;

        btnSubmit.setEnabled(false);
        bookingRepository.submitPickupPhoto(booking.getId(), base64Image)
                .addOnSuccessListener(aVoid -> {
                    BookingSyncScheduler.requestImmediateSync(this, "rental_pickup");
                    Toast.makeText(this, "Pickup photo uploaded! You can now start your journey.", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e("RentalPickupActivity", "Failed to submit pickup photo", e);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnSubmit.setEnabled(true);
                });
    }
}
