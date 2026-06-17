package com.yardenbental_danielcohen_shlomoedelstein.carn_go.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.LayoutRes;
import androidx.annotation.MenuRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.R;

public abstract class BaseNavigationActivity extends AppCompatActivity {

    protected BottomNavigationView bottomNavigationView;
    protected Toolbar toolbar;
    private int selectedBottomItemId;
    private boolean updatingBottomSelection;

    protected void setScreenContent(
            @LayoutRes int layoutResId,
            int selectedBottomItemId,
            boolean showBottomNavigation,
            boolean showToolbar
    ) {
        this.selectedBottomItemId = selectedBottomItemId;
        setTitle(R.string.app_name);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.background));
        root.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));

        toolbar = new Toolbar(this);
        toolbar.setId(View.generateViewId());
        toolbar.setTitle(R.string.app_name);
        toolbar.setTitleTextColor(Color.WHITE);
        toolbar.setBackgroundColor(showToolbar ? ContextCompat.getColor(this, R.color.primary) : Color.TRANSPARENT);
        toolbar.setVisibility(showToolbar ? View.VISIBLE : View.GONE);
        root.addView(toolbar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                getActionBarHeight()
        ));
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.app_name);
        }

        FrameLayout content = new FrameLayout(this);
        content.setId(android.R.id.content);
        root.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        bottomNavigationView = new BottomNavigationView(this);
        bottomNavigationView.setId(View.generateViewId());
        bottomNavigationView.inflateMenu(R.menu.bottom_nav_menu);
        bottomNavigationView.setBackgroundColor(ContextCompat.getColor(this, R.color.surface));
        bottomNavigationView.setVisibility(showBottomNavigation ? View.VISIBLE : View.GONE);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (updatingBottomSelection) {
                return true;
            }
            if (itemId == selectedBottomItemId) {
                return true;
            }
            if (itemId == R.id.nav_browse_cars) {
                openTopLevelActivity(BrowseCarsActivity.class);
                return true;
            }
            if (itemId == R.id.nav_my_cars) {
                openTopLevelActivity(MyCarsActivity.class);
                return true;
            }
            if (itemId == R.id.nav_my_bookings) {
                openTopLevelActivity(MyBookingsActivity.class);
                return true;
            }
            return false;
        });
        syncSelectedBottomItem();
        root.addView(bottomNavigationView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        super.setContentView(root);
        getLayoutInflater().inflate(layoutResId, content, true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncSelectedBottomItem();
    }

    private int getActionBarHeight() {
        TypedValue typedValue = new TypedValue();
        if (getTheme().resolveAttribute(androidx.appcompat.R.attr.actionBarSize, typedValue, true)) {
            return TypedValue.complexToDimensionPixelSize(typedValue.data, getResources().getDisplayMetrics());
        }
        return Math.round(56 * getResources().getDisplayMetrics().density);
    }

    private void openTopLevelActivity(Class<?> target) {
        Intent intent = new Intent(this, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    private void syncSelectedBottomItem() {
        if (bottomNavigationView == null || selectedBottomItemId == 0) {
            return;
        }
        updatingBottomSelection = true;
        bottomNavigationView.setSelectedItemId(selectedBottomItemId);
        updatingBottomSelection = false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
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
