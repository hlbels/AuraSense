package com.example.aurasense.utils;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.LinkedList;
import java.util.Queue;

public class TFLiteEmotionInterpreter {
    private static final String TAG = "TFLiteEmotionInterpreter";
    private static final String MODEL_NAME = "stress_model.tflite";

    private Interpreter interpreter;

    // Smoothing and hysteresis
    private static final int SMOOTH_WINDOW = 5;
    private final Queue<Float> predictionHistory = new LinkedList<>();
    private float lastSmoothedValue = 0;
    private int lastStableLabel = 0; // 0 = normal, 1 = stress

    public TFLiteEmotionInterpreter(Context context) {
        try {
            interpreter = new Interpreter(loadModelFile(context));
            Log.d(TAG, "TFLite model loaded successfully.");
        } catch (IOException e) {
            Log.e(TAG, "Failed to load TFLite model", e);
        }
    }

    private MappedByteBuffer loadModelFile(Context context) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(MODEL_NAME);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    /**
     * Predicts stress (1) or normal (0) with smoothing, compensation, and hysteresis.
     */
    public int predictWithSmoothing(float bpm, float hrv, float temp,
                                    float accX, float accY, float accZ, float accMag) {
        if (interpreter == null) {
            Log.e(TAG, "Interpreter is null");
            return -1;
        }

        // Normalize inputs
        float normBpm = bpm / 100f;
        float normHrv = hrv / 100f;
        float normTemp = (temp - 30f) / 6f;  // Expected 30–36°C
        float normX = accX / 10f;
        float normY = accY / 10f;
        float normZ = accZ / 10f;
        float normMag = (accMag - 9.8f) / 5f;

        float[][] input = {{normBpm, normHrv, normTemp, normX, normY, normZ, normMag}};
        float[][] output = new float[1][1];

        try {
            interpreter.run(input, output);
            float rawPrediction = output[0][0];
            Log.d(TAG, String.format("Raw prediction: %.3f", rawPrediction));

            // Add to prediction history
            predictionHistory.add(rawPrediction);
            if (predictionHistory.size() > SMOOTH_WINDOW)
                predictionHistory.poll();

            // Smooth average
            float avg = 0;
            for (float val : predictionHistory) avg += val;
            avg /= predictionHistory.size();
            lastSmoothedValue = avg;

            Log.d(TAG, String.format("Smoothed prediction: %.3f", avg));

            // ==== Smart Medical Compensation ====
            float adjustedStressThreshold = 0.7f;
            float adjustedNormalThreshold = 0.3f;

            // If temperature is wrist-range (<34°C), raise tolerance
            if (temp < 34.0f) {
                adjustedStressThreshold += 0.15f;  // more forgiving
                adjustedNormalThreshold += 0.10f;
                Log.d(TAG, "Applying wrist-based compensation for temp = " + temp);
            }

            // Suppress false stress if motion + hrv are both low
            if (hrv > 50 && accMag < 10.5f && temp >= 32.0f) {
                Log.d(TAG, "Grace zone detected (low motion + stable HRV)");
                lastStableLabel = 0;
                return 0;
            }

            // ==== Hysteresis ====
            if (avg > adjustedStressThreshold) {
                lastStableLabel = 1;
            } else if (avg < adjustedNormalThreshold) {
                lastStableLabel = 0;
            }

            return lastStableLabel;

        } catch (Exception e) {
            Log.e(TAG, "Model inference failed", e);
            return -1;
        }
    }

    public float getLastSmoothedValue() {
        return lastSmoothedValue;
    }

    public void close() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
    }
}