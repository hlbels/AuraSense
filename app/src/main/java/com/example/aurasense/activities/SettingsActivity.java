package com.example.aurasense.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.aurasense.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class SettingsActivity extends AppCompatActivity {

    private Switch switchStressAlerts, switchModel2;
    private TextView exportDataBtn, deleteDataBtn, backBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Initialize views
        switchStressAlerts = findViewById(R.id.switchStressAlerts);
        switchModel2 = findViewById(R.id.switchModel2);
        exportDataBtn = findViewById(R.id.exportDataBtn);
        deleteDataBtn = findViewById(R.id.deleteDataBtn);
        backBtn = findViewById(R.id.backBtn);

        // Load preferences
        SharedPreferences prefs = getSharedPreferences("AuraPrefs", MODE_PRIVATE);
        switchStressAlerts.setChecked(prefs.getBoolean("stress_alerts_enabled", true));
        switchModel2.setChecked(prefs.getBoolean("model_2_enabled", false));

        // Set up switch listeners
        switchStressAlerts.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            prefs.edit().putBoolean("stress_alerts_enabled", isChecked).apply();
            Toast.makeText(this, "Stress alerts " + (isChecked ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
        });

        switchModel2.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            prefs.edit().putBoolean("model_2_enabled", isChecked).apply();
            Toast.makeText(this, "Model 2 " + (isChecked ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
        });

        // Set up button listeners
        exportDataBtn.setOnClickListener(v -> {
            Toast.makeText(this, "Exporting data...", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse("content://com.example.aurasense.provider/data"), "*/*");
            startActivity(intent);
        });

        deleteDataBtn.setOnClickListener(v -> showDeleteDataDialog());

        backBtn.setOnClickListener(v -> {
            // Navigate back to home screen
            startActivity(new Intent(this, HomeActivity.class));
            finish();
        });

        // Set up bottom navigation
        BottomNavigationView bottomNavigation = findViewById(R.id.bottomNavigation);
        bottomNavigation.setSelectedItemId(R.id.nav_settings);

        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                startActivity(new Intent(this, HomeActivity.class));
                return true;
            } else if (id == R.id.nav_history) {
                startActivity(new Intent(this, HistoryActivity.class));
                return true;
            } else if (id == R.id.nav_notifications) {
                startActivity(new Intent(this, NotificationActivity.class));
                return true;
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
                return true;
            } else if (id == R.id.nav_settings) {
                return true;
            }
            return false;
        });
    }

    private void showDeleteDataDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete All Data")
                .setMessage("Are you sure you want to delete all app data? This will clear:\n\n" +
                        "• All stress history\n" +
                        "• All notifications\n" +
                        "• All settings\n\n" +
                        "This action cannot be undone.")
                .setPositiveButton("Delete All", (dialog, which) -> {
                    deleteAllAppData();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteAllAppData() {
        try {
            // Clear all SharedPreferences
            getSharedPreferences("AuraPrefs", MODE_PRIVATE).edit().clear().apply();
            getSharedPreferences("AuraNotifications", MODE_PRIVATE).edit().clear().apply();
            
            // Clear history storage
            com.example.aurasense.utils.HistoryStorage.clearHistory();
            
            // Reset switches to default values
            switchStressAlerts.setChecked(true);
            switchModel2.setChecked(false);
            
            Toast.makeText(this, "All app data deleted successfully", Toast.LENGTH_LONG).show();
            
        } catch (Exception e) {
            Toast.makeText(this, "Error deleting data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
