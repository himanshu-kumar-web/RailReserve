package com.railreserve.model;

public final class BookingRequest {
    private final FareSnapshot fareSnapshot;
    private final String checkoutReference;
    private final Long expectedAmountPaise;
    private final long journeyId,seatId; private final TravelClass travelClass; private final String passengerName,gender; private final int age;
    public BookingRequest(long journeyId,long seatId,TravelClass travelClass,String passengerName,int age,String gender){this(journeyId,seatId,travelClass,passengerName,age,gender,null,null);}
    public BookingRequest(long journeyId,long seatId,TravelClass travelClass,String passengerName,int age,String gender,String checkoutReference,Long expectedAmountPaise){this(journeyId,seatId,travelClass,passengerName,age,gender,checkoutReference,expectedAmountPaise,null);}
    public BookingRequest(long journeyId,long seatId,TravelClass travelClass,String passengerName,int age,String gender,String checkoutReference,Long expectedAmountPaise,FareSnapshot fareSnapshot){this.journeyId=journeyId;this.seatId=seatId;this.travelClass=travelClass;this.passengerName=passengerName;this.age=age;this.gender=gender;this.checkoutReference=checkoutReference;this.expectedAmountPaise=expectedAmountPaise;this.fareSnapshot=fareSnapshot;}
    public FareSnapshot getFareSnapshot(){return fareSnapshot;}
    public String getCheckoutReference(){return checkoutReference;}
    public Long getExpectedAmountPaise(){return expectedAmountPaise;}
    public long getJourneyId(){return journeyId;} public long getSeatId(){return seatId;} public TravelClass getTravelClass(){return travelClass;} public String getPassengerName(){return passengerName;} public int getAge(){return age;} public String getGender(){return gender;}
}
