package com.example.ad;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.skydoves.colorpickerview.ColorEnvelope;
import com.skydoves.colorpickerview.ColorPickerDialog;
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class DrawingActivity extends AppCompatActivity implements DrawingView.OnUndoRedoChangedListener {
    private static final String TAG = "DrawingActivity";
    private DrawingView drawingView;
    private ImageButton pencilButton, eraserButton, fillButton, textButton, undoButton, redoButton,
            colorPickerButton, saveButton, importImageButton, lockDrawingButton;
    private Button zoomInButton, zoomOutButton;
    private SeekBar sizeSeekBar;
    private DatabaseHelper dbHelper;
    private long cellId = -1;
    private String drawingPath;
    private boolean isDrawingModified = false;
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private boolean isDrawingLocked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_drawing);

        // Инициализация UI элементов
        try {
            drawingView = findViewById(R.id.drawingView);
            pencilButton = findViewById(R.id.pensilButton);
            eraserButton = findViewById(R.id.eraserButton);
            fillButton = findViewById(R.id.fillButton);
            textButton = findViewById(R.id.textButton);
            undoButton = findViewById(R.id.undoButton);
            redoButton = findViewById(R.id.redoButton);
            colorPickerButton = findViewById(R.id.colorPickerButton);
            saveButton = findViewById(R.id.saveButton);
            importImageButton = findViewById(R.id.importImageButton);
            sizeSeekBar = findViewById(R.id.sizeSeekBar);
            lockDrawingButton = findViewById(R.id.lockDrawingButton);
            zoomInButton = findViewById(R.id.zoomInButton);
            zoomOutButton = findViewById(R.id.zoomOutButton);
        } catch (Exception e) {
            Log.e(TAG, "Ошибка инициализации UI: " + e.getMessage(), e);
            Toast.makeText(this, "Ошибка интерфейса: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Инициализация базы данных
        try {
            dbHelper = new DatabaseHelper(this);
        } catch (Exception e) {
            Log.e(TAG, "Ошибка инициализации базы данных: " + e.getMessage(), e);
            Toast.makeText(this, "Ошибка базы данных", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Получение данных из Intent
        Intent intent = getIntent();
        if (intent == null) {
            Log.e(TAG, "Intent null");
            Toast.makeText(this, "Недействительные данные Intent", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        cellId = intent.getLongExtra("cell_id", -1);
        drawingPath = intent.getStringExtra("drawing_path");

        // Формируем полный путь из имени файла
        if (drawingPath != null && !drawingPath.startsWith("/")) {
            drawingPath = new File(getFilesDir(), "comics/" + drawingPath).getAbsolutePath();
        }

        Log.d(TAG, "Получено: cell_id=" + cellId + ", drawing_path=" + drawingPath);

        if (cellId == -1 || drawingPath == null || drawingPath.isEmpty()) {
            Log.e(TAG, "Недействительные данные ячейки: cellId=" + cellId + ", drawingPath=" + drawingPath);
            Toast.makeText(this, "Недействительные данные ячейки", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        File comicsDir = new File(getFilesDir(), "comics");
        if (!comicsDir.exists() && !comicsDir.mkdirs()) {
            Log.e(TAG, "Не удалось создать директорию comics для ячейки " + cellId);
            Toast.makeText(this, "Ошибка создания директории", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        try {
            dbHelper.updateCellDrawingPath(cellId, new File(drawingPath).getName());
            Log.d(TAG, "Обновлен путь к рисунку для ячейки " + cellId);
        } catch (Exception e) {
            Log.e(TAG, "Ошибка базы данных при инициализации ячейки " + cellId + ": " + e.getMessage(), e);
            Toast.makeText(this, "Не удалось обновить путь в базе данных", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        if (imageUri != null) {
                            loadImageFromUri(imageUri);
                        }
                    }
                });

        loadDrawing();
        setupListeners();
    }

    private void setupListeners() {
        drawingView.setOnUndoRedoChangedListener(this);

        pencilButton.setOnClickListener(v -> {
            if (!isDrawingLocked) {
                drawingView.setTool(DrawingView.TOOL_PENCIL);
                isDrawingModified = true;
            }
        });
        eraserButton.setOnClickListener(v -> {
            if (!isDrawingLocked) {
                drawingView.setTool(DrawingView.TOOL_ERASER);
                isDrawingModified = true;
            }
        });
        fillButton.setOnClickListener(v -> {
            if (!isDrawingLocked) {
                drawingView.setTool(DrawingView.TOOL_FILL);
                isDrawingModified = true;
            }
        });
        textButton.setOnClickListener(v -> {
            if (!isDrawingLocked) {
                drawingView.setTool(DrawingView.TOOL_TEXT);
                isDrawingModified = true;
            }
        });
        undoButton.setOnClickListener(v -> drawingView.undo());
        redoButton.setOnClickListener(v -> drawingView.redo());
        colorPickerButton.setOnClickListener(v -> showColorPicker());
        saveButton.setOnClickListener(v -> saveDrawingAndExit());
        importImageButton.setOnClickListener(v -> importImage());
        lockDrawingButton.setOnClickListener(v -> {
            isDrawingLocked = !isDrawingLocked;
            drawingView.setLocked(isDrawingLocked);
            Toast.makeText(this, isDrawingLocked ? "Рисование заблокировано" : "Рисование разблокировано", Toast.LENGTH_SHORT).show();
        });
        zoomInButton.setOnClickListener(v -> drawingView.zoomIn());
        zoomOutButton.setOnClickListener(v -> drawingView.zoomOut());

        sizeSeekBar.setMax(49);
        sizeSeekBar.setProgress(4);
        sizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                drawingView.setToolSize(progress + 1);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Джойстик
        findViewById(R.id.joystickUp).setOnClickListener(v -> drawingView.moveCanvas(0, -50));
        findViewById(R.id.joystickDown).setOnClickListener(v -> drawingView.moveCanvas(0, 50));
        findViewById(R.id.joystickLeft).setOnClickListener(v -> drawingView.moveCanvas(-50, 0));
        findViewById(R.id.joystickRight).setOnClickListener(v -> drawingView.moveCanvas(50, 0));
    }

    private void loadDrawing() {
        File drawingFile = new File(drawingPath);
        Bitmap targetBitmap = drawingView.getBitmap();
        if (targetBitmap == null) {
            Log.e(TAG, "Целевой битмап null для ячейки " + cellId);
            Toast.makeText(this, "Ошибка холста", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (drawingFile.exists()) {
            try {
                Bitmap loadedBitmap = BitmapFactory.decodeFile(drawingPath);
                if (loadedBitmap == null) {
                    Log.w(TAG, "Не удалось декодировать файл: " + drawingPath + " для ячейки " + cellId);
                    targetBitmap.eraseColor(Color.WHITE);
                } else {
                    loadedBitmap = Bitmap.createScaledBitmap(loadedBitmap,
                            targetBitmap.getWidth(), targetBitmap.getHeight(), true);
                    Canvas canvas = new Canvas(targetBitmap);
                    canvas.drawColor(Color.WHITE);
                    canvas.drawBitmap(loadedBitmap, 0, 0, null);
                    Log.d(TAG, "Загружен рисунок из " + drawingPath + " для ячейки " + cellId);
                }
            } catch (Exception e) {
                Log.e(TAG, "Ошибка загрузки рисунка для ячейки " + cellId + ": " + e.getMessage(), e);
                targetBitmap.eraseColor(Color.WHITE);
            }
        } else {
            targetBitmap.eraseColor(Color.WHITE);
            Log.d(TAG, "Инициализирован новый холст по пути " + drawingPath + " для ячейки " + cellId);
        }
        drawingView.invalidate();
    }

    private void showColorPicker() {
        new ColorPickerDialog.Builder(this)
                .setTitle("Выберите цвет")
                .setPreferenceName("MyColorPickerDialog")
                .setPositiveButton("OK", new ColorEnvelopeListener() {
                    @Override
                    public void onColorSelected(ColorEnvelope envelope, boolean fromUser) {
                        int selectedColor = envelope.getColor();
                        drawingView.setColor(selectedColor);
                        Log.d(TAG, "Выбран цвет ARGB(" + Color.alpha(selectedColor) + ", " +
                                Color.red(selectedColor) + ", " + Color.green(selectedColor) + ", " +
                                Color.blue(selectedColor) + ") для ячейки " + cellId);
                    }
                })
                .setNegativeButton("Отмена", (dialogInterface, i) -> dialogInterface.dismiss())
                .attachAlphaSlideBar(true)
                .attachBrightnessSlideBar(true)
                .setBottomSpace(10)
                .show();
    }

    private void saveDrawingAndExit() {
        File file = new File(drawingPath);
        try {
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
                throw new IOException("Не удалось создать директорию: " + parentDir.getAbsolutePath());
            }
            Bitmap bitmap = drawingView.getBitmap();
            if (bitmap == null) {
                throw new IllegalStateException("Битмап null для ячейки " + cellId);
            }
            try (FileOutputStream os = new FileOutputStream(file)) {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)) {
                    throw new IOException("Не удалось сжать битмап");
                }
                os.flush();
            }
            dbHelper.updateCellDrawingPath(cellId, file.getName());
            Log.d(TAG, "Рисунок сохранен по пути " + drawingPath + " для ячейки " + cellId);
            Toast.makeText(this, "Рисунок сохранен", Toast.LENGTH_SHORT).show();
            isDrawingModified = false;
            setResult(RESULT_OK);
            finish();
        } catch (IOException e) {
            Log.e(TAG, "Ошибка сохранения для ячейки " + cellId + ": " + e.getMessage(), e);
            Toast.makeText(this, "Не удалось сохранить: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            if (file.exists()) {
                file.delete();
                Log.d(TAG, "Удален поврежденный файл: " + drawingPath);
            }
        } catch (Exception e) {
            Log.e(TAG, "Неожиданная ошибка сохранения для ячейки " + cellId + ": " + e.getMessage(), e);
            Toast.makeText(this, "Ошибка сохранения", Toast.LENGTH_SHORT).show();
        }
    }

    private void importImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        try {
            imagePickerLauncher.launch(intent);
            Log.d(TAG, "Запущен выбор изображения для ячейки " + cellId);
        } catch (Exception e) {
            Log.e(TAG, "Ошибка запуска выбора изображения для ячейки " + cellId + ": " + e.getMessage(), e);
            Toast.makeText(this, "Ошибка выбора изображения", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadImageFromUri(Uri imageUri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            if (inputStream == null) {
                Log.w(TAG, "Не удалось открыть поток для изображения для ячейки " + cellId);
                Toast.makeText(this, "Не удалось загрузить изображение", Toast.LENGTH_SHORT).show();
                return;
            }
            Bitmap loadedBitmap = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            if (loadedBitmap == null) {
                Log.w(TAG, "Не удалось декодировать изображение для ячейки " + cellId);
                Toast.makeText(this, "Не удалось декодировать изображение", Toast.LENGTH_SHORT).show();
                return;
            }

            Bitmap targetBitmap = drawingView.getBitmap();
            if (targetBitmap == null) {
                Log.e(TAG, "Целевой битмап null для ячейки " + cellId);
                Toast.makeText(this, "Ошибка холста", Toast.LENGTH_SHORT).show();
                return;
            }

            loadedBitmap = Bitmap.createScaledBitmap(loadedBitmap, targetBitmap.getWidth(), targetBitmap.getHeight(), true);
            Canvas canvas = new Canvas(targetBitmap);
            canvas.drawColor(Color.WHITE);
            canvas.drawBitmap(loadedBitmap, 0, 0, null);
            drawingView.invalidate();
            isDrawingModified = true;
            Log.d(TAG, "Изображение загружено из файловой системы для ячейки " + cellId);
            Toast.makeText(this, "Изображение загружено", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Log.e(TAG, "Ошибка загрузки изображения из Uri для ячейки " + cellId + ": " + e.getMessage(), e);
            Toast.makeText(this, "Ошибка загрузки изображения: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (isDrawingModified) {
            new AlertDialog.Builder(this)
                    .setTitle("Сохранить изменения")
                    .setMessage("Сохранить рисунок перед выходом?")
                    .setPositiveButton("Да", (dialog, which) -> saveDrawingAndExit())
                    .setNegativeButton("Нет", (dialog, which) -> {
                        setResult(RESULT_CANCELED);
                        super.onBackPressed();
                    })
                    .setNeutralButton("Отмена", null)
                    .setOnCancelListener(dialog -> Log.d(TAG, "Отмена выхода для ячейки " + cellId))
                    .show();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onUndoRedoChanged(boolean canUndo, boolean canRedo) {
        undoButton.setEnabled(canUndo);
        redoButton.setEnabled(canRedo);
        isDrawingModified = true;
    }
}