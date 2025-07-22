package com.example.aurasense.activities;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
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
    private int connectionAttempts = 0;
    private static final int MAX_CONNECTION_ATTEMPTS = 3;
    private static final int CONNECTION_TIMEOUT_MS = 10000; // 10 seconds
    private android.os.Handler timeoutHandler = new android.os.Handler();
    private Runnable timeoutRunnable;
    
    // UI Components
    private TextView statusText;
    private ProgressBar progressBar;
    private Button connectBtn, skipBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_pairing);

        // Initialize UI components
        statusText = findViewById(R.id.statusText);
        progressBar = findViewById(R.id.progressBar);
        connectBtn = findViewById(R.id.connectButton);
        skipBtn = findViewById(R.id.skipButton);

        // Singleton BLEManager automatically sets callback
        bleManager = BLEManager.getInstance(this, this);

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

        // Show searching status
        updateConnectionStatus("Searching for devices...", true);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            updateConnectionStatus("Bluetooth permissions not granted", false);
            Toast.makeText(this, "Bluetooth permissions not granted", Toast.LENGTH_SHORT).show();
            return;
        }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            updateConnectionStatus("Please enable Bluetooth", false);
            Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }

        for (BluetoothDevice device : adapter.getBondedDevices()) {
            Log.d(TAG, "Found paired device: " + device.getName() + " (" + device.getAddress() + ")");
            
            // Check for multiple possible device names
            String deviceName = device.getName();
            if (deviceName != null && (
                    deviceName.equals("ESP32_EmotionBand") ||
                    deviceName.contains("ESP32") ||
                    deviceName.contains("AuraSense") ||
                    deviceName.contains("EmotionBand"))) {
                
                Log.d(TAG, "Compatible device found: " + deviceName + ". Waiting 500ms before connecting...");

                isConnecting = true;
                updateConnectionStatus("Device found! Connecting to " + deviceName + "...", true);

                new android.os.Handler(getMainLooper()).postDelayed(() -> {
                    Log.d(TAG, "Connecting to device: " + deviceName);
                    updateConnectionStatus("Establishing connection...", true);
                    bleManager.connectToDevice(device);
                    
                    // Start connection timeout
                    startConnectionTimeout();
                }, 500); //Delay to stabilize BLE stack

                return;
            }
        }

        // Show all available devices to help with debugging
        StringBuilder availableDevices = new StringBuilder("Available paired devices:\n");
        for (BluetoothDevice device : adapter.getBondedDevices()) {
            availableDevices.append("- ").append(device.getName()).append("\n");
        }
        Log.d(TAG, availableDevices.toString());
        
        updateConnectionStatus("No compatible device found", false);
        Toast.makeText(this, "No compatible device found. Check paired devices in Bluetooth settings.", Toast.LENGTH_LONG).show();
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
            
            // Cancel connection timeout since we succeeded
            cancelConnectionTimeout();
            
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
            connectionAttempts++;
            Log.d(TAG, "Connection attempt " + connectionAttempts + " failed");
            
            if (connectionAttempts < MAX_CONNECTION_ATTEMPTS) {
                updateConnectionStatus("Connection failed. Retrying... (" + connectionAttempts + "/" + MAX_CONNECTION_ATTEMPTS + ")", true);
                
                // Retry after 2 seconds
                new android.os.Handler(getMainLooper()).postDelayed(() -> {
                    retryConnection();
                }, 2000);
            } else {
                isConnecting = false;
                connectionAttempts = 0;
                updateConnectionStatus("Connection failed after " + MAX_CONNECTION_ATTEMPTS + " attempts. Check ESP32 device.", false);
                Toast.makeText(this, "Connection failed. Make sure ESP32 is powered on and running BLE server.", Toast.LENGTH_LONG).show();
            }
        });
    }
    
    private void retryConnection() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            updateConnectionStatus("Bluetooth disabled", false);
            return;
        }

        for (BluetoothDevice device : adapter.getBondedDevices()) {
            String deviceName = device.getName();
            if (deviceName != null && (
                    deviceName.equals("ESP32_EmotionBand") ||
                    deviceName.contains("ESP32") ||
                    deviceName.contains("AuraSense") ||
                    deviceName.contains("EmotionBand"))) {
                
                Log.d(TAG, "Retrying connection to: " + deviceName);
                updateConnectionStatus("Retrying connection to " + deviceName + "...", true);
                bleManager.connectToDevice(device);
                
                // Start connection timeout for retry
                startConnectionTimeout();
                return;
            }
        }
        
        updateConnectionStatus("Device not found for retry", false);
    }

    /**
     * Starts a timeout timer for the connection attempt
     */
    private void startConnectionTimeout() {
        // Cancel any existing timeout
        if (timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
        }
        
        timeoutRunnable = () -> {
            Log.d(TAG, "Connection timeout reached");
            if (isConnecting) {
                // Force disconnect and trigger retry
                bleManager.disconnect();
                onDisconnected();
            }
        };
        
        timeoutHandler.postDelayed(timeoutRunnable, CONNECTION_TIMEOUT_MS);
    }
    
    /**
     * Cancels the connection timeout
     */
    private void cancelConnectionTimeout() {
        if (timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
    }

    /**
     * Updates the connection status UI with message and loading indicator
     */
    private void updateConnectionStatus(String message, boolean showProgress) {
        runOnUiThread(() -> {
            statusText.setText(message);
            if (showProgress) {
                progressBar.setVisibility(View.VISIBLE);
                connectBtn.setEnabled(false);
                connectBtn.setText("Connecting...");
            } else {
                progressBar.setVisibility(View.GONE);
                connectBtn.setEnabled(true);
                connectBtn.setText("Connect to Device");
            }
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
