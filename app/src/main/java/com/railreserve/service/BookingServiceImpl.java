package com.railreserve.service;
import com.railreserve.dao.BookingDao;
import com.railreserve.model.Booking;
import com.railreserve.model.BookingRequest;
import com.railreserve.model.Seat;
import com.railreserve.model.TravelClass;
import java.util.List;
import java.util.Locale;
public final class BookingServiceImpl implements BookingService {
    private final BookingDao dao;
    public BookingServiceImpl(BookingDao dao){this.dao=dao;}
    @Override public List<Seat> getAvailableSeats(long journeyId,String clazz){if(journeyId<=0)throw new IllegalArgumentException("Choose a valid journey.");TravelClass travelClass=TravelClass.fromCode(clazz);return dao.getAvailableSeats(journeyId,travelClass.getCode());}
    @Override public Booking createBooking(BookingRequest request){if(request==null)throw new IllegalArgumentException("Booking details are missing.");if(request.getJourneyId()<=0||request.getSeatId()<=0)throw new IllegalArgumentException("Choose a valid journey and seat.");if(request.getTravelClass()==null)throw new IllegalArgumentException("Choose a valid travel class.");String name=request.getPassengerName()==null?"":request.getPassengerName().trim();if(name.length()<2||name.length()>80)throw new IllegalArgumentException("Passenger name must be 2 to 80 characters.");if(request.getAge()<1||request.getAge()>120)throw new IllegalArgumentException("Passenger age must be between 1 and 120.");String gender=request.getGender()==null?"":request.getGender().trim();if(!gender.equals("Female")&&!gender.equals("Male")&&!gender.equals("Other"))throw new IllegalArgumentException("Choose a gender.");if(request.getCheckoutReference()!=null){validateCheckoutReference(request.getCheckoutReference());if(request.getExpectedAmountPaise()==null||request.getExpectedAmountPaise()<0)throw new IllegalArgumentException("Review the demo fare before payment.");}else if(request.getExpectedAmountPaise()!=null)throw new IllegalArgumentException("A checkout reference is required for demo payment.");if(request.getFareSnapshot()!=null){request.getFareSnapshot().validate();if(request.getCheckoutReference()==null||request.getExpectedAmountPaise()==null||request.getExpectedAmountPaise()!=request.getFareSnapshot().getAmountPaise())throw new IllegalArgumentException("Review the captured provider fare before payment.");}BookingRequest clean=new BookingRequest(request.getJourneyId(),request.getSeatId(),request.getTravelClass(),name,request.getAge(),gender,request.getCheckoutReference(),request.getExpectedAmountPaise(),request.getFareSnapshot());return dao.createBooking(clean);}
    @Override public List<Booking> getBookings(){return dao.getBookings();}
    @Override public Booking findBookingByPnr(String pnr){String value=pnr==null?"":pnr.trim();if(value.isEmpty())throw new IllegalArgumentException("Enter your 10-digit PNR.");if(!value.matches("[0-9]{10}"))throw new IllegalArgumentException("PNR must contain exactly 10 digits.");return dao.findBookingByPnr(value);}
    @Override public Booking findBookingByCheckoutReference(String reference){validateCheckoutReference(reference);return dao.findBookingByCheckoutReference(reference);}
    private void validateCheckoutReference(String reference){if(reference==null||!reference.matches("DEMO-CHK-[a-fA-F0-9-]{36}"))throw new IllegalArgumentException("Invalid demo checkout reference. Review your booking again.");}
    @Override public void cancelBooking(long bookingId){if(bookingId<=0)throw new IllegalArgumentException("Choose a valid booking.");dao.cancelBooking(bookingId);}
}
