package com.railreserve.service;

import com.railreserve.dao.BookingDao;
import com.railreserve.model.Booking;
import com.railreserve.model.BookingRequest;
import com.railreserve.model.Seat;
import com.railreserve.model.TravelClass;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class BookingServiceImplTest {
    @Test public void checkoutReferenceAndPaiseSurviveServiceValidation() {
        RecordingBookingDao dao = new RecordingBookingDao();
        BookingService service = new BookingServiceImpl(dao);
        String reference = "DEMO-CHK-12345678-1234-1234-1234-123456789abc";
        service.createBooking(new BookingRequest(1, 2, TravelClass.SLEEPER, " Passenger ", 25, "Other", reference, 10000L));
        assertEquals(reference, dao.lastRequest.getCheckoutReference());
        assertEquals(Long.valueOf(10000), dao.lastRequest.getExpectedAmountPaise());
        assertEquals("Passenger", dao.lastRequest.getPassengerName());
    }

    @Test public void malformedCheckoutAndInvalidAmountsNeverReachDao() {
        RecordingBookingDao dao = new RecordingBookingDao();
        BookingService service = new BookingServiceImpl(dao);
        String reference = "DEMO-CHK-12345678-1234-1234-1234-123456789abc";
        org.junit.Assert.assertThrows(IllegalArgumentException.class, () -> service.createBooking(
                new BookingRequest(1, 1, TravelClass.SLEEPER, "Passenger", 25, "Other", "invalid", 10000L)));
        org.junit.Assert.assertThrows(IllegalArgumentException.class, () -> service.createBooking(
                new BookingRequest(1, 1, TravelClass.SLEEPER, "Passenger", 25, "Other", reference, -1L)));
        org.junit.Assert.assertThrows(IllegalArgumentException.class, () -> service.createBooking(
                new BookingRequest(1, 1, TravelClass.SLEEPER, "Passenger", 25, "Other", null, 10000L)));
        assertEquals(0, dao.createCount);
    }
    @Test public void pnrIsTrimmedAndAWellFormedMissingPnrRemainsNotFound() {
        RecordingBookingDao dao = new RecordingBookingDao();
        BookingService service = new BookingServiceImpl(dao);

        assertNull(service.findBookingByPnr(" 1234567890 "));
        assertEquals("1234567890", dao.lastPnr);
        assertNull(service.findBookingByPnr("9999999999"));
        assertEquals("9999999999", dao.lastPnr);
    }

    @Test public void malformedPnrDoesNotReachDao() {
        RecordingBookingDao dao = new RecordingBookingDao();
        BookingService service = new BookingServiceImpl(dao);
        assertInvalidPnr(service, "");
        assertInvalidPnr(service, "123456789");
        assertInvalidPnr(service, "12345x7890");
        assertEquals(0, dao.lookupCount);
    }

    @Test public void invalidPassengerRequestDoesNotCreateBooking() {
        RecordingBookingDao dao = new RecordingBookingDao();
        BookingService service = new BookingServiceImpl(dao);
        try {
            service.createBooking(new BookingRequest(1, 1, TravelClass.SLEEPER,
                    "Passenger", 0, "Other"));
            fail("Expected invalid age to be rejected.");
        } catch (IllegalArgumentException expected) {
            assertEquals(0, dao.createCount);
        }
    }

    private void assertInvalidPnr(BookingService service, String pnr) {
        try {
            service.findBookingByPnr(pnr);
            fail("Expected malformed PNR to be rejected.");
        } catch (IllegalArgumentException expected) {
            // Expected validation behavior.
        }
    }

    private static final class RecordingBookingDao implements BookingDao {
        @Override public Booking findBookingByCheckoutReference(String reference) { return null; }
        String lastPnr;
        int lookupCount;
        int createCount;
        BookingRequest lastRequest;

        @Override public List<Seat> getAvailableSeats(long journeyId, String travelClass) {
            return Collections.emptyList();
        }
        @Override public Booking createBooking(BookingRequest request) {
            lastRequest = request;
            createCount++;
            return null;
        }
        @Override public List<Booking> getBookings() { return Collections.emptyList(); }
        @Override public Booking findBookingByPnr(String pnr) {
            lookupCount++;
            lastPnr = pnr;
            return null;
        }
        @Override public void cancelBooking(long bookingId) { }
    }
}
