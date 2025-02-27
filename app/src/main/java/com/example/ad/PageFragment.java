package com.example.ad;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PageFragment extends Fragment {
    private static final String TAG = "PageFragment";
    private RelativeLayout pageLayout;
    private Button  toggleGridButton;
    private ImageButton refreshPageButton,addCellButton;
    private DatabaseHelper dbHelper;
    private long pageId = -1, comicId = -1;
    private ActivityResultLauncher<Intent> drawingLauncher;
    private float lastTouchX, lastTouchY;
    private ImageView draggedCell;
    private Map<Long, ImageView> cellViews = new HashMap<>();
    private int sheetWidth = 1800; // 6 * 300
    private int sheetHeight = 1800; // 6 * 300
    private static final int CELL_SIZE = 300; // Фиксированный размер ячейки
    private static final int GRID_6X6 = 6;
    private static final int GRID_4X4 = 4;
    private static final int MAX_PATH_LENGTH = 100; // Уменьшен до 100 для большей безопасности
    private boolean isGridMode = true;
    private int RESULT_OK;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        drawingLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Context context = getContext();
                    if (!isAdded() || context == null) {
                        Log.w(TAG, "Фрагмент не прикреплен или контекст недоступен после рисования");
                        return;
                    }
                    if (result.getResultCode() == RESULT_OK) {
                        refreshCells(); // Автоматическое обновление после возврата из DrawingActivity
                    }
                });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_page, container, false);
        pageLayout = view.findViewById(R.id.pageLayout);
        addCellButton = view.findViewById(R.id.addCellButton);
        toggleGridButton = view.findViewById(R.id.toggleGridButton);
        refreshPageButton = view.findViewById(R.id.refreshPageButton);

        Context context = getContext();
        if (context == null) {
            Log.w(TAG, "Начальный контекст недоступен");
            Toast.makeText(getActivity(), "Контекст фрагмента недоступен", Toast.LENGTH_SHORT).show();
            return view;
        }
        dbHelper = new DatabaseHelper(context);
        Bundle args = getArguments();
        if (args == null) {
            Toast.makeText(context, "Недействительные аргументы фрагмента", Toast.LENGTH_SHORT).show();
            return view;
        }
        pageId = args.getLong("page_id", -1);
        comicId = args.getLong("comic_id", -1);
        if (pageId == -1 || comicId == -1) {
            Toast.makeText(context, "Недействительный ID страницы или комикса", Toast.LENGTH_SHORT).show();
            return view;
        }
        Log.d(TAG, "Инициализирован с pageId=" + pageId + ", comicId=" + comicId);

        loadCells();

        addCellButton.setOnClickListener(v -> {
            Context localContext = getContext();
            if (localContext == null) {
                Log.e(TAG, "Контекст недоступен при нажатии на кнопку добавления ячейки");
                Toast.makeText(getActivity(), "Контекст недоступен", Toast.LENGTH_SHORT).show();
                return;
            }
            addNewCell(localContext);
        });

        toggleGridButton.setOnClickListener(v -> {
            isGridMode = !isGridMode;
            toggleGridButton.setText(isGridMode ? "Свободное размещение" : "Сетка");
            refreshCells();
            Toast.makeText(context, isGridMode ? "Включен режим сетки" : "Включено свободное размещение", Toast.LENGTH_SHORT).show();
        });

        refreshPageButton.setOnClickListener(v -> {
            refreshCells();
            Toast.makeText(context, "Страница обновлена", Toast.LENGTH_SHORT).show();
        });

        pageLayout.setOnTouchListener((v, event) -> {
            if (draggedCell != null && !isGridMode) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        lastTouchX = event.getX();
                        lastTouchY = event.getY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getX() - lastTouchX;
                        float dy = event.getY() - lastTouchY;
                        float newX = draggedCell.getX() + dx;
                        float newY = draggedCell.getY() + dy;
                        newX = Math.max(0, Math.min(newX, sheetWidth - CELL_SIZE));
                        newY = Math.max(0, Math.min(newY, sheetHeight - CELL_SIZE));
                        draggedCell.setX(newX);
                        draggedCell.setY(newY);
                        lastTouchX = event.getX();
                        lastTouchY = event.getY();
                        updateCellPosition(draggedCell);
                        break;
                    case MotionEvent.ACTION_UP:
                        draggedCell = null;
                        break;
                }
                return true;
            }
            return false;
        });

        return view;
    }

    private void addNewCell(Context context) {
        List<Cell> cells = dbHelper.getCellsForPage(pageId);
        if (cells == null) {
            Log.e(TAG, "Не удалось получить ячейки для страницы " + pageId);
            Toast.makeText(context, "Не удалось получить ячейки", Toast.LENGTH_SHORT).show();
            return;
        }
        int maxCells = cells.size() < 17 ? 16 : 36;
        if (cells.size() >= maxCells) {
            Toast.makeText(context, "Достигнуто максимальное количество ячеек (" + maxCells + ")", Toast.LENGTH_SHORT).show();
            return;
        }

        float x, y;
        if (isGridMode) {
            int gridSize = cells.size() < 17 ? GRID_4X4 : GRID_6X6;
            int position = cells.size();
            int row = position / gridSize;
            int col = position % gridSize;
            x = col * CELL_SIZE;
            y = row * CELL_SIZE;
        } else {
            x = 0;
            y = 0;
        }

        SQLiteDatabase db = null;
        try {
            db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put("page_id", pageId);
            values.put("x", x);
            values.put("y", y);
            values.put("width", CELL_SIZE);
            values.put("height", CELL_SIZE);
            String drawingPath = getSafeDrawingPath(cells.size());
            values.put("drawing_path", drawingPath);
            long newCellId = db.insert("cells", null, values);
            if (newCellId == -1) {
                Log.e(TAG, "Не удалось добавить новую ячейку для страницы " + pageId);
                Toast.makeText(context, "Не удалось добавить ячейку в базу данных", Toast.LENGTH_SHORT).show();
                return;
            }

            Cell newCell = new Cell();
            newCell.setId(newCellId);
            newCell.setPageId(pageId);
            newCell.setX(x);
            newCell.setY(y);
            newCell.setWidth(CELL_SIZE);
            newCell.setHeight(CELL_SIZE);
            newCell.setDrawingPath(getSafeDrawingPath(newCellId));
            cells.add(newCell);

            ImageView imageView = new ImageView(context);
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(CELL_SIZE, CELL_SIZE);
            params.leftMargin = (int) x;
            params.topMargin = (int) y;
            imageView.setLayoutParams(params);
            imageView.setScaleType(ImageView.ScaleType.FIT_XY);
            imageView.setBackgroundResource(R.drawable.cell_border);
            imageView.setImageResource(android.R.color.white);
            imageView.setOnClickListener(v -> openDrawingActivity(newCell));
            imageView.setOnLongClickListener(v -> {
                if (!isGridMode) {
                    draggedCell = imageView;
                    imageView.setTag(newCell);
                    return true;
                }
                return false;
            });
            pageLayout.addView(imageView);
            cellViews.put(newCellId, imageView);
            Log.d(TAG, "Добавлена ячейка с ID " + newCellId + " на позицию (" + x + ", " + y + ")");

            refreshCells();
        } catch (Exception e) {
            Log.e(TAG, "Ошибка добавления ячейки: " + e.getMessage(), e);
            Toast.makeText(context, "Ошибка добавления ячейки: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            if (db != null && db.isOpen()) {
                db.close();
            }
        }
    }

    private void loadCells() {
        Context context = getContext();
        if (context == null || !isAdded() || getView() == null) {
            Log.w(TAG, "Невозможно загрузить ячейки: фрагмент не прикреплен или вид недоступен");
            return;
        }

        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(sheetWidth, sheetHeight);
        pageLayout.setLayoutParams(layoutParams);

        List<Cell> cells = dbHelper.getCellsForPage(pageId);
        if (cells == null) {
            Log.e(TAG, "Не удалось загрузить ячейки для страницы " + pageId);
            Toast.makeText(context, "Не удалось загрузить ячейки", Toast.LENGTH_SHORT).show();
            return;
        }

        pageLayout.removeAllViews();
        cellViews.clear();

        if (isGridMode) {
            int gridSize = cells.size() < 17 ? GRID_4X4 : GRID_6X6;
            for (int i = 0; i < cells.size(); i++) {
                Cell cell = cells.get(i);
                int row = i / gridSize;
                int col = i % gridSize;
                float x = col * CELL_SIZE;
                float y = row * CELL_SIZE;
                cell.setX(x);
                cell.setY(y);
                updateCellData(cell);
            }
        }

        for (Cell cell : cells) {
            ImageView imageView = new ImageView(context);
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(CELL_SIZE, CELL_SIZE);
            params.leftMargin = (int) cell.getX();
            params.topMargin = (int) cell.getY();
            imageView.setLayoutParams(params);
            imageView.setScaleType(ImageView.ScaleType.FIT_XY);
            imageView.setBackgroundResource(R.drawable.cell_border);

            String path = cell.getDrawingPath();
            if (path != null && !path.isEmpty() && new File(path).exists()) {
                try {
                    Bitmap bitmap = BitmapFactory.decodeFile(path);
                    if (bitmap != null) {
                        bitmap = Bitmap.createScaledBitmap(bitmap, CELL_SIZE, CELL_SIZE, true);
                        imageView.setImageBitmap(bitmap);
                    } else {
                        Log.w(TAG, "Битмап null для ячейки " + cell.getId() + " по пути " + path);
                        imageView.setImageResource(android.R.color.white);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Не удалось загрузить рисунок для ячейки " + cell.getId() + ": " + e.getMessage(), e);
                    imageView.setImageResource(android.R.color.white);
                }
            } else {
                imageView.setImageResource(android.R.color.white);
            }

            imageView.setOnClickListener(v -> openDrawingActivity(cell));
            imageView.setOnLongClickListener(v -> {
                if (!isGridMode) {
                    draggedCell = imageView;
                    imageView.setTag(cell);
                    return true;
                }
                return false;
            });
            pageLayout.addView(imageView);
            cellViews.put(cell.getId(), imageView);
        }
        Log.d(TAG, "Загружено " + cells.size() + " ячеек для страницы " + pageId);
    }

    private void openDrawingActivity(Cell cell) {
        if (!isAdded() || getContext() == null || getActivity() == null) {
            Log.w(TAG, "Нельзя открыть DrawingActivity: фрагмент не прикреплен или контекст/активность недоступны");
            return;
        }
        if (cell == null || cell.getId() == -1) {
            Log.e(TAG, "Недействительная ячейка для перехода к рисованию");
            Toast.makeText(getContext(), "Недействительная ячейка", Toast.LENGTH_SHORT).show();
            return;
        }
        updateCellData(cell);
        String drawingPath = getSafeDrawingPath(cell.getId());
        Intent intent = new Intent(getActivity(), DrawingActivity.class);
        intent.putExtra("cell_id", cell.getId());
        intent.putExtra("drawing_path", drawingPath);
        try {
            drawingLauncher.launch(intent);
            Log.d(TAG, "Успешно запущена DrawingActivity для ячейки " + cell.getId() + " с путем " + drawingPath);
        } catch (Exception e) {
            Log.e(TAG, "Не удалось запустить DrawingActivity для ячейки " + cell.getId() + ": " + e.getMessage(), e);
            Toast.makeText(getContext(), "Ошибка открытия рисования: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String getSafeDrawingPath(long cellId) {
        if (getActivity() == null) {
            Log.w(TAG, "Активность недоступна для создания пути ячейки " + cellId);
            return "";
        }
        // Используем короткий путь: только имя файла без длинного базового пути
        String fileName = "cell_" + cellId + ".png";
        String fullPath = new File(getActivity().getFilesDir(), "comics/" + fileName).getAbsolutePath();
        if (fullPath.length() > MAX_PATH_LENGTH) {
            // Укорачиваем до минимального имени
            fullPath = "c" + cellId + ".png";
            Log.w(TAG, "Путь слишком длинный, используется укороченный путь: " + fullPath);
        }
        return fullPath;
    }

    private void updateCellPosition(ImageView cellView) {
        Cell cell = (Cell) cellView.getTag();
        if (cell == null) return;
        updateCellData(cell, cellView.getX(), cellView.getY(), CELL_SIZE, CELL_SIZE);
    }

    private void updateCellData(Cell cell) {
        updateCellData(cell, cell.getX(), cell.getY(), CELL_SIZE, CELL_SIZE);
    }

    private void updateCellData(Cell cell, float x, float y, float width, float height) {
        if (cell == null || cell.getId() == -1) {
            Log.e(TAG, "Нельзя обновить данные ячейки: недействительная ячейка");
            return;
        }
        SQLiteDatabase db = null;
        try {
            db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put("x", x);
            values.put("y", y);
            values.put("width", width);
            values.put("height", height);
            String drawingPath = getSafeDrawingPath(cell.getId());
            if (drawingPath.length() > MAX_PATH_LENGTH) {
                Log.w(TAG, "Путь к рисунку превышает лимит для ячейки " + cell.getId() + ", обрезается");
                drawingPath = drawingPath.substring(0, MAX_PATH_LENGTH);
            }
            values.put("drawing_path", drawingPath);
            int rows = db.update("cells", values, "id = ?", new String[]{String.valueOf(cell.getId())});
            if (rows > 0) {
                cell.setX(x);
                cell.setY(y);
                cell.setWidth(width);
                cell.setHeight(height);
                cell.setDrawingPath(drawingPath);
                Log.d(TAG, "Обновлены данные ячейки " + cell.getId() + ": x=" + x + ", y=" + y + ", w=" + width + ", h=" + height);
            } else {
                Log.w(TAG, "Не обновлено ни одной строки для ячейки " + cell.getId());
            }
        } catch (Exception e) {
            Log.e(TAG, "Не удалось обновить данные ячейки " + cell.getId() + ": " + e.getMessage(), e);
            if (e.getMessage() != null && e.getMessage().contains("STRING_TOO_LARGE")) {
                Log.e(TAG, "Строка пути слишком длинная для ячейки " + cell.getId());
                Toast.makeText(getContext(), "Ошибка: путь к файлу слишком длинный", Toast.LENGTH_SHORT).show();
            }
        } finally {
            if (db != null && db.isOpen()) {
                db.close();
            }
        }
    }

    public void refreshCells() {
        Context context = getContext();
        if (context == null || !isAdded() || getView() == null) {
            Log.w(TAG, "Нельзя обновить ячейки: фрагмент не прикреплен или вид недоступен");
            return;
        }

        pageLayout.removeAllViews();
        cellViews.clear();

        List<Cell> cells = dbHelper.getCellsForPage(pageId);
        if (cells == null) {
            Log.e(TAG, "Не удалось обновить ячейки для страницы " + pageId + ": база данных вернула null");
            Toast.makeText(context, "Не удалось обновить ячейки", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isGridMode) {
            int gridSize = cells.size() < 17 ? GRID_4X4 : GRID_6X6;
            for (int i = 0; i < cells.size(); i++) {
                Cell cell = cells.get(i);
                int row = i / gridSize;
                int col = i % gridSize;
                float x = col * CELL_SIZE;
                float y = row * CELL_SIZE;
                cell.setX(x);
                cell.setY(y);
                updateCellData(cell);
            }
        }

        for (Cell cell : cells) {
            ImageView imageView = new ImageView(context);
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(CELL_SIZE, CELL_SIZE);
            params.leftMargin = (int) cell.getX();
            params.topMargin = (int) cell.getY();
            imageView.setLayoutParams(params);
            imageView.setScaleType(ImageView.ScaleType.FIT_XY);
            imageView.setBackgroundResource(R.drawable.cell_border);

            String path = cell.getDrawingPath();
            if (path != null && !path.isEmpty() && new File(path).exists()) {
                try {
                    Bitmap bitmap = BitmapFactory.decodeFile(path);
                    if (bitmap != null) {
                        bitmap = Bitmap.createScaledBitmap(bitmap, CELL_SIZE, CELL_SIZE, true);
                        imageView.setImageBitmap(bitmap);
                    } else {
                        Log.w(TAG, "Битмап null для ячейки " + cell.getId() + " по пути " + path);
                        imageView.setImageResource(android.R.color.white);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Не удалось загрузить рисунок для ячейки " + cell.getId() + ": " + e.getMessage(), e);
                    imageView.setImageResource(android.R.color.white);
                }
            } else {
                imageView.setImageResource(android.R.color.white);
            }

            imageView.setOnClickListener(v -> openDrawingActivity(cell));
            imageView.setOnLongClickListener(v -> {
                if (!isGridMode) {
                    draggedCell = imageView;
                    imageView.setTag(cell);
                    return true;
                }
                return false;
            });
            pageLayout.addView(imageView);
            cellViews.put(cell.getId(), imageView);
        }
        Log.d(TAG, "Обновлено " + cells.size() + " ячеек для страницы " + pageId);
    }
}