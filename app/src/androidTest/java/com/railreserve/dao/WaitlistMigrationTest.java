package com.railreserve.dao;

import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.railreserve.model.Booking;
import com.railreserve.model.BookingRequest;
import com.railreserve.model.TravelClass;
import com.railreserve.testing.DemoPaymentTestDatabase;
import com.railreserve.testing.VersionFiveFixture;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Uses an isolated v7 database, never the user's reservations. */
@RunWith(AndroidJUnit4.class)
public class WaitlistMigrationTest {
    private void seed(SQLiteDatabase db) {
        VersionFiveFixture.createVersionSeven(db);
        db.execSQL("INSERT INTO trains VALUES(1,'SIM-v7','Migration Fixture','Alpha','Beta','08:00','12:00',0)");
        db.execSQL("INSERT INTO journeys VALUES(1,1,'2026-11-20')");
        db.execSQL("INSERT INTO seats VALUES(1,1,'SL','S1',1,245,'BOOKED')");
        db.execSQL("INSERT INTO seats VALUES(2,1,'2A','A1',2,500,'AVAILABLE')");
        db.execSQL("INSERT INTO simulation_scenarios VALUES(1,'SELECTED_LIVE_V1','90339','AAA','BBB','2026-11-20','SL',1)");
        db.execSQL("INSERT INTO bookings VALUES(101,'1234567890',1,'Saved Passenger',25,'Other','SL',245,'CONFIRMED','2026-11-18T00:00:00Z',NULL,0)");
        db.execSQL("INSERT INTO bookings VALUES(102,'2234567890',2,'Cancelled Passenger',30,'Female','2A',500,'CANCELLED','2026-11-18T00:00:00Z','2026-11-18T01:00:00Z',1)");
        db.execSQL("INSERT INTO demo_payments(transaction_reference,checkout_reference,booking_id,payment_method,amount_paise,payment_status,created_at) VALUES('DEMO-TXN-v7','DEMO-CHK-12345678-1234-1234-1234-123456789abc',101,'DEMO_UPI',24500,'SUCCESS','2026-11-18T00:00:00Z')");
        db.execSQL("INSERT INTO booking_fare_snapshots(booking_id,train_number,source_code,destination_code,journey_date,class_code,quota_code,amount_paise,provider_source,retrieved_at,availability_text,status_type,availability_source,availability_retrieved_at) VALUES(101,'90339','AAA','BBB','2026-11-20','SL','GN',24500,'RailCore fare estimate','2026-11-18T00:00:00Z','AVAILABLE 4','AVAILABLE','RailCore availability','2026-11-18T00:00:00Z')");
        db.execSQL("UPDATE sqlite_sequence SET seq=1000 WHERE name='bookings'");
    }

    @Test public void versionSevenPreservesBookingsReceiptsVisibilityAndSchemaObjects() {
        try (DemoPaymentTestDatabase f = new DemoPaymentTestDatabase(true)) {
            try (SQLiteDatabase old = SQLiteDatabase.openOrCreateDatabase(f.file, null)) {
                seed(old);
                old.execSQL("CREATE INDEX fixture_passenger ON bookings(passenger_name)");
                old.execSQL("CREATE VIEW fixture_confirmed AS SELECT booking_id FROM bookings WHERE booking_status='CONFIRMED'");
                old.execSQL("CREATE TABLE fixture_audit(booking_id INTEGER)");
                old.execSQL("CREATE TRIGGER fixture_cancel AFTER UPDATE OF booking_status ON bookings WHEN NEW.booking_status='CANCELLED' BEGIN INSERT INTO fixture_audit VALUES(NEW.booking_id); END");
            }
            SQLiteDatabase db = f.helper.getWritableDatabase();
            assertEquals(8, db.getVersion());
            assertEquals(2, f.dao.getBookings().size());
            Booking saved = f.service.findBookingByPnr("1234567890");
            assertEquals(101, saved.getId());
            assertEquals("Saved Passenger", saved.getPassengerName());
            assertEquals(1, saved.getJourneyId());
            assertEquals(1, saved.getSeatId());
            assertNull(saved.getWaitlistNumber());
            assertEquals(24500L, saved.getPayment().getAmountPaise());
            assertEquals("AVAILABLE 4", saved.getFareSnapshot().getAvailability());
            Booking cancelled = f.service.findBookingByPnr("2234567890");
            assertEquals("CANCELLED", cancelled.getStatus());
            assertEquals(TravelClass.AC_TWO_TIER, cancelled.getTravelClass());
            assertEquals(1, DatabaseUtils.longForQuery(db, "SELECT hidden_from_list FROM bookings WHERE booking_id=102", null));
            assertEquals(1, DatabaseUtils.longForQuery(db, "SELECT COUNT(*) FROM fixture_confirmed", null));
            assertEquals(1, DatabaseUtils.longForQuery(db, "SELECT COUNT(*) FROM sqlite_master WHERE name='fixture_passenger'", null));
            assertThrows(RuntimeException.class, () -> db.execSQL("UPDATE booking_fare_snapshots SET amount_paise=0 WHERE booking_id=101"));
            Booking next = f.service.createBooking(new BookingRequest(1, 2, TravelClass.AC_TWO_TIER, "New Passenger", 25, "Other"));
            assertTrue(next.getId() > 1000);
            f.service.cancelBooking(101);
            assertEquals(1, DatabaseUtils.longForQuery(db, "SELECT COUNT(*) FROM fixture_audit WHERE booking_id=101", null));
            assertEquals("SIMULATED_REFUNDED", f.service.findBookingByPnr("1234567890").getPayment().getRefundStatus());
            assertEquals(1, DatabaseUtils.longForQuery(db, "PRAGMA foreign_keys", null));
            try (Cursor c = db.rawQuery("PRAGMA foreign_key_check", null)) { assertFalse(c.moveToFirst()); }
            assertEquals("ok", DatabaseUtils.stringForQuery(db, "PRAGMA quick_check", null));
            f.helper.close();
            assertEquals(3, f.dao.getBookings().size());
            assertEquals(next.getId(), f.service.findBookingByPnr(next.getPnr()).getId());
        }
    }

    @Test public void orphanedLegacyBookingAbortsUpgradeWithoutDroppingAnyRecords() {
        try (DemoPaymentTestDatabase f = new DemoPaymentTestDatabase(true)) {
            try (SQLiteDatabase old = SQLiteDatabase.openOrCreateDatabase(f.file, null)) {
                seed(old);
                old.execSQL("UPDATE bookings SET seat_id=999 WHERE booking_id=102");
                old.execSQL("DROP TRIGGER fare_snapshot_amount_guard");
            }
            assertThrows(RuntimeException.class, () -> f.helper.getWritableDatabase());
            f.helper.close();
            try (SQLiteDatabase unchanged = SQLiteDatabase.openOrCreateDatabase(f.file, null)) {
                assertEquals(7, unchanged.getVersion());
                assertEquals(2, DatabaseUtils.queryNumEntries(unchanged, "bookings"));
                assertEquals(999, DatabaseUtils.longForQuery(unchanged, "SELECT seat_id FROM bookings WHERE booking_id=102", null));
                assertEquals(1, DatabaseUtils.queryNumEntries(unchanged, "demo_payments"));
                assertEquals(0, DatabaseUtils.longForQuery(unchanged, "SELECT COUNT(*) FROM sqlite_master WHERE name='bookings_v8'", null));
            }
        }
    }

    @Test public void emptyLegacyBookingsKeepTheirAutoincrementHighWaterMark() {
        try (DemoPaymentTestDatabase f = new DemoPaymentTestDatabase(true)) {
            try (SQLiteDatabase old = SQLiteDatabase.openOrCreateDatabase(f.file, null)) {
                seed(old);
                old.execSQL("DELETE FROM booking_fare_snapshots");
                old.execSQL("DELETE FROM demo_payments");
                old.execSQL("DELETE FROM bookings");
                old.execSQL("UPDATE seats SET status='AVAILABLE'");
            }
            Booking next = f.service.createBooking(new BookingRequest(1, 2, TravelClass.AC_TWO_TIER, "New Passenger", 25, "Other"));
            assertTrue(next.getId() > 1000);
        }
    }
}
