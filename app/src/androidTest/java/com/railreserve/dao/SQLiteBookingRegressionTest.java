package com.railreserve.dao;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.railreserve.database.DatabaseHelper;
import com.railreserve.model.Booking;
import com.railreserve.model.BookingRequest;
import com.railreserve.model.TravelClass;
import java.io.File;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SQLiteBookingRegressionTest {
    @Test public void bookingPnrCancellationAndRebookingSurviveReopen() {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // A unique cache database exercises the real DAO without touching the user's demo bookings.
        File testFile = new File(target.getCacheDir(), "phase1-booking-" + UUID.randomUUID() + ".db");
        Context isolated = new ContextWrapper(target) {
            @Override public Context getApplicationContext() { return this; }
            @Override public File getDatabasePath(String name) { return testFile; }
            @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory) {
                return SQLiteDatabase.openOrCreateDatabase(testFile, factory);
            }
            @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode,
                    SQLiteDatabase.CursorFactory factory, DatabaseErrorHandler handler) {
                return SQLiteDatabase.openOrCreateDatabase(testFile.getPath(), factory, handler);
            }
        };
        DatabaseHelper helper = new DatabaseHelper(isolated);
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.execSQL("INSERT INTO trains VALUES (1,'90001','Test fixture','From','To','08:00','12:00',0)");
            db.execSQL("INSERT INTO journeys VALUES (1,1,'2026-11-12')");
            db.execSQL("INSERT INTO seats VALUES (1,1,'SL','S1',1,100,'AVAILABLE')");
            SQLiteBookingDao dao = new SQLiteBookingDao(helper);
            BookingRequest request = new BookingRequest(1, 1, TravelClass.SLEEPER, "Test Passenger", 25, "Other");
            assertEquals(1, dao.getAvailableSeats(1, "SL").size());
            Booking first = dao.createBooking(request);
            assertTrue(first.getPnr().matches("[1-9][0-9]{9}"));
            assertEquals("CONFIRMED", first.getStatus());
            assertTrue(dao.getAvailableSeats(1, "SL").isEmpty());
            assertThrows(IllegalStateException.class, () -> new SQLiteBookingDao(helper).createBooking(request));
            assertEquals(1, dao.getBookings().size());
            helper.close();

            dao = new SQLiteBookingDao(helper);
            assertEquals("CONFIRMED", dao.findBookingByPnr(first.getPnr()).getStatus());
            dao.cancelBooking(first.getId());
            assertEquals("CANCELLED", dao.findBookingByPnr(first.getPnr()).getStatus());
            assertEquals(1, dao.getAvailableSeats(1, "SL").size());
            assertThrows(IllegalStateException.class, () -> new SQLiteBookingDao(helper).cancelBooking(first.getId()));
            Booking second = dao.createBooking(request);
            assertNotEquals(first.getPnr(), second.getPnr());
            helper.close();

            dao = new SQLiteBookingDao(helper);
            assertEquals(2, dao.getBookings().size());
            assertEquals("CANCELLED", dao.findBookingByPnr(first.getPnr()).getStatus());
            assertEquals("CONFIRMED", dao.findBookingByPnr(second.getPnr()).getStatus());
            assertNull(dao.findBookingByPnr("0000000000"));
        } finally {
            helper.close();
            SQLiteDatabase.deleteDatabase(testFile);
        }
    }

        @Test public void visibleListHidesPastAndManuallyRemovedBookingsButRetainsReceiptAndReservation() {
            Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
            File testFile = new File(target.getCacheDir(), "ticket-retention-" + UUID.randomUUID() + ".db");
            Context isolated = new ContextWrapper(target) {
                @Override public Context getApplicationContext() { return this; }
                @Override public File getDatabasePath(String name) { return testFile; }
                @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory) {
                    return SQLiteDatabase.openOrCreateDatabase(testFile, factory);
                }
                @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode,
                        SQLiteDatabase.CursorFactory factory, DatabaseErrorHandler handler) {
                    return SQLiteDatabase.openOrCreateDatabase(testFile.getPath(), factory, handler);
                }
            };
            DatabaseHelper helper = new DatabaseHelper(isolated);
            try {
                SQLiteDatabase db = helper.getWritableDatabase();
                db.execSQL("INSERT INTO trains VALUES (1,'90001','Test fixture','From','To','08:00','12:00',0)");
                db.execSQL("INSERT INTO journeys VALUES (1,1,'2026-11-11')");
                db.execSQL("INSERT INTO journeys VALUES (2,1,'2026-11-12')");
                db.execSQL("INSERT INTO journeys VALUES (3,1,'2026-11-13')");
                for (int id = 1; id <= 3; id++) {
                    db.execSQL("INSERT INTO seats VALUES (?,?,'SL','S1',1,100,'AVAILABLE')", new Object[]{id, id});
                }
                SQLiteBookingDao dao = new SQLiteBookingDao(helper);
                Booking past = dao.createBooking(new BookingRequest(1, 1, TravelClass.SLEEPER, "Past Passenger", 25, "Other"));
                Booking today = dao.createBooking(new BookingRequest(2, 2, TravelClass.SLEEPER, "Today Passenger", 25, "Other",
                        "DEMO-CHK-" + UUID.randomUUID(), 10000L));
                Booking future = dao.createBooking(new BookingRequest(3, 3, TravelClass.SLEEPER, "Future Passenger", 25, "Other"));

                assertEquals(2, dao.getVisibleBookings(LocalDate.of(2026, 11, 12)).size());
                dao.hideBookingFromList(today.getId());
                helper.close();

                assertEquals(1, dao.getVisibleBookings(LocalDate.of(2026, 11, 12)).size());
                assertEquals(future.getId(), dao.getVisibleBookings(LocalDate.of(2026, 11, 12)).get(0).getId());
                Booking retained = dao.findBookingByPnr(today.getPnr());
                assertEquals("CONFIRMED", retained.getStatus());
                assertNotNull(retained.getPayment());
                assertEquals("SUCCESS", retained.getPayment().getStatus());
                assertEquals(3, dao.getBookings().size());
                assertTrue(dao.getAvailableSeats(2, "SL").isEmpty());
                assertNotNull(dao.findBookingByPnr(past.getPnr()));
            } finally {
                helper.close();
                SQLiteDatabase.deleteDatabase(testFile);
            }
        }
}
