package com.railreserve.model;

public final class BookingRequest {
    private final long journeyId,seatId; private final TravelClass travelClass; private final String passengerName,gender; private final int age;
    public BookingRequest(long journeyId,long seatId,TravelClass travelClass,String passengerName,int age,String gender){this.journeyId=journeyId;this.seatId=seatId;this.travelClass=travelClass;this.passengerName=passengerName;this.age=age;this.gender=gender;}
    public long getJourneyId(){return journeyId;} public long getSeatId(){return seatId;} public TravelClass getTravelClass(){return travelClass;} public String getPassengerName(){return passengerName;} public int getAge(){return age;} public String getGender(){return gender;}
}
