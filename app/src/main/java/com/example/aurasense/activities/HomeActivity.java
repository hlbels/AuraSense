package com.example.aurasense.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.aurasense.R;
import com.example.aurasense.ble.BLEManager;
import com.example.aurasense.utils.HistoryStorage;
import com.example.aurasense.utils.NotificationManager;
import com.example.aurasense.utils.TFLiteEmotionInterpreter;
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

    private LinearLayout statusSummaryCard;
    private ImageView statusIcon;
    private TextView statusMessage;

    // Debounce for notifications
    private int lastNotifiedLabel = -99;

    // ---- Stale-data watchdog & auto-reconnect ----
    private final Handler watchdog = new Handler(Looper.getMainLooper());
    private long lastDataMs = 0L;
    private static final long DATA_STALE_MS = 6000L;  // 6s without packets => disconnected
    private static final long WATCHDOG_PERIOD_MS = 2000L;

    private final Runnable staleCheck = new Runnable() {
        @Override public void run() {
            long now = System.currentTimeMillis();
            boolean stale = (now - lastDataMs) > DATA_STALE_MS;

            if (stale) {
                // UI: show disconnected and clear readings so we don't display stale values
                clearRealtimeReadings();
                updateStatusCard("disconnected", "Device Disconnected — reconnecting…");

                // Try to reconnect (see notes in triggerReconnect)
                triggerReconnect();
            }
            watchdog.postDelayed(this, WATCHDOG_PERIOD_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        SharedPreferences prefs = getSharedPreferences("AuraPrefs", MODE_PRIVATE);
        // kept for future toggles
        boolean discomfortAlertsEnabled = prefs.getBoolean("stress_alerts_enabled", true);
        boolean model2Enabled = prefs.getBoolean("model_2_enabled", false);

        hrValue = findViewById(R.id.hrValue);
        tempCard = findViewById(R.id.tempCard);
        motionCard = findViewById(R.id.motionCard);
        debugRawJsonText = findViewById(R.id.debugRawJsonText);
        connectDeviceBtn = findViewById(R.id.connectDeviceBtn);

        motionCardLayout = findViewById(R.id.motionCardLayout);
        detailedMotionData = findViewById(R.id.detailedMotionData);
        accXValue = findViewById(R.id.accXValue);
        accYValue = findViewById(R.id.accYValue);
        accZValue = findViewById(R.id.accZValue);

        statusSummaryCard = findViewById(R.id.statusSummaryCard);
        statusIcon = findViewById(R.id.statusIcon);
        statusMessage = findViewById(R.id.statusMessage);

        motionCardLayout.setOnClickListener(v -> toggleMotionDetails());
        updateStatusCard("normal", "All Good");

        try {
            interpreter = new TFLiteEmotionInterpreter(this);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load ONNX interpreter", e);
        }

        notificationManager = new NotificationManager(this);

        boolean isConnected = getIntent().getBooleanExtra("isConnected", false);
        bleManager = BLEManager.getInstance(this, this);
        bleManager.setCallback(this);

        // If your BLEManager exposes an auto-reconnect toggle, use it:
        // bleManager.setAutoReconnectEnabled(true);

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
        bottomNavigation.setSelectedItemId(R.id.nav_home);
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
                try {
                    startActivity(new Intent(this, SettingsActivity.class));
                } catch (Exception e) {
                    Log.e(TAG, "Settings error: " + e.getMessage());
                    Toast.makeText(this, "Error opening settings", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            return false;
        });
    }

    // ---- BLE callbacks ----

    @Override
    public void onConnected() {
        runOnUiThread(() -> {
            Toast.makeText(this, "Device connected!", Toast.LENGTH_SHORT).show();
            updateStatusCard("normal", "Connected");
            connectDeviceBtn.setVisibility(Button.GONE);
            lastDataMs = System.currentTimeMillis(); // reset staleness
        });
    }

    @Override
    public void onDisconnected() {
        runOnUiThread(() -> {
            Toast.makeText(this, "Device disconnected!", Toast.LENGTH_SHORT).show();
            clearRealtimeReadings();
            updateStatusCard("disconnected", "Device Disconnected — reconnecting…");
            connectDeviceBtn.setVisibility(Button.VISIBLE);
        });
        // kick off reconnect attempts
        triggerReconnect();
    }

    @Override
    public void onDataReceived(String data) {
        lastDataMs = System.currentTimeMillis(); // fresh packet, not stale
        Log.d(TAG, "Final JSON received in HomeActivity: " + data);
        runOnUiThread(() -> debugRawJsonText.setText(data));

        try {
            JSONObject json = new JSONObject(data);

            float bpm  = (float) json.optDouble("bpm", Float.NaN);
            float hrv  = (float) json.optDouble("hrv", Float.NaN);
            float temp = (float) json.optDouble("temp", Float.NaN);
            float accX = (float) json.optDouble("acc_x", Float.NaN);
            float accY = (float) json.optDouble("acc_y", Float.NaN);
            float accZ = (float) json.optDouble("acc_z", Float.NaN);
            float bvp  = (float) json.optDouble("bvp", Float.NaN);
            int finger = json.optInt("finger", 0); // 1 = worn, 0 = not worn

            // Wear detection: if not worn, show prompt and don't compute
            if (finger != 1) {
                runOnUiThread(() -> {
                    updateStatusCard("not_worn", "Please wear the device to start measuring");
                    clearRealtimeReadings();
                });
                lastNotifiedLabel = -99;
                return;
            }

            float accMag = (float) Math.sqrt(accX * accX + accY * accY + accZ * accZ);

            // Store history only when worn
            HistoryStorage.add(new HistoryStorage.Entry(
                    System.currentTimeMillis(), bpm, temp, hrv, accX, accY, accZ, accMag, bvp));

            runOnUiThread(() -> {
                hrValue.setText(Float.isNaN(bpm) ? "--" : String.format("%.0f", bpm));
                tempCard.setText(Float.isNaN(temp) ? "--" : String.format("%.1f°C", temp));
                motionCard.setText(Float.isNaN(accMag) ? "--" : String.format("%.2f m/s²", accMag));
                accXValue.setText(Float.isNaN(accX) ? "--" : String.format("%.2f", accX));
                accYValue.setText(Float.isNaN(accY) ? "--" : String.format("%.2f", accY));
                accZValue.setText(Float.isNaN(accZ) ? "--" : String.format("%.2f", accZ));
            });

            int prediction = (interpreter != null)
                    ? interpreter.predictFromRawSensors(accX, accY, accZ, temp, bvp)
                    : -1;

            Log.d(TAG, "Prediction from model: " + prediction);

            String timestamp = android.text.format.DateFormat
                    .format("yyyy-MM-dd HH:mm:ss", System.currentTimeMillis()).toString();

            runOnUiThread(() -> {
                // Mapping: 0=baseline, 1=amusement, 2=stress
                switch (prediction) {
                    case 2:
                        updateStatusCard("high_discomfort", "High Stress Detected");
                        if (lastNotifiedLabel != 2) {
                            appendNotificationHistoryLine("[Stress] " + timestamp + " • HR=" +
                                    (Float.isNaN(bpm) ? "--" : String.format("%.0f", bpm)) + " bpm");
                            notificationManager.sendEmotionAlert(2, Float.isNaN(bpm) ? 0f : bpm, timestamp);
                            lastNotifiedLabel = 2;
                        }
                        break;

                    case 1:
                        updateStatusCard("amusement", "You Seem Amused 😊");
                        if (lastNotifiedLabel != 1) {
                            appendNotificationHistoryLine("[Amusement] " + timestamp + " • HR=" +
                                    (Float.isNaN(bpm) ? "--" : String.format("%.0f", bpm)) + " bpm");
                            notificationManager.sendEmotionAlert(1, Float.isNaN(bpm) ? 0f : bpm, timestamp);
                            lastNotifiedLabel = 1;
                        }
                        break;

                    case 0:
                        updateStatusCard("normal", "All Good");
                        lastNotifiedLabel = 0;
                        break;

                    default:
                        updateStatusCard("error", "Analyzing…");
                        break;
                }
            });

        } catch (JSONException e) {
            Log.e(TAG, "JSON parsing error", e);
            runOnUiThread(() -> debugRawJsonText.setText("Error parsing JSON:\n" + e.getMessage()));
        }
    }

    // ---- Lifecycle ----
    @Override
    protected void onResume() {
        super.onResume();
        if (bleManager != null) bleManager.setCallback(this);
        watchdog.removeCallbacks(staleCheck);
        watchdog.postDelayed(staleCheck, WATCHDOG_PERIOD_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        watchdog.removeCallbacks(staleCheck);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        watchdog.removeCallbacks(staleCheck);
    }

    // ---- UI helpers ----

    private void updateStatusCard(String status, String message) {
        if (statusSummaryCard == null || statusIcon == null || statusMessage == null) return;

        switch (status.toLowerCase()) {
            case "normal":
                statusSummaryCard.setBackground(ContextCompat.getDrawable(this, R.drawable.status_card_normal));
                statusIcon.setImageResource(R.drawable.ic_check_circle);
                statusMessage.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                statusMessage.setText(message);
                break;

            case "high_discomfort":
                statusSummaryCard.setBackground(ContextCompat.getDrawable(this, R.drawable.status_card_warning));
                statusIcon.setImageResource(R.drawable.ic_warning);
                statusMessage.setTextColor(ContextCompat.getColor(this, R.color.text_on_warning));
                statusMessage.setText(message);
                break;

            case "amusement":
                // Strong yellow->orange gradient + dark text for contrast
                statusSummaryCard.setBackground(ContextCompat.getDrawable(this, R.drawable.status_card_amusement_strong));
                statusIcon.setImageResource(R.drawable.ic_smile);
                statusMessage.setTextColor(ContextCompat.getColor(this, R.color.amusement_text_dark));
                statusMessage.setText(message);
                break;

            case "not_worn":
                statusSummaryCard.setBackground(ContextCompat.getDrawable(this, R.drawable.status_card_disconnected));
                statusIcon.setImageResource(R.drawable.ic_home);
                statusMessage.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                statusMessage.setText(message);
                break;

            case "disconnected":
                statusSummaryCard.setBackground(ContextCompat.getDrawable(this, R.drawable.status_card_disconnected));
                statusIcon.setImageResource(R.drawable.ic_home);
                statusMessage.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                statusMessage.setText(message);
                break;

            case "error":
                statusSummaryCard.setBackground(ContextCompat.getDrawable(this, R.drawable.status_card_warning));
                statusIcon.setImageResource(R.drawable.ic_warning);
                statusMessage.setTextColor(ContextCompat.getColor(this, R.color.text_on_warning));
                statusMessage.setText(message);
                break;

            default:
                statusSummaryCard.setBackground(ContextCompat.getDrawable(this, R.drawable.status_card_normal));
                statusIcon.setImageResource(R.drawable.ic_check_circle);
                statusMessage.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                statusMessage.setText("Unknown Status");
                break;
        }
    }

    private void toggleMotionDetails() {
        if (detailedMotionData.getVisibility() == LinearLayout.VISIBLE) {
            detailedMotionData.setVisibility(LinearLayout.GONE);
        } else {
            detailedMotionData.setVisibility(LinearLayout.VISIBLE);
        }
    }

    private void clearRealtimeReadings() {
        runOnUiThread(() -> {
            hrValue.setText("--");
            tempCard.setText("--");
            motionCard.setText("--");
            accXValue.setText("--");
            accYValue.setText("--");
            accZValue.setText("--");
        });
    }

    private void appendNotificationHistoryLine(String line) {
        SharedPreferences notifPrefs = getSharedPreferences("AuraNotifications", MODE_PRIVATE);
        String oldLog = notifPrefs.getString("notifications", "");
        String newLog = oldLog.isEmpty() ? line : oldLog + "\n" + line;
        notifPrefs.edit().putString("notifications", newLog).apply();
    }

    // Try to keep BLE always connected.
    private void triggerReconnect() {
        if (bleManager == null) return;

        // If BLEManager has these, prefer them:
        // if (!bleManager.isConnected()) bleManager.reconnect();

        // Otherwise fall back to scanning & connect:
        try {
            bleManager.requestReconnect(); // <— implemented this in BLEManager to wrap reconnect logic
        } catch (Throwable t) {
            Log.w(TAG, "Reconnect request failed (implement BLEManager.requestReconnect())", t);
        }
    }
}
