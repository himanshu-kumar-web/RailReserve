package com.railreserve.service;
import com.railreserve.model.Booking;
import com.railreserve.model.BookingRequest;
import com.railreserve.model.Seat;
import java.util.List;
public interface BookingService { List<Seat> getAvailableSeats(long journeyId,String travelClass); Booking createBooking(BookingRequest request); List<Booking> getBookings(); Booking findBookingByPnr(String pnr); void cancelBooking(long bookingId); }
