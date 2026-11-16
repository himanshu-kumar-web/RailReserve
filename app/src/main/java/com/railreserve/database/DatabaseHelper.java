package com.railreserve.database;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public final class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME="railreserve.db"; private static final int VERSION=7;
    public DatabaseHelper(Context context){super(context.getApplicationContext(),DB_NAME,null,VERSION);}
    @Override public void onConfigure(SQLiteDatabase db){
        super.onConfigure(db);
        // SQLite requires foreign keys off BEFORE the upgrade transaction when rebuilding CHECK constraints.
        // The migration verifies every reference; onOpen restores enforcement after the transaction commits.
        db.setForeignKeyConstraintsEnabled(db.getVersion()==0 || db.getVersion()>=6);
    }
    @Override public void onOpen(SQLiteDatabase db){super.onOpen(db);db.setForeignKeyConstraintsEnabled(true);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE trains (train_id INTEGER PRIMARY KEY, train_number TEXT NOT NULL UNIQUE, name TEXT NOT NULL, source_station TEXT NOT NULL, destination_station TEXT NOT NULL, departure_time TEXT NOT NULL, arrival_time TEXT NOT NULL, arrival_day_offset INTEGER NOT NULL CHECK(arrival_day_offset IN (0,1)))");
        db.execSQL("CREATE TABLE journeys (journey_id INTEGER PRIMARY KEY, train_id INTEGER NOT NULL REFERENCES trains(train_id), journey_date TEXT NOT NULL, UNIQUE(train_id,journey_date))");
        db.execSQL("CREATE TABLE seats (seat_id INTEGER PRIMARY KEY, journey_id INTEGER NOT NULL REFERENCES journeys(journey_id), travel_class TEXT NOT NULL CHECK(travel_class IN ('SL','3A','2A','1A','3E','CC','EC','2S')), coach TEXT NOT NULL, seat_number INTEGER NOT NULL, fare INTEGER NOT NULL CHECK(fare>=0), status TEXT NOT NULL CHECK(status IN ('AVAILABLE','BOOKED')), UNIQUE(journey_id,travel_class,coach,seat_number))");
        db.execSQL("CREATE INDEX idx_journeys_date_train ON journeys(journey_date,train_id)"); db.execSQL("CREATE INDEX idx_seats_journey_class ON seats(journey_id,travel_class,status)");
        createBookingsSchema(db);
        createPaymentsSchema(db);
        createSimulationScenarios(db);
        createFareSnapshots(db);
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        if(oldVersion<2) createBookingsSchema(db);
        if(oldVersion<3) createPaymentsSchema(db);
        if(oldVersion<4) createSimulationScenarios(db);
        if(oldVersion<5) createFareSnapshots(db);
        if(oldVersion<6) TravelClassMigration.expand(db);
        if(oldVersion<7) addBookingListVisibilityColumn(db);
    }
    private void addBookingListVisibilityColumn(SQLiteDatabase db) {
        try (Cursor columns = db.rawQuery("PRAGMA table_info(bookings)", null)) {
            while (columns.moveToNext()) {
                if ("hidden_from_list".equals(columns.getString(columns.getColumnIndexOrThrow("name")))) return;
            }
        }
        db.execSQL("ALTER TABLE bookings ADD COLUMN hidden_from_list INTEGER NOT NULL DEFAULT 0 CHECK(hidden_from_list IN (0,1))");
    }
    private void createFareSnapshots(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE booking_fare_snapshots (booking_id INTEGER PRIMARY KEY REFERENCES bookings(booking_id), "
                + "train_number TEXT NOT NULL, source_code TEXT NOT NULL, destination_code TEXT NOT NULL, journey_date TEXT NOT NULL, "
                + "class_code TEXT NOT NULL, quota_code TEXT NOT NULL CHECK(quota_code='GN'), "
                + "amount_paise INTEGER NOT NULL CHECK(typeof(amount_paise)='integer' AND amount_paise>=0 AND amount_paise%100=0 AND amount_paise<=214748364700), "
                + "provider_source TEXT NOT NULL CHECK(provider_source='RailCore fare estimate'), retrieved_at TEXT NOT NULL, "
                + "availability_text TEXT,status_type TEXT,availability_source TEXT,availability_retrieved_at TEXT, "
                + "CHECK((availability_text IS NULL AND status_type IS NULL AND availability_source IS NULL AND availability_retrieved_at IS NULL) OR "
                + "(availability_text IS NOT NULL AND status_type IS NOT NULL AND availability_source IS NOT NULL AND availability_source='RailCore availability' AND availability_retrieved_at IS NOT NULL)))");
        db.execSQL("CREATE TRIGGER fare_snapshot_amount_guard BEFORE INSERT ON booking_fare_snapshots "
                + "WHEN NOT EXISTS (SELECT 1 FROM bookings b JOIN seats s ON s.seat_id=b.seat_id JOIN journeys j ON j.journey_id=s.journey_id "
                + "JOIN simulation_scenarios sc ON sc.train_id=j.train_id WHERE b.booking_id=NEW.booking_id AND b.fare*100=NEW.amount_paise "
                + "AND b.travel_class=NEW.class_code AND sc.display_train_number=NEW.train_number AND sc.source_code=NEW.source_code "
                + "AND sc.destination_code=NEW.destination_code AND j.journey_date=NEW.journey_date) BEGIN SELECT RAISE(ABORT,'Quote context mismatch'); END");
        db.execSQL("CREATE TRIGGER fare_snapshot_immutable BEFORE UPDATE ON booking_fare_snapshots BEGIN SELECT RAISE(ABORT,'Quote snapshots are immutable'); END");
    }
    private void createSimulationScenarios(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE simulation_scenarios (scenario_id INTEGER PRIMARY KEY, "
                + "namespace TEXT NOT NULL CHECK(namespace='SELECTED_LIVE_V1'), "
                + "display_train_number TEXT NOT NULL, source_code TEXT NOT NULL, destination_code TEXT NOT NULL, "
                + "journey_date TEXT NOT NULL, travel_class TEXT NOT NULL CHECK(travel_class IN ('SL','3A','2A','1A','3E','CC','EC','2S')), "
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
        db.execSQL("CREATE TABLE IF NOT EXISTS bookings (booking_id INTEGER PRIMARY KEY AUTOINCREMENT, pnr TEXT NOT NULL UNIQUE, seat_id INTEGER NOT NULL REFERENCES seats(seat_id), passenger_name TEXT NOT NULL CHECK(length(trim(passenger_name)) BETWEEN 2 AND 80), passenger_age INTEGER NOT NULL CHECK(passenger_age BETWEEN 1 AND 120), passenger_gender TEXT NOT NULL CHECK(passenger_gender IN ('Female','Male','Other')), travel_class TEXT NOT NULL CHECK(travel_class IN ('SL','3A','2A','1A','3E','CC','EC','2S')), fare INTEGER NOT NULL CHECK(fare>=0), booking_status TEXT NOT NULL CHECK(booking_status IN ('CONFIRMED','CANCELLED')), booked_at TEXT NOT NULL, cancelled_at TEXT, hidden_from_list INTEGER NOT NULL DEFAULT 0 CHECK(hidden_from_list IN (0,1)))");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_one_confirmed_booking_per_seat ON bookings(seat_id) WHERE booking_status='CONFIRMED'");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_bookings_booked_at ON bookings(booked_at DESC)");
    }
}
