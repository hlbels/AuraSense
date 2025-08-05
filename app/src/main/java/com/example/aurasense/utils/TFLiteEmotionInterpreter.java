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

    private static final int SMOOTH_WINDOW = 5;
    private final Queue<Float> predictionHistory = new LinkedList<>();
    private float lastSmoothedValue = 0;
    private int lastStableLabel = 0; // 0 = calm, 1 = aroused

    private static final float AROUSED_THRESHOLD = 0.7f;
    private static final float CALM_THRESHOLD = 0.3f;

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
     * Predicts arousal (1) or calm (0) using a 5-feature input:
     * ACCx, ACCy, ACCz, TEMP, BVP
     */
    public int predictWithSmoothing(float accX, float accY, float accZ, float temp, float bvp) {
        if (interpreter == null) {
            Log.e(TAG, "Interpreter is null");
            return -1;
        }

        // === Optional: normalize if needed ===
        // Only apply normalization if your model was trained on scaled features

        // TEMP: Assume typical range ~30–36°C (rescaled to [0,1])
        temp = (temp - 30f) / 6f;
        if (temp < 0f) temp = 0f;
        if (temp > 1f) temp = 1f;

        // BVP: rough scaling (optional, depends on training)
        bvp = bvp / 2.0f;

        accX = accX / 10f;
        accY = accY / 10f;
        accZ = accZ / 10f;

        float[][] input = new float[1][5];
        input[0][0] = accX;
        input[0][1] = accY;
        input[0][2] = accZ;
        input[0][3] = temp;
        input[0][4] = bvp;

        float[][] output = new float[1][1];

        try {
            interpreter.run(input, output);
            float rawPrediction = output[0][0];
            Log.d(TAG, String.format("Raw prediction: %.3f", rawPrediction));

            // === Smoothing window ===
            predictionHistory.add(rawPrediction);
            if (predictionHistory.size() > SMOOTH_WINDOW)
                predictionHistory.poll();

            float avg = 0;
            for (float p : predictionHistory) avg += p;
            avg /= predictionHistory.size();
            lastSmoothedValue = avg;
            Log.d(TAG, String.format("Smoothed prediction: %.3f", avg));

            // === Hysteresis for stable label ===
            if (avg > AROUSED_THRESHOLD) {
                lastStableLabel = 1;
            } else if (avg < CALM_THRESHOLD) {
                lastStableLabel = 0;
            }

            return lastStableLabel;

        } catch (Exception e) {
            Log.e(TAG, "Inference failed", e);
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