package com.example.aurasense.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.aurasense.R;
import com.example.aurasense.utils.HistoryStorage;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";
    private Switch switchStressAlerts, switchModel2;
    private TextView exportDataBtn, deleteDataBtn, backBtn;

    private enum ExportFormat { PDF, CSV, JSON }
    private ExportFormat pendingFormat = ExportFormat.PDF;

    private final ActivityResultLauncher<Intent> exportLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri == null) {
                        Toast.makeText(this, "No file selected.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        switch (pendingFormat) {
                            case PDF:
                                writeHistoryPdf(uri);
                                break;
                            case CSV:
                                writeHistoryCsv(uri);
                                break;
                            case JSON:
                                writeHistoryJson(uri);
                                break;
                        }
                    } catch (Exception ex) {
                        Log.e(TAG, "Export failed", ex);
                        Toast.makeText(this, "Export failed: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(this, "Export canceled.", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "SettingsActivity onCreate called");
        setContentView(R.layout.activity_settings);

        // Views
        switchStressAlerts = findViewById(R.id.switchStressAlerts);
        switchModel2 = findViewById(R.id.switchModel2);
        exportDataBtn = findViewById(R.id.exportDataBtn);
        deleteDataBtn = findViewById(R.id.deleteDataBtn);
        backBtn = findViewById(R.id.backBtn);

        // Prefs
        SharedPreferences prefs = getSharedPreferences("AuraPrefs", MODE_PRIVATE);
        switchStressAlerts.setChecked(prefs.getBoolean("stress_alerts_enabled", true));
        switchModel2.setChecked(prefs.getBoolean("model_2_enabled", false));

        switchStressAlerts.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            prefs.edit().putBoolean("stress_alerts_enabled", isChecked).apply();
            Toast.makeText(this, "Stress alerts " + (isChecked ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
        });

        switchModel2.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            prefs.edit().putBoolean("model_2_enabled", isChecked).apply();
            Toast.makeText(this, "Model 2 " + (isChecked ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
        });

        // Export
        exportDataBtn.setOnClickListener(v -> {
            List<HistoryStorage.Entry> history = HistoryStorage.getHistory();
            if (history == null || history.isEmpty()) {
                Toast.makeText(this, "No data to export yet.", Toast.LENGTH_SHORT).show();
                return;
            }
            showExportChooser();
        });

        // Delete
        deleteDataBtn.setOnClickListener(v -> showDeleteDataDialog());

        backBtn.setOnClickListener(v -> {
            startActivity(new Intent(this, HomeActivity.class));
            finish();
        });

        // Bottom nav
        BottomNavigationView bottomNavigation = findViewById(R.id.bottomNavigation);
        bottomNavigation.setSelectedItemId(R.id.nav_settings);
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                startActivity(new Intent(this, HomeActivity.class));
                return true;
            } else if (id == R.id.nav_history) {
                startActivity(new Intent(this, HistoryActivity.class));
                return true;
            } else if (id == R.id.nav_notifications) {
                startActivity(new Intent(this, NotificationActivity.class));
                return true;
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
                return true;
            } else if (id == R.id.nav_settings) {
                return true;
            }
            return false;
        });
    }

    private void showExportChooser() {
        final String[] items = {"PDF (recommended)", "CSV (spreadsheet)", "JSON"};
        new AlertDialog.Builder(this)
                .setTitle("Export format")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0:
                            pendingFormat = ExportFormat.PDF;
                            launchCreateDocument("application/pdf", defaultBaseName() + ".pdf");
                            break;
                        case 1:
                            pendingFormat = ExportFormat.CSV;
                            launchCreateDocument("text/csv", defaultBaseName() + ".csv");
                            break;
                        case 2:
                            pendingFormat = ExportFormat.JSON;
                            launchCreateDocument("application/json", defaultBaseName() + ".json");
                            break;
                    }
                })
                .show();
    }

    private String defaultBaseName() {
        return "aura_history_" + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
                .format(System.currentTimeMillis());
    }

    private void launchCreateDocument(String mime, String filename) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mime);
        intent.putExtra(Intent.EXTRA_TITLE, filename);
        exportLauncher.launch(intent);
    }

    // ---------- PDF ----------
    private void writeHistoryPdf(Uri uri) throws Exception {
        List<HistoryStorage.Entry> history = HistoryStorage.getHistory();
        if (history == null || history.isEmpty()) throw new IllegalStateException("No history");

        // Create PDF (A4 @ 72 dpi: 595 x 842 points)
        PdfDocument doc = new PdfDocument();

        // Layout
        final int pageWidth = 595;
        final int pageHeight = 842;
        final int margin = 32;

        Paint titlePaint = new Paint();
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        titlePaint.setTextSize(18f);

        Paint headerPaint = new Paint();
        headerPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        headerPaint.setTextSize(12f);

        Paint cellPaint = new Paint();
        cellPaint.setTextSize(11f);

        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

        // columns
        String[] headers = {"Time", "BPM", "Temp (°C)", "HRV", "Move (m/s²)", "BVP"};
        int[] colX = new int[]{
                margin,
                margin + 180,
                margin + 240,
                margin + 320,
                margin + 380,
                margin + 470
        };

        int rowHeight = 16;
        int y = margin + 24;

        PdfDocument.Page page = doc.startPage(new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create());
        Canvas canvas = page.getCanvas();

        // Title
        canvas.drawText("AuraSense — Exported History", margin, y, titlePaint);
        y += 18 + 6;

        // Header row
        drawHeaderRow(canvas, headers, colX, y, headerPaint);
        y += rowHeight;

        int pageNum = 1;
        for (HistoryStorage.Entry e : history) {
            if (y > pageHeight - margin) {
                doc.finishPage(page);
                pageNum++;
                page = doc.startPage(new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create());
                canvas = page.getCanvas();
                y = margin;

                // repeat title + header on new page
                canvas.drawText("AuraSense — Exported History (cont.)", margin, y, titlePaint);
                y += 18 + 6;
                drawHeaderRow(canvas, headers, colX, y, headerPaint);
                y += rowHeight;
            }

            String t = iso.format(e.timestamp);
            String bpm = numOrBlank(e.bpm, 0);
            String temp = numOrBlank(e.temp, 1);
            String hrv = numOrBlank(e.hrv, 0);
            float accMag = Float.isNaN(e.accMag) ? (float)Math.sqrt(e.accX*e.accX + e.accY*e.accY + e.accZ*e.accZ) : e.accMag;
            String mov = numOrBlank(accMag, 2);
            String bvp = numOrBlank(e.bvp, 3);

            canvas.drawText(t,    colX[0], y, cellPaint);
            canvas.drawText(bpm,  colX[1], y, cellPaint);
            canvas.drawText(temp, colX[2], y, cellPaint);
            canvas.drawText(hrv,  colX[3], y, cellPaint);
            canvas.drawText(mov,  colX[4], y, cellPaint);
            canvas.drawText(bvp,  colX[5], y, cellPaint);

            y += rowHeight;
        }

        doc.finishPage(page);

        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            if (os == null) throw new IllegalStateException("Cannot open output stream");
            doc.writeTo(os);
            Toast.makeText(this, "PDF exported: " + uri.getLastPathSegment(), Toast.LENGTH_LONG).show();
        } finally {
            doc.close();
        }
    }

    private void drawHeaderRow(Canvas c, String[] headers, int[] colX, int y, Paint p) {
        for (int i = 0; i < headers.length; i++) {
            c.drawText(headers[i], colX[i], y, p);
        }
    }

    // ---------- CSV ----------
    private void writeHistoryCsv(Uri uri) throws Exception {
        try (OutputStream os = getContentResolver().openOutputStream(uri);
             OutputStreamWriter osw = new OutputStreamWriter(os);
             PrintWriter pw = new PrintWriter(osw)) {

            pw.println("timestamp_iso,timestamp_ms,bpm,temp,hrv,acc_x,acc_y,acc_z,acc_mag,bvp");

            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            List<HistoryStorage.Entry> history = HistoryStorage.getHistory();

            for (HistoryStorage.Entry e : history) {
                String ts = iso.format(e.timestamp);
                float accMag = Float.isNaN(e.accMag) ? (float)Math.sqrt(e.accX*e.accX + e.accY*e.accY + e.accZ*e.accZ) : e.accMag;
                pw.printf(Locale.US,
                        "%s,%d,%s,%s,%s,%s,%s,%s,%s,%s%n",
                        ts,
                        e.timestamp,
                        numOrBlank(e.bpm, 0),
                        numOrBlank(e.temp, 1),
                        numOrBlank(e.hrv, 0),
                        numOrBlank(e.accX, 2),
                        numOrBlank(e.accY, 2),
                        numOrBlank(e.accZ, 2),
                        numOrBlank(accMag, 2),
                        numOrBlank(e.bvp, 3)
                );
            }

            pw.flush();
            Toast.makeText(this, "CSV exported: " + uri.getLastPathSegment(), Toast.LENGTH_LONG).show();
        }
    }

    // ---------- JSON ----------
    private void writeHistoryJson(Uri uri) throws Exception {
        try (OutputStream os = getContentResolver().openOutputStream(uri);
             OutputStreamWriter osw = new OutputStreamWriter(os);
             PrintWriter pw = new PrintWriter(osw)) {

            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            List<HistoryStorage.Entry> history = HistoryStorage.getHistory();

            pw.println("[");
            for (int i = 0; i < history.size(); i++) {
                HistoryStorage.Entry e = history.get(i);
                float accMag = Float.isNaN(e.accMag) ? (float)Math.sqrt(e.accX*e.accX + e.accY*e.accY + e.accZ*e.accZ) : e.accMag;
                String obj = String.format(Locale.US,
                        "{\"timestamp_iso\":\"%s\",\"timestamp_ms\":%d," +
                                "\"bpm\":%s,\"temp\":%s,\"hrv\":%s," +
                                "\"acc_x\":%s,\"acc_y\":%s,\"acc_z\":%s,\"acc_mag\":%s," +
                                "\"bvp\":%s}",
                        iso.format(e.timestamp), e.timestamp,
                        numOrNull(e.bpm, 0), numOrNull(e.temp, 1), numOrNull(e.hrv, 0),
                        numOrNull(e.accX, 2), numOrNull(e.accY, 2), numOrNull(e.accZ, 2), numOrNull(accMag, 2),
                        numOrNull(e.bvp, 3)
                );
                pw.print(obj);
                if (i < history.size() - 1) pw.println(",");
            }
            pw.println();
            pw.println("]");

            pw.flush();
            Toast.makeText(this, "JSON exported: " + uri.getLastPathSegment(), Toast.LENGTH_LONG).show();
        }
    }

    private String numOrBlank(float v, int decimals) {
        if (Float.isNaN(v) || Float.isInfinite(v)) return "";
        String fmt = decimals <= 0 ? "%.0f" : "%." + decimals + "f";
        return String.format(Locale.US, fmt, v);
    }

    private String numOrNull(float v, int decimals) {
        if (Float.isNaN(v) || Float.isInfinite(v)) return "null";
        String fmt = decimals <= 0 ? "%.0f" : "%." + decimals + "f";
        return String.format(Locale.US, fmt, v);
    }

    private void showDeleteDataDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete All Data")
                .setMessage("Are you sure you want to delete all app data? This will clear:\n\n" +
                        "• All stress history\n" +
                        "• All notifications\n" +
                        "• All settings\n\n" +
                        "This action cannot be undone.")
                .setPositiveButton("Delete All", (dialog, which) -> deleteAllAppData())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteAllAppData() {
        try {
            getSharedPreferences("AuraPrefs", MODE_PRIVATE).edit().clear().apply();
            getSharedPreferences("AuraNotifications", MODE_PRIVATE).edit().clear().apply();
            HistoryStorage.clearHistory();

            switchStressAlerts.setChecked(true);
            switchModel2.setChecked(false);

            Toast.makeText(this, "All app data deleted successfully", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error deleting data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
