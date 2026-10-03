package com.ftiktokmanager.app;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class DbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "f_clones.db";
    private static final int DB_VER = 3;

    private static final String ACC_COLS = "_id, name, cookies, vcam, vcamon, last_opened, "
            + "notes, email, username, password, desk, pxon, px_host, px_port, px_user, px_pass";

    private final Context ctx;

    public DbHelper(Context context) {
        super(context, DB_NAME, null, DB_VER);
        this.ctx = context.getApplicationContext();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE accounts (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT, " +
                "cookies TEXT DEFAULT '{}', " +
                "vcam TEXT DEFAULT '', " +
                "vcamon INTEGER DEFAULT 0, " +
                "last_opened INTEGER DEFAULT 0, " +
                "notes TEXT DEFAULT '', " +
                "email TEXT DEFAULT '', " +
                "username TEXT DEFAULT '', " +
                "password TEXT DEFAULT '', " +
                "desk INTEGER DEFAULT 0, " +
                "pxon INTEGER DEFAULT 0, " +
                "px_host TEXT DEFAULT '', " +
                "px_port INTEGER DEFAULT 0, " +
                "px_user TEXT DEFAULT '', " +
                "px_pass TEXT DEFAULT '')");
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
        if (oldV < 3) {
            String[] cols = {
                    "notes TEXT DEFAULT ''", "email TEXT DEFAULT ''", "username TEXT DEFAULT ''",
                    "password TEXT DEFAULT ''", "desk INTEGER DEFAULT 0", "pxon INTEGER DEFAULT 0",
                    "px_host TEXT DEFAULT ''", "px_port INTEGER DEFAULT 0",
                    "px_user TEXT DEFAULT ''", "px_pass TEXT DEFAULT ''"};
            for (String c : cols) {
                try {
                    db.execSQL("ALTER TABLE accounts ADD COLUMN " + c);
                } catch (Exception ignored) {
                }
            }
            seedExtraLinks(db);
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
        seedExtraLinks(db);
    }

    private static final String[][] EXTRA_LINKS = {
            {"🎬 TikTok Studio Upload", "https://www.tiktok.com/tiktokstudio/upload"},
            {"💰 Payout Dashboard", "https://www.tiktok.com/periodic/dashboard"},
            {"💵 Payout / Monthly Earning", "https://www.tiktok.com/reward-onboarding?wallet_type=MONTHLY_EARNING&click_entrance=monthly_earnings_page"},
            {"🧾 Tax Info", "https://www.tiktok.com/tax/us-w9-tax-select?type"},
            {"📊 Tax Status", "https://www.tiktok.com/tax/info-us?enter_from"},
            {"⚠️ Report a Problem", "https://www.tiktok.com/legal/report/feedback"}
    };

    private void seedExtraLinks(SQLiteDatabase db) {
        for (String[] l : EXTRA_LINKS) {
            Cursor c = db.rawQuery("SELECT 1 FROM links WHERE url=? LIMIT 1", new String[]{l[1]});
            boolean exists;
            try {
                exists = c.moveToFirst();
            } finally {
                c.close();
            }
            if (!exists) insertLink(db, l[0], l[1]);
        }
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

    /** Used by restore: keeps every saved field of a backed-up account. */
    public long addAccountFull(String name, String cookies, String notes, String email, String username,
                               String password, int desk, int pxOn, String pxHost, int pxPort,
                               String pxUser, String pxPass) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        if (cookies != null && !cookies.isEmpty()) cv.put("cookies", cookies);
        cv.put("notes", notes);
        cv.put("email", email);
        cv.put("username", username);
        cv.put("password", password);
        cv.put("desk", desk);
        cv.put("pxon", pxOn);
        cv.put("px_host", pxHost);
        cv.put("px_port", pxPort);
        cv.put("px_user", pxUser);
        cv.put("px_pass", pxPass);
        return db.insert("accounts", null, cv);
    }

    private CloneModel read(Cursor c) {
        return new CloneModel(c.getInt(0), c.getString(1), c.getString(2),
                c.getString(3), c.getInt(4), c.getLong(5),
                c.getString(6), c.getString(7), c.getString(8), c.getString(9),
                c.getInt(10), c.getInt(11), c.getString(12), c.getInt(13),
                c.getString(14), c.getString(15));
    }

    public void updateLogin(int id, String email, String username, String password, String notes) {
        ContentValues cv = new ContentValues();
        cv.put("email", email);
        cv.put("username", username);
        cv.put("password", password);
        cv.put("notes", notes);
        getWritableDatabase().update("accounts", cv, "_id=?", new String[]{String.valueOf(id)});
    }

    public void setDesktop(int id, int desk) {
        ContentValues cv = new ContentValues();
        cv.put("desk", desk);
        getWritableDatabase().update("accounts", cv, "_id=?", new String[]{String.valueOf(id)});
    }

    public void updateProxy(int id, String host, int port, String user, String pass, int on) {
        ContentValues cv = new ContentValues();
        cv.put("px_host", host);
        cv.put("px_port", port);
        cv.put("px_user", user);
        cv.put("px_pass", pass);
        cv.put("pxon", on);
        getWritableDatabase().update("accounts", cv, "_id=?", new String[]{String.valueOf(id)});
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
