package com.example.aurasense;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class WelcomeActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        Button loginButton = findViewById(R.id.loginButton);
        Button signupButton = findViewById(R.id.signupButton);

        loginButton.setOnClickListener(v -> startActivity(
                new Intent(WelcomeActivity.this, LoginActivity.class)));

        signupButton.setOnClickListener(v -> startActivity(
                new Intent(WelcomeActivity.this, SignupActivity.class)));
    }
}