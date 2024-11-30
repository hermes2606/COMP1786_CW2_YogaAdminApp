package com.example.yogaadminapp;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.facebook.stetho.Stetho;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "YogaAdmin.db";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE_TEACHERS = "teachers";
    private static final String TABLE_CLASSES = "classes";

    private static final String COLUMN_ID = "id";
    private static final String COLUMN_NAME = "name";
    private static final String COLUMN_EMAIL = "email";

    private static final String COLUMN_DATE = "date";
    private static final String COLUMN_TIME = "time";
    private static final String COLUMN_CAPACITY = "capacity";
    private static final String COLUMN_DURATION = "duration";
    private static final String COLUMN_PRICE = "price";
    private static final String COLUMN_CLASS_TYPE = "class_type";
    private static final String COLUMN_TEACHER_NAME = "teacher_name";
    private static final String COLUMN_DESCRIPTION = "description";
    public static final String TABLE_CUSTOMERS = "customers";


    private final DatabaseReference firebaseDatabaseRef;
    private SQLiteDatabase database;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        firebaseDatabaseRef = FirebaseDatabase.getInstance().getReference();
        Stetho.initializeWithDefaults(context); // Khởi tạo Stetho để hỗ trợ App Inspection

    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d("DatabaseHelper", "onCreate() called");
        System.out.println("Database onCreate() called");
        String CREATE_TEACHERS_TABLE = "CREATE TABLE " + TABLE_TEACHERS + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                COLUMN_NAME + " TEXT," +
                COLUMN_EMAIL + " TEXT)";
        db.execSQL(CREATE_TEACHERS_TABLE);

        String CREATE_CLASSES_TABLE = "CREATE TABLE " + TABLE_CLASSES + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                COLUMN_DATE + " TEXT," +
                COLUMN_TIME + " TEXT," +
                COLUMN_CAPACITY + " INTEGER," +
                COLUMN_DURATION + " INTEGER," +
                COLUMN_PRICE + " REAL," +
                COLUMN_CLASS_TYPE + " TEXT," +
                COLUMN_TEACHER_NAME + " TEXT," +
                COLUMN_DESCRIPTION + " TEXT)";
        db.execSQL(CREATE_CLASSES_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_TEACHERS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CLASSES);
        onCreate(db);

    }

    public SQLiteDatabase openDatabase() {
        if (database == null || !database.isOpen()) {
            database = this.getWritableDatabase();
        }
        return database;
    }

    public void addTeacher(String name, String email) {
        SQLiteDatabase db = openDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME, name);
        values.put(COLUMN_EMAIL, email);
        long id = db.insert(TABLE_TEACHERS, null, values);

        if (id != -1) {
            firebaseDatabaseRef.child("teachers").child(String.valueOf(id))
                    .setValue(new Teacher((int) id, name, email));
        }
    }

    public void addClass(String date, String time, int capacity, int duration, double price, String classType, String teacherName, String description) {
        SQLiteDatabase db = openDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_DATE, date);
        values.put(COLUMN_TIME, time);
        values.put(COLUMN_CAPACITY, capacity);
        values.put(COLUMN_DURATION, duration);
        values.put(COLUMN_PRICE, price);
        values.put(COLUMN_CLASS_TYPE, classType);
        values.put(COLUMN_TEACHER_NAME, teacherName);
        values.put(COLUMN_DESCRIPTION, description);
        long id = db.insert(TABLE_CLASSES, null, values);

        if (id != -1) {
            String firebaseId = firebaseDatabaseRef.child("classes").push().getKey();
            if (firebaseId != null) {
                YogaClass yogaClass = new YogaClass(firebaseId, date, time, capacity, duration, price, classType, teacherName, description);
                firebaseDatabaseRef.child("classes").child(firebaseId).setValue(yogaClass);
            }
        }
    }

    public void updateTeacher(int id, String name, String email) {
        SQLiteDatabase db = openDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME, name);
        values.put(COLUMN_EMAIL, email);

        db.update(TABLE_TEACHERS, values, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
        firebaseDatabaseRef.child("teachers").child(String.valueOf(id))
                .setValue(new Teacher(id, name, email));
    }

    public void deleteTeacher(int id) {
        SQLiteDatabase db = openDatabase();
        db.delete(TABLE_TEACHERS, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
        firebaseDatabaseRef.child("teachers").child(String.valueOf(id)).removeValue();
    }

    public void deleteClass(String firebaseId) {
        SQLiteDatabase db = openDatabase();
        db.delete(TABLE_CLASSES, COLUMN_ID + " = ?", new String[]{firebaseId});
        firebaseDatabaseRef.child("classes").child(firebaseId).removeValue();
    }

    public void syncWithFirebase() {
        // Sync teachers
        Log.d("DatabaseHelper", "Syncing teachers from Firebase...");
        firebaseDatabaseRef.child("teachers").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                SQLiteDatabase db = openDatabase();
                db.beginTransaction();
                try {
                    db.delete(TABLE_TEACHERS, null, null);
                    for (DataSnapshot teacherSnapshot : snapshot.getChildren()) {
                        Teacher teacher = teacherSnapshot.getValue(Teacher.class);
                        if (teacher != null) {
                            ContentValues values = new ContentValues();
                            values.put(COLUMN_ID, teacher.getId());
                            values.put(COLUMN_NAME, teacher.getName());
                            values.put(COLUMN_EMAIL, teacher.getEmail());
                            db.insert(TABLE_TEACHERS, null, values);
                        }
                    }
                    db.setTransactionSuccessful();
                    Log.d("DatabaseHelper", "Teachers data synced successfully.");
                } catch (Exception e) {
                    Log.e("DatabaseHelper", "Error syncing teachers: " + e.getMessage());
                } finally {
                    db.endTransaction();
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e("DatabaseHelper", "Failed to sync teachers: " + error.getMessage());
            }
        });

        // Sync classes
        Log.d("DatabaseHelper", "Syncing classes from Firebase...");
        firebaseDatabaseRef.child("classes").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                SQLiteDatabase db = openDatabase();
                db.beginTransaction();
                try {
                    db.delete(TABLE_CLASSES, null, null);
                    for (DataSnapshot classSnapshot : snapshot.getChildren()) {
                        YogaClass yogaClass = classSnapshot.getValue(YogaClass.class);
                        if (yogaClass != null) {
                            ContentValues values = new ContentValues();
                            values.put(COLUMN_DATE, yogaClass.getDate());
                            values.put(COLUMN_TIME, yogaClass.getTime());
                            values.put(COLUMN_CAPACITY, yogaClass.getCapacity());
                            values.put(COLUMN_DURATION, yogaClass.getDuration());
                            values.put(COLUMN_PRICE, yogaClass.getPrice());
                            values.put(COLUMN_CLASS_TYPE, yogaClass.getClassType());
                            values.put(COLUMN_TEACHER_NAME, yogaClass.getTeacherName());
                            values.put(COLUMN_DESCRIPTION, yogaClass.getDescription());
                            db.insert(TABLE_CLASSES, null, values);
                        }
                    }
                    db.setTransactionSuccessful();
                    Log.d("DatabaseHelper", "Classes data synced successfully.");
                } catch (Exception e) {
                    Log.e("DatabaseHelper", "Error syncing classes: " + e.getMessage());
                } finally {
                    db.endTransaction();
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e("DatabaseHelper", "Failed to sync classes: " + error.getMessage());
            }
        });
    }
//    //Sync Customer
//    public void syncCustomersWithFirebase() {
//        DatabaseReference customersRef = FirebaseDatabase.getInstance().getReference("customers");
//
//        customersRef.addValueEventListener(new ValueEventListener() {
//            @Override
//            public void onDataChange(DataSnapshot dataSnapshot) {
//                SQLiteDatabase db = openDatabase();
//                db.beginTransaction();
//
//                try {
//                    db.delete(TABLE_CUSTOMERS, null, null);
//
//                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
//                        String email = snapshot.getKey().replace("_com", ".com");
//                        ContentValues values = new ContentValues();
//                        values.put(COLUMN_EMAIL, email);
//                        db.insert(TABLE_CUSTOMERS, null, values);
//                    }
//                    db.setTransactionSuccessful();
//                    Log.d("DatabaseHelper", "Customers data synced successfully.");
//                } catch (Exception e) {
//                    Log.e("DatabaseHelper", "Error syncing customers: " + e.getMessage());
//                } finally {
//                    db.endTransaction();
//                }
//            }
//
//            @Override
//            public void onCancelled(DatabaseError error) {
//                Log.e("DatabaseHelper", "Failed to sync customers: " + error.getMessage());
//            }
//        });
//    }
    public void printDatabaseContents() {
        SQLiteDatabase db = openDatabase();
        Cursor cursor = db.query(TABLE_CLASSES, null, null, null, null, null, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                @SuppressLint("Range") int id = cursor.getInt(cursor.getColumnIndex(COLUMN_ID));
                @SuppressLint("Range") String date = cursor.getString(cursor.getColumnIndex(COLUMN_DATE));
                @SuppressLint("Range") String time = cursor.getString(cursor.getColumnIndex(COLUMN_TIME));
                System.out.println("Class ID: " + id + ", Date: " + date + ", Time: " + time);
            }

        }
    }
}
