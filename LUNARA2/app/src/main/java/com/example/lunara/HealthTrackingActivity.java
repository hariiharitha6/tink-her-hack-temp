package com.example.lunara;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.*;
import android.content.SharedPreferences;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import android.content.Context;
import android.text.TextUtils;
import android.graphics.Color;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.*;

import java.util.*;

public class HealthTrackingActivity extends AppCompatActivity {

    EditText bpInput, sugarInput, weightInput, hemoInput;
    Button saveHealthBtn;
    LineChart healthChart;   // ✅ Moved inside class properly

    ProgressBar riskProgress;
    TextView riskStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_health_tracking);

        bpInput = findViewById(R.id.bpInput);
        sugarInput = findViewById(R.id.sugarInput);
        weightInput = findViewById(R.id.weightInput);
        hemoInput = findViewById(R.id.hemoInput);
        saveHealthBtn = findViewById(R.id.saveHealthBtn);
        healthChart = findViewById(R.id.healthChart);

        loadGraph(); // Load previous history

        saveHealthBtn.setOnClickListener(v -> {
            saveHealthData();
            loadGraph(); // Refresh graph after saving
            riskProgress = findViewById(R.id.riskProgress);
            riskStatus = findViewById(R.id.riskStatus);
        });
    }

    private void saveHealthData() {

        String bp = bpInput.getText().toString().trim();
        String sugar = sugarInput.getText().toString().trim();
        String weight = weightInput.getText().toString().trim();
        String hemo = hemoInput.getText().toString().trim();

        if (TextUtils.isEmpty(bp) || TextUtils.isEmpty(sugar)
                || TextUtils.isEmpty(weight) || TextUtils.isEmpty(hemo)) {

            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("HealthHistory", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        long timestamp = System.currentTimeMillis();

        editor.putString("bp_" + timestamp, bp);
        editor.putString("sugar_" + timestamp, sugar);
        editor.putString("weight_" + timestamp, weight);
        editor.putString("hemo_" + timestamp, hemo);

        editor.apply();

        analyzeRisk(bp, sugar, hemo);

        Toast.makeText(this, "Health Data Saved", Toast.LENGTH_SHORT).show();
    }

    private void analyzeRisk(String bp, String sugar, String hemo) {

        try {

            int riskScore = 0;

            int systolic;
            if (bp.contains("/")) {
                String[] parts = bp.split("/");
                systolic = Integer.parseInt(parts[0]);
            } else {
                systolic = Integer.parseInt(bp);
            }

            int sugarValue = Integer.parseInt(sugar);
            double hemoValue = Double.parseDouble(hemo);

            // 🧠 Risk Calculation Logic
            if (systolic >= 140) riskScore += 30;
            if (sugarValue >= 140) riskScore += 25;
            if (hemoValue < 10) riskScore += 20;

            // Get pregnancy week
            SharedPreferences prefs = getSharedPreferences("UserData", MODE_PRIVATE);
            int year = prefs.getInt("year", 2024);
            int month = prefs.getInt("month", 0);
            int day = prefs.getInt("day", 1);

            Calendar lmp = Calendar.getInstance();
            lmp.set(year, month, day);
            Calendar today = Calendar.getInstance();

            long diff = today.getTimeInMillis() - lmp.getTimeInMillis();
            long weeks = (diff / (1000 * 60 * 60 * 24)) / 7;

            if (weeks > 36) riskScore += 15;

            // 🎯 Update ProgressBar
            riskProgress.setProgress(riskScore);

            // 🎨 Risk Level Display
            if (riskScore <= 30) {
                riskStatus.setText("🟢 Low Risk (" + riskScore + "%)");
                riskStatus.setTextColor(android.graphics.Color.GREEN);
            }
            else if (riskScore <= 60) {
                riskStatus.setText("🟡 Moderate Risk (" + riskScore + "%)");
                riskStatus.setTextColor(android.graphics.Color.parseColor("#FFA500"));
            }
            else {
                riskStatus.setText("🔴 High Risk (" + riskScore + "%)");
                riskStatus.setTextColor(android.graphics.Color.RED);
            }

            // 🔔 Keep your existing alerts
            if (systolic >= 140) {
                showNotification("⚠ High Blood Pressure",
                        "Your BP is high. Please consult doctor immediately.");
            }

            if (sugarValue >= 140) {
                showNotification("⚠ High Sugar Level",
                        "Monitor sugar levels carefully.");
            }

            if (hemoValue < 10) {
                showNotification("⚠ Low Hemoglobin",
                        "Iron deficiency detected.");
            }

        } catch (Exception e) {
            Toast.makeText(this,
                    "Invalid Input Format (BP example: 120/80)",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void loadGraph() {

        SharedPreferences prefs = getSharedPreferences("HealthHistory", MODE_PRIVATE);
        Map<String, ?> allData = prefs.getAll();

        List<Long> timestamps = new ArrayList<>();

        // Collect timestamps properly
        for (String key : allData.keySet()) {
            if (key.startsWith("sugar_")) {
                timestamps.add(Long.parseLong(key.replace("sugar_", "")));
            }
        }

        Collections.sort(timestamps);

        List<Entry> sugarEntries = new ArrayList<>();
        List<Entry> bpEntries = new ArrayList<>();

        int index = 0;

        for (Long time : timestamps) {

            String sugar = prefs.getString("sugar_" + time, "0");
            String bp = prefs.getString("bp_" + time, "0");

            float sugarVal = Float.parseFloat(sugar);
            sugarEntries.add(new Entry(index, sugarVal));

            if (bp.contains("/")) {
                String[] parts = bp.split("/");
                float systolic = Float.parseFloat(parts[0]);
                bpEntries.add(new Entry(index, systolic));
            }

            index++;
        }

        LineDataSet sugarDataSet = new LineDataSet(sugarEntries, "Sugar Level");
        sugarDataSet.setColor(Color.RED);
        sugarDataSet.setCircleColor(Color.RED);
        sugarDataSet.setValueTextColor(Color.BLACK);
        sugarDataSet.setLineWidth(3f);

        LineDataSet bpDataSet = new LineDataSet(bpEntries, "BP (Systolic)");
        bpDataSet.setColor(Color.BLUE);
        bpDataSet.setCircleColor(Color.BLUE);
        bpDataSet.setValueTextColor(Color.BLACK);
        bpDataSet.setLineWidth(3f);

        LineData lineData = new LineData(sugarDataSet, bpDataSet);

        healthChart.setData(lineData);
        healthChart.animateY(1000);
        healthChart.invalidate();
    }

    private void showNotification(String title, String message) {

        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        String channelId = "health_channel";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel =
                    new NotificationChannel(channelId,
                            "Health Alerts",
                            NotificationManager.IMPORTANCE_HIGH);
            channel.enableVibration(true);
            manager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setDefaults(NotificationCompat.DEFAULT_ALL);

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }
}