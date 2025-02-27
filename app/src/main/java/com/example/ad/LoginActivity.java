package com.example.ad;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {
    private EditText usernameEditText, passwordEditText;
    private Button loginButton, registerButton;
    private DatabaseHelper dbHelper;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        usernameEditText = findViewById(R.id.usernameEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        loginButton = findViewById(R.id.loginButton);
        registerButton = findViewById(R.id.registerButton);
        dbHelper = new DatabaseHelper(this);
        prefs = getSharedPreferences("ComicSketchPrefs", MODE_PRIVATE);

        // Check if already logged in
        long userId = prefs.getLong("user_id", -1);
        if (userId != -1) {
            startMainActivity(userId);
            return;
        }

        loginButton.setOnClickListener(v -> {
            String username = usernameEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString().trim();

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            User user = dbHelper.loginUser(username, password);
            if (user != null) {
                prefs.edit().putLong("user_id", user.getId()).apply();
                Toast.makeText(this, "Успешный вход", Toast.LENGTH_SHORT).show();
                startMainActivity(user.getId());
            } else {
                Toast.makeText(this, "Ошибка входа", Toast.LENGTH_SHORT).show();
            }
        });

        registerButton.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });
    }

    private void startMainActivity(long userId) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("user_id", userId);
        startActivity(intent);
        finish();
    }
}