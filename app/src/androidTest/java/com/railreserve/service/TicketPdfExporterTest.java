package com.railreserve.service;

import com.railreserve.model.Booking;
import com.railreserve.model.TravelClass;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class TicketPdfExporterTest {
    @Test
    public void confirmedBookingExportsAsPdf() throws Exception {
        Booking booking = new Booking(1, "1234567890", 1, 1, "12345", "Demo Express",
                "NDLS", "HWH", "2026-11-20", "20:00", "08:00", 1,
                "Test Passenger", 30, "Other", "S1", 12, TravelClass.SLEEPER,
                250, "CONFIRMED", "2026-11-16T10:00:00Z", null);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        TicketPdfExporter.write(output, booking);

        byte[] pdf = output.toByteArray();
        assertTrue(pdf.length > 1000);
        assertTrue(new String(pdf, 0, 4, StandardCharsets.US_ASCII).startsWith("%PDF"));
    }
}
