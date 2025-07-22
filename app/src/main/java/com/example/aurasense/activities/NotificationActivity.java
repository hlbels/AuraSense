package com.example.aurasense.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.aurasense.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class NotificationActivity extends AppCompatActivity {

    private TextView notificationHistory;
    private Button clearNotificationsBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification);

        // Initialize views
        notificationHistory = findViewById(R.id.notificationHistory);
        clearNotificationsBtn = findViewById(R.id.clearNotificationsBtn);

        // Load notifications
        loadNotifications();

        // Set up clear notifications button with confirmation dialog
        clearNotificationsBtn.setOnClickListener(v -> showClearNotificationsDialog());

        // Set up bottom navigation
        BottomNavigationView bottomNavigation = findViewById(R.id.bottomNavigation);
        bottomNavigation.setSelectedItemId(R.id.nav_notifications);

        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                startActivity(new Intent(this, HomeActivity.class));
                return true;
            } else if (id == R.id.nav_history) {
                startActivity(new Intent(this, HistoryActivity.class));
                return true;
            } else if (id == R.id.nav_notifications) {
                return true;
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
                return true;
            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            }
            return false;
        });
    }

    private void loadNotifications() {
        SharedPreferences prefs = getSharedPreferences("AuraNotifications", MODE_PRIVATE);
        String history = prefs.getString("notifications", "");
        notificationHistory.setText(history.isEmpty() ? "No stress notifications." : history);
    }

    private void showClearNotificationsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Clear Notifications")
                .setMessage("Are you sure you want to clear all notification history? This action cannot be undone.")
                .setPositiveButton("Clear", (dialog, which) -> {
                    SharedPreferences prefs = getSharedPreferences("AuraNotifications", MODE_PRIVATE);
                    prefs.edit().remove("notifications").apply();
                    loadNotifications(); // Refresh the display
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
