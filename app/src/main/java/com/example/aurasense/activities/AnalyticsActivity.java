package com.example.aurasense.activities;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.aurasense.R;
import com.example.aurasense.utils.HistoryStorage;
import com.example.aurasense.utils.TFLiteEmotionInterpreter;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AnalyticsActivity extends AppCompatActivity {

    private LineChart chart;
    private Button dayBtn, weekBtn, monthBtn;
    private TextView analyticsTitle, stressCountText, avgHeartRateText, avgTempText;
    private TFLiteEmotionInterpreter interpreter;
    private String currentPeriod = "day";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analytics);

        interpreter = new TFLiteEmotionInterpreter(this);

        // Initialize views
        chart = findViewById(R.id.analyticsChart);
        dayBtn = findViewById(R.id.dayBtn);
        weekBtn = findViewById(R.id.weekBtn);
        monthBtn = findViewById(R.id.monthBtn);
        analyticsTitle = findViewById(R.id.analyticsTitle);
        stressCountText = findViewById(R.id.stressCountText);
        avgHeartRateText = findViewById(R.id.avgHeartRateText);
        avgTempText = findViewById(R.id.avgTempText);

        // Set up chart
        setupChart();

        // Set up period buttons
        dayBtn.setOnClickListener(v -> {
            currentPeriod = "day";
            updateButtonStates();
            loadAnalytics();
        });

        weekBtn.setOnClickListener(v -> {
            currentPeriod = "week";
            updateButtonStates();
            loadAnalytics();
        });

        monthBtn.setOnClickListener(v -> {
            currentPeriod = "month";
            updateButtonStates();
            loadAnalytics();
        });

        // Load initial data
        updateButtonStates();
        loadAnalytics();

        // Set up bottom navigation
        BottomNavigationView bottomNavigation = findViewById(R.id.bottomNavigation);
        bottomNavigation.setSelectedItemId(R.id.nav_history);

        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                startActivity(new Intent(this, HomeActivity.class));
                return true;
            } else if (id == R.id.nav_history) {
                startActivity(new Intent(this, HistoryActivity.class));
                return true;
            } else if (id == R.id.nav_notifications) {
                startActivity(new Intent(this, NotificationActivity.class));
                return true;
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
                return true;
            } else if (id == R.id.nav_settings) {
                try {
                    startActivity(new Intent(this, SettingsActivity.class));
                } catch (Exception e) {
                    Toast.makeText(this, "Error opening settings", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            return false;
        });
    }

    private void setupChart() {
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);

        Description desc = new Description();
        desc.setText("Stress Level Over Time");
        desc.setTextColor(Color.GRAY);
        chart.setDescription(desc);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Color.GRAY);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                return sdf.format(new Date((long) value));
            }
        });

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setTextColor(Color.GRAY);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setAxisMaximum(1f);
        leftAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return value == 1f ? "High Stress" : "Normal";
            }
        });

        YAxis rightAxis = chart.getAxisRight();
        rightAxis.setEnabled(false);
    }

    private void updateButtonStates() {
        // Reset all buttons
        dayBtn.setBackgroundColor(getResources().getColor(android.R.color.transparent));
        weekBtn.setBackgroundColor(getResources().getColor(android.R.color.transparent));
        monthBtn.setBackgroundColor(getResources().getColor(android.R.color.transparent));

        // Highlight selected button
        int selectedColor = getResources().getColor(R.color.primary_teal);
        switch (currentPeriod) {
            case "day":
                dayBtn.setBackgroundColor(selectedColor);
                analyticsTitle.setText("Today's Analytics");
                break;
            case "week":
                weekBtn.setBackgroundColor(selectedColor);
                analyticsTitle.setText("This Week's Analytics");
                break;
            case "month":
                monthBtn.setBackgroundColor(selectedColor);
                analyticsTitle.setText("This Month's Analytics");
                break;
        }
    }

    private void loadAnalytics() {
        List<HistoryStorage.Entry> entries = getEntriesForPeriod();

        if (entries.isEmpty()) {
            // Show empty state
            stressCountText.setText("No data available");
            avgHeartRateText.setText("--");
            avgTempText.setText("--");
            chart.clear();
            return;
        }

        // Calculate statistics
        int stressCount = 0;
        float totalHeartRate = 0;
        float totalTemp = 0;
        List<Entry> chartEntries = new ArrayList<>();

        for (HistoryStorage.Entry entry : entries) {
            float accMag = (float) Math
                    .sqrt(entry.accX * entry.accX + entry.accY * entry.accY + entry.accZ * entry.accZ);
            int prediction = interpreter.predictWithSmoothing(entry.bpm, entry.hrv, entry.temp, entry.accX, entry.accY,
                    entry.accZ, accMag);

            if (prediction == 1) {
                stressCount++;
            }

            totalHeartRate += entry.bpm;
            totalTemp += entry.temp;

            // Add to chart (timestamp as x, stress level as y)
            chartEntries.add(new Entry(entry.timestamp, prediction == 1 ? 1f : 0f));
        }

        // Update statistics
        stressCountText.setText(String.format("%d stress events", stressCount));
        avgHeartRateText.setText(String.format("%.0f bpm", totalHeartRate / entries.size()));
        avgTempText.setText(String.format("%.1f°C", totalTemp / entries.size()));

        // Update chart
        LineDataSet dataSet = new LineDataSet(chartEntries, "Stress Level");
        dataSet.setColor(getResources().getColor(R.color.primary_teal));
        dataSet.setCircleColor(getResources().getColor(R.color.primary_teal));
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawValues(false);

        LineData lineData = new LineData(dataSet);
        chart.setData(lineData);
        chart.invalidate();
    }

    private List<HistoryStorage.Entry> getEntriesForPeriod() {
        List<HistoryStorage.Entry> allEntries = HistoryStorage.getHistory();
        List<HistoryStorage.Entry> filteredEntries = new ArrayList<>();

        Calendar calendar = Calendar.getInstance();
        long currentTime = calendar.getTimeInMillis();

        long startTime;
        switch (currentPeriod) {
            case "day":
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                startTime = calendar.getTimeInMillis();
                break;
            case "week":
                calendar.set(Calendar.DAY_OF_WEEK, calendar.getFirstDayOfWeek());
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                startTime = calendar.getTimeInMillis();
                break;
            case "month":
                calendar.set(Calendar.DAY_OF_MONTH, 1);
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                startTime = calendar.getTimeInMillis();
                break;
            default:
                startTime = 0;
        }

        for (HistoryStorage.Entry entry : allEntries) {
            if (entry.timestamp >= startTime && entry.timestamp <= currentTime) {
                filteredEntries.add(entry);
            }
        }

        return filteredEntries;
    }
}