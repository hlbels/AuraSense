package com.example.aurasense;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class HomeActivity extends AppCompatActivity {

    private TextView hrValue, tempCard, motionCard;
    private final Handler handler = new Handler();
    private boolean highStressTriggered = false;
    private BLEManager bleManager;
    private TFLiteEmotionInterpreter interpreter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        hrValue = findViewById(R.id.hrValue);
        tempCard = findViewById(R.id.tempCard);
        motionCard = findViewById(R.id.motionCard);

        bleManager = new BLEManager(this);
        interpreter = new TFLiteEmotionInterpreter(this);

        bleManager.startScan(new BLEManager.BLECallback() {
            @Override
            public void onConnected() {
                // Optional connection handling
            }

            @Override
            public void onDataReceived(String jsonString) {
                try {
                    JSONObject json = new JSONObject(jsonString);
                    int bpm = json.getInt("bpm");
                    float temp = (float) json.getDouble("temp");
                    float accX = (float) json.getDouble("acc_x");
                    float accY = (float) json.getDouble("acc_y");
                    float accZ = (float) json.getDouble("acc_z");

                    runOnUiThread(() -> {
                        hrValue.setText(bpm + "\nBPM");
                        tempCard.setText(String.format("Temp: %.1f°C", temp));
                        motionCard.setText(String.format("Motion\nX: %.2f Y: %.2f Z: %.2f", accX, accY, accZ));
                    });

                    int stressLevel = interpreter.predictStress(bpm, temp, accX, accY, accZ);

                    // 🔴 History logging starts here
                    SharedPreferences prefs = getSharedPreferences("StressHistory", MODE_PRIVATE);
                    Set<String> set = new HashSet<>(prefs.getStringSet("entries", new HashSet<>()));
                    String timestamp = new SimpleDateFormat("HH:mm:ss dd/MM", Locale.getDefault()).format(new Date());
                    String record = "🕒 " + timestamp + "\n" +
                            "❤️ BPM: " + bpm + "\n" +
                            "🌡 Temp: " + temp + "\n" +
                            "📈 Stress Level: " + stressLevel;
                    set.add(record);
                    prefs.edit().putStringSet("entries", set).apply();
                    // 🔴 History logging ends here

                    if (stressLevel > 1 && !highStressTriggered) {
                        highStressTriggered = true;
                        Intent alertIntent = new Intent(HomeActivity.this, AlertActivity.class);
                        alertIntent.putExtra("stressLevel", stressLevel);
                        startActivity(alertIntent);
                    } else if (stressLevel <= 1) {
                        highStressTriggered = false;
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_history) {
                startActivity(new Intent(this, HistoryActivity.class));
            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
            } else if (id == R.id.nav_notifications) {
                startActivity(new Intent(this, NotificationsActivity.class));
            }
            return true;
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bleManager != null) bleManager.stop();
    }
}
