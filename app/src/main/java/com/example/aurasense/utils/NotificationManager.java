package com.example.aurasense.utils;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.aurasense.R;
import com.example.aurasense.activities.HomeActivity;

public class NotificationManager {
    private static final String CHANNEL_ID = "stress_alerts";
    private static final String CHANNEL_NAME = "Stress Alerts";
    private static final String CHANNEL_DESCRIPTION = "Notifications for high stress detection";
    private static final int NOTIFICATION_ID = 1001;

    private Context context;
    private android.app.NotificationManager notificationManager;
    private Vibrator vibrator;

    public NotificationManager(Context context) {
        this.context = context;
        this.notificationManager = (android.app.NotificationManager) 
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    android.app.NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(CHANNEL_DESCRIPTION);
            channel.enableLights(true);
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 250, 250, 250});
            
            notificationManager.createNotificationChannel(channel);
        }
    }

    public void sendStressAlert(float heartRate, String timestamp) {
        // Create intent to open the app when notification is tapped
        Intent intent = new Intent(context, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Build the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_warning)
                .setContentTitle("⚠️ High Stress Detected")
                .setContentText(String.format("Heart rate: %.0f bpm • Take a moment to breathe", heartRate))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(String.format("High stress level detected at %s\n" +
                                "Heart rate: %.0f bpm\n\n" +
                                "Consider taking a break, practicing deep breathing, or doing a relaxation exercise.",
                                timestamp, heartRate)))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(new long[]{0, 250, 250, 250})
                .setLights(0xFFFF5722, 1000, 1000); // Orange light

        // Send the notification
        try {
            NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(context);
            notificationManagerCompat.notify(NOTIFICATION_ID, builder.build());
            
            // Vibrate the device
            vibrateDevice();
            
        } catch (SecurityException e) {
            // Handle case where notification permission is not granted
            android.util.Log.w("NotificationManager", "Notification permission not granted", e);
        }
    }

    private void vibrateDevice() {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // For Android 8.0 and above
                VibrationEffect effect = VibrationEffect.createWaveform(
                        new long[]{0, 250, 250, 250}, -1
                );
                vibrator.vibrate(effect);
            } else {
                // For older versions
                vibrator.vibrate(new long[]{0, 250, 250, 250}, -1);
            }
        }
    }

    public void cancelStressAlert() {
        notificationManager.cancel(NOTIFICATION_ID);
    }
}