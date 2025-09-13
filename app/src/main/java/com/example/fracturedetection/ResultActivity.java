package com.example.fracturedetection;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ResultActivity extends AppCompatActivity implements com.example.fracturedetection.Detector.DetectorListener {

    private ImageView imageView;
    private com.example.fracturedetection.OverlayView overlay;
    private TextView inferenceTime;

    private com.example.fracturedetection.Detector detector;
    private ExecutorService executor;

    private static final String TAG = "ResultActivity";

    private static final int MODEL_INPUT_SIZE = 640; // model input size (e.g. 640x640)

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        imageView = findViewById(R.id.imageView);
        overlay = findViewById(R.id.overlay);
        inferenceTime = findViewById(R.id.inferenceTime);

        executor = Executors.newSingleThreadExecutor();

        Uri imageUri = null;
        if (getIntent() != null && getIntent().hasExtra("imageUri")) {
            imageUri = Uri.parse(getIntent().getStringExtra("imageUri"));
        }

        if (imageUri == null) {
            Log.e(TAG, "No image URI passed!");
            Toast.makeText(this, "No image provided.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Bitmap bitmap = loadBitmapFromUri(imageUri);
        if (bitmap == null) {
            Log.e(TAG, "Failed to load bitmap from URI!");
            Toast.makeText(this, "Failed to load image.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        imageView.setImageBitmap(bitmap);

        // Setup overlay sizes for proper bounding box scaling
        overlay.setBitmapSize(bitmap.getWidth(), bitmap.getHeight());

        imageView.post(() -> {
            int ivWidth = imageView.getWidth();
            int ivHeight = imageView.getHeight();

            overlay.setImageViewSize(ivWidth, ivHeight);
            overlay.invalidate();
        });

        Bitmap resizedBitmap = resizeBitmap(bitmap, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE);

        // Get model path passed from intent
        String modelPath = getIntent().getStringExtra("modelPath");
        if (modelPath == null || modelPath.isEmpty()) {
            Toast.makeText(this, "Model path not provided.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        final String finalModelPath = modelPath;

        executor.execute(() -> {
            detector = new com.example.fracturedetection.Detector(getBaseContext(), finalModelPath, com.example.fracturedetection.Constants.LABELS_PATH, ResultActivity.this);
            detector.detect(resizedBitmap);
        });
    }

    private Bitmap resizeBitmap(Bitmap bitmap, int targetWidth, int targetHeight) {
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
    }

    private Bitmap loadBitmapFromUri(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (inputStream != null) inputStream.close();
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "Error loading bitmap", e);
            return null;
        }
    }

    @Override
    public void onEmptyDetect() {
        Log.d(TAG, "No detection results.");
        runOnUiThread(() -> {
            overlay.clear();
            inferenceTime.setText("No detection");
        });
    }

    @Override
    public void onDetect(List<com.example.fracturedetection.BoundingBox> boundingBoxes, long inferenceTimeMs) {
        Log.d(TAG, "Detection results: " + boundingBoxes.size());
        for (com.example.fracturedetection.BoundingBox box : boundingBoxes) {
            Log.d(TAG, "Box: left=" + box.getX1() + ", top=" + box.getY1() + ", right=" + box.getX2() + ", bottom=" + box.getY2());
        }
        runOnUiThread(() -> {
            inferenceTime.setText("Inference time: " + inferenceTimeMs + " ms");
            overlay.setResults(boundingBoxes);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (detector != null) {
            detector.close();
        }
        executor.shutdown();
    }
}
