package com.example.ad;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.io.File;

public class MainActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private ComicAdapter adapter;
    private DatabaseHelper dbHelper;
    private long userId;
    //private Button profileButton;
    private ImageButton profileButton, settingButton;
    private FloatingActionButton fab; // Ensure type matches XML
    private static final String TAG = "MainActivity";

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        settingButton = findViewById(R.id.settingsButton);
        recyclerView = findViewById(R.id.recyclerView);
        fab = findViewById(R.id.fab); // Correct type: FloatingActionButton
        profileButton = findViewById(R.id.profileButton); // Line 44
        dbHelper = new DatabaseHelper(this);
        userId = getIntent().getLongExtra("user_id", -1);

        if (userId == -1) {
            Log.e(TAG, "Invalid user ID, redirecting to LoginActivity");
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        try {
            List<Comic> comics = dbHelper.getComicsForUser(userId);
            if (comics == null) {
                comics = new ArrayList<>();
                Log.w(TAG, "No comics found for user " + userId);
            }
            adapter = new ComicAdapter(comics);
            recyclerView.setAdapter(adapter);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load comics: " + e.getMessage(), e);
            Toast.makeText(this, "Error loading comics: " + e.getMessage(), Toast.LENGTH_LONG).show();
            adapter = new ComicAdapter(new ArrayList<>());
            recyclerView.setAdapter(adapter);
        }

        fab.setOnClickListener(v -> {
            Intent intent = new Intent(this, CoverPickerActivity.class);
            intent.putExtra("user_id", userId);
            startActivityForResult(intent, 1);
        });

        profileButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, ProfileActivity.class);
            intent.putExtra("user_id", userId);
            startActivity(intent);
        });

        settingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAssistantDialog();
            }
        });
    }

    private void showAssistantDialog() {
        // Создаем диалоговое окно
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_assistant);
        dialog.setTitle("Помощник");

        // Находим TextView и задаем отформатированный текст
        TextView assistantText = dialog.findViewById(R.id.assistantText);
        assistantText.setText(getAssistantDescription());

        // Показываем диалог
        dialog.show();
    }

    private String getAssistantDescription() {
        return "Привет! Я твой помощник в приложении для создания комиксов!\n\n" +
                "Во т что ты можешь делать:\n\n" +
                "  1. Создание нового комикса\n" +
                "  Начни с чистого листа и создай свою историю.\n\n" +
                "  2. Список сохраненных комиксов\n" +
                "  Все твои проекты с миниатюрами на главном экране.\n\n" +
                "  3. Область рисования\n" +
                "  Рисуй в ячейках, создавай сцены и оживляй идеи.\n\n" +
                "  4. Панель инструментов\n" +
                "  Используй маркер, карандаш, заливку, ластик, выбирай цвет и толщину линий.\n\n" +
                "  5. Шаг назад и вперед\n" +
                "  Отменяй (Undo) или возвращай (Redo) свои действия.\n\n" +
                "  6. Добавление ячеек\n" +
                "  Размещай их по сетке (4×4, 6×6) или свободно перетаскивай.\n\n" +
                "  7. Добавление изображений\n" +
                "  Загружай фото прямо из телефона.\n\n" +
                "  8. Текстовые облака\n" +
                "  Добавляй диалоги или мысли персонажей.\n\n" +
                "  9. Новая страница\n" +
                "  Продолжай историю на новой пустой странице.\n\n" +
                " 10. Сохранение\n" +
                " Сохраняй комиксы и возвращайся к ним позже.\n\n" +
                " 11. Просмотр\n" +
                " Листай готовый комикс, как настоящий читатель.\n\n" +
                " 12. Удаление\n" +
                " Убирай ненужные проекты одним нажатием.\n\n" +
                "Готов творить? Я здесь, чтобы помочь!";
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK) {
            try {
                adapter.updateComics(dbHelper.getComicsForUser(userId));
            } catch (Exception e) {
                Log.e(TAG, "Failed to update comics after adding: " + e.getMessage(), e);
                Toast.makeText(this, "Error updating comics: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class ComicAdapter extends RecyclerView.Adapter<ComicAdapter.ViewHolder> {
        private List<Comic> comics;

        public ComicAdapter(List<Comic> comics) {
            this.comics = comics != null ? comics : new ArrayList<>();
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_comic, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            Comic comic = comics.get(position);
            holder.titleTextView.setText(comic.getTitle() != null ? comic.getTitle() : "Untitled");
            String coverPath = comic.getCoverImagePath();
            if (coverPath != null && !coverPath.isEmpty() && new File(coverPath).exists()) {
                try {
                    Bitmap bitmap = BitmapFactory.decodeFile(coverPath);
                    if (bitmap != null) {
                        holder.coverImageView.setImageBitmap(bitmap);
                    } else {
                        holder.coverImageView.setImageResource(android.R.drawable.ic_menu_gallery);
                        Log.w(TAG, "Failed to decode bitmap for cover: " + coverPath);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error loading cover image for comic " + comic.getId() + ": " + e.getMessage(), e);
                    holder.coverImageView.setImageResource(android.R.drawable.ic_menu_gallery);
                }
            } else {
                holder.coverImageView.setImageResource(android.R.drawable.ic_menu_gallery);
            }
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, ComicEditorActivity.class);
                intent.putExtra("comic_id", comic.getId());
                intent.putExtra("user_id", userId);
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to open ComicEditorActivity: " + e.getMessage(), e);
                    Toast.makeText(MainActivity.this, "Failed to open comic: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
            holder.itemView.setOnLongClickListener(v -> {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Delete Comic")
                        .setMessage("Are you sure you want to delete this comic?")
                        .setPositiveButton("Yes", (dialog, which) -> {
                            try {
                                dbHelper.deleteComic(comic.getId(), MainActivity.this);
                                updateComics(dbHelper.getComicsForUser(userId));
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to delete comic: " + e.getMessage(), e);
                                Toast.makeText(MainActivity.this, "Error deleting comic: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("No", null)
                        .show();
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return comics.size();
        }

        public void updateComics(List<Comic> newComics) {
            comics = newComics != null ? newComics : new ArrayList<>();
            notifyDataSetChanged();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView coverImageView;
            TextView titleTextView;

            public ViewHolder(View itemView) {
                super(itemView);
                coverImageView = itemView.findViewById(R.id.coverImageView);
                titleTextView = itemView.findViewById(R.id.titleTextView);
            }
        }
    }
}