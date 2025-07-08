package com.example.aurasense;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import androidx.appcompat.app.AppCompatActivity;

public class AlertActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alert);

        int stressLevel = getIntent().getIntExtra("stressLevel", -1);
        TextView stressText = findViewById(R.id.stressLevelText);
        stressText.setText("Stress Level: " + (stressLevel == 2 ? "Moderate" : "High"));

        Button startBreathing = findViewById(R.id.startBreathingButton);
        Button dismiss = findViewById(R.id.dismissButton);
        ToggleButton reminder = findViewById(R.id.reminderToggle);

        dismiss.setOnClickListener(v -> {
            if (reminder.isChecked()) {
                Toast.makeText(this, "Reminder set for 5 minutes.", Toast.LENGTH_SHORT).show();
            }
            finish();
        });

        startBreathing.setOnClickListener(v -> {
            Toast.makeText(this, "Starting breathing exercise...", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
