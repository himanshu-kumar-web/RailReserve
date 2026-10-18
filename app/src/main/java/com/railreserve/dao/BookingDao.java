package com.railreserve.dao;
import com.railreserve.model.Booking;
import com.railreserve.model.BookingRequest;
import com.railreserve.model.Seat;
import java.util.List;
public interface BookingDao { List<Seat> getAvailableSeats(long journeyId,String travelClass); Booking createBooking(BookingRequest request); List<Booking> getBookings(); void cancelBooking(long bookingId); }
