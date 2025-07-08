package com.example.aurasense;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class NotificationsActivity extends AppCompatActivity {

    private TextView notificationText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        notificationText = findViewById(R.id.notificationText);

        // Placeholder — you can extend this with real-time alerts, reminders, logs
        notificationText.setText("No notifications yet.\nYou will see stress alerts or reminders here.");
    }
}
