package com.example.aurasense.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.aurasense.R;

public class SignupActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        EditText emailInput = findViewById(R.id.emailInput);
        EditText passwordInput = findViewById(R.id.passwordInput);
        CheckBox consentCheck = findViewById(R.id.consentCheck);
        Button signupBtn = findViewById(R.id.submitSignup);
        TextView consentLink = findViewById(R.id.readConsentLink);

        consentLink.setText(Html.fromHtml("<u>Read Consent Form</u>"));
        consentLink.setMovementMethod(LinkMovementMethod.getInstance());
        consentLink.setOnClickListener(v -> {
            startActivity(new Intent(SignupActivity.this, ConsentFormActivity.class));
        });

        signupBtn.setOnClickListener(v -> {
            if (!consentCheck.isChecked()) {
                Toast.makeText(this, "You must agree to the consent policy.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Skip auth for Sprint 1
            startActivity(new Intent(this, HomeActivity.class));
            finish();
        });
    }
}
