package com.example.aurasense.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.aurasense.R;
import com.example.aurasense.utils.HistoryStorage;
import com.example.aurasense.utils.TFLiteEmotionInterpreter;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {

    private ListView historyListView;
    private Button clearHistoryBtn, analyticsBtn;
    private TFLiteEmotionInterpreter interpreter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        // Initialize TensorFlow Lite interpreter for stress prediction
        interpreter = new TFLiteEmotionInterpreter(this);

        // Initialize views
        historyListView = findViewById(R.id.historyListView);
        clearHistoryBtn = findViewById(R.id.clearHistoryBtn);
        analyticsBtn = findViewById(R.id.analyticsBtn);

        // Set up buttons
        analyticsBtn.setOnClickListener(v -> {
            Intent intent = new Intent(HistoryActivity.this, AnalyticsActivity.class);
            startActivity(intent);
        });

        clearHistoryBtn.setOnClickListener(v -> showClearHistoryDialog());

        // Load and display history
        loadHistory();

        // Set up bottom navigation
        BottomNavigationView bottomNavigation = findViewById(R.id.bottomNavigation);
        bottomNavigation.setSelectedItemId(R.id.nav_history);

        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                startActivity(new Intent(this, HomeActivity.class));
                return true;
            } else if (id == R.id.nav_history) {
                return true;
            } else if (id == R.id.nav_notifications) {
                startActivity(new Intent(this, NotificationActivity.class));
                return true;
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
                return true;
            } else if (id == R.id.nav_settings) {
                try {
                    startActivity(new Intent(this, SettingsActivity.class));
                } catch (Exception e) {
                    Toast.makeText(this, "Error opening settings: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            return false;
        });
    }

    private void loadHistory() {
        ArrayList<String> entries = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());

        for (HistoryStorage.Entry entry : HistoryStorage.getHistory()) {
            // Use the TensorFlow model to determine stress level
            float accMag = (float) Math
                    .sqrt(entry.accX * entry.accX + entry.accY * entry.accY + entry.accZ * entry.accZ);
            int prediction = interpreter.predictWithSmoothing(entry.bpm, entry.hrv, entry.temp, entry.accX, entry.accY,
                    entry.accZ, accMag);

            String stressLevel = (prediction == 1) ? "High Stress" : "Normal";
            String heartRate = String.format("%.0f bpm", entry.bpm);

            String formatted = String.format(
                    "Stress: %s • %s\n%s",
                    stressLevel,
                    heartRate,
                    sdf.format(new Date(entry.timestamp)));
            entries.add(formatted);
        }

        if (entries.isEmpty()) {
            entries.add("No history data available");
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, entries);
        historyListView.setAdapter(adapter);
    }

    private void showClearHistoryDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Clear History")
                .setMessage("Are you sure you want to clear all history data? This action cannot be undone.")
                .setPositiveButton("Clear", (dialog, which) -> {
                    HistoryStorage.clearHistory();
                    loadHistory(); // Refresh the list
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
