package com.example.fracturedetection;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class OverlayView extends View {

    private List<com.example.fracturedetection.BoundingBox> results = new ArrayList<>();
    private final Paint boxPaint = new Paint();
    private final Paint textBackgroundPaint = new Paint();
    private final Paint textPaint = new Paint();
    private final Rect bounds = new Rect();

    private static final int BOUNDING_RECT_TEXT_PADDING = 8;

    // Sizes for coordinate mapping
    private int bitmapWidth = 1;
    private int bitmapHeight = 1;
    private int imageViewWidth = 1;
    private int imageViewHeight = 1;

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPaints();
    }

    public void clear() {
        results.clear();
        invalidate();
    }

    private void initPaints() {
        textBackgroundPaint.setColor(Color.BLACK);
        textBackgroundPaint.setStyle(Paint.Style.FILL);
        textBackgroundPaint.setAlpha(160); // semi-transparent background for readability
        textBackgroundPaint.setTextSize(30f);

        textPaint.setColor(Color.WHITE);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextSize(30f);

        boxPaint.setColor(ContextCompat.getColor(getContext(), R.color.bounding_box_color));
        boxPaint.setStrokeWidth(8f);
        boxPaint.setStyle(Paint.Style.STROKE);
    }

    public void setBitmapSize(int width, int height) {
        bitmapWidth = width;
        bitmapHeight = height;
    }

    public void setImageViewSize(int width, int height) {
        imageViewWidth = width;
        imageViewHeight = height;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (results == null || results.isEmpty()) return;

        // Calculate scale and padding for fitCenter scaling
        float scaleX = (float) imageViewWidth / bitmapWidth;
        float scaleY = (float) imageViewHeight / bitmapHeight;
        float scale;
        float dx = 0, dy = 0;

        if (scaleX < scaleY) {
            scale = scaleX;
            dy = (imageViewHeight - bitmapHeight * scale) / 2f;
        } else {
            scale = scaleY;
            dx = (imageViewWidth - bitmapWidth * scale) / 2f;
        }

        for (com.example.fracturedetection.BoundingBox box : results) {
            // Coordinates are normalized [0..1], map to bitmap pixel, then scale + offset to ImageView display coords
            float left = box.getX1() * bitmapWidth * scale + dx;
            float top = box.getY1() * bitmapHeight * scale + dy;
            float right = box.getX2() * bitmapWidth * scale + dx;
            float bottom = box.getY2() * bitmapHeight * scale + dy;

            // Draw bounding box rectangle
            canvas.drawRect(left, top, right, bottom, boxPaint);

            // Draw label background rectangle with padding
            String label = box.getClsName() + String.format(" %.2f", box.getConfidence());
            textBackgroundPaint.getTextBounds(label, 0, label.length(), bounds);
            int textWidth = bounds.width() + BOUNDING_RECT_TEXT_PADDING * 2;
            int textHeight = bounds.height() + BOUNDING_RECT_TEXT_PADDING * 2;

            // Draw background rect behind text (above the top-left corner of the box)
            canvas.drawRect(
                    left,
                    top - textHeight,
                    left + textWidth,
                    top,
                    textBackgroundPaint
            );

            // Draw label text (offset by padding)
            canvas.drawText(label, left + BOUNDING_RECT_TEXT_PADDING, top - BOUNDING_RECT_TEXT_PADDING, textPaint);
        }
    }

    public void setResults(List<com.example.fracturedetection.BoundingBox> boundingBoxes) {
        this.results = boundingBoxes;
        invalidate();
    }
}
