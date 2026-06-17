package com.yardenbental_danielcohen_shlomoedelstein.carn_go;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.yardenbental_danielcohen_shlomoedelstein.carn_go.ui.BrowseCarsActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startActivity(new Intent(this, BrowseCarsActivity.class));
        finish();
    }
}
