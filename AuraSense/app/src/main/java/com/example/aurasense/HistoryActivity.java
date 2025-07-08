package com.example.aurasense;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Set;

public class HistoryActivity extends AppCompatActivity {

    private static final String PREF_NAME = "StressHistory";
    private static final String HISTORY_KEY = "entries";

    private TextView historyText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        historyText = findViewById(R.id.historyText);

        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        Set<String> entries = prefs.getStringSet(HISTORY_KEY, null);

        if (entries == null || entries.isEmpty()) {
            historyText.setText("No stress data available yet.");
        } else {
            StringBuilder builder = new StringBuilder();
            for (String entry : entries) {
                builder.append(entry).append("\n\n");
            }
            historyText.setText(builder.toString());
        }
    }
}
