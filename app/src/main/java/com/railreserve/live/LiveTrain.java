package com.railreserve.live;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LiveTrain implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final String number, name, type, departure, arrival;
    private final LiveStation source, destination;
    private final Integer departureDay, arrivalDay, durationMinutes, totalHaltsBetween;
    private final Double distanceKm;
    private final List<String> runDays;
    private final List<String> classes;
    private final String classSource;

    public LiveTrain(String number, String name, String type, LiveStation source, LiveStation destination,
                     String departure, Integer departureDay, String arrival, Integer arrivalDay,
                     Integer durationMinutes, Double distanceKm, Integer totalHaltsBetween, List<String> runDays) {
        this(number, name, type, source, destination, departure, departureDay, arrival, arrivalDay,
                durationMinutes, distanceKm, totalHaltsBetween, runDays, null, null);
    }

    public LiveTrain(String number, String name, String type, LiveStation source, LiveStation destination,
                     String departure, Integer departureDay, String arrival, Integer arrivalDay,
                     Integer durationMinutes, Double distanceKm, Integer totalHaltsBetween, List<String> runDays,
                     List<String> classes, String classSource) {
        this.number = number;
        this.name = name;
        this.type = type;
        this.source = source;
        this.destination = destination;
        this.departure = departure;
        this.departureDay = departureDay;
        this.arrival = arrival;
        this.arrivalDay = arrivalDay;
        this.durationMinutes = durationMinutes;
        this.distanceKm = distanceKm;
        this.totalHaltsBetween = totalHaltsBetween;
        this.runDays = runDays == null ? null : Collections.unmodifiableList(new ArrayList<>(runDays));
        this.classes = classes == null ? null : Collections.unmodifiableList(new ArrayList<>(classes));
        this.classSource = classSource;
    }

    public String getNumber() { return number; }
    public String getName() { return name; }
    public String getType() { return type; }
    public LiveStation getSource() { return source; }
    public LiveStation getDestination() { return destination; }
    public String getDeparture() { return departure; }
    public Integer getDepartureDay() { return departureDay; }
    public String getArrival() { return arrival; }
    public Integer getArrivalDay() { return arrivalDay; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public Double getDistanceKm() { return distanceKm; }
    public Integer getTotalHaltsBetween() { return totalHaltsBetween; }
    public List<String> getRunDays() { return runDays; }
    public List<String> getClasses() { return classes; }
    public String getClassSource() { return classSource; }
}
