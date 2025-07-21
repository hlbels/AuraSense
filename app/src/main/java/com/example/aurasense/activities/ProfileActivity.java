package com.example.aurasense.activities;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.aurasense.R;

public class ProfileActivity extends AppCompatActivity {

    private TextView nameText, emailText, userIdText;
    private ImageView profilePic;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // Show back arrow in the action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Profile");
        }

        profilePic = findViewById(R.id.profileImageView);
        nameText = findViewById(R.id.nameTextView);
        emailText = findViewById(R.id.emailTextView);
        userIdText = findViewById(R.id.userIdTextView);

        // Static content for now
        nameText.setText("Jane Doe");
        emailText.setText("janedoe@example.com");
        userIdText.setText("User ID: AURA-001");

        // You can optionally set a default drawable:
        profilePic.setImageResource(R.drawable.ic_profile);
    }
    // Handle back arrow press
    @Override
    public boolean onSupportNavigateUp() {
        finish(); // Close this activity and go back
        return true;
    }
}
