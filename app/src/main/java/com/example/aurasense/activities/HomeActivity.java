package com.example.aurasense.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.aurasense.R;
import com.example.aurasense.ble.BLEManager;
import com.example.aurasense.utils.HistoryStorage;
import com.example.aurasense.utils.TFLiteEmotionInterpreter;
import com.example.aurasense.utils.NotificationManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.json.JSONException;
import org.json.JSONObject;

public class HomeActivity extends AppCompatActivity implements BLEManager.BLECallback {

    private static final String TAG = "HomeActivity";

    private TextView hrValue, tempCard, motionCard, debugRawJsonText;
    private TextView accXValue, accYValue, accZValue;
    private LinearLayout motionCardLayout, detailedMotionData;
    private Button connectDeviceBtn;
    private BLEManager bleManager;
    private TFLiteEmotionInterpreter interpreter;
    private NotificationManager notificationManager;
    private boolean highStressTriggered = false;
    private boolean motionDetailsExpanded = false;

    // Status Summary Card components
    private LinearLayout statusSummaryCard;
    private ImageView statusIcon;
    private TextView statusMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Load saved settings
        SharedPreferences prefs = getSharedPreferences("AuraPrefs", MODE_PRIVATE);
        boolean stressAlertsEnabled = prefs.getBoolean("stress_alerts_enabled", true);
        boolean model2Enabled = prefs.getBoolean("model_2_enabled", false);

        // Initialize views
        hrValue = findViewById(R.id.hrValue);
        tempCard = findViewById(R.id.tempCard);
        motionCard = findViewById(R.id.motionCard);
        debugRawJsonText = findViewById(R.id.debugRawJsonText);
        connectDeviceBtn = findViewById(R.id.connectDeviceBtn);

        // Initialize motion detail views
        motionCardLayout = findViewById(R.id.motionCardLayout);
        detailedMotionData = findViewById(R.id.detailedMotionData);
        accXValue = findViewById(R.id.accXValue);
        accYValue = findViewById(R.id.accYValue);
        accZValue = findViewById(R.id.accZValue);

        // Initialize Status Summary Card components
        statusSummaryCard = findViewById(R.id.statusSummaryCard);
        statusIcon = findViewById(R.id.statusIcon);
        statusMessage = findViewById(R.id.statusMessage);

        // Set up motion card click listener
        motionCardLayout.setOnClickListener(v -> toggleMotionDetails());

        // Set initial status
        updateStatusCard("normal", "All Good");

        // Load interpreter and notification manager
        interpreter = new TFLiteEmotionInterpreter(this);
        notificationManager = new NotificationManager(this);

        // BLE Manager setup
        boolean isConnected = getIntent().getBooleanExtra("isConnected", false);
        bleManager = BLEManager.getInstance(this, this);
        bleManager.setCallback(this);

        // Connection logic
        if (isConnected) {
            connectDeviceBtn.setVisibility(Button.GONE);
        } else {
            connectDeviceBtn.setVisibility(Button.VISIBLE);
            connectDeviceBtn.setOnClickListener(v -> {
                Intent intent = new Intent(HomeActivity.this, DevicePairingActivity.class);
                startActivity(intent);
                finish();
            });
        }
        BottomNavigationView bottomNavigation = findViewById(R.id.bottomNavigation);
        bottomNavigation.setSelectedItemId(R.id.nav_home); // default

        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
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
                Log.d(TAG, "Settings navigation clicked");
                try {
                    Intent intent = new Intent(this, SettingsActivity.class);
                    startActivity(intent);
                    Log.d(TAG, "Settings activity started successfully");
                } catch (Exception e) {
                    Log.e(TAG, "Error starting SettingsActivity: " + e.getMessage());
                    Toast.makeText(this, "Error opening settings: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            return false;
        });

    }

    @Override
    public void onConnected() {
        runOnUiThread(() -> Toast.makeText(this, "Device connected!", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onDisconnected() {
        runOnUiThread(() -> {
            Toast.makeText(this, "Device disconnected!", Toast.LENGTH_SHORT).show();
            updateStatusCard("disconnected", "Device Disconnected");
        });
    }

    @Override
    public void onDataReceived(String data) {
        Log.d(TAG, "Final JSON received in HomeActivity: " + data);
        runOnUiThread(() -> debugRawJsonText.setText(data));

        try {
            JSONObject json = new JSONObject(data);

            float bpm = (float) json.getDouble("bpm");
            float hrv = (float) json.getDouble("hrv");
            float temp = (float) json.getDouble("temp");
            float accX = (float) json.getDouble("acc_x");
            float accY = (float) json.getDouble("acc_y");
            float accZ = (float) json.getDouble("acc_z");

            float accMag = (float) Math.sqrt(accX * accX + accY * accY + accZ * accZ);

            HistoryStorage.add(new HistoryStorage.Entry(
                    System.currentTimeMillis(), bpm, temp, hrv, accX, accY, accZ, accMag));

            Log.d(TAG,
                    String.format(
                            "Parsed values — bpm: %.1f, hrv: %.1f, temp: %.1f, acc: [%.2f, %.2f, %.2f], mag: %.2f",
                            bpm, hrv, temp, accX, accY, accZ, accMag));

            runOnUiThread(() -> {
                hrValue.setText(String.format("%.0f", bpm));
                tempCard.setText(String.format("%.1f°C", temp));

                // Display real sensor data instead of activity levels
                motionCard.setText(String.format("%.2f m/s²", accMag));

                // Update detailed motion data
                accXValue.setText(String.format("%.2f", accX));
                accYValue.setText(String.format("%.2f", accY));
                accZValue.setText(String.format("%.2f", accZ));
            });

            int prediction = interpreter.predictWithSmoothing(bpm, hrv, temp, accX, accY, accZ, accMag);
            Log.d(TAG, "Prediction from model: " + prediction);

            runOnUiThread(() -> {
                if (prediction == 1 && !highStressTriggered) {
                    highStressTriggered = true;
                    updateStatusCard("high_stress", "High Stress Detected!");

                    // Save notification to history
                    SharedPreferences notifPrefs = getSharedPreferences("AuraNotifications", MODE_PRIVATE);
                    SharedPreferences.Editor editor = notifPrefs.edit();
                    String timestamp = android.text.format.DateFormat
                            .format("yyyy-MM-dd HH:mm:ss", System.currentTimeMillis()).toString();
                    String oldLog = notifPrefs.getString("notifications", "");
                    String newLog = oldLog + "\n[" + timestamp + "] High stress detected.";
                    editor.putString("notifications", newLog.trim());
                    editor.apply();

                    // Send real-time system notification
                    notificationManager.sendStressAlert(bpm, timestamp);

                } else if (prediction == 0) {
                    highStressTriggered = false;
                    updateStatusCard("normal", "All Good");
                } else if (prediction == -1) {
                    updateStatusCard("error", "Unable to Analyze");
                }
            });

        } catch (JSONException e) {
            Log.e(TAG, "JSON parsing error", e);
            runOnUiThread(() -> debugRawJsonText.setText("Error parsing JSON:\n" + e.getMessage()));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bleManager != null) {
            bleManager.setCallback(this);
        }
    }

    /**
     * Updates the status summary card with appropriate styling and content
     */
    private void updateStatusCard(String status, String message) {
        if (statusSummaryCard == null || statusIcon == null || statusMessage == null) {
            return;
        }

        switch (status.toLowerCase()) {
            case "normal":
                statusSummaryCard.setBackground(ContextCompat.getDrawable(this, R.drawable.status_card_normal));
                statusIcon.setImageResource(R.drawable.ic_check_circle);
                statusMessage.setText(message);
                break;
            case "high_stress":
                statusSummaryCard.setBackground(ContextCompat.getDrawable(this, R.drawable.status_card_warning));
                statusIcon.setImageResource(R.drawable.ic_warning);
                statusMessage.setText(message);
                break;
            case "disconnected":
                statusSummaryCard.setBackground(ContextCompat.getDrawable(this, R.drawable.status_card_disconnected));
                statusIcon.setImageResource(R.drawable.ic_home);
                statusMessage.setText(message);
                break;
            case "error":
                statusSummaryCard.setBackground(ContextCompat.getDrawable(this, R.drawable.status_card_warning));
                statusIcon.setImageResource(R.drawable.ic_warning);
                statusMessage.setText(message);
                break;
            default:
                statusSummaryCard.setBackground(ContextCompat.getDrawable(this, R.drawable.status_card_normal));
                statusIcon.setImageResource(R.drawable.ic_check_circle);
                statusMessage.setText("Unknown Status");
                break;
        }
    }

    /**
     * Toggles the visibility of detailed motion sensor data
     */
    private void toggleMotionDetails() {
        if (motionDetailsExpanded) {
            detailedMotionData.setVisibility(LinearLayout.GONE);
            motionDetailsExpanded = false;
        } else {
            detailedMotionData.setVisibility(LinearLayout.VISIBLE);
            motionDetailsExpanded = true;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
