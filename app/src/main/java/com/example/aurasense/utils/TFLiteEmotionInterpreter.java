package com.example.aurasense.utils;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class TFLiteEmotionInterpreter {
    private static final String TAG = "TFLiteEmotionInterpreter";
    private static final String MODEL_NAME = "stress_model.tflite";

    private Interpreter interpreter;

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
     * Predicts stress (1) or normal (0) using 7 input features:
     * bpm, hrv, temp, acc_x, acc_y, acc_z, acc_mag
     */
    public int predict(float bpm, float hrv, float temp, float accX, float accY, float accZ, float accMag) {
        if (interpreter == null) {
            Log.e(TAG, "Interpreter is null");
            return -1;
        }

        // Normalize inputs (adjust based on your model's training setup)
        bpm /= 100f;            // Normalize bpm (assuming range 0–200)
        hrv /= 100f;            // Normalize HRV (e.g., RMSSD in ms, 0–200)
        temp = (temp - 30f) / 6f;  // maps 30–36°C → [0–1]
        accX /= 10f;
        accY /= 10f;
        accZ /= 10f;
        accMag = (accMag - 9.8f) / 5f; // Centered around gravity, fine for WESAD

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
            Log.d(TAG, String.format("Model input: bpm=%.2f, hrv=%.2f, temp=%.2f, acc=[%.2f %.2f %.2f], mag=%.2f",
                    bpm, hrv, temp, accX, accY, accZ, accMag));
            interpreter.run(input, output);
            float prediction = output[0][0];
            Log.d(TAG, "Model prediction: " + prediction);
            return Math.round(prediction);  // 0 = normal, 1 = stressed
        } catch (Exception e) {
            Log.e(TAG, "Model inference failed", e);
            return -1;
        }
    }

    public void close() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
    }
}
