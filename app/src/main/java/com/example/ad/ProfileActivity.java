package com.example.ad;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class ProfileActivity extends AppCompatActivity {
    private EditText usernameEditText, emailEditText, passwordEditText;
    private Button saveButton, logoutButton, backButton;
    private DatabaseHelper dbHelper;
    private SharedPreferences prefs;
    private long userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        usernameEditText = findViewById(R.id.usernameEditText);
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        saveButton = findViewById(R.id.saveButton);
        logoutButton = findViewById(R.id.logoutButton);
        backButton = findViewById(R.id.backButton);
        dbHelper = new DatabaseHelper(this);
        prefs = getSharedPreferences("ComicSketchPrefs", MODE_PRIVATE);

        userId = getIntent().getLongExtra("user_id", -1);
        if (userId == -1) {
            Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadUserProfile();

        saveButton.setOnClickListener(v -> {
            String username = usernameEditText.getText().toString().trim();
            String email = emailEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString().trim();

            if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            dbHelper.updateUser(userId, username, email, password);
            Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show();
        });

        logoutButton.setOnClickListener(v -> {
            prefs.edit().remove("user_id").apply();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("user_id", userId);
            startActivity(intent);
            finish();
        });
    }

    private void loadUserProfile() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT username, email, password FROM users WHERE id = ?", new String[]{String.valueOf(userId)});
        if (cursor.moveToFirst()) {
            usernameEditText.setText(cursor.getString(0));
            emailEditText.setText(cursor.getString(1));
            passwordEditText.setText(cursor.getString(2));
        }
        cursor.close();
        db.close();
    }
}