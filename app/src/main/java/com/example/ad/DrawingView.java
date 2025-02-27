package com.example.ad;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;

public class DrawingView extends View {
    private static final String TAG = "DrawingView";
    public static final int TOOL_PENCIL = 1;
    public static final int TOOL_ERASER = 2;
    public static final int TOOL_FILL = 3;
    public static final int TOOL_TEXT = 4;

    private Bitmap bitmap;
    private Canvas bitmapCanvas;
    private List<Bitmap> undoStack = new ArrayList<>();
    private List<Bitmap> redoStack = new ArrayList<>();
    private Paint pencilPaint, eraserPaint, textPaint, cloudPaint;
    private Path currentPath;
    private int currentTool = TOOL_PENCIL;
    private int currentColor = Color.BLACK;
    private float textX, textY;
    private OnUndoRedoChangedListener listener;

    private float scaleFactor = 1.0f;
    private float canvasX = 0, canvasY = 0; // Для перемещения
    private static final float MIN_SCALE = 0.5f;
    private static final float MAX_SCALE = 3.0f;
    private boolean isLocked = false;

    public DrawingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        bitmap = Bitmap.createBitmap(900, 900, Bitmap.Config.ARGB_8888);
        bitmapCanvas = new Canvas(bitmap);
        bitmapCanvas.drawColor(Color.WHITE);

        pencilPaint = new Paint();
        pencilPaint.setColor(currentColor);
        pencilPaint.setStyle(Paint.Style.STROKE);
        pencilPaint.setStrokeWidth(5);
        pencilPaint.setAntiAlias(true);

        eraserPaint = new Paint();
        eraserPaint.setColor(Color.WHITE);
        eraserPaint.setStyle(Paint.Style.STROKE);
        eraserPaint.setStrokeWidth(10);
        eraserPaint.setAntiAlias(true);

        textPaint = new Paint();
        textPaint.setColor(currentColor);
        textPaint.setTextSize(20);
        textPaint.setAntiAlias(true);

        cloudPaint = new Paint();
        cloudPaint.setColor(currentColor);
        cloudPaint.setStyle(Paint.Style.STROKE);
        cloudPaint.setStrokeWidth(2);
        cloudPaint.setAntiAlias(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.translate(canvasX, canvasY);
        canvas.scale(scaleFactor, scaleFactor, bitmap.getWidth() / 2f, bitmap.getHeight() / 2f);
        canvas.drawBitmap(bitmap, 0, 0, null);
        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        if (isLocked) {
            return true; // Блокируем взаимодействие с холстом, кроме кнопок
        }

        // Преобразование координат с учетом масштаба и смещения
        float adjustedX = (x - canvasX) / scaleFactor;
        float adjustedY = (y - canvasY) / scaleFactor;

        // Проверка, находятся ли координаты в пределах битмапа
        if (adjustedX < 0 || adjustedX >= bitmap.getWidth() || adjustedY < 0 || adjustedY >= bitmap.getHeight()) {
            return true; // Игнорируем события вне холста
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (currentTool != 0) { // Проверяем, что инструмент выбран
                    saveUndoState();
                    currentPath = new Path();
                    currentPath.moveTo(adjustedX, adjustedY);
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (currentTool != 0 && currentPath != null) {
                    currentPath.lineTo(adjustedX, adjustedY);
                    bitmapCanvas.drawPath(currentPath, currentTool == TOOL_PENCIL ? pencilPaint : eraserPaint);
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (currentTool != 0 && currentPath != null) {
                    currentPath.lineTo(adjustedX, adjustedY);
                    bitmapCanvas.drawPath(currentPath, currentTool == TOOL_PENCIL ? pencilPaint : eraserPaint);
                    currentPath = null;
                    invalidate();
                    notifyUndoRedoChanged();

                    if (currentTool == TOOL_FILL) {
                        saveUndoState();
                        int targetColor = bitmap.getPixel((int) adjustedX, (int) adjustedY);
                        if (targetColor != currentColor) {
                            floodFill((int) adjustedX, (int) adjustedY, targetColor, currentColor);
                            invalidate();
                            notifyUndoRedoChanged();
                        }
                    } else if (currentTool == TOOL_TEXT) {
                        textX = adjustedX;
                        textY = adjustedY;
                        showTextDialog();
                    }
                }
                return true;
        }
        return false;
    }

    private void saveUndoState() {
        undoStack.add(bitmap.copy(Bitmap.Config.ARGB_8888, true));
        redoStack.clear();
        if (undoStack.size() > 10) undoStack.remove(0);
    }

    private void floodFill(int startX, int startY, int targetColor, int replacementColor) {
        if (startX < 0 || startX >= bitmap.getWidth() || startY < 0 || startY >= bitmap.getHeight()) return;
        if (bitmap.getPixel(startX, startY) != targetColor) return;
        Queue<Point> queue = new LinkedList<>();
        queue.add(new Point(startX, startY));
        while (!queue.isEmpty()) {
            Point p = queue.poll();
            if (p.x < 0 || p.x >= bitmap.getWidth() || p.y < 0 || p.y >= bitmap.getHeight()) continue;
            if (bitmap.getPixel(p.x, p.y) != targetColor) continue;
            bitmap.setPixel(p.x, p.y, replacementColor);
            queue.add(new Point(p.x + 1, p.y));
            queue.add(new Point(p.x - 1, p.y));
            queue.add(new Point(p.x, p.y + 1));
            queue.add(new Point(p.x, p.y - 1));
        }
    }

    private void showTextDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Введите текст");
        final EditText input = new EditText(getContext());
        builder.setView(input);
        builder.setPositiveButton("OK", (dialog, which) -> {
            String text = input.getText().toString();
            if (!text.isEmpty()) {
                saveUndoState();
                drawTextWithCloud(text);
                invalidate();
                notifyUndoRedoChanged();
            }
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void drawTextWithCloud(String text) {
        bitmapCanvas.drawText(text, textX, textY, textPaint);

        Rect textBounds = new Rect();
        textPaint.getTextBounds(text, 0, text.length(), textBounds);
        float textWidth = textPaint.measureText(text);
        float textHeight = textBounds.height();
        float padding = 20f;

        float left = textX - padding;
        float top = textY - textHeight - padding;
        float right = textX + textWidth + padding;
        float bottom = textY + padding;

        Random random = new Random();
        int lineCount = 20;
        float maxLength = 30f;
        for (int i = 0; i < lineCount; i++) {
            float startX, startY;
            int side = random.nextInt(4);
            switch (side) {
                case 0: // Top
                    startX = left + random.nextFloat() * (right - left);
                    startY = top;
                    break;
                case 1: // Right
                    startX = right;
                    startY = top + random.nextFloat() * (bottom - top);
                    break;
                case 2: // Bottom
                    startX = left + random.nextFloat() * (right - left);
                    startY = bottom;
                    break;
                case 3: // Left
                    startX = left;
                    startY = top + random.nextFloat() * (bottom - top);
                    break;
                default:
                    continue;
            }

            float angle = random.nextFloat() * 2 * (float) Math.PI;
            float length = random.nextFloat() * maxLength;
            float endX = startX + (float) Math.cos(angle) * length;
            float endY = startY + (float) Math.sin(angle) * length;

            bitmapCanvas.drawLine(startX, startY, endX, endY, cloudPaint);
        }
    }

    public void setTool(int tool) {
        currentTool = tool;
    }

    public void setColor(int color) {
        currentColor = color;
        pencilPaint.setColor(color);
        textPaint.setColor(color);
        cloudPaint.setColor(color);
    }

    public void setToolSize(float size) {
        pencilPaint.setStrokeWidth(size);
        eraserPaint.setStrokeWidth(size * 2);
    }

    public void undo() {
        if (!undoStack.isEmpty()) {
            redoStack.add(bitmap.copy(Bitmap.Config.ARGB_8888, true));
            bitmap = undoStack.remove(undoStack.size() - 1);
            bitmapCanvas = new Canvas(bitmap);
            invalidate();
            notifyUndoRedoChanged();
        }
    }

    public void redo() {
        if (!redoStack.isEmpty()) {
            undoStack.add(bitmap.copy(Bitmap.Config.ARGB_8888, true));
            bitmap = redoStack.remove(redoStack.size() - 1);
            bitmapCanvas = new Canvas(bitmap);
            invalidate();
            notifyUndoRedoChanged();
        }
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    public void zoomIn() {
        scaleFactor += 0.1f;
        if (scaleFactor > MAX_SCALE) scaleFactor = MAX_SCALE;
        invalidate();
    }

    public void zoomOut() {
        scaleFactor -= 0.1f;
        if (scaleFactor < MIN_SCALE) scaleFactor = MIN_SCALE;
        invalidate();
    }

    public void moveCanvas(float dx, float dy) {
        canvasX += dx;
        canvasY += dy;
        invalidate();
    }

    public void clearCanvas() {
        bitmap.eraseColor(Color.WHITE);
        undoStack.clear();
        redoStack.clear();
        invalidate();
        notifyUndoRedoChanged();
    }

    public int getCurrentColor() {
        return currentColor;
    }

    public void setLocked(boolean locked) {
        this.isLocked = locked;
    }

    public interface OnUndoRedoChangedListener {
        void onUndoRedoChanged(boolean canUndo, boolean canRedo);
    }

    public void setOnUndoRedoChangedListener(OnUndoRedoChangedListener listener) {
        this.listener = listener;
    }

    private void notifyUndoRedoChanged() {
        if (listener != null) {
            listener.onUndoRedoChanged(!undoStack.isEmpty(), !redoStack.isEmpty());
        }
    }
}