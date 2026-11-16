package com.railreserve.dao;
import com.railreserve.model.Booking;
import com.railreserve.model.BookingRequest;
import com.railreserve.model.Seat;
import java.time.LocalDate;
import java.util.List;
public interface BookingDao {
    List<Seat> getAvailableSeats(long journeyId, String travelClass);
    Booking createBooking(BookingRequest request);
    List<Booking> getBookings();
    List<Booking> getVisibleBookings(LocalDate today);
    Booking findBookingByPnr(String pnr);
    Booking findBookingByCheckoutReference(String checkoutReference);
    void cancelBooking(long bookingId);
    void hideBookingFromList(long bookingId);
}
