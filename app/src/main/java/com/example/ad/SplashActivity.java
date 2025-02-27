package com.example.ad;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {
    private TextView splashText;
    private ImageView splashImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        splashText = findViewById(R.id.splashText);
        splashImage = findViewById(R.id.splashImage);

        // Animate text appearance
        animateText();
    }

    private void animateText() {
        // Start with text invisible
        splashText.setAlpha(0f);
        ObjectAnimator textFadeIn = ObjectAnimator.ofFloat(splashText, "alpha", 0f, 1f);
        textFadeIn.setDuration(2000); // 2 seconds fade-in

        textFadeIn.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // After text appears, animate the image
                animateImage();
            }
        });
        textFadeIn.start();
    }

    private void animateImage() {
        // Start with image invisible
        splashImage.setAlpha(0f);
        ObjectAnimator imageFadeIn = ObjectAnimator.ofFloat(splashImage, "alpha", 0f, 1f);
        imageFadeIn.setDuration(1500); // 1.5 seconds fade-in

        imageFadeIn.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // After image appears, wait briefly then transition to LoginActivity
                splashText.postDelayed(() -> {
                    Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
                    startActivity(intent);
                    finish();
                    // Add a fade transition for smoothness
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                }, 1000); // 1 second delay before transitioning
            }
        });
        imageFadeIn.start();
    }
}