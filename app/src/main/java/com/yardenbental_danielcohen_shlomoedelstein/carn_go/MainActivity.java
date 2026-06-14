package com.yardenbental_danielcohen_shlomoedelstein.carn_go;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import androidx.appcompat.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessaging;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.firebase.FirestoreHelper;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.sync.BookingSyncScheduler;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.ui.AboutActivity;
import com.yardenbental_danielcohen_shlomoedelstein.carn_go.ui.SettingsActivity;


public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Enable edge-to-edge display for a modern UI look
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Request notification permissions for Android 13+ (Tiramisu)
        askNotificationPermission();

        // Ensure user is signed in anonymously to have a consistent UID for notifications and car listings
        signInAnonymously();
        // Adjust view padding to account for system bars (status bar, navigation bar)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        // Set up Navigation component with BottomNavigationView
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();
            BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
            NavigationUI.setupWithNavController(bottomNav, navController);

            // Handle tab re-selection: if the user clicks the current tab icon, pop to its root
            bottomNav.setOnItemReselectedListener(item -> {
                navController.popBackStack(item.getItemId(), false);
            });
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
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.action_about) {
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        } else if (id == R.id.action_exit) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Launcher for notification permission request.
     * Handles the user's response to the permission dialog.
     */
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    // FCM SDK (and your app) can post notifications.
                    Log.d("Permission", "Notification permission granted");
                } else {
                    // TODO: Inform user that that your app will not show notifications.
                    Log.d("Permission", "Notification permission denied");
                }
            });

    /**
     * Signs in the user anonymously if they are not already signed in.
     * This provides a consistent UID for car ownership and FCM token storage.
     */
    private void signInAnonymously() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            auth.signInAnonymously().addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) {
                    Log.d("Auth", "signInAnonymously:success");
                    initializeFCM();
                    BookingSyncScheduler.requestImmediateSync(MainActivity.this, "app_launch");
                } else {
                    Exception e = task.getException();
                    Log.w("Auth", "signInAnonymously:failure", e);
                    if (e != null && e.getMessage() != null && e.getMessage().contains("CONFIGURATION_NOT_FOUND")) {
                        Log.e("Auth", "ERROR: Anonymous Authentication is not enabled in the Firebase Console.");
                    }
                    // Fallback to initializing FCM anyway (FirestoreHelper will use SharedPreferences ID)
                    initializeFCM();
                    BookingSyncScheduler.requestImmediateSync(MainActivity.this, "app_launch_fallback");
                }
            });
        } else {
            // Already signed in
            initializeFCM();
            BookingSyncScheduler.requestImmediateSync(this, "app_launch_existing_user");
        }
    }

    /**
     * Initializes FCM and retrieves the registration token.
     */
    private void initializeFCM() {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w("FCM", "Fetching FCM registration token failed", task.getException());
                        return;
                    }
                    String token = task.getResult();
                    FirestoreHelper.updateUserToken(MainActivity.this, token);
                });
    }

    /**
     * Checks and requests notification permissions if required by the Android version.
     */
    private void askNotificationPermission() {
        // This is only necessary for API level >= 33 (TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED) {
                // FCM SDK (and your app) can post notifications.
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                // TODO: display an educational UI explaining to the user the features that will be enabled
                //       by them granting the POST_NOTIFICATION permission. This UI should provide the user
                //       "OK" and "No thanks" buttons. If the user selects "OK," directly request the permission.
                //       If the user selects "No thanks," allow the user to continue without notifications.
            } else {
                // Directly ask for the permission
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }
}
