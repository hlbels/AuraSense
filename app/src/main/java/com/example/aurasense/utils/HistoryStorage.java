package com.example.aurasense.utils;

import java.util.ArrayList;
import java.util.List;

public class HistoryStorage {
    public static class Entry {
        public final long timestamp;
        public final float bpm, temp, hrv, accX, accY, accZ, accMag, bvp; // Added bvp

        public Entry(long timestamp, float bpm, float temp, float hrv,
                     float accX, float accY, float accZ, float accMag, float bvp) {
            this.timestamp = timestamp;
            this.bpm = bpm;
            this.temp = temp;
            this.hrv = hrv;
            this.accX = accX;
            this.accY = accY;
            this.accZ = accZ;
            this.accMag = accMag;
            this.bvp = bvp; // assign bvp
        }
    }

    private static final List<Entry> history = new ArrayList<>();

    public static void add(Entry entry) {
        history.add(0, entry);  // newest first
    }

    public static List<Entry> getHistory() {
        return history;
    }

    public static void clearHistory() {
        history.clear();
    }
}
