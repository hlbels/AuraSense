package com.example.aurasense.utils;

import android.content.Context;
import android.util.Log;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

public class TFLiteEmotionInterpreter {
    private static final String TAG = "TFLiteEmotionInterpreter";
    private static final boolean ENABLE_DEBUG_LOGS = true;

    // ---- Sampling & windowing (match training) ----
    // One sensor packet about every ~2s -> 15 packets ≈ 30s window
    private static final float SAMPLE_PERIOD_SEC = 2.0f;
    private static final int   WINDOW_SECONDS    = 30;
    private static final int   WINDOW_SIZE       = Math.max(1, Math.round(WINDOW_SECONDS / SAMPLE_PERIOD_SEC));

    // ---- Sensor pre-map to WESAD-ish units (same as before) ----
    private static final float ACC_SCALE  = 10f;   // ESP32 g -> Empatica-like range
    private static final float TEMP_SHIFT = 1.8f;  // bring skin T toward WESAD mean
    private static final float BVP_SCALE  = 100f;  // amplitude scale to match train distro (kept)

    // Rolling window of raw-but-rescaled samples: [accX, accY, accZ, temp, bvp_rawScaled]
    private final Queue<float[]> window = new LinkedList<>();

    // ONNX runtime
    private final OrtEnvironment env;
    private final OrtSession session;

    // === Train-only scaler (from models/feature_scaler_train.pkl) ===
    // Order: [acc_x_mean, acc_y_mean, acc_z_mean, temp_mean, bvp_mean,
    //         acc_x_std,  acc_y_std,  acc_z_std,  temp_std,  bvp_std]
    private static final double[] MEANS = new double[] {
            10.29446888, -2.68146610, 17.71344376, 32.70376205, 0.03583246,
            7.64594269,  8.24621105, 10.22108555, 0.02311826, 56.15372849
    };
    private static final double[] STDS  = new double[] {
            41.47072220, 26.64478111, 27.80905151,  1.47868752,  1.05989313,
            7.00220728,  9.34066677,  9.21833134,  0.01729131, 41.47352600
    };

    // Smoothing of discrete predictions
    private static final int SMOOTH_WINDOW = 5;
    private final Queue<Integer> predictionHistory = new LinkedList<>();
    private int lastStableLabel = 0;

    public TFLiteEmotionInterpreter(Context context) throws Exception {
        copyModelFromAssetsIfNeeded(context);
        File modelFile = new File(context.getFilesDir(), "stress_model.onnx");
        env = OrtEnvironment.getEnvironment();
        session = env.createSession(modelFile.getAbsolutePath(), new OrtSession.SessionOptions());
        if (ENABLE_DEBUG_LOGS) {
            Log.d(TAG, "ONNX model loaded. WINDOW_SIZE=" + WINDOW_SIZE);
        }
    }

    private void copyModelFromAssetsIfNeeded(Context context) {
        File modelFile = new File(context.getFilesDir(), "stress_model.onnx");
        if (!modelFile.exists()) {
            try (InputStream is = context.getAssets().open("stress_model.onnx");
                 OutputStream os = new FileOutputStream(modelFile)) {
                byte[] buffer = new byte[4096];
                int length;
                while ((length = is.read(buffer)) > 0) os.write(buffer, 0, length);
                if (ENABLE_DEBUG_LOGS) Log.d(TAG, "Copied stress_model.onnx to internal storage.");
            } catch (IOException e) {
                Log.e(TAG, "Failed to copy ONNX model from assets", e);
            }
        } else if (ENABLE_DEBUG_LOGS) {
            Log.d(TAG, "ONNX model already present in internal storage.");
        }
    }

    /**
     * Feed one raw sample; returns smoothed label.
     * Label map (WESAD wrist): 0 = Baseline, 1 = Amusement, 2 = Stress
     */
    public int predictFromRawSensors(float accX, float accY, float accZ, float temp, float bvp) {
        // ---- 1) Map raw ESP32 units to training-like ranges (no centering here) ----
        final float accX_scaled = accX * ACC_SCALE;
        final float accY_scaled = accY * ACC_SCALE;
        final float accZ_scaled = accZ * ACC_SCALE;
        final float temp_scaled = temp + TEMP_SHIFT;

        // Keep BVP in a raw scaled form (no EMA offset) → center per-window below
        final float bvp_rawScaled = bvp * BVP_SCALE;

        // Update window
        window.add(new float[]{ accX_scaled, accY_scaled, accZ_scaled, temp_scaled, bvp_rawScaled });
        if (window.size() < WINDOW_SIZE) return lastStableLabel;
        if (window.size() > WINDOW_SIZE) window.poll();

        // ---- 2) Compute 10 features in training order, with PER-WINDOW BVP centering ----
        // First pass: sums for means on raw-scaled values
        final int W = window.size();
        float[] sum = new float[5];
        for (float[] s : window) {
            sum[0] += s[0]; sum[1] += s[1]; sum[2] += s[2]; sum[3] += s[3]; sum[4] += s[4];
        }
        float accX_mean = sum[0] / W;
        float accY_mean = sum[1] / W;
        float accZ_mean = sum[2] / W;
        float temp_mean = sum[3] / W;
        float bvp_mean_raw = sum[4] / W; // mean of raw-scaled BVP in this window

        // Second pass: population std. For BVP, use (bvp - bvp_mean_raw) to zero center per window.
        float accX_var = 0f, accY_var = 0f, accZ_var = 0f, temp_var = 0f, bvp_var = 0f;
        for (float[] s : window) {
            float dx = s[0] - accX_mean; accX_var += dx*dx;
            float dy = s[1] - accY_mean; accY_var += dy*dy;
            float dz = s[2] - accZ_mean; accZ_var += dz*dz;
            float dt = s[3] - temp_mean; temp_var += dt*dt;
            float db = (s[4] - bvp_mean_raw); bvp_var += db*db;
        }
        float accX_std = (float)Math.sqrt(accX_var / W);
        float accY_std = (float)Math.sqrt(accY_var / W);
        float accZ_std = (float)Math.sqrt(accZ_var / W);
        float temp_std = (float)Math.sqrt(temp_var / W);
        float bvp_std  = (float)Math.sqrt(bvp_var  / W);

        // Assemble features in EXACT order. Note: we inject a centered BVP mean (~0).
        float[] features = new float[] {
                accX_mean, accY_mean, accZ_mean, temp_mean, 0f /* centered BVP mean */,
                accX_std,  accY_std,  accZ_std,  temp_std,  bvp_std
        };

        // ---- 3) Standardize with train-only scaler ----
        float[] inputScaled = new float[10];
        for (int i = 0; i < 10; i++) {
            inputScaled[i] = (float)((features[i] - MEANS[i]) / STDS[i]);
        }
        if (ENABLE_DEBUG_LOGS) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < inputScaled.length; i++) {
                sb.append(inputScaled[i]);
                if (i < inputScaled.length - 1) sb.append(", ");
            }
            Log.d(TAG, "Scaled features: [" + sb + "]");
        }

        // ---- 4) ONNX inference (robust to different export heads) ----
        try {
            String inputName = firstInputName(session.getInputNames());
            try (OnnxTensor input = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputScaled), new long[]{1, 10});
                 OrtSession.Result result = session.run(Collections.singletonMap(inputName, input))) {

                Integer predicted = readLabelFromResult(result);
                if (predicted == null) {
                    Log.e(TAG, "Unexpected ONNX outputs; cannot infer label.");
                    return -1;
                }

                // ---- 5) Temporal smoothing ----
                predictionHistory.add(predicted);
                if (predictionHistory.size() > SMOOTH_WINDOW) predictionHistory.poll();

                int[] counts = new int[3];
                for (int lab : predictionHistory) counts[lab]++;
                lastStableLabel = argmax(counts);

                if (ENABLE_DEBUG_LOGS) {
                    Log.d(TAG, "Predicted=" + predicted + " Stable=" + lastStableLabel);
                }
                return lastStableLabel;
            }
        } catch (OrtException e) {
            Log.e(TAG, "ONNX inference failed", e);
            return -1;
        }
    }

    // -------- Helpers --------

    private static String firstInputName(Set<String> names) {
        Iterator<String> it = names.iterator();
        return it.hasNext() ? it.next() : "input";
    }

    /** Accept either a direct label (long[]) or probabilities (float[] / float[][]). */
    private static Integer readLabelFromResult(OrtSession.Result result) throws OrtException {
        Integer lab = tryReadLabelFromIndex(result, 0);
        if (lab != null) return lab;
        return tryReadLabelFromIndex(result, 1);
    }

    private static Integer tryReadLabelFromIndex(OrtSession.Result result, int idx) throws OrtException {
        OnnxValue val;
        try {
            val = result.get(idx);
        } catch (IndexOutOfBoundsException ex) {
            return null;
        }
        if (!(val instanceof OnnxTensor)) return null;

        Object raw = ((OnnxTensor) val).getValue();
        if (raw instanceof long[]) {
            long[] labs = (long[]) raw;
            return labs.length > 0 ? (int) labs[0] : null;
        } else if (raw instanceof float[][]) {
            float[] probs = ((float[][]) raw)[0];
            return argmax(probs);
        } else if (raw instanceof float[]) {
            float[] probs = (float[]) raw;
            return argmax(probs);
        }
        return null;
    }

    private static int argmax(float[] a) {
        int idx = 0;
        float max = a[0];
        for (int i = 1; i < a.length; i++) {
            if (a[i] > max) { max = a[i]; idx = i; }
        }
        return idx;
    }

    private static int argmax(int[] a) {
        int idx = 0;
        int max = a[0];
        for (int i = 1; i < a.length; i++) {
            if (a[i] > max) { max = a[i]; idx = i; }
        }
        return idx;
    }

    public int getLastStableLabel() { return lastStableLabel; }

    public void close() {
        try {
            if (session != null) session.close();
            if (env != null) env.close();
        } catch (Exception e) {
            Log.e(TAG, "Failed to close ONNX session", e);
        }
    }
}
