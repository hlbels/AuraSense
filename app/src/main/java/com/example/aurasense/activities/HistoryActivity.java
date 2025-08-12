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

        try {
            interpreter = new TFLiteEmotionInterpreter(this);
        } catch (Exception e) {
            Toast.makeText(this, "Failed to load model: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

        historyListView = findViewById(R.id.historyListView);
        clearHistoryBtn = findViewById(R.id.clearHistoryBtn);
        analyticsBtn = findViewById(R.id.analyticsBtn);

        analyticsBtn.setOnClickListener(v -> {
            Intent intent = new Intent(HistoryActivity.this, AnalyticsActivity.class);
            startActivity(intent);
        });

        clearHistoryBtn.setOnClickListener(v -> showClearHistoryDialog());

        loadHistory();

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
            try {
                float accMag = (float) Math.sqrt(
                        entry.accX * entry.accX + entry.accY * entry.accY + entry.accZ * entry.accZ
                );

                // Default to 0 in case bvp is not yet tracked in Entry
                float bvp = entry.bvp != 0 ? entry.bvp : 0.005f;

                int prediction = interpreter.predictFromRawSensors(
                        entry.accX, entry.accY, entry.accZ, entry.temp, bvp
                );

                String emotionLabel;
                switch (prediction) {
                    case 1:
                        emotionLabel = "High Stress";
                        break;
                    case 2:
                        emotionLabel = "Amusement";
                        break;
                    case 0:
                        emotionLabel = "Baseline";
                        break;
                    default:
                        emotionLabel = "Unknown";
                }

                String movement;
                if (accMag < 0.5) {
                    movement = "Not Moving";
                } else if (accMag < 2.5) {
                    movement = "Moving";
                } else {
                    movement = "Moving Fast";
                }

                String heartRate = String.format("%.0f bpm", entry.bpm);
                String temperature = String.format("%.1f°C", entry.temp);
                String formatted = String.format(
                        "Emotion: %s • %s • %s • %s\n%s",
                        emotionLabel, heartRate, temperature, movement,
                        sdf.format(new Date(entry.timestamp))
                );

                entries.add(formatted);
            } catch (Exception e) {
                entries.add("Error analyzing entry: " + e.getMessage());
            }
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
