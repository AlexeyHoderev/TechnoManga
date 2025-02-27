package com.example.ad;

public class Cell {
    private long id;
    private long pageId;
    private float x, y, width, height;
    private String drawingPath;

    // Getters and setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getPageId() { return pageId; }
    public void setPageId(long pageId) { this.pageId = pageId; }
    public float getX() { return x; }
    public void setX(float x) { this.x = x; }
    public float getY() { return y; }
    public void setY(float y) { this.y = y; }
    public float getWidth() { return width; }
    public void setWidth(float width) { this.width = width; }
    public float getHeight() { return height; }
    public void setHeight(float height) { this.height = height; }
    public String getDrawingPath() { return drawingPath; }
    public void setDrawingPath(String drawingPath) { this.drawingPath = drawingPath; }
}