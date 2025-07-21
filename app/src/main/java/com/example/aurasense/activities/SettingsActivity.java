package com.example.aurasense.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.aurasense.R;

public class SettingsActivity extends AppCompatActivity {

    private Switch switchStressAlerts, switchModel2;
    private TextView exportDataBtn, deleteDataBtn, backBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Show back arrow in the action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Settings");
        }

        switchStressAlerts = findViewById(R.id.switchStressAlerts);
        switchModel2 = findViewById(R.id.switchModel2);
        exportDataBtn = findViewById(R.id.exportDataBtn);
        deleteDataBtn = findViewById(R.id.deleteDataBtn);
        backBtn = findViewById(R.id.backBtn);

        SharedPreferences prefs = getSharedPreferences("AuraPrefs", MODE_PRIVATE);
        switchStressAlerts.setChecked(prefs.getBoolean("stress_alerts_enabled", true));
        switchModel2.setChecked(prefs.getBoolean("model_2_enabled", false));

        switchStressAlerts.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            prefs.edit().putBoolean("stress_alerts_enabled", isChecked).apply();
            Toast.makeText(this, "Stress alerts " + (isChecked ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
        });

        switchModel2.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            prefs.edit().putBoolean("model_2_enabled", isChecked).apply();
            Toast.makeText(this, "Model 2 " + (isChecked ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
        });

        exportDataBtn.setOnClickListener(v -> {
            // Open file manager (optional, adjust if implementing real export)
            Toast.makeText(this, "Exporting data...", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse("content://com.example.aurasense.provider/data"), "*/*");
            startActivity(intent);
        });

        deleteDataBtn.setOnClickListener(v -> {
            getSharedPreferences("AuraPrefs", MODE_PRIVATE).edit().clear().apply();
            Toast.makeText(this, "Data deleted successfully", Toast.LENGTH_SHORT).show();
        });

        backBtn.setOnClickListener(v -> finish());
    }
    // Handle back arrow press
    @Override
    public boolean onSupportNavigateUp() {
        finish(); // Close this activity and go back
        return true;
    }
}
