package com.railreserve.service;

import com.railreserve.model.Booking;
import com.railreserve.model.TravelClass;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class BookingHistoryOrganizerTest {
    private final LocalDate today = LocalDate.of(2026, 10, 19);

    @Test public void classifiesCancelledBeforeDatesAndSeparatesToday() {
        Booking past = booking(1, "2026-10-18", "CONFIRMED", "Past Train");
        Booking todayBooking = booking(2, "2026-10-19", "CONFIRMED", "Today Train");
        Booking future = booking(3, "2026-10-20", "CONFIRMED", "Future Train");
        Booking cancelledFuture = booking(4, "2026-10-20", "CANCELLED", "Cancelled Future");
        Booking cancelledPast = booking(5, "2026-10-18", "CANCELLED", "Cancelled Past");
        List<Booking> all = Arrays.asList(past, todayBooking, future, cancelledFuture, cancelledPast);

        assertEquals(Arrays.asList(todayBooking, future), BookingHistoryOrganizer.organize(all, "",
                BookingHistoryOrganizer.StatusFilter.UPCOMING,
                BookingHistoryOrganizer.JourneySort.NEAREST_FIRST, today));
        assertEquals(Arrays.asList(past), BookingHistoryOrganizer.organize(all, "",
                BookingHistoryOrganizer.StatusFilter.COMPLETED,
                BookingHistoryOrganizer.JourneySort.NEAREST_FIRST, today));
        assertEquals(Arrays.asList(cancelledPast, cancelledFuture), BookingHistoryOrganizer.organize(all, "",
                BookingHistoryOrganizer.StatusFilter.CANCELLED,
                BookingHistoryOrganizer.JourneySort.NEAREST_FIRST, today));
    }

    @Test public void searchSortAndInvalidDatesAreSafeAndStable() {
        Booking firstEqual = booking(1, "2026-10-20", "CONFIRMED", "Awadh Express");
        Booking secondEqual = booking(2, "2026-10-20", "CONFIRMED", "Awadh Local");
        Booking past = booking(3, "2026-10-18", "CONFIRMED", "Other Train");
        Booking invalid = booking(4, "not-a-date", "CONFIRMED", "Awadh Invalid Date");
        List<Booking> all = Arrays.asList(firstEqual, secondEqual, past, invalid);

        List<Booking> searched = BookingHistoryOrganizer.organize(all, "AWADH",
                BookingHistoryOrganizer.StatusFilter.ALL,
                BookingHistoryOrganizer.JourneySort.NEAREST_FIRST, today);
        assertEquals(Arrays.asList(firstEqual, secondEqual, invalid), searched);
        assertSame(firstEqual, searched.get(0));

        assertEquals(Arrays.asList(firstEqual, secondEqual, past, invalid),
                BookingHistoryOrganizer.organize(all, "",
                        BookingHistoryOrganizer.StatusFilter.ALL,
                        BookingHistoryOrganizer.JourneySort.MOST_RECENT_FIRST, today));
        assertEquals(Arrays.asList(invalid), BookingHistoryOrganizer.organize(all, "0000000004",
                BookingHistoryOrganizer.StatusFilter.ALL,
                BookingHistoryOrganizer.JourneySort.NEAREST_FIRST, today));
    }

    private Booking booking(long id, String date, String status, String trainName) {
        return new Booking(id, String.format(java.util.Locale.ROOT, "%010d", id), id, id,
                "90" + id, trainName, "Prayagraj", "New Delhi", date, "08:00", "12:00", 0,
                "Passenger", 30, "Other", "S1", (int) id, TravelClass.SLEEPER, 500,
                status, "2026-10-01T10:00:00Z", null);
    }
}
