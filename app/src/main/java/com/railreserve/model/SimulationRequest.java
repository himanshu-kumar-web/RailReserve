package com.railreserve.model;

import java.io.Serializable;
import java.util.Locale;

/** Timetable snapshot only; no provider fares or seat counts enter this request. */
public final class SimulationRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String number, name, sourceCode, sourceName, destinationCode, destinationName, date, departure, arrival;
    private final TravelClass travelClass;
    private final Integer departureDay, arrivalDay;

    public SimulationRequest(String number, String name, String sourceCode, String sourceName,
            String destinationCode, String destinationName, String date, TravelClass travelClass,
            String departure, String arrival, Integer departureDay, Integer arrivalDay) {
        this.number = clean(number); this.name = clean(name);
        this.sourceCode = clean(sourceCode).toUpperCase(Locale.ROOT); this.sourceName = clean(sourceName);
        this.destinationCode = clean(destinationCode).toUpperCase(Locale.ROOT); this.destinationName = clean(destinationName);
        this.date = clean(date); this.travelClass = travelClass;
        this.departure = clean(departure); this.arrival = clean(arrival);
        this.departureDay = departureDay; this.arrivalDay = arrivalDay;
    }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
    public String getNumber() { return number; }
    public String getName() { return name; }
    public String getSourceCode() { return sourceCode; }
    public String getDestinationCode() { return destinationCode; }
    public String getSourceLabel() { return sourceName.isEmpty() || sourceName.equalsIgnoreCase(sourceCode) ? sourceCode : sourceName + " (" + sourceCode + ")"; }
    public String getDestinationLabel() { return destinationName.isEmpty() || destinationName.equalsIgnoreCase(destinationCode) ? destinationCode : destinationName + " (" + destinationCode + ")"; }
    public String getDate() { return date; }
    public TravelClass getTravelClass() { return travelClass; }
    public String getDeparture() { return departure.isEmpty() ? "Not provided" : departure; }
    public String getArrival() { return arrival.isEmpty() ? "Not provided" : arrival; }
    public Integer getDepartureDay() { return departureDay; }
    public Integer getArrivalDay() { return arrivalDay; }
    public int getArrivalDayOffset() { return arrivalDay - departureDay; }
}
