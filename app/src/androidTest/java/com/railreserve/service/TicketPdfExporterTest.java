package com.railreserve.service;

import com.railreserve.model.Booking;
import com.railreserve.model.TravelClass;
import android.graphics.Canvas;
import android.graphics.Paint;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.assertFalse;
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

    @Test public void activeWaitlistPdfHasANumberAndNoAllocatedSeat() throws Exception {
        verifyWaitlistPdf("WAITING_LIST", "No seat allocated");
    }

    @Test public void cancelledWaitlistPdfKeepsNumberWithoutInventingASeat() throws Exception {
        verifyWaitlistPdf("CANCELLED", "Cancelled wait-list booking; no seat was allocated.");
    }

    private void verifyWaitlistPdf(String status, String expectedDescription) throws Exception {
        Booking booking = new Booking(1, "1234567890", 0, 1, "90339", "Fixture Express",
                "AAA", "BBB", "2026-11-20", "08:00", "12:00", 0,
                "Test Passenger", 25, "Other", null, 0, TravelClass.SLEEPER,
                245, status, "2026-11-18T00:00:00Z", null, null, null, 3);
        List<String> drawn = new ArrayList<>();
        Canvas canvas = new Canvas() {
            @Override public void drawText(String text, float x, float y, Paint paint) { drawn.add(text); }
        };
        Method draw = TicketPdfExporter.class.getDeclaredMethod("drawTicket", Canvas.class, Booking.class);
        draw.setAccessible(true);
        draw.invoke(null, canvas, booking);
        String content = String.join("\n", drawn);
        assertTrue(content, content.contains("WL 3"));
        assertTrue(content, content.contains(expectedDescription));
        assertFalse(content, content.contains("Seat 0"));
        assertFalse(content, content.contains("Not provided 0"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        TicketPdfExporter.write(output, booking);
        assertTrue(output.size() > 1000);
        assertTrue(new String(output.toByteArray(), 0, 4, StandardCharsets.US_ASCII).equals("%PDF"));
    }
}
