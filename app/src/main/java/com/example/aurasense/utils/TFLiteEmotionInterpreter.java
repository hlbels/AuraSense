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

    // Smoothing + hysteresis
    private static final int SMOOTH_WINDOW = 5;
    private final Queue<Float> predictionHistory = new LinkedList<>();
    private float lastSmoothedValue = 0;
    private int lastStableLabel = 0; // 0 = normal, 1 = stress

    // Hysteresis thresholds
    private static final float STRESS_THRESHOLD = 0.7f;
    private static final float NORMAL_THRESHOLD = 0.3f;

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

    public int predictWithSmoothing(float bpm, float hrv, float temp,
                                float accX, float accY, float accZ, float accMag) {
    if (interpreter == null) {
        Log.e(TAG, "Interpreter is null");
        return lastStableLabel;  // Fallback to last known state
    }

    // ----------- Validation checks ----------
    if (bpm <= 20 || temp <= 28) {  // Lower threshold slightly for wrist skin temp
        Log.w(TAG, "Skipping unstable reading: bpm=" + bpm + ", temp=" + temp);
        return lastStableLabel;  // Do NOT return -1; fallback to last known state
    }

    // ----------- Temperature Compensation (for wrist) ----------
    if (temp < 34.0f) {
        temp += 4.5f;  // Calibrated wrist offset
        Log.d(TAG, "Temp corrected for wrist: " + temp);
    }

    // Normalize inputs
    bpm /= 100f;
    hrv /= 100f;
    temp = (temp - 30f) / 6f;
    accX /= 10f;
    accY /= 10f;
    accZ /= 10f;
    accMag = (accMag - 9.8f) / 5f;

    float[][] input = new float[1][7];
    input[0][0] = bpm;
    input[0][1] = hrv;
    input[0][2] = temp;
    input[0][3] = accX;
    input[0][4] = accY;
    input[0][5] = accZ;
    input[0][6] = accMag;

    float[][] output = new float[1][1];

    try {
        interpreter.run(input, output);
        float raw = output[0][0];
        Log.d(TAG, String.format("Raw prediction: %.3f", raw));

        predictionHistory.add(raw);
        if (predictionHistory.size() > SMOOTH_WINDOW) predictionHistory.poll();

        float avg = 0;
        for (float val : predictionHistory) avg += val;
        avg /= predictionHistory.size();
        lastSmoothedValue = avg;
        Log.d(TAG, String.format("Smoothed prediction: %.3f", avg));

        if (avg > STRESS_THRESHOLD) {
            lastStableLabel = 1;
        } else if (avg < NORMAL_THRESHOLD) {
            lastStableLabel = 0;
        }

        return lastStableLabel;

    } catch (Exception e) {
        Log.e(TAG, "Inference failed", e);
        return lastStableLabel;  // Fallback gracefully
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