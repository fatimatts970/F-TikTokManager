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
    private static final int DB_VER = 1;

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
                "vcamon INTEGER DEFAULT 0)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        db.execSQL("DROP TABLE IF EXISTS accounts");
        onCreate(db);
    }

    public long addAccount(String name) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        return db.insert("accounts", null, cv);
    }

    public List<CloneModel> getAllAccounts() {
        List<CloneModel> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT _id, name, cookies, vcam, vcamon FROM accounts ORDER BY _id ASC", null);
        while (cursor.moveToNext()) {
            list.add(new CloneModel(
                    cursor.getInt(0),
                    cursor.getString(1),
                    cursor.getString(2),
                    cursor.getString(3),
                    cursor.getInt(4)
            ));
        }
        cursor.close();
        return list;
    }

    public void updateCookies(int id, String cookies) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("cookies", cookies);
        db.update("accounts", cv, "_id=?", new String[]{String.valueOf(id)});
    }

    public void updateVcam(int id, String path, int on) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("vcam", path);
        cv.put("vcamon", on);
        db.update("accounts", cv, "_id=?", new String[]{String.valueOf(id)});
    }
}
