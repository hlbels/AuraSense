package com.example.aurasense.utils;

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
    private static final String CHANNEL_NAME = "Stress & Emotion Alerts";
    private static final String CHANNEL_DESCRIPTION = "Notifications for detected stress or amusement";
    private static final int NOTIFICATION_ID = 1001;

    private static final String PREFS = "AuraNotifications";
    private static final String PREFS_KEY = "notifications";

    private final Context context;
    private final android.app.NotificationManager notificationManager;
    private final Vibrator vibrator;

    public NotificationManager(Context context) {
        this.context = context.getApplicationContext();
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

    /**
     * Send alert based on model label:
     * 0 = baseline (no alert), 1 = amusement, 2 = stress
     */
    public void sendEmotionAlert(int label, float heartRate, String timestamp) {
        // 0 -> Baseline: no notification
        if (label == 0) {
            return;
        }

        String title;
        String message;
        String bigText;
        int icon = R.drawable.ic_warning;
        String tagForHistory;

        if (label == 2) { // Stress
            title = "⚠️ High Stress Detected";
            message = String.format("Heart rate: %.0f bpm • Take a moment to breathe", heartRate);
            bigText = String.format(
                    "High stress detected at %s\nHeart rate: %.0f bpm\n\n" +
                            "Consider a short break, deep breathing, or a quick walk.",
                    timestamp, heartRate
            );
            tagForHistory = "[Stress]";
        } else if (label == 1) { // Amusement
            title = "😊 Positive Emotion Detected";
            message = "You're feeling amused!";
            bigText = String.format(
                    "Amusement detected at %s\nHeart rate: %.0f bpm\n\n" +
                            "Great to see you're feeling good. Keep it up!",
                    timestamp, heartRate
            );
            icon = R.drawable.ic_smile;
            tagForHistory = "[Amusement]";
        } else {
            // Unknown label; do nothing
            return;
        }

        // Build tap intent
        Intent intent = new Intent(context, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Build notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(bigText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(new long[]{0, 250, 250, 250})
                .setLights(0xFFFF5722, 1000, 1000);

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build());
            vibrateDevice();
            // Persist a clean, parseable history line
            appendToHistory(String.format(
                    "%s %s • HR=%.0f bpm", tagForHistory, timestamp, heartRate
            ));
        } catch (SecurityException e) {
            android.util.Log.w("NotificationManager", "Notification permission not granted", e);
        }
    }

    private void vibrateDevice() {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VibrationEffect effect = VibrationEffect.createWaveform(
                        new long[]{0, 250, 250, 250}, -1
                );
                vibrator.vibrate(effect);
            } else {
                vibrator.vibrate(new long[]{0, 250, 250, 250}, -1);
            }
        }
    }

    public void cancelAlert() {
        notificationManager.cancel(NOTIFICATION_ID);
    }

    private void appendToHistory(String line) {
        android.content.SharedPreferences prefs =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String existing = prefs.getString(PREFS_KEY, "");
        String updated = existing.isEmpty() ? line : existing + "\n" + line;
        prefs.edit().putString(PREFS_KEY, updated).apply();
    }
}
