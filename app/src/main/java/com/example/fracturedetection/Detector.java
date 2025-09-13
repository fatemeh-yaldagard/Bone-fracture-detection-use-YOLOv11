package com.example.fracturedetection;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.ops.CastOp;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class Detector {
    private final Context context;
    private final String modelPath;
    private final String labelPath;
    private final DetectorListener detectorListener;

    private Interpreter interpreter;
    private final List<String> labels = new ArrayList<>();

    private int tensorWidth = 0;
    private int tensorHeight = 0;
    private int numChannel = 0;
    private int numElements = 0;

    private final ImageProcessor imageProcessor = new ImageProcessor.Builder()
            .add(new NormalizeOp(INPUT_MEAN, INPUT_STANDARD_DEVIATION))
            .add(new CastOp(INPUT_IMAGE_TYPE))
            .build();

    public Detector(Context context, String modelPath, String labelPath, DetectorListener listener) {
        this.context = context;
        this.modelPath = modelPath;
        this.labelPath = labelPath;
        this.detectorListener = listener;

        Interpreter.Options options = new Interpreter.Options();

        // DISABLE GPU delegate because batch size != 1 crashes GPU delegate
        options.setNumThreads(4);

        try {
            MappedByteBuffer model = FileUtil.loadMappedFile(context, modelPath);
            interpreter = new Interpreter(model, options);

            int[] inputShape = interpreter.getInputTensor(0).shape(); // [1, 640, 640, 3]
            tensorHeight = inputShape[1];  // 640
            tensorWidth = inputShape[2];   // 640
            numChannel = inputShape[3];    // 3

            int[] outputShape = interpreter.getOutputTensor(0).shape(); // [1, 5, 8400]
            numChannel = outputShape[1];   // 5
            numElements = outputShape[2];

        } catch (IOException e) {
            throw new RuntimeException("Failed to load model", e);
        }

        // Load labels
        try (InputStream is = context.getAssets().open(labelPath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                labels.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    public void restart(boolean isGpu) {
        interpreter.close();
        Interpreter.Options options = new Interpreter.Options();
        if (isGpu) {
            CompatibilityList compatList = new CompatibilityList();
            if (compatList.isDelegateSupportedOnThisDevice()) {
                options.addDelegate(new GpuDelegate(compatList.getBestOptionsForThisDevice()));
            } else {
                options.setNumThreads(4);
            }
        } else {
            options.setNumThreads(4);
        }
        try {
            MappedByteBuffer model = FileUtil.loadMappedFile(context, modelPath);
            interpreter = new Interpreter(model, options);
        } catch (IOException e) {
            throw new RuntimeException("Failed to reload model", e);
        }
    }

    public void close() {
        interpreter.close();
    }

    public void detect(Bitmap frame) {
        if (tensorWidth == 0 || tensorHeight == 0) return;

        long start = SystemClock.uptimeMillis();

        // Resize and normalize input
        Bitmap resized = Bitmap.createScaledBitmap(frame, tensorWidth, tensorHeight, false);
        TensorImage tensorImage = new TensorImage(INPUT_IMAGE_TYPE);
        tensorImage.load(resized);
        TensorImage processed = imageProcessor.process(tensorImage);

        // New output array for model output: [1, 5, 8400]
        float[][][] output = new float[1][5][8400];
        interpreter.run(processed.getBuffer(), output);

        List<com.example.fracturedetection.BoundingBox> boxes = new ArrayList<>();

        float[][] preds = output[0]; // shape: [5][8400]

        for (int i = 0; i < preds[0].length; i++) {
            float cx = preds[0][i];       // center x (normalized)
            float cy = preds[1][i];       // center y
            float w = preds[2][i];        // width
            float h = preds[3][i];        // height
            float confidence = preds[4][i];  // object confidence

            if (confidence < CONFIDENCE_THRESHOLD) continue;

            // Convert center to corner coordinates
            float x1 = cx - w / 2f;
            float y1 = cy - h / 2f;
            float x2 = cx + w / 2f;
            float y2 = cy + h / 2f;

            // Skip if coordinates are invalid
            if (x1 < 0f || y1 < 0f || x2 > 1f || y2 > 1f) continue;

            String className = labels.isEmpty() ? "object" : labels.get(0);
            int classId = 0;

            boxes.add(new com.example.fracturedetection.BoundingBox(x1, y1, x2, y2, cx, cy, w, h, confidence, classId, className));
        }

        // Apply NMS
        boxes = applyNMS(boxes);

        long inferenceTime = SystemClock.uptimeMillis() - start;

        if (boxes.isEmpty()) {
            detectorListener.onEmptyDetect();
        } else {
            detectorListener.onDetect(boxes, inferenceTime);
        }
    }


    private List<com.example.fracturedetection.BoundingBox> bestBox(float[] array) {
        List<com.example.fracturedetection.BoundingBox> boxes = new ArrayList<>();

        int boxInfo = 4;
        int objIndex = 4;
        int classStart = 5;  // class scores start at index 5

        for (int c = 0; c < numElements; c++) {
            float objectness = array[c + numElements * objIndex];

            if (objectness < CONFIDENCE_THRESHOLD)
                continue;

            float maxClass = 0;
            int classId = -1;
            for (int j = 0; j < labels.size(); j++) {
                float clsScore = array[c + numElements * (classStart + j)];
                if (clsScore > maxClass) {
                    maxClass = clsScore;
                    classId = j;
                }
            }

            float confidence = objectness * maxClass;
            if (confidence < CONFIDENCE_THRESHOLD)
                continue;

            float cx = array[c];                          // center x
            float cy = array[c + numElements];            // center y
            float w  = array[c + numElements * 2];        // width
            float h  = array[c + numElements * 3];        // height

            float x1 = cx - w / 2f;
            float y1 = cy - h / 2f;
            float x2 = cx + w / 2f;
            float y2 = cy + h / 2f;

            if (x1 < 0f || x1 > 1f || y1 < 0f || y1 > 1f ||
                    x2 < 0f || x2 > 1f || y2 < 0f || y2 > 1f) {
                continue;
            }

            String clsName = labels.get(classId);
            boxes.add(new com.example.fracturedetection.BoundingBox(
                    x1, y1, x2, y2,
                    cx, cy, w, h,
                    confidence, classId, clsName
            ));
        }

        return boxes.isEmpty() ? null : applyNMS(boxes);
    }


    private List<com.example.fracturedetection.BoundingBox> applyNMS(List<com.example.fracturedetection.BoundingBox> boxes) {
        List<com.example.fracturedetection.BoundingBox> sorted = new ArrayList<>(boxes);
        sorted.sort((a, b) -> Float.compare(b.getConfidence(), a.getConfidence()));

        List<com.example.fracturedetection.BoundingBox> selected = new ArrayList<>();
        while (!sorted.isEmpty()) {
            com.example.fracturedetection.BoundingBox first = sorted.remove(0);
            selected.add(first);
            Iterator<com.example.fracturedetection.BoundingBox> it = sorted.iterator();
            while (it.hasNext()) {
                com.example.fracturedetection.BoundingBox next = it.next();
                if (calculateIoU(first, next) >= IOU_THRESHOLD) {
                    it.remove();
                }
            }
        }
        return selected;
    }

    private float calculateIoU(com.example.fracturedetection.BoundingBox b1, com.example.fracturedetection.BoundingBox b2) {
        float x1 = Math.max(b1.getX1(), b2.getX1());
        float y1 = Math.max(b1.getY1(), b2.getY1());
        float x2 = Math.min(b1.getX2(), b2.getX2());
        float y2 = Math.min(b1.getY2(), b2.getY2());

        float interArea = Math.max(0f, x2 - x1) * Math.max(0f, y2 - y1);
        float area1 = b1.getWidth() * b1.getHeight();
        float area2 = b2.getWidth() * b2.getHeight();
        return interArea / (area1 + area2 - interArea);
    }

    public interface DetectorListener {
        void onEmptyDetect();
        void onDetect(List<com.example.fracturedetection.BoundingBox> boundingBoxes, long inferenceTime);
    }

    private static final float INPUT_MEAN                   = 0f;
    private static final float INPUT_STANDARD_DEVIATION     = 255f;
    private static final DataType INPUT_IMAGE_TYPE          = DataType.FLOAT32;
    private static final DataType OUTPUT_IMAGE_TYPE         = DataType.FLOAT32;
    private static final float CONFIDENCE_THRESHOLD = 0.4f;  // Lower if needed
    private static final float IOU_THRESHOLD = 0.45f;
}
