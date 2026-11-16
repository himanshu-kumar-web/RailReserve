package com.railreserve.dao;

import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.railreserve.model.Booking;
import com.railreserve.testing.DemoPaymentTestDatabase;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SimulationScenarioMigrationTest {
    @Test public void versionThreeUpgradePreservesPaidAndLegacyTicketsWithoutRebuildingTables() {
        try (DemoPaymentTestDatabase f = new DemoPaymentTestDatabase(true)) {
            Map<String, String> originalSchemas = new HashMap<>();
            String[] tables = {"trains", "journeys", "seats", "bookings", "demo_payments"};
            try (SQLiteDatabase old = SQLiteDatabase.openOrCreateDatabase(f.file, null)) {
                DemoPaymentMigrationTest.createVersionTwo(old);
                old.execSQL("CREATE TABLE demo_payments (payment_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "transaction_reference TEXT NOT NULL UNIQUE CHECK(length(trim(transaction_reference))>0), "
                        + "checkout_reference TEXT NOT NULL UNIQUE CHECK(length(trim(checkout_reference))>0), "
                        + "booking_id INTEGER NOT NULL UNIQUE REFERENCES bookings(booking_id), "
                        + "payment_method TEXT NOT NULL CHECK(payment_method='DEMO_UPI'), "
                        + "amount_paise INTEGER NOT NULL CHECK(typeof(amount_paise)='integer' AND amount_paise>=0), "
                        + "payment_status TEXT NOT NULL CHECK(payment_status='SUCCESS'), created_at TEXT NOT NULL, "
                        + "refund_status TEXT NOT NULL DEFAULT 'NONE' CHECK(refund_status IN ('NONE','SIMULATED_REFUNDED')), "
                        + "refunded_at TEXT, CHECK((refund_status='NONE' AND refunded_at IS NULL) OR "
                        + "(refund_status='SIMULATED_REFUNDED' AND refunded_at IS NOT NULL)))");
                old.execSQL("INSERT INTO trains VALUES (1,'90001','Existing Train','From','To','08:00','12:00',0)");
                old.execSQL("INSERT INTO journeys VALUES (1,1,'2026-11-12')");
                old.execSQL("INSERT INTO seats VALUES (1,1,'SL','S1',1,100,'BOOKED')");
                old.execSQL("INSERT INTO seats VALUES (2,1,'SL','S1',2,100,'AVAILABLE')");
                old.execSQL("INSERT INTO bookings VALUES (1,'1234567890',1,'Paid Passenger',25,'Other','SL',100,'CONFIRMED','2026-11-12T00:00:00Z',NULL)");
                old.execSQL("INSERT INTO bookings VALUES (2,'2234567890',2,'Legacy Passenger',25,'Other','SL',100,'CANCELLED','2026-11-12T00:00:00Z','2026-11-12T01:00:00Z')");
                old.execSQL("INSERT INTO demo_payments (transaction_reference,checkout_reference,booking_id,payment_method,amount_paise,payment_status,created_at) "
                        + "VALUES ('DEMO-TXN-existing','DEMO-CHK-12345678-1234-1234-1234-123456789abc',1,'DEMO_UPI',10000,'SUCCESS','2026-11-12T00:00:00Z')");
                for (String table : tables) originalSchemas.put(table, schema(old, table));
                old.setVersion(3);
            }
            SQLiteDatabase upgraded = f.helper.getWritableDatabase();
            assertEquals(7, upgraded.getVersion());
            for (String table : tables) {
                String expected = expanded(originalSchemas.get(table));
                if ("bookings".equals(table)) {
                    expected = expected.replace("cancelled_at TEXT)",
                            "cancelled_at TEXT, hidden_from_list INTEGER NOT NULL DEFAULT 0 CHECK(hidden_from_list IN (0,1)))");
                }
                assertEquals(table, expected, expanded(schema(upgraded, table)));
            }
            assertEquals(0, DatabaseUtils.queryNumEntries(upgraded, "simulation_scenarios"));
            Booking paid = f.service.findBookingByPnr("1234567890");
            assertEquals("90001", paid.getTrainNumber());
            assertEquals("Existing Train", paid.getTrainName());
            assertEquals("DEMO-TXN-existing", paid.getPayment().getTransactionReference());
            assertEquals("SUCCESS", paid.getPayment().getStatus());
            assertNull(f.service.findBookingByPnr("2234567890").getPayment());
            assertEquals(2, f.service.getBookings().size());
            f.service.cancelBooking(paid.getId());
            assertEquals("SIMULATED_REFUNDED", f.service.findBookingByPnr(paid.getPnr()).getPayment().getRefundStatus());
            assertEquals(2, f.service.getAvailableSeats(1, "SL").size());
        }
    }

    private String schema(SQLiteDatabase db, String table) {
        return DatabaseUtils.stringForQuery(db, "SELECT sql FROM sqlite_master WHERE type='table' AND name=?", new String[]{table});
    }
    private String expanded(String sql) { return sql.replace("\"", "").replace("travel_class IN ('SL','3A')", "travel_class IN ('SL','3A','2A','1A','3E','CC','EC','2S')"); }

}
