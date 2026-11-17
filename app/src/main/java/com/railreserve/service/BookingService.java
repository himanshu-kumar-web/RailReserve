package com.railreserve.service;
import com.railreserve.model.Booking;
import com.railreserve.model.BookingRequest;
import com.railreserve.model.Seat;
import java.util.List;
public interface BookingService {
    List<Seat> getAvailableSeats(long journeyId, String travelClass);
    Booking createBooking(BookingRequest request);
    Booking confirmWaitingList(long bookingId);
    List<Booking> getBookings();
    List<Booking> getVisibleBookings();
    Booking findBookingByPnr(String pnr);
    Booking findBookingByCheckoutReference(String checkoutReference);
    void cancelBooking(long bookingId);
    void hideBookingFromList(long bookingId);
}
