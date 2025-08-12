package com.example.aurasense.activities;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.aurasense.R;
import com.example.aurasense.utils.HistoryStorage;
import com.github.mikephil.charting.charts.CombinedChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.CombinedData;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AnalyticsActivity extends AppCompatActivity {

    private CombinedChart chart;
    private Button btnLine, btnBar;
    private Spinner featureSpinner;
    private TextView analyticsTitle, summaryText;

    private enum Mode { LINE, BAR }
    private Mode currentMode = Mode.LINE;

    private enum Feature { BPM, TEMPERATURE, MOVEMENT }
    private Feature currentFeature = Feature.BPM;

    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analytics);

        // IMPORTANT: Do NOT touch BLEManager here (keeps HomeActivity’s callback intact)

        chart = findViewById(R.id.analyticsCombinedChart);
        btnLine = findViewById(R.id.btnLine);
        btnBar = findViewById(R.id.btnBar);
        featureSpinner = findViewById(R.id.featureSpinner);
        analyticsTitle = findViewById(R.id.analyticsTitle);
        summaryText = findViewById(R.id.summaryText);

        setupChart();
        setupFeatureSpinner();
        setupButtons();

        render();

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

    private void setupButtons() {
        btnLine.setOnClickListener(v -> {
            currentMode = Mode.LINE;
            styleButtons();
            render();
        });
        btnBar.setOnClickListener(v -> {
            currentMode = Mode.BAR;
            styleButtons();
            render();
        });
        styleButtons(); // init styles
    }

    private void styleButtons() {
        // Visual selection without disabling clicks
        int sel = getResources().getColor(R.color.primary_teal);
        int txt = getResources().getColor(R.color.text_primary);

        btnLine.setBackgroundResource(currentMode == Mode.LINE ? R.drawable.card_background_selected : R.drawable.card_background);
        btnBar.setBackgroundResource(currentMode == Mode.BAR ? R.drawable.card_background_selected : R.drawable.card_background);

        btnLine.setTextColor(txt);
        btnBar.setTextColor(txt);
    }

    private void setupFeatureSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"BPM", "Temperature", "Movement"}
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        featureSpinner.setAdapter(adapter);
        featureSpinner.setSelection(0);
        featureSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0: currentFeature = Feature.BPM; break;
                    case 1: currentFeature = Feature.TEMPERATURE; break;
                    case 2: currentFeature = Feature.MOVEMENT; break;
                }
                render();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
    }

    private void setupChart() {
        chart.setNoDataText("No data yet");
        chart.setDrawOrder(new CombinedChart.DrawOrder[]{ CombinedChart.DrawOrder.BAR, CombinedChart.DrawOrder.LINE });
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);

        Description desc = new Description();
        desc.setText("");
        chart.setDescription(desc);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Color.GRAY);
        xAxis.setGranularity(1f);
        xAxis.setValueFormatter(new IndexAxisValueFormatter()); // labels applied in render()

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setTextColor(Color.GRAY);
        chart.getAxisRight().setEnabled(false);
    }

    private void render() {
        List<HistoryStorage.Entry> raw = HistoryStorage.getHistory();
        if (raw == null || raw.isEmpty()) {
            analyticsTitle.setText("Analytics");
            summaryText.setText("No data available yet.");
            chart.clear();
            chart.invalidate();
            return;
        }

        // Build X labels (HH:mm) and index-based entries 0..N-1
        List<String> xLabels = new ArrayList<>();
        List<Entry> linePoints = new ArrayList<>();
        List<BarEntry> barPoints = new ArrayList<>();

        float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY, sum = 0f;
        int n = 0;

        for (int i = 0; i < raw.size(); i++) {
            HistoryStorage.Entry e = raw.get(i);
            float y;
            switch (currentFeature) {
                case TEMPERATURE: y = e.temp; break;
                case MOVEMENT:
                    y = Float.isNaN(e.accMag) ? (float)Math.sqrt(e.accX*e.accX + e.accY*e.accY + e.accZ*e.accZ) : e.accMag;
                    break;
                case BPM:
                default: y = e.bpm;
            }
            if (Float.isNaN(y) || Float.isInfinite(y)) continue;

            linePoints.add(new Entry(i, y));
            barPoints.add(new BarEntry(i, y));
            xLabels.add(timeFmt.format(new Date(e.timestamp)));

            min = Math.min(min, y);
            max = Math.max(max, y);
            sum += y;
            n++;
        }

        if (n == 0) {
            analyticsTitle.setText("Analytics");
            summaryText.setText("No valid points for this feature.");
            chart.clear();
            chart.invalidate();
            return;
        }

        float avg = sum / n;
        analyticsTitle.setText(featureTitle(currentFeature) + " over time");
        summaryText.setText(String.format(Locale.getDefault(),
                "Samples: %d   Min: %.1f   Max: %.1f   Avg: %.1f", n, min, max, avg));

        // Apply X labels
        chart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(xLabels));

        // Y axis padding
        YAxis left = chart.getAxisLeft();
        float pad = padding(currentFeature);
        left.setAxisMinimum(Math.max(0f, min - pad));
        left.setAxisMaximum(max + pad);

        CombinedData data = new CombinedData();
        int color = colorForFeature(currentFeature);

        if (currentMode == Mode.LINE) {
            LineDataSet ds = new LineDataSet(linePoints, featureTitle(currentFeature));
            ds.setColor(color);
            ds.setCircleColor(color);
            ds.setLineWidth(2f);
            ds.setCircleRadius(2.5f);
            ds.setDrawValues(false);
            data.setData(new LineData(ds));
        } else {
            BarDataSet bs = new BarDataSet(barPoints, featureTitle(currentFeature));
            bs.setColor(color);
            bs.setDrawValues(false);
            BarData barData = new BarData(bs);
            barData.setBarWidth(0.6f); // relative to index spacing
            data.setData(barData);
        }

        chart.setData(data);
        chart.invalidate();
    }

    private String featureTitle(Feature f) {
        switch (f) {
            case TEMPERATURE: return "Temperature (°C)";
            case MOVEMENT:    return "Movement (m/s²)";
            case BPM:
            default:          return "Heart Rate (bpm)";
        }
    }

    private int colorForFeature(Feature f) {
        switch (f) {
            case TEMPERATURE: return Color.parseColor("#FF5722"); // deep orange
            case MOVEMENT:    return Color.parseColor("#3F51B5"); // indigo
            case BPM:
            default:          return Color.parseColor("#009688"); // teal
        }
    }

    private float padding(Feature f) {
        switch (f) {
            case TEMPERATURE: return 0.5f;
            case MOVEMENT:    return 0.5f;
            case BPM:
            default:          return 5f;
        }
    }
}
