package com.example.aurasense.activities;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.aurasense.R;
import com.example.aurasense.ble.BLEManager;

public class DevicePairingActivity extends AppCompatActivity implements BLEManager.BLECallback {

    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;
    private static final String TAG = "DevicePairingActivity";

    private BLEManager bleManager;
    private boolean isConnecting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_pairing);

        // Singleton BLEManager automatically sets callback
        bleManager = BLEManager.getInstance(this, this);

        Button connectBtn = findViewById(R.id.connectButton);
        Button skipBtn = findViewById(R.id.skipButton);

        connectBtn.setOnClickListener(v -> requestBluetoothPermissions());

        skipBtn.setOnClickListener(v -> {
            Log.d(TAG, "User chose to skip BLE connection.");
            Intent intent = new Intent(this, HomeActivity.class);
            intent.putExtra("isConnected", false);
            startActivity(intent);
            finish();
        });
    }

    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.ACCESS_FINE_LOCATION
                }, REQUEST_BLUETOOTH_PERMISSIONS);
            } else {
                scanAndConnect();
            }
        } else {
            scanAndConnect(); //For Android < 12
        }
    }

    private void scanAndConnect() {
        if (isConnecting) {
            Log.d(TAG, "Already connecting. Ignoring duplicate request.");
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Bluetooth permissions not granted", Toast.LENGTH_SHORT).show();
            return;
        }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }

        for (BluetoothDevice device : adapter.getBondedDevices()) {
            if ("ESP32_EmotionBand".equals(device.getName())) {
                Log.d(TAG, "ESP32_EmotionBand found. Waiting 500ms before connecting...");

                isConnecting = true;

                new android.os.Handler(getMainLooper()).postDelayed(() -> {
                    Log.d(TAG, "Connecting now...");
                    bleManager.connectToDevice(device);
                }, 500); //Delay to stabilize BLE stack

                return;
            }
        }

        Toast.makeText(this, "ESP32_EmotionBand not paired. Please pair in Bluetooth settings.", Toast.LENGTH_LONG).show();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                scanAndConnect();
            } else {
                Toast.makeText(this, "Bluetooth permissions denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onConnected() {
        runOnUiThread(() -> {
            Log.d(TAG, "BLE connected. Moving to HomeActivity...");
            Toast.makeText(this, "Device Connected", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(this, HomeActivity.class);
            intent.putExtra("isConnected", true);
            startActivity(intent);
            finish();  //Don't keep pairing activity alive
        });
    }

    @Override
    public void onDisconnected() {
        runOnUiThread(() -> {
            isConnecting = false;
            Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onDataReceived(String data) {
        Log.d(TAG, "Data received in PairingActivity (ignored): " + data);
        //Do nothing — HomeActivity handles data
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "DevicePairingActivity destroyed. BLEManager stays connected.");
    }
}
