package com.railreserve.ui;

import com.railreserve.model.Booking;
import com.railreserve.model.TravelClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class BookingSeatDisplayTest {
    private Booking booking(long seatId, String status, Integer waitlistNumber) {
        return new Booking(1, "1234567890", seatId, 9,
                "15004", "Demo Train", "GKP", "PRYJ", "2026-11-20",
                "20:00", "08:00", 1,
                "Test Passenger", 25, "Other", seatId == 0 ? null : "DEMO-S1",
                seatId == 0 ? 0 : 1, TravelClass.SLEEPER, 245, status,
                "2026-11-18T00:00:00Z",
                "CANCELLED".equals(status) ? "2026-11-18T01:00:00Z" : null,
                null, null, waitlistNumber);
    }

    @Test public void cancelledWaitlistRetainsNumberWithoutFabricatingSeat() {
        Booking cancelled = booking(0, "CANCELLED", 3);
        assertTrue(BookingSeatDisplay.hasNoAllocatedSeat(cancelled));
        assertEquals("WL 3 · Cancelled wait-list booking; no seat was allocated.",
                BookingSeatDisplay.waitlistDescription(cancelled));
        assertFalse(BookingSeatDisplay.waitlistDescription(cancelled).contains("null"));
    }

    @Test public void activeWaitlistUsesSeatlessDescription() {
        Booking waiting = booking(0, "WAITING_LIST", 2);
        assertTrue(BookingSeatDisplay.hasNoAllocatedSeat(waiting));
        assertEquals("WL 2 · No seat allocated while waitlisted.",
                BookingSeatDisplay.waitlistDescription(waiting));
    }

    @Test public void seatedBookingIsNotDescribedAsWaitlisted() {
        Booking confirmed = booking(5, "CONFIRMED", null);
        assertFalse(BookingSeatDisplay.hasNoAllocatedSeat(confirmed));
        assertThrows(IllegalArgumentException.class,
                () -> BookingSeatDisplay.waitlistDescription(confirmed));
    }
}
