package com.railreserve.model;
public final class Journey { private final long id, trainId; private final String departureDate; public Journey(long id,long trainId,String date){this.id=id;this.trainId=trainId;this.departureDate=date;} public long getId(){return id;} public long getTrainId(){return trainId;} public String getDepartureDate(){return departureDate;} }
