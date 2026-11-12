package com.railreserve.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public final class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME="railreserve.db"; private static final int VERSION=4;
    public DatabaseHelper(Context context){super(context.getApplicationContext(),DB_NAME,null,VERSION);}
    @Override public void onConfigure(SQLiteDatabase db){super.onConfigure(db);db.setForeignKeyConstraintsEnabled(true);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE trains (train_id INTEGER PRIMARY KEY, train_number TEXT NOT NULL UNIQUE, name TEXT NOT NULL, source_station TEXT NOT NULL, destination_station TEXT NOT NULL, departure_time TEXT NOT NULL, arrival_time TEXT NOT NULL, arrival_day_offset INTEGER NOT NULL CHECK(arrival_day_offset IN (0,1)))");
        db.execSQL("CREATE TABLE journeys (journey_id INTEGER PRIMARY KEY, train_id INTEGER NOT NULL REFERENCES trains(train_id), journey_date TEXT NOT NULL, UNIQUE(train_id,journey_date))");
        db.execSQL("CREATE TABLE seats (seat_id INTEGER PRIMARY KEY, journey_id INTEGER NOT NULL REFERENCES journeys(journey_id), travel_class TEXT NOT NULL CHECK(travel_class IN ('SL','3A')), coach TEXT NOT NULL, seat_number INTEGER NOT NULL, fare INTEGER NOT NULL CHECK(fare>=0), status TEXT NOT NULL CHECK(status IN ('AVAILABLE','BOOKED')), UNIQUE(journey_id,travel_class,coach,seat_number))");
        db.execSQL("CREATE INDEX idx_journeys_date_train ON journeys(journey_date,train_id)"); db.execSQL("CREATE INDEX idx_seats_journey_class ON seats(journey_id,travel_class,status)");
        createBookingsSchema(db);
        createPaymentsSchema(db);
        createSimulationScenarios(db);
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        if(oldVersion<2) createBookingsSchema(db);
        if(oldVersion<3) createPaymentsSchema(db);
        if(oldVersion<4) createSimulationScenarios(db);
    }
    private void createSimulationScenarios(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE simulation_scenarios (scenario_id INTEGER PRIMARY KEY, "
                + "namespace TEXT NOT NULL CHECK(namespace='SELECTED_LIVE_V1'), "
                + "display_train_number TEXT NOT NULL, source_code TEXT NOT NULL, destination_code TEXT NOT NULL, "
                + "journey_date TEXT NOT NULL, travel_class TEXT NOT NULL CHECK(travel_class IN ('SL','3A')), "
                + "train_id INTEGER NOT NULL UNIQUE REFERENCES trains(train_id), "
                + "UNIQUE(namespace,display_train_number,source_code,destination_code,journey_date,travel_class))");
    }
    private void createPaymentsSchema(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE demo_payments (payment_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "transaction_reference TEXT NOT NULL UNIQUE CHECK(length(trim(transaction_reference))>0), "
                + "checkout_reference TEXT NOT NULL UNIQUE CHECK(length(trim(checkout_reference))>0), "
                + "booking_id INTEGER NOT NULL UNIQUE REFERENCES bookings(booking_id), "
                + "payment_method TEXT NOT NULL CHECK(payment_method='DEMO_UPI'), "
                + "amount_paise INTEGER NOT NULL CHECK(typeof(amount_paise)='integer' AND amount_paise>=0), "
                + "payment_status TEXT NOT NULL CHECK(payment_status='SUCCESS'), created_at TEXT NOT NULL, "
                + "refund_status TEXT NOT NULL DEFAULT 'NONE' CHECK(refund_status IN ('NONE','SIMULATED_REFUNDED')), "
                + "refunded_at TEXT, CHECK((refund_status='NONE' AND refunded_at IS NULL) OR "
                + "(refund_status='SIMULATED_REFUNDED' AND refunded_at IS NOT NULL)))");
    }
    private void createBookingsSchema(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS bookings (booking_id INTEGER PRIMARY KEY AUTOINCREMENT, pnr TEXT NOT NULL UNIQUE, seat_id INTEGER NOT NULL REFERENCES seats(seat_id), passenger_name TEXT NOT NULL CHECK(length(trim(passenger_name)) BETWEEN 2 AND 80), passenger_age INTEGER NOT NULL CHECK(passenger_age BETWEEN 1 AND 120), passenger_gender TEXT NOT NULL CHECK(passenger_gender IN ('Female','Male','Other')), travel_class TEXT NOT NULL CHECK(travel_class IN ('SL','3A')), fare INTEGER NOT NULL CHECK(fare>=0), booking_status TEXT NOT NULL CHECK(booking_status IN ('CONFIRMED','CANCELLED')), booked_at TEXT NOT NULL, cancelled_at TEXT)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_one_confirmed_booking_per_seat ON bookings(seat_id) WHERE booking_status='CONFIRMED'");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_bookings_booked_at ON bookings(booked_at DESC)");
    }
}
