package com.yardenbental_danielcohen_shlomoedelstein.carn_go.ui;

import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.yardenbental_danielcohen_shlomoedelstein.carn_go.AppPreferences;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.R;

public class SettingsActivity extends AppCompatActivity {

    private static final int[] SEARCH_RADIUS_OPTIONS = {0, 5, 10, 20, 50};

    private TextView tvRangeValue;
    private SeekBar sliderSearchRadius;
    private Switch switchBookingReminders;
    private Switch switchDarkMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbarSettings);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.app_name);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        tvRangeValue = findViewById(R.id.tvRangeValue);
        sliderSearchRadius = findViewById(R.id.sliderSearchRadius);
        switchBookingReminders = findViewById(R.id.switchBookingReminders);
        switchDarkMode = findViewById(R.id.switchDarkMode);

        int savedRadiusKm = AppPreferences.getSearchRadiusKm(this);
        sliderSearchRadius.setProgress(findIndexForRadius(savedRadiusKm));
        updateRangeLabel(savedRadiusKm);
        switchBookingReminders.setChecked(AppPreferences.areBookingRemindersEnabled(this));
        switchDarkMode.setChecked(AppPreferences.isDarkModeEnabled(this));

        sliderSearchRadius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int radiusKm = SEARCH_RADIUS_OPTIONS[progress];
                AppPreferences.setSearchRadiusKm(SettingsActivity.this, radiusKm);
                updateRangeLabel(radiusKm);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        if (id == R.id.action_about) {
            showAboutDialog();
            return true;
        }
        if (id == R.id.action_exit) {
            showExitDialog();
            return true;
        }
        if (id == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showAboutDialog() {
        String deviceInfo = Build.MANUFACTURER + " " + Build.MODEL
                + ", Android " + Build.VERSION.RELEASE
                + " (API " + Build.VERSION.SDK_INT + ")";
        String message = getString(
                R.string.about_dialog_message,
                getString(R.string.app_name),
                getPackageName(),
                deviceInfo
        );
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.menu_about)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void showExitDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.menu_exit)
                .setMessage(R.string.exit_dialog_message)
                .setPositiveButton(R.string.exit_confirm, (dialog, which) -> finishAffinity())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
