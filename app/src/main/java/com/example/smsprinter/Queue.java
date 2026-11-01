package com.example.smsprinter;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

final class Queue extends SQLiteOpenHelper {
    Queue(Context context) { super(context, "messages.db", null, 1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT, body TEXT NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }
    void add(String body) { android.content.ContentValues values = new android.content.ContentValues(); values.put("body", body); getWritableDatabase().insertOrThrow("messages", null, values); }
    int count() { try (Cursor c = getReadableDatabase().rawQuery("SELECT count(*) FROM messages", null)) { c.moveToFirst(); return c.getInt(0); } }
    void drain(Context context) {
        while (true) {
            long id; String body;
            try (Cursor c = getReadableDatabase().rawQuery("SELECT id, body FROM messages ORDER BY id LIMIT 1", null)) {
                if (!c.moveToFirst()) return;
                id = c.getLong(0); body = c.getString(1);
            }
            if (!Printer.print(context, body)) return;
            getWritableDatabase().delete("messages", "id=?", new String[]{Long.toString(id)});
        }
    }
}
