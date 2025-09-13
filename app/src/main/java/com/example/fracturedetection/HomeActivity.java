package com.example.fracturedetection;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class HomeActivity extends AppCompatActivity {

    private static final int REQUEST_STORAGE_PERMISSION = 101;
    private ImageView imagePreview;
    private Uri selectedImageUri;
    private ActivityResultLauncher<Intent> galleryLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        Button uploadButton = findViewById(R.id.uploadButton);
        Button predictButton = findViewById(R.id.predictButton);
        ImageButton infoButton = findViewById(R.id.infoButton);
        imagePreview = findViewById(R.id.imagePreview);
        Spinner modelSelector = findViewById(R.id.modelSelectorSpinner);

        // Register the gallery launcher
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        selectedImageUri = result.getData().getData();
                        imagePreview.setImageURI(selectedImageUri);
                    }
                });

        uploadButton.setOnClickListener(v -> checkAndRequestPermission());

        predictButton.setOnClickListener(v -> {
            if (selectedImageUri != null) {
                // Get selected model here dynamically
                String selectedModel = modelSelector.getSelectedItem().toString();
                String modelPath;

                switch (selectedModel) {
                    case "YOLOv11n float16":
                        modelPath = com.example.fracturedetection.Constants.MODEL_YOLO11_NANO_16;
                        break;
                    case "YOLOv11n int8":
                        modelPath = com.example.fracturedetection.Constants.MODEL_YOLO11_NANO_8;
                        break;
                    case "YOLOv11n int8 quant":
                        modelPath = com.example.fracturedetection.Constants.MODEL_YOLO11_NANO_8q;
                        break;


                    case "YOLOv11s float16":
                        modelPath = com.example.fracturedetection.Constants.MODEL_YOLO11_SMALL_16;
                        break;
                    case "YOLOv11s int8":
                        modelPath = com.example.fracturedetection.Constants.MODEL_YOLO11_SMALL_8;
                        break;
                    case "YOLOv11s int8 quant":
                        modelPath = com.example.fracturedetection.Constants.MODEL_YOLO11_SMALL_8q;
                        break;

                    default:
                        modelPath = com.example.fracturedetection.Constants.MODEL_YOLO11_NANO_16; // fallback default
                }

                Intent intent = new Intent(HomeActivity.this, ResultActivity.class);
                intent.putExtra("imageUri", selectedImageUri.toString());
                intent.putExtra("modelPath", modelPath); // pass the chosen model file
                startActivity(intent);

            } else {
                Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show();
            }
        });

        infoButton.setOnClickListener(v ->
                Toast.makeText(this, "This app detects bone fractures using a trained model.", Toast.LENGTH_LONG).show()
        );
    }

    private void checkAndRequestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_MEDIA_IMAGES},
                        REQUEST_STORAGE_PERMISSION);
            } else {
                openGallery();
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQUEST_STORAGE_PERMISSION);
            } else {
                openGallery();
            }
        }
    }

    private void openGallery() {
        Intent pickPhoto = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryLauncher.launch(pickPhoto);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openGallery();
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
