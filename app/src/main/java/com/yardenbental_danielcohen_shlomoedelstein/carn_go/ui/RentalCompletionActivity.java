package com.yardenbental_danielcohen_shlomoedelstein.carn_go.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.firebase.firestore.FirebaseFirestore;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.R;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.model.Booking;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.sync.BookingStatus;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.sync.BookingSyncScheduler;

import java.io.ByteArrayOutputStream;

public class RentalCompletionActivity extends BaseNavigationActivity {

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
        setScreenContent(R.layout.fragment_rental_completion, 0, false, true);
        View view = findViewById(android.R.id.content);
        booking = (Booking) getIntent().getSerializableExtra("booking");

        ivPhoto = view.findViewById(R.id.ivCompletionPhoto);
        btnSubmit = view.findViewById(R.id.btnSubmitCompletion);
        layoutOverlay = view.findViewById(R.id.layoutPhotoOverlay);

        view.findViewById(R.id.cardCompletionPhoto).setOnClickListener(v -> {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cameraLauncher.launch(takePictureIntent);
        });

        btnSubmit.setOnClickListener(v -> completeRental());
    }

    private String encodeImage(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
        byte[] b = baos.toByteArray();
        return Base64.encodeToString(b, Base64.DEFAULT);
    }

    private void completeRental() {
        if (base64Image == null || booking == null) return;

        btnSubmit.setEnabled(false);
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("bookings").document(booking.getId())
                .update("status", BookingStatus.COMPLETED, "endPhotoUrl", base64Image)
                .addOnSuccessListener(aVoid -> {
                    BookingSyncScheduler.requestImmediateSync(this, "rental_completed");
                    Toast.makeText(this, "Rental completed successfully!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnSubmit.setEnabled(true);
                });
    }
}
