package com.example.ad;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "comics.db";
    private static final int DATABASE_VERSION = 2;
    private static final String TAG = "DatabaseHelper";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, email TEXT UNIQUE, password TEXT)");
        db.execSQL("CREATE TABLE comics (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER, title TEXT, cover_image_path TEXT, created_date TEXT, FOREIGN KEY(user_id) REFERENCES users(id))");
        db.execSQL("CREATE TABLE pages (id INTEGER PRIMARY KEY AUTOINCREMENT, comic_id INTEGER, page_number INTEGER, width INTEGER DEFAULT 1200, height INTEGER DEFAULT 1600, FOREIGN KEY(comic_id) REFERENCES comics(id))");
        db.execSQL("CREATE TABLE cells (id INTEGER PRIMARY KEY AUTOINCREMENT, page_id INTEGER, x REAL, y REAL, width REAL, height REAL, drawing_path TEXT, FOREIGN KEY(page_id) REFERENCES pages(id))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE pages ADD COLUMN width INTEGER DEFAULT 1200");
            db.execSQL("ALTER TABLE pages ADD COLUMN height INTEGER DEFAULT 1600");
        }
    }

    public long registerUser(String username, String email, String password) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("username", username);
        values.put("email", email);
        values.put("password", password);
        long id = db.insert("users", null, values);
        db.close();
        return id;
    }

    public User loginUser(String username, String password) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM users WHERE username = ? AND password = ?", new String[]{username, password});
        User user = null;
        if (cursor.moveToFirst()) {
            user = new User();
            user.setId(cursor.getLong(0));
            user.setUsername(cursor.getString(1));
            user.setEmail(cursor.getString(2));
            user.setPassword(cursor.getString(3));
        }
        cursor.close();
        db.close();
        return user;
    }

    public void updateUser(long userId, String username, String email, String password) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("username", username);
        values.put("email", email);
        values.put("password", password);
        db.update("users", values, "id = ?", new String[]{String.valueOf(userId)});
        db.close();
    }

    public long insertComic(long userId, String title, String coverImagePath) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("user_id", userId);
        values.put("title", title);
        values.put("cover_image_path", coverImagePath);
        values.put("created_date", System.currentTimeMillis() + "");
        long id = db.insert("comics", null, values);
        if (id != -1) {
            insertPage(id, 1);
        }
        db.close();
        return id;
    }

    public List<Comic> getComicsForUser(long userId) {
        List<Comic> comics = new ArrayList<>();
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = this.getReadableDatabase();
            cursor = db.rawQuery("SELECT * FROM comics WHERE user_id = ?", new String[]{String.valueOf(userId)});
            if (cursor.moveToFirst()) {
                do {
                    Comic comic = new Comic();
                    comic.setId(cursor.getLong(0));
                    comic.setUserId(cursor.getLong(1));
                    String title = cursor.getString(2);
                    comic.setTitle(title != null ? title : "Untitled");
                    String coverPath = cursor.getString(3);
                    if (coverPath != null && coverPath.length() > 1000) {
                        Log.w(TAG, "Cover image path too long for comic " + comic.getId() + ", truncating");
                        coverPath = coverPath.substring(0, 1000);
                    }
                    comic.setCoverImagePath(coverPath);
                    comics.add(comic);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving comics for user " + userId + ": " + e.getMessage(), e);
        } finally {
            if (cursor != null) cursor.close();
            if (db != null && db.isOpen()) db.close();
        }
        return comics;
    }

    public boolean deleteComic(long comicId, Context context) {
        SQLiteDatabase db = null;
        try {
            db = this.getWritableDatabase();
            db.beginTransaction();
            db.execSQL("DELETE FROM cells WHERE page_id IN (SELECT id FROM pages WHERE comic_id = ?)", new String[]{String.valueOf(comicId)});
            db.execSQL("DELETE FROM pages WHERE comic_id = ?", new String[]{String.valueOf(comicId)});
            int rowsDeleted = db.delete("comics", "id = ?", new String[]{String.valueOf(comicId)});
            if (rowsDeleted > 0) {
                db.setTransactionSuccessful();
            }
            File comicDir = new File(context.getFilesDir(), "comics/comic_" + comicId);
            deleteDirectory(comicDir);
        } catch (Exception e) {
            Log.e(TAG, "Error deleting comic " + comicId + ": " + e.getMessage(), e);
        } finally {
            if (db != null && db.isOpen()) {
                db.endTransaction();
                db.close();
            }
        }
        return false;
    }

    private void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            for (File child : dir.listFiles()) {
                deleteDirectory(child);
            }
        }
        dir.delete();
    }

    public long insertPage(long comicId, int pageNumber) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("comic_id", comicId);
        values.put("page_number", pageNumber);
        long pageId = db.insert("pages", null, values);
        db.close();
        return pageId;
    }

    public List<Page> getPagesForComic(long comicId) {
        List<Page> pages = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM pages WHERE comic_id = ?", new String[]{String.valueOf(comicId)});
        if (cursor.moveToFirst()) {
            do {
                Page page = new Page();
                page.setId(cursor.getLong(0));
                page.setComicId(cursor.getLong(1));
                page.setPageNumber(cursor.getInt(2));
                pages.add(page);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return pages;
    }

    public List<Cell> getCellsForPage(long pageId) {
        List<Cell> cells = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM cells WHERE page_id = ?", new String[]{String.valueOf(pageId)});
        if (cursor.moveToFirst()) {
            do {
                Cell cell = new Cell();
                cell.setId(cursor.getLong(0));
                cell.setPageId(cursor.getLong(1));
                cell.setX(cursor.getFloat(2));
                cell.setY(cursor.getFloat(3));
                cell.setWidth(cursor.getFloat(4));
                cell.setHeight(cursor.getFloat(5));
                cell.setDrawingPath(cursor.getString(6));
                cells.add(cell);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return cells;
    }

    public void updateCellDrawingPath(long cellId, String drawingPath) {
        SQLiteDatabase db = null;
        try {
            db = this.getWritableDatabase();
            if (drawingPath != null && drawingPath.length() > 1000) {
                Log.w(TAG, "Drawing path too long for cell " + cellId + ", truncating");
                drawingPath = drawingPath.substring(0, 1000);
            }
            ContentValues values = new ContentValues();
            values.put("drawing_path", drawingPath);
            db.update("cells", values, "id = ?", new String[]{String.valueOf(cellId)});
        } catch (Exception e) {
            Log.e(TAG, "Failed to update cell drawing path: " + e.getMessage(), e);
            throw e;
        } finally {
            if (db != null && db.isOpen()) {
                db.close();
            }
        }
    }

    public void clearCellDrawing(long cellId, Context context) {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery("SELECT drawing_path FROM cells WHERE id = ?", new String[]{String.valueOf(cellId)});
        if (cursor.moveToFirst() && cursor.getString(0) != null) {
            File file = new File(cursor.getString(0));
            if (file.exists()) file.delete();
        }
        cursor.close();
        ContentValues values = new ContentValues();
        values.put("drawing_path", (String) null);
        db.update("cells", values, "id = ?", new String[]{String.valueOf(cellId)});
        db.close();
    }
}