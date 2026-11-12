package com.railreserve.testing;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;
import com.railreserve.dao.SQLiteBookingDao;
import com.railreserve.database.DatabaseHelper;
import com.railreserve.model.BookingRequest;
import com.railreserve.model.TravelClass;
import com.railreserve.service.BookingServiceImpl;
import java.io.File;
import java.util.UUID;

/** Real SQLite, isolated from the user's railreserve.db. */
public final class DemoPaymentTestDatabase implements AutoCloseable {
    public final File file;
    public final Context context;
    public final DatabaseHelper helper;
    public final SQLiteBookingDao dao;
    public final BookingServiceImpl service;

    public DemoPaymentTestDatabase() { this(false); }
    public DemoPaymentTestDatabase(boolean deferOpenForMigration) {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        file = new File(target.getCacheDir(), "phase3-" + UUID.randomUUID() + ".db");
        context = new ContextWrapper(target) {
            @Override public Context getApplicationContext() { return this; }
            @Override public File getDatabasePath(String name) { return file; }
            @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory) {
                return SQLiteDatabase.openOrCreateDatabase(file, factory);
            }
            @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode,
                    SQLiteDatabase.CursorFactory factory, DatabaseErrorHandler handler) {
                return SQLiteDatabase.openOrCreateDatabase(file.getPath(), factory, handler);
            }
        };
        helper = new DatabaseHelper(context);
        dao = new SQLiteBookingDao(helper);
        service = new BookingServiceImpl(dao);
        if (!deferOpenForMigration) {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.execSQL("INSERT INTO trains VALUES (1,'90001','TEST FIXTURE','From','To','08:00','12:00',0)");
            db.execSQL("INSERT INTO journeys VALUES (1,1,'2026-11-12')");
            for (int seat = 1; seat <= 20; seat++) {
                db.execSQL("INSERT INTO seats VALUES (?,1,'SL','S1',?,100,'AVAILABLE')", new Object[]{seat, seat});
            }
        }
    }

    public BookingRequest checkout(long seat) {
        return new BookingRequest(1, seat, TravelClass.SLEEPER, "Test Passenger", 25, "Other",
                "DEMO-CHK-" + UUID.randomUUID(), 10000L);
    }

    @Override public void close() { helper.close(); SQLiteDatabase.deleteDatabase(file); }
}
