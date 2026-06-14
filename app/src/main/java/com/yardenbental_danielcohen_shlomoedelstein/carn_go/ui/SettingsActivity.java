package com.yardenbental_danielcohen_shlomoedelstein.carn_go.ui;

import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.AppPreferences;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.R;

public class SettingsActivity extends AppCompatActivity {

    private static final int[] SEARCH_RADIUS_OPTIONS = {0, 5, 10, 20, 50};

    private TextView tvRangeValue;
    private Slider sliderSearchRadius;
    private SwitchMaterial switchBookingReminders;
    private SwitchMaterial switchDarkMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbarSettings);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvRangeValue = findViewById(R.id.tvRangeValue);
        sliderSearchRadius = findViewById(R.id.sliderSearchRadius);
        switchBookingReminders = findViewById(R.id.switchBookingReminders);
        switchDarkMode = findViewById(R.id.switchDarkMode);

        int savedRadiusKm = AppPreferences.getSearchRadiusKm(this);
        sliderSearchRadius.setValue(findIndexForRadius(savedRadiusKm));
        updateRangeLabel(savedRadiusKm);
        switchBookingReminders.setChecked(AppPreferences.areBookingRemindersEnabled(this));
        switchDarkMode.setChecked(AppPreferences.isDarkModeEnabled(this));

        sliderSearchRadius.addOnChangeListener((slider, value, fromUser) -> {
            int radiusKm = SEARCH_RADIUS_OPTIONS[(int) value];
            AppPreferences.setSearchRadiusKm(this, radiusKm);
            updateRangeLabel(radiusKm);
        });

        switchBookingReminders.setOnCheckedChangeListener((buttonView, isChecked) ->
                AppPreferences.setBookingRemindersEnabled(this, isChecked));

        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppPreferences.setDarkModeEnabled(this, isChecked);
            AppPreferences.applyThemeMode(this);
        });
    }

    private int findIndexForRadius(int radiusKm) {
        for (int i = 0; i < SEARCH_RADIUS_OPTIONS.length; i++) {
            if (SEARCH_RADIUS_OPTIONS[i] == radiusKm) {
                return i;
            }
        }
        return 0;
    }

    private void updateRangeLabel(int radiusKm) {
        if (radiusKm == 0) {
            tvRangeValue.setText(R.string.search_range_no_limit);
        } else {
            tvRangeValue.setText(getString(R.string.search_range_value, radiusKm));
        }
    }
}
