package com.mantra.morfinauthdemo;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class FingerprintDatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "fingerprint_auth.db";
    private static final int DB_VERSION = 1;


    public static final String TABLE_FINGERPRINTS = "fingerprints";

    public static final String COL_ID = "id";
    public static final String COL_USER_ID = "user_id";
    public static final String COL_IMAGE = "image";
    public static final String COL_TEMPLATE = "template";
    public static final String COL_QUALITY = "quality";
    public static final String COL_NFIQ = "nfiq";
    public static final String COL_CREATED_AT = "created_at";

    public FingerprintDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d("DB", "Creating fingerprints table...");

        String createTable = "CREATE TABLE " + TABLE_FINGERPRINTS + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_USER_ID + " TEXT UNIQUE NOT NULL, " +
                COL_IMAGE + " BLOB NOT NULL, " +
                COL_TEMPLATE + " BLOB NOT NULL, " +
                COL_QUALITY + " INTEGER, " +
                COL_NFIQ + " INTEGER, " +
                COL_CREATED_AT + " DATETIME DEFAULT CURRENT_TIMESTAMP)";

        db.execSQL(createTable);
        Log.d("DB", " Fingerprints table created");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_FINGERPRINTS);
        onCreate(db);
    }

    public String saveFingerprint(byte[] image, byte[] template, int quality, int nfiq) {
        try {
            String userId = generateUserId();

            SQLiteDatabase db = this.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(COL_USER_ID, userId);
            values.put(COL_IMAGE, image);
            values.put(COL_TEMPLATE, template);
            values.put(COL_QUALITY, quality);
            values.put(COL_NFIQ, nfiq);

            long result = db.insert(TABLE_FINGERPRINTS, null, values);
            db.close();

            if (result != -1) {
                Log.d("DB", " Saved: " + userId + " (Image: " + image.length + " bytes, Template: " + template.length + " bytes)");
                return userId;
            } else {
                Log.e("DB", "Failed to save fingerprint");
                return null;
            }
        } catch (Exception e) {
            Log.e("DB", "Error saving fingerprint", e);
            return null;
        }
    }


    private String generateUserId() {
        try {
            SQLiteDatabase db = this.getReadableDatabase();
            Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_FINGERPRINTS, null);
            cursor.moveToFirst();
            int count = cursor.getInt(0);
            cursor.close();
            db.close();

            return String.format("USER_%03d", count + 1);
        } catch (Exception e) {
            Log.e("DB", "Error generating ID", e);
            return "USER_001";
        }
    }


    public Cursor getAllFingerprints() {
        try {
            SQLiteDatabase db = this.getReadableDatabase();
            return db.query(TABLE_FINGERPRINTS, null, null, null, null, null,
                    COL_CREATED_AT + " DESC");
        } catch (Exception e) {
            Log.e("DB", "Error getting fingerprints", e);
            return null;
        }
    }


    public int getTotalFingerprints() {
        try {
            SQLiteDatabase db = this.getReadableDatabase();
            Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_FINGERPRINTS, null);
            cursor.moveToFirst();
            int count = cursor.getInt(0);
            cursor.close();
            db.close();
            return count;
        } catch (Exception e) {
            Log.e("DB", "Error getting count", e);
            return 0;
        }
    }


    public boolean deleteAll() {
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            db.delete(TABLE_FINGERPRINTS, null, null);
            db.close();
            Log.d("DB", " All fingerprints deleted");
            return true;
        } catch (Exception e) {
            Log.e("DB", "Error deleting all", e);
            return false;
        }
    }
}

