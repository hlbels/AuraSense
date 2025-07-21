package com.example.aurasense.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.aurasense.R;

public class LoginActivity extends AppCompatActivity {

    private EditText emailInput, passwordInput;
    private CheckBox consentCheck;
    private Button loginSubmit;
    private TextView consentLink;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        consentCheck = findViewById(R.id.consentCheck);
        loginSubmit = findViewById(R.id.submitLogin);
        consentLink = findViewById(R.id.readConsentLink);

        consentLink.setText(Html.fromHtml("<u>Read Consent Form</u>"));
        consentLink.setMovementMethod(LinkMovementMethod.getInstance());
        consentLink.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, ConsentFormActivity.class));
        });

        loginSubmit.setOnClickListener(v -> {
            if (!consentCheck.isChecked()) {
                Toast.makeText(LoginActivity.this, "Consent required to proceed", Toast.LENGTH_SHORT).show();
                return;
            }

            // For Sprint 1, proceed to pairing
            Intent intent = new Intent(LoginActivity.this, DevicePairingActivity.class);
            startActivity(intent);
            finish();
        });
    }
}
