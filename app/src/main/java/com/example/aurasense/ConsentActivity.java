package com.example.aurasense;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class ConsentActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_consent);

        SharedPreferences prefs = getSharedPreferences("AuraSensePrefs", MODE_PRIVATE);
        CheckBox ageCheckBox = findViewById(R.id.ageCheckBox);
        CheckBox cameraMicCheckBox = findViewById(R.id.cameraMicCheckBox);
        Button agreeButton = findViewById(R.id.agreeButton);
        Button disagreeButton = findViewById(R.id.disagreeButton);

        ageCheckBox.setChecked(prefs.getBoolean("ageConsent", false));
        cameraMicCheckBox.setChecked(prefs.getBoolean("mediaConsent", false));

        agreeButton.setOnClickListener(v -> {
            if (!ageCheckBox.isChecked()) {
                Toast.makeText(this, "You must agree to the terms.", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit()
                    .putBoolean("ageConsent", true)
                    .putBoolean("mediaConsent", cameraMicCheckBox.isChecked())
                    .apply();
            startActivity(new Intent(ConsentActivity.this, DevicePairingActivity.class));
        });

        disagreeButton.setOnClickListener(v -> {
            Intent intent = new Intent(ConsentActivity.this, WelcomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }
}