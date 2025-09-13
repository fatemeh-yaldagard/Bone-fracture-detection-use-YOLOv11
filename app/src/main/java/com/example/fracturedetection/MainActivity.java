package com.example.fracturedetection;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // Make a layout with your welcome message

        new Handler().postDelayed(() -> {
            startActivity(new Intent(com.example.fracturedetection.MainActivity.this, com.example.fracturedetection.HomeActivity.class));
            finish();
        }, 3000); // 3 seconds delay
    }
}
