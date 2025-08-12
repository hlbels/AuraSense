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

    private static final String PREFS = "AuraNotifications";
    private static final String PREFS_KEY = "notifications";

    private TextView notificationHistory;
    private Button clearNotificationsBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification);

        notificationHistory = findViewById(R.id.notificationHistory);
        clearNotificationsBtn = findViewById(R.id.clearNotificationsBtn);

        loadNotifications();

        clearNotificationsBtn.setOnClickListener(v -> showClearNotificationsDialog());

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
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String allLogs = prefs.getString(PREFS_KEY, "");

        StringBuilder stressLogs = new StringBuilder();
        StringBuilder amusementLogs = new StringBuilder();

        if (!allLogs.isEmpty()) {
            String[] lines = allLogs.split("\n");
            for (String line : lines) {
                String lower = line.toLowerCase();

                // Prefer our new canonical tags
                if (line.startsWith("[Stress]")) {
                    stressLogs.append(line).append("\n");
                } else if (line.startsWith("[Amusement]")) {
                    amusementLogs.append(line).append("\n");
                } else {
                    // Backward compatibility with legacy strings
                    if (lower.contains("high discomfort") || lower.contains("stress")) {
                        stressLogs.append(line).append("\n");
                    } else if (lower.contains("amusement") || lower.contains("positive emotion")) {
                        amusementLogs.append(line).append("\n");
                    }
                }
            }
        }

        StringBuilder display = new StringBuilder();
        display.append("📉 Stress Notifications:\n");
        display.append(stressLogs.length() > 0 ? stressLogs.toString() : "No stress notifications.\n");

        display.append("\n🎉 Amusement Notifications:\n");
        display.append(amusementLogs.length() > 0 ? amusementLogs.toString() : "No amusement notifications.");

        notificationHistory.setText(display.toString().trim());
    }

    private void showClearNotificationsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Clear Notifications")
                .setMessage("Are you sure you want to clear all notification history? This action cannot be undone.")
                .setPositiveButton("Clear", (dialog, which) -> {
                    SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
                    prefs.edit().remove(PREFS_KEY).apply();
                    loadNotifications();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
