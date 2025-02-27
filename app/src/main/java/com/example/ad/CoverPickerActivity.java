package com.example.ad;

import android.content.ContentValues;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;

public class CoverPickerActivity extends AppCompatActivity {
    private EditText titleEditText;
    private ImageView coverImageView;
    private Button pickImageButton, saveButton;
    private Uri coverUri;
    private DatabaseHelper dbHelper;
    private long userId;
    private static final int PICK_IMAGE_REQUEST = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cover_picker);

        titleEditText = findViewById(R.id.titleEditText);
        coverImageView = findViewById(R.id.coverImageView);
        pickImageButton = findViewById(R.id.pickImageButton);
        saveButton = findViewById(R.id.saveButton);
        dbHelper = new DatabaseHelper(this);

        userId = getIntent().getLongExtra("user_id", -1);
        if (userId == -1) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        pickImageButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, PICK_IMAGE_REQUEST);
        });

        saveButton.setOnClickListener(v -> saveComic());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            coverUri = data.getData();
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), coverUri);
                coverImageView.setImageBitmap(bitmap); // Display as-is, no scaling here
            } catch (IOException e) {
                Toast.makeText(this, "Failed to load image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void saveComic() {
        String title = titleEditText.getText().toString().trim();
        if (title.isEmpty()) {
            Toast.makeText(this, "Please enter a title", Toast.LENGTH_SHORT).show();
            return;
        }
        if (coverUri == null) {
            Toast.makeText(this, "Please select a cover image", Toast.LENGTH_SHORT).show();
            return;
        }

        long comicId = dbHelper.insertComic(userId, title, "");
        if (comicId == -1) {
            Toast.makeText(this, "Failed to create comic", Toast.LENGTH_SHORT).show();
            return;
        }

        File comicDir = new File(getFilesDir(), "comics/comic_" + comicId);
        if (!comicDir.mkdirs() && !comicDir.exists()) {
            Toast.makeText(this, "Failed to create comic directory", Toast.LENGTH_SHORT).show();
            dbHelper.deleteComic(comicId, this);
            return;
        }

        File coverFile = new File(comicDir, "cover.jpg");
        try {
            InputStream is = getContentResolver().openInputStream(coverUri);
            FileOutputStream os = new FileOutputStream(coverFile);
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.flush();
            os.close();

            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put("cover_image_path", coverFile.getAbsolutePath());
            int rowsUpdated = db.update("comics", values, "id = ?", new String[]{String.valueOf(comicId)});
            db.close();

            if (rowsUpdated > 0) {
                Toast.makeText(this, "Комикс создан успешно!", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            } else {
                Toast.makeText(this, "Failed to update comic cover path", Toast.LENGTH_SHORT).show();
                if (!dbHelper.deleteComic(comicId, this)) {
                    Toast.makeText(this, "Cleanup failed, comic may remain in database", Toast.LENGTH_LONG).show();
                }
                if (coverFile.exists()) coverFile.delete();
            }
        } catch (IOException e) {
            Toast.makeText(this, "Failed to save cover image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            if (!dbHelper.deleteComic(comicId, this)) {
                Toast.makeText(this, "Cleanup failed, comic may remain in database", Toast.LENGTH_LONG).show();
            }
            if (coverFile.exists()) coverFile.delete();
        }
    }
}