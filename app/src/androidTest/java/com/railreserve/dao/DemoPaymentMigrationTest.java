package com.railreserve.dao;

import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.railreserve.model.Booking;
import com.railreserve.testing.DemoPaymentTestDatabase;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DemoPaymentMigrationTest {
    @Test public void versionTwoMigrationPreservesLegacyPnrSeatsAndCancellation() {
        try (DemoPaymentTestDatabase fixture = new DemoPaymentTestDatabase(true)) {
            try (SQLiteDatabase old = SQLiteDatabase.openOrCreateDatabase(fixture.file, null)) {
                createVersionTwo(old);
                old.execSQL("INSERT INTO trains VALUES (1,'90001','Legacy Train','From','To','08:00','12:00',0)");
                old.execSQL("INSERT INTO journeys VALUES (1,1,'2026-11-12')");
                old.execSQL("INSERT INTO seats VALUES (1,1,'SL','S1',1,100,'BOOKED')");
                old.execSQL("INSERT INTO seats VALUES (2,1,'SL','S1',2,100,'AVAILABLE')");
                old.execSQL("INSERT INTO bookings VALUES (1,'1234567890',1,'Legacy Passenger',25,'Other','SL',100,'CONFIRMED','2026-11-01T00:00:00Z',NULL)");
                old.setVersion(2);
            }
            SQLiteDatabase upgraded = fixture.helper.getWritableDatabase();
            assertEquals(7, upgraded.getVersion());
            assertEquals(1, DatabaseUtils.longForQuery(upgraded, "PRAGMA foreign_keys", null));
            Booking legacy = fixture.service.findBookingByPnr("1234567890");
            assertEquals(1L, legacy.getId());
            assertEquals("Legacy Passenger", legacy.getPassengerName());
            assertEquals("2026-11-01T00:00:00Z", legacy.getBookedAt());
            assertEquals("CONFIRMED", legacy.getStatus());
            assertNull(legacy.getPayment());
            assertEquals(1, fixture.dao.getAvailableSeats(1, "SL").size());
            assertEquals(1, DatabaseUtils.longForQuery(upgraded,
                    "SELECT count(*) FROM sqlite_master WHERE type='index' AND name='idx_one_confirmed_booking_per_seat'", null));
            fixture.service.cancelBooking(legacy.getId());
            assertEquals("CANCELLED", fixture.service.findBookingByPnr(legacy.getPnr()).getStatus());
            assertEquals(2, fixture.dao.getAvailableSeats(1, "SL").size());
            Booking paid = fixture.service.createBooking(fixture.checkout(2));
            assertNotNull(paid.getPayment());
            assertEquals(2, fixture.service.getBookings().size());
            fixture.helper.close();
            assertEquals(legacy.getPnr(), fixture.service.findBookingByPnr(legacy.getPnr()).getPnr());
            assertEquals(paid.getPnr(), fixture.service.findBookingByCheckoutReference(paid.getPayment().getCheckoutReference()).getPnr());
        }
    }

    static void createVersionTwo(SQLiteDatabase db) {
        // Exact pre-payment schema, constructed directly rather than dropping any version-3 tables.
        db.execSQL("CREATE TABLE trains (train_id INTEGER PRIMARY KEY, train_number TEXT NOT NULL UNIQUE, name TEXT NOT NULL, source_station TEXT NOT NULL, destination_station TEXT NOT NULL, departure_time TEXT NOT NULL, arrival_time TEXT NOT NULL, arrival_day_offset INTEGER NOT NULL CHECK(arrival_day_offset IN (0,1)))");
        db.execSQL("CREATE TABLE journeys (journey_id INTEGER PRIMARY KEY, train_id INTEGER NOT NULL REFERENCES trains(train_id), journey_date TEXT NOT NULL, UNIQUE(train_id,journey_date))");
        db.execSQL("CREATE TABLE seats (seat_id INTEGER PRIMARY KEY, journey_id INTEGER NOT NULL REFERENCES journeys(journey_id), travel_class TEXT NOT NULL CHECK(travel_class IN ('SL','3A')), coach TEXT NOT NULL, seat_number INTEGER NOT NULL, fare INTEGER NOT NULL CHECK(fare>=0), status TEXT NOT NULL CHECK(status IN ('AVAILABLE','BOOKED')), UNIQUE(journey_id,travel_class,coach,seat_number))");
        db.execSQL("CREATE INDEX idx_journeys_date_train ON journeys(journey_date,train_id)");
        db.execSQL("CREATE INDEX idx_seats_journey_class ON seats(journey_id,travel_class,status)");
        db.execSQL("CREATE TABLE bookings (booking_id INTEGER PRIMARY KEY AUTOINCREMENT, pnr TEXT NOT NULL UNIQUE, seat_id INTEGER NOT NULL REFERENCES seats(seat_id), passenger_name TEXT NOT NULL CHECK(length(trim(passenger_name)) BETWEEN 2 AND 80), passenger_age INTEGER NOT NULL CHECK(passenger_age BETWEEN 1 AND 120), passenger_gender TEXT NOT NULL CHECK(passenger_gender IN ('Female','Male','Other')), travel_class TEXT NOT NULL CHECK(travel_class IN ('SL','3A')), fare INTEGER NOT NULL CHECK(fare>=0), booking_status TEXT NOT NULL CHECK(booking_status IN ('CONFIRMED','CANCELLED')), booked_at TEXT NOT NULL, cancelled_at TEXT)");
        db.execSQL("CREATE UNIQUE INDEX idx_one_confirmed_booking_per_seat ON bookings(seat_id) WHERE booking_status='CONFIRMED'");
        db.execSQL("CREATE INDEX idx_bookings_booked_at ON bookings(booked_at DESC)");
    }
}
