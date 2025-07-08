package com.example.aurasense;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        Button doneButton = findViewById(R.id.doneButton);
        doneButton.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, ConsentActivity.class));
            finish();
        });
    }
}