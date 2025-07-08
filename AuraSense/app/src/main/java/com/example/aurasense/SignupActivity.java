package com.example.aurasense;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class SignupActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        Button registerButton = findViewById(R.id.registerButton);
        registerButton.setOnClickListener(v -> {
            startActivity(new Intent(SignupActivity.this, ConsentActivity.class));
            finish();
        });
    }
}