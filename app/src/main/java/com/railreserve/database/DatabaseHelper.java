package com.railreserve.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public final class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME="railreserve.db"; private static final int VERSION=1;
    public DatabaseHelper(Context context){super(context.getApplicationContext(),DB_NAME,null,VERSION);}
    @Override public void onConfigure(SQLiteDatabase db){super.onConfigure(db);db.setForeignKeyConstraintsEnabled(true);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE trains (train_id INTEGER PRIMARY KEY, train_number TEXT NOT NULL UNIQUE, name TEXT NOT NULL, source_station TEXT NOT NULL, destination_station TEXT NOT NULL, departure_time TEXT NOT NULL, arrival_time TEXT NOT NULL, arrival_day_offset INTEGER NOT NULL CHECK(arrival_day_offset IN (0,1)))");
        db.execSQL("CREATE TABLE journeys (journey_id INTEGER PRIMARY KEY, train_id INTEGER NOT NULL REFERENCES trains(train_id), journey_date TEXT NOT NULL, UNIQUE(train_id,journey_date))");
        db.execSQL("CREATE TABLE seats (seat_id INTEGER PRIMARY KEY, journey_id INTEGER NOT NULL REFERENCES journeys(journey_id), travel_class TEXT NOT NULL CHECK(travel_class IN ('SL','3A')), coach TEXT NOT NULL, seat_number INTEGER NOT NULL, fare INTEGER NOT NULL CHECK(fare>=0), status TEXT NOT NULL CHECK(status IN ('AVAILABLE','BOOKED')), UNIQUE(journey_id,travel_class,coach,seat_number))");
        db.execSQL("CREATE INDEX idx_journeys_date_train ON journeys(journey_date,train_id)"); db.execSQL("CREATE INDEX idx_seats_journey_class ON seats(journey_id,travel_class,status)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){ /* Future migrations must preserve existing journeys and seat states. */ }
}
