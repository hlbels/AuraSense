package com.example.aurasense.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.aurasense.R;
import com.example.aurasense.ble.BLEManager;
import com.example.aurasense.utils.HistoryStorage;
import com.example.aurasense.utils.TFLiteEmotionInterpreter;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.json.JSONException;
import org.json.JSONObject;

public class HomeActivity extends AppCompatActivity implements BLEManager.BLECallback {

    private static final String TAG = "HomeActivity";

    private TextView hrValue, tempCard, motionCard, stressStatus, debugRawJsonText;
    private Button connectDeviceBtn;
    private BLEManager bleManager;
    private TFLiteEmotionInterpreter interpreter;
    private boolean highStressTriggered = false;

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
        stressStatus = findViewById(R.id.stressStatus);
        debugRawJsonText = findViewById(R.id.debugRawJsonText);
        connectDeviceBtn = findViewById(R.id.connectDeviceBtn);

        // Load interpreter
        interpreter = new TFLiteEmotionInterpreter(this);

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
                startActivity(new Intent(this, SettingsActivity.class));
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
            stressStatus.setText("Disconnected");
            stressStatus.setTextColor(getResources().getColor(android.R.color.darker_gray));
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
                    System.currentTimeMillis(), bpm, temp, hrv, accX, accY, accZ, accMag
            ));

            Log.d(TAG, String.format("Parsed values — bpm: %.1f, hrv: %.1f, temp: %.1f, acc: [%.2f, %.2f, %.2f], mag: %.2f",
                    bpm, hrv, temp, accX, accY, accZ, accMag));

            runOnUiThread(() -> {
                hrValue.setText(String.format("%.0f bpm", bpm));
                tempCard.setText(String.format("%.1f °C", temp));
                motionCard.setText(String.format("x: %.2f y: %.2f z: %.2f", accX, accY, accZ));
            });

            int prediction = interpreter.predict(bpm, hrv, temp, accX, accY, accZ, accMag);
            Log.d(TAG, "Prediction from model: " + prediction);

            runOnUiThread(() -> {
                if (prediction == 1 && !highStressTriggered) {
                    highStressTriggered = true;
                    stressStatus.setText("High Stress Detected!");
                    stressStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                    SharedPreferences notifPrefs = getSharedPreferences("AuraNotifications", MODE_PRIVATE);
                    SharedPreferences.Editor editor = notifPrefs.edit();
                    String timestamp = android.text.format.DateFormat.format("yyyy-MM-dd HH:mm:ss", System.currentTimeMillis()).toString();
                    String oldLog = notifPrefs.getString("notifications", "");
                    String newLog = oldLog + "\n[" + timestamp + "] High stress detected.";
                    editor.putString("notifications", newLog.trim());
                    editor.apply();

                } else if (prediction == 0) {
                    highStressTriggered = false;
                    stressStatus.setText("Normal");
                    stressStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                } else if (prediction == -1) {
                    stressStatus.setText("Prediction Error");
                    stressStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
