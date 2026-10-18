package com.railreserve.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.railreserve.database.DatabaseHelper;
import com.railreserve.model.Booking;
import com.railreserve.model.BookingRequest;
import com.railreserve.model.Seat;
import com.railreserve.model.SeatStatus;
import com.railreserve.model.TravelClass;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class SQLiteBookingDao implements BookingDao {
    private final DatabaseHelper helper; private final SecureRandom random=new SecureRandom();
    public SQLiteBookingDao(DatabaseHelper helper){this.helper=helper;}
    @Override public List<Seat> getAvailableSeats(long journeyId,String travelClass){List<Seat> seats=new ArrayList<>();try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT seat_id,journey_id,travel_class,coach,seat_number,fare,status FROM seats WHERE journey_id=? AND travel_class=? AND status='AVAILABLE' ORDER BY coach,seat_number",new String[]{Long.toString(journeyId),travelClass})){while(c.moveToNext())seats.add(new Seat(c.getLong(0),c.getLong(1),TravelClass.fromCode(c.getString(2)),c.getString(3),c.getInt(4),c.getInt(5),SeatStatus.valueOf(c.getString(6))));}return seats;}
    @Override public Booking createBooking(BookingRequest request){SQLiteDatabase db=helper.getWritableDatabase();db.beginTransaction();try{
        long journeyId;String clazz;int fare;
        try(Cursor c=db.rawQuery("SELECT s.journey_id,s.travel_class,s.fare FROM seats s JOIN journeys j ON j.journey_id=s.journey_id WHERE s.seat_id=? AND j.journey_id=?",new String[]{Long.toString(request.getSeatId()),Long.toString(request.getJourneyId())})){if(!c.moveToFirst())throw new IllegalStateException("That seat does not belong to this journey.");journeyId=c.getLong(0);clazz=c.getString(1);fare=c.getInt(2);}
        if(!clazz.equals(request.getTravelClass().getCode()))throw new IllegalStateException("That seat is not in the selected class.");
        ContentValues seatUpdate=new ContentValues();seatUpdate.put("status","BOOKED");int changed=db.update("seats",seatUpdate,"seat_id=? AND journey_id=? AND travel_class=? AND status='AVAILABLE'",new String[]{Long.toString(request.getSeatId()),Long.toString(journeyId),clazz});if(changed!=1)throw new IllegalStateException("This seat is no longer available. Please choose another seat.");
        String pnr=null;for(int attempt=0;attempt<12;attempt++){StringBuilder digits=new StringBuilder(10);digits.append(1+random.nextInt(9));for(int i=0;i<9;i++)digits.append(random.nextInt(10));String candidate=digits.toString();try(Cursor c=db.rawQuery("SELECT 1 FROM bookings WHERE pnr=?",new String[]{candidate})){if(!c.moveToFirst()){pnr=candidate;break;}}}if(pnr==null)throw new IllegalStateException("Could not generate a unique demo PNR. Please try again.");
        ContentValues values=new ContentValues();values.put("pnr",pnr);values.put("seat_id",request.getSeatId());values.put("passenger_name",request.getPassengerName().trim());values.put("passenger_age",request.getAge());values.put("passenger_gender",request.getGender());values.put("travel_class",clazz);values.put("fare",fare);values.put("booking_status","CONFIRMED");values.put("booked_at",Instant.now().toString());long id=db.insertOrThrow("bookings",null,values);Booking booking=readBooking(db,"b.booking_id=?",new String[]{Long.toString(id)});db.setTransactionSuccessful();return booking;
    }finally{db.endTransaction();}}
    @Override public List<Booking> getBookings(){List<Booking> list=new ArrayList<>();String sql=bookingSelect()+" ORDER BY b.booked_at DESC,b.booking_id DESC";try(Cursor c=helper.getReadableDatabase().rawQuery(sql,null)){while(c.moveToNext())list.add(readBooking(c));}return list;}
    @Override public void cancelBooking(long bookingId){SQLiteDatabase db=helper.getWritableDatabase();db.beginTransaction();try{long seatId;try(Cursor c=db.rawQuery("SELECT seat_id FROM bookings WHERE booking_id=? AND booking_status='CONFIRMED'",new String[]{Long.toString(bookingId)})){if(!c.moveToFirst())throw new IllegalStateException("This booking is already cancelled or no longer exists.");seatId=c.getLong(0);}ContentValues booking=new ContentValues();booking.put("booking_status","CANCELLED");booking.put("cancelled_at",Instant.now().toString());if(db.update("bookings",booking,"booking_id=? AND booking_status='CONFIRMED'",new String[]{Long.toString(bookingId)})!=1)throw new IllegalStateException("This booking has already been cancelled.");ContentValues seat=new ContentValues();seat.put("status","AVAILABLE");if(db.update("seats",seat,"seat_id=? AND status='BOOKED'",new String[]{Long.toString(seatId)})!=1)throw new IllegalStateException("The booked seat could not be released; cancellation was rolled back.");db.setTransactionSuccessful();}finally{db.endTransaction();}}
    private Booking readBooking(SQLiteDatabase db,String where,String[] args){try(Cursor c=db.rawQuery(bookingSelect()+" WHERE "+where,args)){if(!c.moveToFirst())throw new IllegalStateException("Saved booking could not be loaded.");return readBooking(c);}}
    private Booking readBooking(Cursor c){return new Booking(c.getLong(0),c.getString(1),c.getLong(2),c.getLong(3),c.getString(4),c.getString(5),c.getString(6),c.getString(7),c.getString(8),c.getString(9),c.getString(10),c.getInt(11),c.getString(12),c.getInt(13),c.getString(14),c.getString(15),c.getInt(16),TravelClass.fromCode(c.getString(17)),c.getInt(18),c.getString(19),c.getString(20),c.isNull(21)?null:c.getString(21));}
    private String bookingSelect(){return "SELECT b.booking_id,b.pnr,b.seat_id,j.journey_id,t.train_number,t.name,t.source_station,t.destination_station,j.journey_date,t.departure_time,t.arrival_time,t.arrival_day_offset,b.passenger_name,b.passenger_age,b.passenger_gender,s.coach,s.seat_number,b.travel_class,b.fare,b.booking_status,b.booked_at,b.cancelled_at FROM bookings b JOIN seats s ON s.seat_id=b.seat_id JOIN journeys j ON j.journey_id=s.journey_id JOIN trains t ON t.train_id=j.train_id";}
}
