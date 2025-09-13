package com.example.fracturedetection;

public class BoundingBox {
    private final float x1;
    private final float y1;
    private final float x2;
    private final float y2;
    private final float cx;
    private final float cy;
    private final float width;
    private final float height;
    private final float confidence;
    private final int classIndex;
    private final String clsName;

    public BoundingBox(
            float x1,
            float y1,
            float x2,
            float y2,
            float cx,
            float cy,
            float width,
            float height,
            float confidence,
            int classIndex,
            String clsName
    ) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.cx = cx;
        this.cy = cy;
        this.width = width;
        this.height = height;
        this.confidence = confidence;
        this.classIndex = classIndex;
        this.clsName = clsName;
    }

    // Getters, use these in Detector.java instead of direct field access
    public float getX1() { return x1; }
    public float getY1() { return y1; }
    public float getX2() { return x2; }
    public float getY2() { return y2; }
    public float getCx() { return cx; }
    public float getCy() { return cy; }
    public float getWidth() { return width; }
    public float getHeight() { return height; }
    public float getConfidence() { return confidence; }
    public int getClassIndex() { return classIndex; }
    public String getClsName() { return clsName; }
}
