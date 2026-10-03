package com.ftiktokmanager.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class DbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "f_clones.db";
    private static final int DB_VER = 2;

    private static final String ACC_COLS = "_id, name, cookies, vcam, vcamon, last_opened";

    public DbHelper(Context context) {
        super(context, DB_NAME, null, DB_VER);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE accounts (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT, " +
                "cookies TEXT DEFAULT '{}', " +
                "vcam TEXT DEFAULT '', " +
                "vcamon INTEGER DEFAULT 0, " +
                "last_opened INTEGER DEFAULT 0)");
        createLinks(db);
        seedLinks(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        // Never drop user data on upgrade - migrate step by step.
        if (oldV < 2) {
            db.execSQL("ALTER TABLE accounts ADD COLUMN last_opened INTEGER DEFAULT 0");
            createLinks(db);
            seedLinks(db);
        }
    }

    private void createLinks(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS links (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "title TEXT, " +
                "url TEXT)");
    }

    private void seedLinks(SQLiteDatabase db) {
        insertLink(db, "TikTok Home", "https://www.tiktok.com/");
        insertLink(db, "TikTok Login", "https://www.tiktok.com/login");
        insertLink(db, "TikTok Studio", "https://www.tiktok.com/tiktokstudio");
    }

    private void insertLink(SQLiteDatabase db, String title, String url) {
        ContentValues cv = new ContentValues();
        cv.put("title", title);
        cv.put("url", url);
        db.insert("links", null, cv);
    }

    // ------------------------------------------------------------ accounts

    public long addAccount(String name) {
        return addAccount(name, "");
    }

    public long addAccount(String name, String cookies) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        if (cookies != null && !cookies.isEmpty()) cv.put("cookies", cookies);
        return db.insert("accounts", null, cv);
    }

    private CloneModel read(Cursor c) {
        return new CloneModel(c.getInt(0), c.getString(1), c.getString(2),
                c.getString(3), c.getInt(4), c.getLong(5));
    }

    public List<CloneModel> getAllAccounts() {
        List<CloneModel> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT " + ACC_COLS + " FROM accounts ORDER BY _id ASC", null);
        try {
            while (cursor.moveToNext()) list.add(read(cursor));
        } finally {
            cursor.close();
        }
        return list;
    }

    public CloneModel getAccount(int id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT " + ACC_COLS + " FROM accounts WHERE _id=?",
                new String[]{String.valueOf(id)});
        try {
            return cursor.moveToFirst() ? read(cursor) : null;
        } finally {
            cursor.close();
        }
    }

    public void updateCookies(int id, String cookies) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("cookies", cookies);
        db.update("accounts", cv, "_id=?", new String[]{String.valueOf(id)});
    }

    public void clearCookies(int id) {
        updateCookies(id, "");
    }

    public void updateVcam(int id, String path, int on) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("vcam", path);
        cv.put("vcamon", on);
        db.update("accounts", cv, "_id=?", new String[]{String.valueOf(id)});
    }

    public void touchAccount(int id) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("last_opened", System.currentTimeMillis());
        db.update("accounts", cv, "_id=?", new String[]{String.valueOf(id)});
    }

    public void renameAccount(int id, String name) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        db.update("accounts", cv, "_id=?", new String[]{String.valueOf(id)});
    }

    public void deleteAccount(int id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("accounts", "_id=?", new String[]{String.valueOf(id)});
    }

    // --------------------------------------------------------------- links

    public List<LinkModel> getLinks() {
        List<LinkModel> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT _id, title, url FROM links ORDER BY _id ASC", null);
        try {
            while (c.moveToNext()) list.add(new LinkModel(c.getInt(0), c.getString(1), c.getString(2)));
        } finally {
            c.close();
        }
        return list;
    }

    public boolean hasLink(String url) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT 1 FROM links WHERE url=? LIMIT 1", new String[]{url});
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    public void addLink(String title, String url) {
        insertLink(getWritableDatabase(), title, url);
    }

    public void deleteLink(int id) {
        getWritableDatabase().delete("links", "_id=?", new String[]{String.valueOf(id)});
    }
}
