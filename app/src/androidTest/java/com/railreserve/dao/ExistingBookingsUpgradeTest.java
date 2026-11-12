package com.railreserve.dao;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.railreserve.database.DatabaseHelper;
import com.railreserve.model.Booking;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Read-only verification of real saved records; stores hashes in the app's private cache. */
@RunWith(AndroidJUnit4.class)
public class ExistingBookingsUpgradeTest {
    @Test public void savedBookingsRemainReadableAcrossApkUpgrade() throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File baseline = new File(target.getCacheDir(), "phase3-existing-booking-fingerprints.json");
        DatabaseHelper helper = new DatabaseHelper(target);
        try {
            SQLiteBookingDao dao = new SQLiteBookingDao(helper);
            String mode = InstrumentationRegistry.getArguments().getString("compatibilityMode", "verify");
            if ("capture".equals(mode)) {
                JSONObject snapshot = new JSONObject();
                for (Booking booking : dao.getBookings()) snapshot.put(Long.toString(booking.getId()), fingerprint(booking));
                Files.write(baseline.toPath(), snapshot.toString().getBytes(StandardCharsets.UTF_8));
            } else {
                assumeTrue("Capture the installed application's records before upgrade", baseline.isFile());
                JSONObject expected = new JSONObject(new String(Files.readAllBytes(baseline.toPath()), StandardCharsets.UTF_8));
                JSONObject current = new JSONObject();
                for (Booking booking : dao.getBookings()) {
                    current.put(Long.toString(booking.getId()), fingerprint(booking));
                    assertEquals(booking.getId(), dao.findBookingByPnr(booking.getPnr()).getId());
                }
                java.util.Iterator<String> ids = expected.keys();
                while (ids.hasNext()) {
                    String id = ids.next();
                    assertTrue("Existing booking missing after update", current.has(id));
                    assertEquals("Existing record changed during upgrade", expected.getString(id), current.getString(id));
                }
            }
        } finally { helper.close(); }
    }

    private String fingerprint(Booking booking) throws Exception {
        String value = booking.getId() + "|" + booking.getPnr() + "|" + booking.getSeatId() + "|" + booking.getJourneyId()
                + "|" + booking.getPassengerName() + "|" + booking.getAge() + "|" + booking.getGender()
                + "|" + booking.getTravelClass().getCode() + "|" + booking.getFare() + "|" + booking.getStatus()
                + "|" + booking.getBookedAt() + "|" + booking.getCancelledAt() + "|" + booking.getJourneyDate()
                + "|" + booking.getTrainNumber() + "|" + booking.getTrainName() + "|" + booking.getSource()
                + "|" + booking.getDestination() + "|" + booking.getDeparture() + "|" + booking.getArrival()
                + "|" + booking.getArrivalDayOffset();
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder text = new StringBuilder();
        for (byte item : hash) text.append(String.format(java.util.Locale.ROOT, "%02x", item & 0xff));
        return text.toString();
    }
}
