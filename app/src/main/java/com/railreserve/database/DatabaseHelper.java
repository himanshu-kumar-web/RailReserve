package com.railreserve.database;

import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import java.util.ArrayList;
import java.util.List;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public final class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME="railreserve.db"; private static final int VERSION=8;
    public DatabaseHelper(Context context){super(context.getApplicationContext(),DB_NAME,null,VERSION);}
    @Override public void onConfigure(SQLiteDatabase db){
        super.onConfigure(db);
        // SQLite requires foreign keys off BEFORE the upgrade transaction when rebuilding CHECK constraints.
        // The migration verifies every reference; onOpen restores enforcement after the transaction commits.
        db.setForeignKeyConstraintsEnabled(db.getVersion()==0 || db.getVersion()>=8);
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
        if(oldVersion<8) upgradeWaitlistBookings(db);
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
                + "WHEN NOT EXISTS (SELECT 1 FROM bookings b JOIN journeys j ON j.journey_id=b.journey_id "
                + "JOIN simulation_scenarios sc ON sc.train_id=j.train_id LEFT JOIN seats s ON s.seat_id=b.seat_id "
                + "WHERE b.booking_id=NEW.booking_id AND b.fare*100=NEW.amount_paise AND b.travel_class=NEW.class_code "
                + "AND sc.display_train_number=NEW.train_number AND sc.source_code=NEW.source_code "
                + "AND sc.destination_code=NEW.destination_code AND j.journey_date=NEW.journey_date "
                + "AND ((b.booking_status='WAITING_LIST' AND b.seat_id IS NULL) OR (s.journey_id=j.journey_id AND s.travel_class=b.travel_class))) "
                + "BEGIN SELECT RAISE(ABORT,'Quote context mismatch'); END");
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
        db.execSQL("CREATE TABLE IF NOT EXISTS bookings (booking_id INTEGER PRIMARY KEY AUTOINCREMENT, pnr TEXT NOT NULL UNIQUE, journey_id INTEGER NOT NULL REFERENCES journeys(journey_id), seat_id INTEGER REFERENCES seats(seat_id), waitlist_number INTEGER CHECK(waitlist_number IS NULL OR waitlist_number>0), passenger_name TEXT NOT NULL CHECK(length(trim(passenger_name)) BETWEEN 2 AND 80), passenger_age INTEGER NOT NULL CHECK(passenger_age BETWEEN 1 AND 120), passenger_gender TEXT NOT NULL CHECK(passenger_gender IN ('Female','Male','Other')), travel_class TEXT NOT NULL CHECK(travel_class IN ('SL','3A','2A','1A','3E','CC','EC','2S')), fare INTEGER NOT NULL CHECK(fare>=0), booking_status TEXT NOT NULL CHECK(booking_status IN ('CONFIRMED','WAITING_LIST','CANCELLED')), booked_at TEXT NOT NULL, cancelled_at TEXT, hidden_from_list INTEGER NOT NULL DEFAULT 0 CHECK(hidden_from_list IN (0,1)), CHECK((seat_id IS NULL AND waitlist_number IS NOT NULL AND booking_status IN ('WAITING_LIST','CANCELLED')) OR (seat_id IS NOT NULL AND waitlist_number IS NULL AND booking_status IN ('CONFIRMED','CANCELLED'))))");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_one_confirmed_booking_per_seat ON bookings(seat_id) WHERE booking_status='CONFIRMED'");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_waitlist_number_per_journey_class ON bookings(journey_id,travel_class,waitlist_number) WHERE waitlist_number IS NOT NULL");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_bookings_booked_at ON bookings(booked_at DESC)");
    }
    private void upgradeWaitlistBookings(SQLiteDatabase db) {
        boolean hasJourneyId = false;
        try (Cursor columns = db.rawQuery("PRAGMA table_info(bookings)", null)) {
            while (columns.moveToNext()) {
                if ("journey_id".equals(columns.getString(columns.getColumnIndexOrThrow("name")))) {
                    hasJourneyId = true;
                    break;
                }
            }
        }
        if (!hasJourneyId) {
            // Keep every saved row and every dependent schema object inside the upgrade transaction.
            List<String[]> objects = new ArrayList<>();
            try (Cursor c = db.rawQuery("SELECT type,name,sql FROM sqlite_master WHERE sql IS NOT NULL "
                    + "AND name<>'fare_snapshot_amount_guard' AND (type IN ('trigger','view') "
                    + "OR (type='index' AND tbl_name='bookings')) "
                    + "ORDER BY CASE type WHEN 'view' THEN 0 WHEN 'index' THEN 1 ELSE 2 END", null)) {
                while (c.moveToNext()) objects.add(new String[]{c.getString(0), c.getString(1), c.getString(2)});
            }
            Long bookingSequence = null;
            try (Cursor sequence = db.rawQuery("SELECT seq FROM sqlite_sequence WHERE name='bookings'", null)) {
                if (sequence.moveToFirst()) bookingSequence = sequence.getLong(0);
            }
            db.execSQL("CREATE TABLE bookings_v8 (booking_id INTEGER PRIMARY KEY AUTOINCREMENT, pnr TEXT NOT NULL UNIQUE, journey_id INTEGER NOT NULL REFERENCES journeys(journey_id), seat_id INTEGER REFERENCES seats(seat_id), waitlist_number INTEGER CHECK(waitlist_number IS NULL OR waitlist_number>0), passenger_name TEXT NOT NULL CHECK(length(trim(passenger_name)) BETWEEN 2 AND 80), passenger_age INTEGER NOT NULL CHECK(passenger_age BETWEEN 1 AND 120), passenger_gender TEXT NOT NULL CHECK(passenger_gender IN ('Female','Male','Other')), travel_class TEXT NOT NULL CHECK(travel_class IN ('SL','3A','2A','1A','3E','CC','EC','2S')), fare INTEGER NOT NULL CHECK(fare>=0), booking_status TEXT NOT NULL CHECK(booking_status IN ('CONFIRMED','WAITING_LIST','CANCELLED')), booked_at TEXT NOT NULL, cancelled_at TEXT, hidden_from_list INTEGER NOT NULL DEFAULT 0 CHECK(hidden_from_list IN (0,1)), CHECK((seat_id IS NULL AND waitlist_number IS NOT NULL AND booking_status IN ('WAITING_LIST','CANCELLED')) OR (seat_id IS NOT NULL AND waitlist_number IS NULL AND booking_status IN ('CONFIRMED','CANCELLED'))))");
            db.execSQL("INSERT INTO bookings_v8(booking_id,pnr,journey_id,seat_id,passenger_name,passenger_age,passenger_gender,travel_class,fare,booking_status,booked_at,cancelled_at,hidden_from_list) SELECT b.booking_id,b.pnr,s.journey_id,b.seat_id,b.passenger_name,b.passenger_age,b.passenger_gender,b.travel_class,b.fare,b.booking_status,b.booked_at,b.cancelled_at,b.hidden_from_list FROM bookings b JOIN seats s ON s.seat_id=b.seat_id");
            if (DatabaseUtils.queryNumEntries(db, "bookings_v8") != DatabaseUtils.queryNumEntries(db, "bookings")) {
                throw new IllegalStateException("Wait-list migration could not resolve every saved booking; the original database was preserved.");
            }
            // Newer SQLite versions reject ALTER TABLE while a trigger/view references a missing table.
            db.execSQL("DROP TRIGGER IF EXISTS fare_snapshot_amount_guard");
            for (String[] object : objects) {
                if (!"index".equals(object[0])) {
                    db.execSQL("DROP " + object[0] + " \"" + object[1].replace("\"", "\"\"") + "\"");
                }
            }
            db.execSQL("DROP TABLE bookings");
            db.execSQL("ALTER TABLE bookings_v8 RENAME TO bookings");
            for (String[] object : objects) db.execSQL(object[2]);
            if (bookingSequence != null) {
                db.execSQL("UPDATE sqlite_sequence SET seq=MAX(seq,?) WHERE name='bookings'", new Object[]{bookingSequence});
            }
        }
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_one_confirmed_booking_per_seat ON bookings(seat_id) WHERE booking_status='CONFIRMED'");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_waitlist_number_per_journey_class ON bookings(journey_id,travel_class,waitlist_number) WHERE waitlist_number IS NOT NULL");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_bookings_booked_at ON bookings(booked_at DESC)");
        db.execSQL("DROP TRIGGER IF EXISTS fare_snapshot_amount_guard");
        db.execSQL("CREATE TRIGGER fare_snapshot_amount_guard BEFORE INSERT ON booking_fare_snapshots WHEN NOT EXISTS (SELECT 1 FROM bookings b JOIN journeys j ON j.journey_id=b.journey_id JOIN simulation_scenarios sc ON sc.train_id=j.train_id LEFT JOIN seats s ON s.seat_id=b.seat_id WHERE b.booking_id=NEW.booking_id AND b.fare*100=NEW.amount_paise AND b.travel_class=NEW.class_code AND sc.display_train_number=NEW.train_number AND sc.source_code=NEW.source_code AND sc.destination_code=NEW.destination_code AND j.journey_date=NEW.journey_date AND ((b.booking_status='WAITING_LIST' AND b.seat_id IS NULL) OR (s.journey_id=j.journey_id AND s.travel_class=b.travel_class))) BEGIN SELECT RAISE(ABORT,'Quote context mismatch'); END");
        try (Cursor c = db.rawQuery("PRAGMA foreign_key_check", null)) {
            if (c.moveToFirst()) throw new IllegalStateException("Wait-list migration failed its foreign-key check; the original database was preserved.");
        }
        if (!"ok".equals(DatabaseUtils.stringForQuery(db, "PRAGMA quick_check", null))) {
            throw new IllegalStateException("Wait-list migration failed its integrity check; the original database was preserved.");
        }
    }
}
