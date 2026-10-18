package com.railreserve.model;
public final class Train {
    private final long id; private final String number, name, source, destination, departure, arrival; private final int arrivalDayOffset;
    public Train(long id, String number, String name, String source, String destination, String departure, String arrival, int offset) { this.id=id; this.number=number; this.name=name; this.source=source; this.destination=destination; this.departure=departure; this.arrival=arrival; this.arrivalDayOffset=offset; }
    public long getId(){return id;} public String getNumber(){return number;} public String getName(){return name;} public String getSource(){return source;} public String getDestination(){return destination;} public String getDeparture(){return departure;} public String getArrival(){return arrival;} public int getArrivalDayOffset(){return arrivalDayOffset;}
}
