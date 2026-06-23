package com.yardenbental_danielcohen_shlomoedelstein.carn_go.util;

import android.graphics.Bitmap;
import android.util.Base64;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;

public final class ImageCodec {

    private ImageCodec() {
    }

    @NonNull
    public static String encodeJpegBase64(@NonNull Bitmap bitmap, int quality) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream);
        return Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT);
    }
}
