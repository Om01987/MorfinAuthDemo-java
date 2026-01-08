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


    public static final String TABLE_USERS = "users";
    public static final String TABLE_FINGERPRINTS = "fingerprints";


    public static final String COL_USER_ID = "user_id";
    public static final String COL_NAME = "name";
    public static final String COL_ENROLLED_FINGERS = "enrolled_fingers";
    public static final String COL_CREATED_AT = "created_at";


    public static final String COL_FP_ID = "id";
    public static final String COL_FP_USER_ID = "user_id";
    public static final String COL_FP_INDEX = "finger_index";
    public static final String COL_FP_TEMPLATE = "template";
    public static final String COL_FP_QUALITY = "quality";
    public static final String COL_FP_NFIQ = "nfiq";
    public static final String COL_FP_CREATED_AT = "created_at";

    public FingerprintDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d("DB", "Creating database tables...");

        // users table
        String createUsersTable = "CREATE TABLE " + TABLE_USERS + " (" +
                COL_USER_ID + " TEXT PRIMARY KEY, " +
                COL_NAME + " TEXT NOT NULL, " +
                COL_ENROLLED_FINGERS + " INTEGER DEFAULT 0, " +
                COL_CREATED_AT + " DATETIME DEFAULT CURRENT_TIMESTAMP)";
        db.execSQL(createUsersTable);
        Log.d("DB", " Users table created");

        // fingerprints table
        String createFingerprintsTable = "CREATE TABLE " + TABLE_FINGERPRINTS + " (" +
                COL_FP_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_FP_USER_ID + " TEXT NOT NULL, " +
                COL_FP_INDEX + " INTEGER NOT NULL, " +
                COL_FP_TEMPLATE + " BLOB NOT NULL, " +
                COL_FP_QUALITY + " INTEGER, " +
                COL_FP_NFIQ + " INTEGER, " +
                COL_FP_CREATED_AT + " DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (" + COL_FP_USER_ID + ") REFERENCES " + TABLE_USERS + "(" + COL_USER_ID + "))";
        db.execSQL(createFingerprintsTable);
        Log.d("DB", " Fingerprints table created");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_FINGERPRINTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        onCreate(db);
    }


    public boolean createUser(String userId, String name) {
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(COL_USER_ID, userId);
            values.put(COL_NAME, name);
            values.put(COL_ENROLLED_FINGERS, 0);

            long result = db.insert(TABLE_USERS, null, values);
            db.close();

            if (result != -1) {
                Log.d("DB", " User created: " + userId);
                return true;
            }
            return false;
        } catch (Exception e) {
            Log.e("DB", "Error creating user", e);
            return false;
        }
    }


    public boolean userExists(String userId) {
        try {
            SQLiteDatabase db = this.getReadableDatabase();
            Cursor cursor = db.query(TABLE_USERS, null, COL_USER_ID + " = ?",
                    new String[]{userId}, null, null, null);

            boolean exists = cursor.getCount() > 0;
            cursor.close();
            db.close();
            return exists;
        } catch (Exception e) {
            Log.e("DB", "Error checking user", e);
            return false;
        }
    }


    public Cursor getAllUsers() {
        try {
            SQLiteDatabase db = this.getReadableDatabase();
            return db.query(TABLE_USERS, null, null, null, null, null,
                    COL_CREATED_AT + " DESC");
        } catch (Exception e) {
            Log.e("DB", "Error getting users", e);
            return null;
        }
    }


    public int getUserCount() {
        try {
            SQLiteDatabase db = this.getReadableDatabase();
            Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_USERS, null);
            cursor.moveToFirst();
            int count = cursor.getInt(0);
            cursor.close();
            db.close();
            return count;
        } catch (Exception e) {
            Log.e("DB", "Error getting user count", e);
            return 0;
        }
    }


    public boolean saveTemplate(String userId, int fingerIndex, byte[] template,
                                int quality, int nfiq) {
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(COL_FP_USER_ID, userId);
            values.put(COL_FP_INDEX, fingerIndex);
            values.put(COL_FP_TEMPLATE, template);  // BLOB
            values.put(COL_FP_QUALITY, quality);
            values.put(COL_FP_NFIQ, nfiq);

            long result = db.insert(TABLE_FINGERPRINTS, null, values);
            db.close();

            if (result != -1) {
                Log.d("DB", " Template " + fingerIndex + " saved for " + userId);
                updateEnrolledCount(userId);
                return true;
            }
            return false;
        } catch (Exception e) {
            Log.e("DB", "Error saving template", e);
            return false;
        }
    }


    public String getUserNameById(String userId) {
        try {
            SQLiteDatabase db = this.getReadableDatabase();
            Cursor cursor = db.query(TABLE_USERS,
                    new String[]{COL_NAME},
                    COL_USER_ID + " = ?",
                    new String[]{userId},
                    null, null, null);

            String name = "Unknown";
            if (cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(COL_NAME);
                if (nameIndex >= 0) {
                    name = cursor.getString(nameIndex);
                }
            }
            cursor.close();
            db.close();
            return name;
        } catch (Exception e) {
            Log.e("DB", "Error getting user name", e);
            return "Unknown";
        }
    }


    public byte[] getTemplate(String userId, int fingerIndex) {
        try {
            SQLiteDatabase db = this.getReadableDatabase();
            Cursor cursor = db.query(TABLE_FINGERPRINTS,
                    new String[]{COL_FP_TEMPLATE},
                    COL_FP_USER_ID + " = ? AND " + COL_FP_INDEX + " = ?",
                    new String[]{userId, String.valueOf(fingerIndex)},
                    null, null, null);

            byte[] template = null;
            if (cursor.moveToFirst()) {
                template = cursor.getBlob(0);
            }
            cursor.close();
            db.close();
            return template;
        } catch (Exception e) {
            Log.e("DB", "Error getting template", e);
            return null;
        }
    }


    public Cursor getAllTemplatesForUser(String userId) {
        try {
            SQLiteDatabase db = this.getReadableDatabase();
            return db.query(TABLE_FINGERPRINTS, null,
                    COL_FP_USER_ID + " = ?",
                    new String[]{userId},
                    null, null, COL_FP_INDEX + " ASC");
        } catch (Exception e) {
            Log.e("DB", "Error getting templates", e);
            return null;
        }
    }


    private void updateEnrolledCount(String userId) {
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            Cursor cursor = db.rawQuery(
                    "SELECT COUNT(*) FROM " + TABLE_FINGERPRINTS +
                            " WHERE " + COL_FP_USER_ID + " = ?",
                    new String[]{userId});
            cursor.moveToFirst();
            int count = cursor.getInt(0);
            cursor.close();

            ContentValues values = new ContentValues();
            values.put(COL_ENROLLED_FINGERS, count);
            db.update(TABLE_USERS, values, COL_USER_ID + " = ?",
                    new String[]{userId});
            db.close();

            Log.d("DB", "✓ Updated finger count for " + userId + ": " + count);
        } catch (Exception e) {
            Log.e("DB", "Error updating count", e);
        }
    }


    public boolean deleteUser(String userId) {
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            db.delete(TABLE_FINGERPRINTS, COL_FP_USER_ID + " = ?",
                    new String[]{userId});
            db.delete(TABLE_USERS, COL_USER_ID + " = ?",
                    new String[]{userId});
            db.close();
            Log.d("DB", " User deleted: " + userId);
            return true;
        } catch (Exception e) {
            Log.e("DB", "Error deleting user", e);
            return false;
        }
    }
}
