package com.example.aurasense.ble;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.util.UUID;

public class BLEManager {

    public interface BLECallback {
        void onConnected();
        void onDisconnected();
        void onDataReceived(String data);
    }

    private static final String TAG = "BLEManager";

    public static final UUID SERVICE_UUID = UUID.fromString("a0e6fc00-df5e-11ee-a506-0050569c1234");
    public static final UUID CHARACTERISTIC_UUID = UUID.fromString("a0e6fc01-df5e-11ee-a506-0050569c1234");
    public static final UUID CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static BLEManager instance;
    private final Context context;
    private BLECallback callback;
    private final BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;

    private final StringBuilder bleBuffer = new StringBuilder();

    public static BLEManager getInstance(Context context, BLECallback callback) {
        if (instance == null) {
            instance = new BLEManager(context.getApplicationContext(), callback);
        } else {
            instance.setCallback(callback);
        }
        return instance;
    }

    private BLEManager(Context context, BLECallback callback) {
        this.context = context;
        this.callback = callback;
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        this.bluetoothAdapter = bluetoothManager.getAdapter();
    }

    public void setCallback(BLECallback callback) {
        this.callback = callback;
    }

    public void connectToDevice(BluetoothDevice device) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission denied: BLUETOOTH_CONNECT");
            return;
        }

        // Always close previous GATT before connecting again
        if (bluetoothGatt != null) {
            Log.w(TAG, "Closing stale GATT connection before reconnecting...");
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
        }

        Log.d(TAG, "Connecting to GATT server...");
        bluetoothGatt = device.connectGatt(context, false, gattCallback);
    }

    public void disconnect() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission denied: BLUETOOTH_CONNECT");
            return;
        }

        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
        }

        instance = null;
        bleBuffer.setLength(0);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return;

            Log.d(TAG, "onConnectionStateChange: status=" + status + ", newState=" + newState);

            if (newState == android.bluetooth.BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected to GATT. Requesting MTU...");
                gatt.requestMtu(512);
            } else if (newState == android.bluetooth.BluetoothProfile.STATE_DISCONNECTED) {
                Log.w(TAG, "Disconnected from GATT. Status: " + status);
                if (callback != null) callback.onDisconnected();
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "MTU changed to: " + mtu);
            } else {
                Log.w(TAG, "MTU change failed. Using default MTU.");
            }

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Discovering services...");
                gatt.discoverServices();
            } else {
                Log.e(TAG, "Missing BLUETOOTH_CONNECT permission.");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return;

            Log.d(TAG, "Services discovered: " + (status == BluetoothGatt.GATT_SUCCESS));
            BluetoothGattService service = gatt.getService(SERVICE_UUID);
            if (service != null) {
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                if (characteristic != null) {
                    gatt.setCharacteristicNotification(characteristic, true);
                    BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID);
                    if (descriptor != null) {
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(descriptor);
                        Log.d(TAG, "Notifications enabled.");
                        if (callback != null) callback.onConnected();
                    } else {
                        Log.e(TAG, "Descriptor not found.");
                    }
                } else {
                    Log.e(TAG, "Characteristic not found.");
                }
            } else {
                Log.e(TAG, "Service not found.");
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return;

            if (CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                String fragment = new String(characteristic.getValue());
                Log.d(TAG, "BLE Fragment Received: " + fragment);

                bleBuffer.append(fragment);
                String bufferStr = bleBuffer.toString();

                int start = bufferStr.indexOf('{');
                int end = bufferStr.indexOf('}');

                while (start != -1 && end > start) {
                    String fullJson = bufferStr.substring(start, end + 1);
                    Log.d(TAG, "Full JSON Detected: " + fullJson);

                    if (callback != null) {
                        callback.onDataReceived(fullJson.trim());
                    }

                    bufferStr = bufferStr.substring(end + 1);
                    start = bufferStr.indexOf('{');
                    end = bufferStr.indexOf('}');
                }

                bleBuffer.setLength(0);
                bleBuffer.append(bufferStr);
            }
        }
    };
}
