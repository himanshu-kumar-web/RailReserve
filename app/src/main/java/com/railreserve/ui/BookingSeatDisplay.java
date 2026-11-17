package com.railreserve.ui;

import com.railreserve.model.Booking;

/** Formats local wait-list reservations that have never received a demo seat. */
public final class BookingSeatDisplay {
    private BookingSeatDisplay() { }

    public static boolean hasNoAllocatedSeat(Booking booking) {
        return booking != null && booking.getSeatId() <= 0 && booking.getWaitlistNumber() != null;
    }

    public static String waitlistDescription(Booking booking) {
        if (!hasNoAllocatedSeat(booking)) {
            throw new IllegalArgumentException("A seatless wait-list booking is required.");
        }
        String state = "CANCELLED".equalsIgnoreCase(booking.getStatus())
                ? "Cancelled wait-list booking; no seat was allocated."
                : "No seat allocated while waitlisted.";
        return "WL " + booking.getWaitlistNumber() + " · " + state;
    }
}
