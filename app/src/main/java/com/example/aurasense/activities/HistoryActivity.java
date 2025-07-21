package com.example.aurasense.activities;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.aurasense.R;
import com.example.aurasense.utils.HistoryStorage;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {

    private ListView historyListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        // Show back arrow in the action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("History");
        }

        historyListView = findViewById(R.id.historyListView);

        ArrayList<String> entries = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault());

        for (HistoryStorage.Entry entry : HistoryStorage.getHistory()) {
            String formatted = String.format(
                    "%s\nHR: %.0f bpm | Temp: %.1f°C | HRV: %.1f\nAcc[x: %.2f, y: %.2f, z: %.2f, mag: %.2f]",
                    sdf.format(new Date(entry.timestamp)),
                    entry.bpm, entry.temp, entry.hrv,
                    entry.accX, entry.accY, entry.accZ, entry.accMag
            );
            entries.add(formatted);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, entries);
        historyListView.setAdapter(adapter);
    }
    // Handle back arrow press
    @Override
    public boolean onSupportNavigateUp() {
        finish(); // Close this activity and go back
        return true;
    }
}
