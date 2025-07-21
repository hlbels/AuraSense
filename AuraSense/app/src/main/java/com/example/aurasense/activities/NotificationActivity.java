package com.example.aurasense.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.aurasense.R;

public class NotificationActivity extends AppCompatActivity {

    private TextView notificationHistory;
    private Button clearNotificationsBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification);

        // Show back arrow in the action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Notifications");
        }

        notificationHistory = findViewById(R.id.notificationHistory);
        clearNotificationsBtn = findViewById(R.id.clearNotificationsBtn);

        loadNotifications();

        clearNotificationsBtn.setOnClickListener(v -> {
            SharedPreferences prefs = getSharedPreferences("AuraNotifications", MODE_PRIVATE);
            prefs.edit().remove("notifications").apply();
            notificationHistory.setText("No stress notifications.");
        });
    }

    private void loadNotifications() {
        SharedPreferences prefs = getSharedPreferences("AuraNotifications", MODE_PRIVATE);
        String history = prefs.getString("notifications", "");
        notificationHistory.setText(history.isEmpty() ? "No stress notifications." : history);
    }
    // Handle back arrow press
    @Override
    public boolean onSupportNavigateUp() {
        finish(); // Close this activity and go back
        return true;
    }
}
