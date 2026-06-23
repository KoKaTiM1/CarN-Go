package com.yardenbental_danielcohen_shlomoedelstein.carn_go.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;
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

public class RentalCompletionActivity extends BaseNavigationActivity {

    private final BookingRepository bookingRepository = new BookingRepository();
    private ImageView ivPhoto;
    private Button btnSubmit;
    private View layoutOverlay;
    private RatingBar ratingBar;
    private TextView tvRatingValue;
    private String base64Image = null;
    private float selectedRating = 0f;
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
                        updateSubmitEnabledState();
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.app_name);
        setScreenContent(R.layout.fragment_rental_completion, 0, false, true);
        View view = findViewById(android.R.id.content);
        booking = (Booking) getIntent().getSerializableExtra("booking");

        ivPhoto = view.findViewById(R.id.ivCompletionPhoto);
        btnSubmit = view.findViewById(R.id.btnSubmitCompletion);
        layoutOverlay = view.findViewById(R.id.layoutPhotoOverlay);
        ratingBar = view.findViewById(R.id.ratingBarCompletion);
        tvRatingValue = view.findViewById(R.id.tvCompletionRatingValue);

        view.findViewById(R.id.cardCompletionPhoto).setOnClickListener(v -> {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cameraLauncher.launch(takePictureIntent);
        });

        ratingBar.setOnRatingBarChangeListener((bar, rating, fromUser) -> {
            float normalizedRating = rating <= 0f ? 0f : Math.max(1f, rating);
            if (normalizedRating != rating) {
                bar.setRating(normalizedRating);
                return;
            }
            selectedRating = normalizedRating;
            updateRatingLabel();
            updateSubmitEnabledState();
        });

        updateRatingLabel();
        updateSubmitEnabledState();
        btnSubmit.setOnClickListener(v -> completeRental());
    }

    private String encodeImage(Bitmap bitmap) {
        return ImageCodec.encodeJpegBase64(bitmap, 70);
    }

    private void completeRental() {
        if (base64Image == null || booking == null || selectedRating < 1f) return;
        if (!NetworkUtils.checkAndToast(this)) return;

        btnSubmit.setEnabled(false);
        bookingRepository.completeBooking(booking, base64Image, selectedRating)
                .addOnSuccessListener(aVoid -> {
                    BookingSyncScheduler.requestImmediateSync(this, "rental_completed");
                    Toast.makeText(this, R.string.rental_completed_successfully, Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e("RentalCompletionActivity", "Failed to complete rental", e);
                    Toast.makeText(this, getString(R.string.rating_completion_error, e.getMessage()), Toast.LENGTH_SHORT).show();
                    updateSubmitEnabledState();
                });
    }

    private void updateRatingLabel() {
        if (selectedRating > 0f) {
            tvRatingValue.setText(getString(R.string.selected_rating_value, selectedRating));
        } else {
            tvRatingValue.setText(R.string.rating_required_hint);
        }
    }

    private void updateSubmitEnabledState() {
        btnSubmit.setEnabled(base64Image != null && selectedRating >= 1f);
    }
}
