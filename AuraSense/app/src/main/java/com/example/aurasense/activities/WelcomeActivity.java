package com.example.aurasense.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.example.aurasense.R;

public class WelcomeActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        Button loginBtn = findViewById(R.id.loginButton);
        Button signupBtn = findViewById(R.id.signupButton);

        loginBtn.setOnClickListener(v -> startActivity(new Intent(this, LoginActivity.class)));
        signupBtn.setOnClickListener(v -> startActivity(new Intent(this, SignupActivity.class)));
    }
}
