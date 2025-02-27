package com.example.ad;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import java.util.ArrayList;
import java.util.List;

public class ComicEditorActivity extends AppCompatActivity {
    private ViewPager2 viewPager;

    private ImageButton prevPageButton, nextPageButton,addPageButton;
    private DatabaseHelper dbHelper;
    private long comicId;
    private PageAdapter pageAdapter;
    private List<Page> pages;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_comic_editor);

        viewPager = findViewById(R.id.viewPager);
        addPageButton = findViewById(R.id.addPageButton);
        prevPageButton = findViewById(R.id.prevPageButton);
        nextPageButton = findViewById(R.id.nextPageButton);
        dbHelper = new DatabaseHelper(this);
        comicId = getIntent().getLongExtra("comic_id", -1);

        if (comicId == -1) {
            Toast.makeText(this, "Invalid comic ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        pages = new ArrayList<>();
        try {
            pages.addAll(dbHelper.getPagesForComic(comicId));
            if (pages.isEmpty()) {
                Toast.makeText(this, "No pages found for this comic", Toast.LENGTH_SHORT).show();
                long newPageId = dbHelper.insertPage(comicId, 1);
                if (newPageId != -1) {
                    Page newPage = new Page();
                    newPage.setId(newPageId);
                    newPage.setComicId(comicId);
                    newPage.setPageNumber(1);
                    pages.add(newPage);
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed to load pages: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        pageAdapter = new PageAdapter(this, pages);
        viewPager.setAdapter(pageAdapter);
        updateNavigationButtons();

        addPageButton.setOnClickListener(v -> {
            int newPageNumber = pages.size() + 1;
            long newPageId = dbHelper.insertPage(comicId, newPageNumber);
            if (newPageId != -1) {
                Page newPage = new Page();
                newPage.setId(newPageId);
                newPage.setComicId(comicId);
                newPage.setPageNumber(newPageNumber);
                pages.add(newPage);
                pageAdapter.notifyItemInserted(pages.size() - 1);
                viewPager.setCurrentItem(pages.size() - 1, true);
                updateNavigationButtons();
            } else {
                Toast.makeText(this, "Failed to add page", Toast.LENGTH_SHORT).show();
            }
        });

        prevPageButton.setOnClickListener(v -> {
            int current = viewPager.getCurrentItem();
            if (current > 0) {
                viewPager.setCurrentItem(current - 1, true);
                updateNavigationButtons();
            }
        });

        nextPageButton.setOnClickListener(v -> {
            int current = viewPager.getCurrentItem();
            if (current < pages.size() - 1) {
                viewPager.setCurrentItem(current + 1, true);
                updateNavigationButtons();
            }
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updateNavigationButtons();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 3 && resultCode == RESULT_OK) {
            int currentPosition = viewPager.getCurrentItem();
            Fragment fragment = getSupportFragmentManager().findFragmentByTag("f" + currentPosition);
            if (fragment instanceof PageFragment && fragment.isAdded()) {
                ((PageFragment) fragment).refreshCells();
            }
        }
    }

    private void updateNavigationButtons() {
        int current = viewPager.getCurrentItem();
        prevPageButton.setEnabled(current > 0);
        nextPageButton.setEnabled(current < pages.size() - 1);
    }

    private class PageAdapter extends FragmentStateAdapter {
        private List<Page> pages;

        public PageAdapter(ComicEditorActivity activity, List<Page> pages) {
            super(activity);
            this.pages = pages;
        }

        @Override
        public Fragment createFragment(int position) {
            PageFragment fragment = new PageFragment();
            Bundle args = new Bundle();
            args.putLong("page_id", pages.get(position).getId());
            args.putLong("comic_id", comicId);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public int getItemCount() {
            return pages.size();
        }
    }
}