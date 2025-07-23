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
    private int lastStableLabel = 0; // 0 = normal, 1 = stress

    private static final float STRESS_THRESHOLD = 0.7f;
    private static final float NORMAL_THRESHOLD = 0.3f;

    private static final float WRIST_TEMP_OFFSET = 4.5f;  // Calibration offset

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
     * Predicts stress (1) or normal (0) with smoothing and hysteresis.
     * Uses 7 normalized inputs: bpm, hrv, temp, accX, accY, accZ, accMag
     */
    public int predictWithSmoothing(float bpm, float hrv, float temp,
                                    float accX, float accY, float accZ, float accMag) {
        if (interpreter == null) {
            Log.e(TAG, "Interpreter is null");
            return -1;
        }

        // === Normalize Inputs ===
        bpm /= 100f;
        hrv /= 100f;

        // === FIXED: Wrist temperature adjustment ===
        temp += WRIST_TEMP_OFFSET; // Compensate for wrist reading
        temp = (temp - 30f) / 6f;  // Normalize to [0,1] assuming 30–36°C after offset

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
            float rawPrediction = output[0][0];
            Log.d(TAG, String.format("Model raw prediction: %.3f", rawPrediction));

            predictionHistory.add(rawPrediction);
            if (predictionHistory.size() > SMOOTH_WINDOW)
                predictionHistory.poll();

            float avg = 0;
            for (float p : predictionHistory) avg += p;
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