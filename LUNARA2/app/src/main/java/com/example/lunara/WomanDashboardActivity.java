package com.example.lunara;

import androidx.appcompat.app.AppCompatActivity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import androidx.core.app.NotificationCompat;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.Calendar;
import android.util.Log;

public class WomanDashboardActivity extends AppCompatActivity {

    TextView nameText, areaText, mobileText, weightText;
    TextView weekText, trimesterText, dueDateText, tipsText;
    Button healthBtn, babyBtn, emergencyBtn, riskBtn, alertBtn;
    TextView sidebarHealth, sidebarBaby, sidebarLogout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_woman_dashboard);

        // Initialize Views
        nameText = findViewById(R.id.nameText);
        areaText = findViewById(R.id.areaText);
        mobileText = findViewById(R.id.mobileText);
        weightText = findViewById(R.id.weightText);
        weekText = findViewById(R.id.weekText);
        trimesterText = findViewById(R.id.trimesterText);
        dueDateText = findViewById(R.id.dueDateText);
        tipsText = findViewById(R.id.tipsText);

        healthBtn = findViewById(R.id.healthBtn);
        babyBtn = findViewById(R.id.babyBtn);
        emergencyBtn = findViewById(R.id.emergencyBtn);
        riskBtn = findViewById(R.id.riskBtn);
        alertBtn = findViewById(R.id.alertBtn);

        sidebarHealth = findViewById(R.id.sidebarHealth);
        sidebarBaby = findViewById(R.id.sidebarBaby);
        sidebarLogout = findViewById(R.id.sidebarLogout);

        loadUserData();

        // Button Listeners
        healthBtn.setOnClickListener(v -> startActivity(new Intent(this, HealthTrackingActivity.class)));
        babyBtn.setOnClickListener(v -> startActivity(new Intent(this, BabyDevelopmentActivity.class)));
        emergencyBtn.setOnClickListener(v -> startActivity(new Intent(this, EmergencyActivity.class)));
        riskBtn.setOnClickListener(v -> startActivity(new Intent(this, RiskAlertActivity.class)));
        
        // Fix: Call sendEmergencyAlert() to trigger notification and sound on Admin/ASHA side
        alertBtn.setOnClickListener(v -> sendEmergencyAlert());

        sidebarHealth.setOnClickListener(v -> startActivity(new Intent(this, HealthTrackingActivity.class)));
        sidebarBaby.setOnClickListener(v -> startActivity(new Intent(this, BabyDevelopmentActivity.class)));
        sidebarLogout.setOnClickListener(v -> {
            SharedPreferences.Editor editor = getSharedPreferences("UserData", MODE_PRIVATE).edit();
            editor.clear();
            editor.apply();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void loadUserData() {
        SharedPreferences prefs = getSharedPreferences("UserData", MODE_PRIVATE);
        int currentUser = prefs.getInt("current_user", 1);

        String name = prefs.getString("user_" + currentUser + "_name", "User");
        String area = prefs.getString("user_" + currentUser + "_area", "Area");
        String mobile = prefs.getString("user_" + currentUser + "_mobile", "Mobile");
        String weight = prefs.getString("user_" + currentUser + "_weight", "60");

        nameText.setText("Name: " + name);
        areaText.setText("Area: " + area);
        mobileText.setText("Mobile: " + mobile);
        weightText.setText("Current Weight: " + weight + " kg");

        // Pregnancy logic
        int year = prefs.getInt("user_" + currentUser + "_year", 2024);
        int month = prefs.getInt("user_" + currentUser + "_month", 0);
        int day = prefs.getInt("user_" + currentUser + "_day", 1);

        Calendar lmp = Calendar.getInstance();
        lmp.set(year, month, day);

        Calendar today = Calendar.getInstance();
        long diff = today.getTimeInMillis() - lmp.getTimeInMillis();
        long days = diff / (1000 * 60 * 60 * 24);
        long weeks = days / 7;

        weekText.setText("Week: " + weeks);

        if (weeks <= 12) {
            trimesterText.setText("Trimester: First Trimester");
            tipsText.setText("Eat healthy, stay hydrated, and take your folic acid.");
        } else if (weeks <= 27) {
            trimesterText.setText("Trimester: Second Trimester");
            tipsText.setText("Start feeling the baby's movements and keep up with checkups.");
        } else {
            trimesterText.setText("Trimester: Third Trimester");
            tipsText.setText("Prepare for labor and monitor baby movements closely.");
        }

        // Calculate Due Date (LMP + 280 days)
        Calendar edd = (Calendar) lmp.clone();
        edd.add(Calendar.DAY_OF_YEAR, 280);
        dueDateText.setText("Due Date: " + edd.get(Calendar.DAY_OF_MONTH) + "/" + (edd.get(Calendar.MONTH) + 1) + "/" + edd.get(Calendar.YEAR));
    }

    private void sendEmergencyAlert() {
        SharedPreferences prefs = getSharedPreferences("UserData", MODE_PRIVATE);
        int currentUser = prefs.getInt("current_user", 1);

        String name = prefs.getString("user_" + currentUser + "_name", "User");
        String area = prefs.getString("user_" + currentUser + "_area", "Area");
        String mobile = prefs.getString("user_" + currentUser + "_mobile", "Mobile");

        Log.d("LUNARA_ALERT", "Sending alert for user " + currentUser + ": name=" + name + ", area=" + area + ", mobile=" + mobile);

        // Play feedback sound IMMEDIATELY so user knows button was pressed
        playFeedbackSound();
        Toast.makeText(this, "Sending Emergency Alert...", Toast.LENGTH_SHORT).show();

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("alerts");
        String alertId = ref.push().getKey();

        if (alertId == null) {
            Log.e("LUNARA_ALERT", "Failed to generate alert ID!");
            Toast.makeText(this, "Error: Could not create alert ID", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertModel alert = new AlertModel(name, mobile, area, System.currentTimeMillis());

        ref.child(alertId).setValue(alert)
                .addOnSuccessListener(unused -> {
                    Log.d("LUNARA_ALERT", "Alert sent successfully! ID: " + alertId);
                    showNotification("\uD83D\uDEA8 Emergency Alert Sent", "ASHA Worker has been notified!");
                    Toast.makeText(this, "Emergency Alert Sent Successfully!", Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> {
                    Log.e("LUNARA_ALERT", "Failed to send alert: " + e.getMessage(), e);
                    Toast.makeText(this, "Failed to send alert: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void playFeedbackSound() {
        try {
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
            r.play();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showNotification(String title, String message) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "emergency_channel";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Emergency Alerts", NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL);

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }
}
