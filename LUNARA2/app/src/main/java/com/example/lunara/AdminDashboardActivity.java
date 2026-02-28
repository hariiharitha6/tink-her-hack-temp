package com.example.lunara;

import androidx.appcompat.app.AppCompatActivity;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.Context;
import android.os.Vibrator;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;

import androidx.core.app.NotificationCompat;

import com.google.firebase.database.*;

import java.util.Calendar;
import android.util.Log;

public class AdminDashboardActivity extends AppCompatActivity {

    TextView areaSummary, trimesterSummary, riskSummary, alertStatusText;
    Button mapBtn, clearAlertBtn;

    DatabaseReference alertRef;
    ValueEventListener alertListener;
    Ringtone ringtone;
    long lastAlertTimestamp = 0; // Track last seen alert to avoid replaying old ones

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        // Initialize Views
        areaSummary = findViewById(R.id.areaSummary);
        trimesterSummary = findViewById(R.id.trimesterSummary);
        riskSummary = findViewById(R.id.riskSummary);
        alertStatusText = findViewById(R.id.alertStatusText);
        mapBtn = findViewById(R.id.mapBtn);
        clearAlertBtn = findViewById(R.id.clearAlertBtn);

        // Load Summary
        loadSummary();

        // Map Navigation
        mapBtn.setOnClickListener(v ->
                startActivity(new Intent(this, RegionDashboardActivity.class)));

        // Firebase Reference
        alertRef = FirebaseDatabase.getInstance().getReference("alerts");

        alertListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Log.d("LUNARA_ADMIN", "onDataChange fired. exists=" + snapshot.exists() + ", childrenCount=" + snapshot.getChildrenCount());

                if (!snapshot.exists()) {
                    // Stop sound if it's playing when alerts are cleared from another source
                    if (ringtone != null && ringtone.isPlaying()) {
                        ringtone.stop();
                        ringtone = null;
                    }
                    alertStatusText.setText("No Emergency Alerts");
                    alertStatusText.setTextColor(
                            getResources().getColor(android.R.color.black));
                    return;
                }

                DataSnapshot latest = null;

                for (DataSnapshot data : snapshot.getChildren()) {
                    latest = data;
                }

                if (latest != null) {

                    AlertModel alert = latest.getValue(AlertModel.class);

                    if (alert != null) {
                        Log.d("LUNARA_ADMIN", "Alert received: name=" + alert.name + ", mobile=" + alert.mobile + ", area=" + alert.area + ", timestamp=" + alert.timestamp);

                        alertStatusText.setText(
                                "\uD83D\uDEA8 EMERGENCY ALERT\n\n" +
                                        "Name: " + alert.name + "\n" +
                                        "Mobile: " + alert.mobile + "\n" +
                                        "Area: " + alert.area
                        );

                        alertStatusText.setTextColor(
                                getResources().getColor(android.R.color.holo_red_dark));

                        // Only trigger sound + notification for NEW alerts
                        if (alert.timestamp > lastAlertTimestamp) {
                            Log.d("LUNARA_ADMIN", "NEW alert detected! Playing sound + notification. lastTimestamp=" + lastAlertTimestamp + " -> newTimestamp=" + alert.timestamp);
                            lastAlertTimestamp = alert.timestamp;
                            showNotification("\uD83D\uDEA8 Emergency Alert",
                                    "Patient: " + alert.name + " | Mobile: " + alert.mobile + " | Area: " + alert.area);
                            playAlertSound();
                            vibrateDevice();
                        } else {
                            Log.d("LUNARA_ADMIN", "Old alert, skipping sound. alertTimestamp=" + alert.timestamp + " <= lastAlertTimestamp=" + lastAlertTimestamp);
                        }
                    } else {
                        Log.e("LUNARA_ADMIN", "Alert model is null from Firebase snapshot");
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e("LUNARA_ADMIN", "Firebase listener cancelled: " + error.getMessage());
                Toast.makeText(AdminDashboardActivity.this, "Firebase Error: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        };

        alertRef.addValueEventListener(alertListener);

        // Clear Alert Button
        clearAlertBtn.setOnClickListener(v -> clearEmergencyAlert());
    }

    // ===============================
    // LOAD PREGNANCY SUMMARY
    // ===============================
    private void loadSummary() {

        SharedPreferences prefs = getSharedPreferences("UserData", MODE_PRIVATE);

        String area = prefs.getString("area", "No Area");
        int year = prefs.getInt("year", 2024);
        int month = prefs.getInt("month", 0);
        int day = prefs.getInt("day", 1);

        Calendar lmp = Calendar.getInstance();
        lmp.set(year, month, day);

        Calendar today = Calendar.getInstance();
        long diff = today.getTimeInMillis() - lmp.getTimeInMillis();
        long weeks = (diff / (1000 * 60 * 60 * 24)) / 7;

        String trimester;

        if (weeks <= 12)
            trimester = "First Trimester";
        else if (weeks <= 27)
            trimester = "Second Trimester";
        else
            trimester = "Third Trimester";

        areaSummary.setText("Area: " + area);
        trimesterSummary.setText("Current Trimester: " + trimester);

        if (weeks > 36)
            riskSummary.setText("⚠ High Monitoring Required");
        else
            riskSummary.setText("Stable Pregnancy Status");
    }

    // ===============================
    // CLEAR ALERT FROM FIREBASE
    // ===============================
    private void clearEmergencyAlert() {

        alertRef.removeValue().addOnCompleteListener(task -> {

            if (task.isSuccessful()) {

                // 🔴 STOP SOUND IMMEDIATELY
                if (ringtone != null && ringtone.isPlaying()) {
                    ringtone.stop();
                    ringtone = null; // reset reference
                }

                alertStatusText.setText("No Emergency Alerts");
                alertStatusText.setTextColor(
                        getResources().getColor(android.R.color.black));

                Toast.makeText(this,
                        "Alert Cleared Successfully",
                        Toast.LENGTH_SHORT).show();

            } else {

                Toast.makeText(this,
                        "Failed to Clear Alert",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }
    // ===============================
    // PLAY ALERT SOUND
    // ===============================
    public void playAlertSound() {
        try {
            if (ringtone != null && ringtone.isPlaying()) return;

            Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmSound == null) {
                alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            }

            ringtone = RingtoneManager.getRingtone(getApplicationContext(), alarmSound);

            if (ringtone != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ringtone.setLooping(true);
                }
                ringtone.play();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void vibrateDevice() {
        try {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                // Vibrate pattern: wait 0ms, vibrate 500ms, wait 200ms, vibrate 500ms, repeat
                long[] pattern = {0, 500, 200, 500, 200, 500};
                vibrator.vibrate(pattern, -1); // -1 = don't repeat
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendEmergencyAlert() {

        SharedPreferences userPrefs = getSharedPreferences("UserData", MODE_PRIVATE);
        int currentUser = userPrefs.getInt("current_user", 1);

        String name = userPrefs.getString("user_" + currentUser + "_name", "Unknown");
        String mobile = userPrefs.getString("user_" + currentUser + "_mobile", "N/A");
        String area = userPrefs.getString("user_" + currentUser + "_area", "N/A");

        DatabaseReference ref =
                FirebaseDatabase.getInstance().getReference("alerts");

        String alertId = ref.push().getKey();

        AlertModel alert = new AlertModel();
        alert.name = name;
        alert.mobile = mobile;
        alert.area = area;
        alert.timestamp = System.currentTimeMillis();

        ref.child(alertId).setValue(alert)
                .addOnSuccessListener(unused -> {

                    showNotification("🚨 Emergency Alert Sent",
                            "ASHA Worker has been notified!");

                    Toast.makeText(this,
                            "Emergency Alert Sent Successfully!",
                            Toast.LENGTH_LONG).show();

                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Failed to send alert",
                               Toast.LENGTH_SHORT).show());
    }

    private void showNotification(String title, String message) {

        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        String channelId = "admin_channel";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel =
                    new NotificationChannel(channelId,
                            "Admin Alerts",
                            NotificationManager.IMPORTANCE_HIGH);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (alertRef != null && alertListener != null) {
            alertRef.removeEventListener(alertListener);
        }
        if (ringtone != null && ringtone.isPlaying()) {
            ringtone.stop();
        }
    }

}
