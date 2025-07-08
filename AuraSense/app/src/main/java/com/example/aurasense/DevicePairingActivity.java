package com.example.aurasense;

import android.Manifest;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

public class DevicePairingActivity extends AppCompatActivity {

    private TextView statusText, heartRateText;
    private Button pairButton;
    private BLEManager bleManager;
    private boolean isConnected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_pairing);

        statusText = findViewById(R.id.statusText);
        heartRateText = findViewById(R.id.heartRateText);
        pairButton = findViewById(R.id.pairButton);

        bleManager = new BLEManager(this);
        requestBluetoothPermissions();

        pairButton.setOnClickListener(v -> {
            statusText.setText("Scanning for devices...");
            pairButton.setEnabled(false);

            bleManager.startScan(new BLEManager.BLECallback() {
                @Override
                public void onConnected() {
                    isConnected = true;
                    runOnUiThread(() -> statusText.setText("Paired with ESP32_EmotionBand"));
                }

                @Override
                public void onDataReceived(String jsonString) {
                    runOnUiThread(() -> {
                        try {
                            JSONObject obj = new JSONObject(jsonString);
                            int hr = obj.getInt("bpm");
                            heartRateText.setText("❤️ " + hr + " BPM");
                            heartRateText.setVisibility(View.VISIBLE);
                        } catch (Exception e) {
                            Toast.makeText(DevicePairingActivity.this, "Invalid sensor data", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });

            new Handler().postDelayed(() -> {
                if (isConnected) {
                    startActivity(new Intent(DevicePairingActivity.this, HomeActivity.class));
                    finish();
                } else {
                    statusText.setText("Connection failed. Try again.");
                    pairButton.setEnabled(true);
                }
            }, 10000);
        });
    }

    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, 101);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 101) {
            for (int res : results) {
                if (res != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Bluetooth permissions are required", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bleManager != null) {
            bleManager.stop();
        }
    }
}
