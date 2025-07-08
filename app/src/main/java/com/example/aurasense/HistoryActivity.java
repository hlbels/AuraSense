package com.example.aurasense;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Set;

public class HistoryActivity extends AppCompatActivity {

    private static final String PREF_NAME  = "StressHistory";
    private static final String HISTORY_KEY = "entries";

    private TextView historyText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        // Back arrow
        ImageButton backBtn = findViewById(R.id.backButton);
        backBtn.setOnClickListener(v -> finish());

        // Export button
        Button exportBtn = findViewById(R.id.exportButton);

        historyText = findViewById(R.id.historyText);

        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        Set<String> entries = prefs.getStringSet(HISTORY_KEY, null);

        String display;
        if (entries == null || entries.isEmpty()) {
            display = "No stress data available yet.";
        } else {
            StringBuilder builder = new StringBuilder();
            for (String entry : entries) {
                builder.append(entry).append("\n\n");
            }
            display = builder.toString();
        }
        historyText.setText(display);

        exportBtn.setOnClickListener(v -> {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_SUBJECT, "AuraSense Stress History");
            share.putExtra(Intent.EXTRA_TEXT, display);
            startActivity(Intent.createChooser(share, "Export via"));
        });
    }
}