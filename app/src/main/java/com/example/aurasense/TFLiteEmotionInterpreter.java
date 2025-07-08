package com.example.aurasense;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class TFLiteEmotionInterpreter {

    private Interpreter interpreter;

    public TFLiteEmotionInterpreter(Context context) {
        try {
            interpreter = new Interpreter(loadModelFile(context));
        } catch (IOException e) {
            Log.e("TFLiteModel", "Model loading failed", e);

        }
    }

    private MappedByteBuffer loadModelFile(Context context) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd("stress_model.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public int predictStress(int bpm, float temp, float accX, float accY, float accZ) {
        float[][] input = new float[][]{{bpm, temp, accX, accY, accZ}};
        float[][] output = new float[1][1];
        interpreter.run(input, output);
        return Math.round(output[0][0]);
    }
}
