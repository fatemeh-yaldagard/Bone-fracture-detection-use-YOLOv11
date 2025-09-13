package com.example.fracturedetection;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // بدون setContentView چون فقط می‌خوایم سریع رد بشه
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        boolean isLoggedIn = prefs.getBoolean("isLoggedIn", false);

        Intent intent = isLoggedIn
                ? new Intent(SplashActivity.this, HomeActivity.class)
                : new Intent(SplashActivity.this, MainActivity.class);

        startActivity(intent);
        finish();
    }
}
