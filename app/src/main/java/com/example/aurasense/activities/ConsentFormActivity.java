package com.example.aurasense.activities;

import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.example.aurasense.R;

public class ConsentFormActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_consent_form);

        Button closeBtn = findViewById(R.id.consentCloseBtn);
        closeBtn.setOnClickListener(v -> finish());
    }
}
