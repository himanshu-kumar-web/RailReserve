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
    private final LiveStation actualOrigin, actualDestination, boardingStation, alightingStation;
    private final boolean scheduleVerified, boardingStopVerified, destinationStopVerified;

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
        this(number,name,type,source,destination,departure,departureDay,arrival,arrivalDay,durationMinutes,
                distanceKm,totalHaltsBetween,runDays,classes,classSource,null,null,null,null,false,false,false);
    }

    private LiveTrain(String number, String name, String type, LiveStation source, LiveStation destination,
                      String departure, Integer departureDay, String arrival, Integer arrivalDay,
                      Integer durationMinutes, Double distanceKm, Integer totalHaltsBetween, List<String> runDays,
                      List<String> classes, String classSource, LiveStation actualOrigin,
                      LiveStation actualDestination, LiveStation boardingStation, LiveStation alightingStation,
                      boolean scheduleVerified, boolean boardingStopVerified, boolean destinationStopVerified) {
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
        this.actualOrigin = actualOrigin;
        this.actualDestination = actualDestination;
        this.boardingStation = boardingStation;
        this.alightingStation = alightingStation;
        this.scheduleVerified = scheduleVerified;
        this.boardingStopVerified = boardingStopVerified;
        this.destinationStopVerified = destinationStopVerified;
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
    public LiveStation getActualOrigin() { return actualOrigin; }
    public LiveStation getActualDestination() { return actualDestination; }
    public LiveStation getBoardingStation() { return boardingStation; }
    public LiveStation getAlightingStation() { return alightingStation; }
    public boolean isScheduleVerified() { return scheduleVerified; }
    public boolean isBoardingStopVerified() { return boardingStopVerified; }
    public boolean isDestinationStopVerified() { return destinationStopVerified; }
    public boolean isRouteVerified() { return boardingStopVerified && destinationStopVerified; }

    LiveTrain withSchedule(LiveSchedule schedule, LiveTrainStop boarding, LiveTrainStop alighting,
                           LiveStation origin, LiveStation terminus) {
        return new LiveTrain(number, name, type, source, destination,
                boarding == null ? departure : boarding.getDeparture(),
                boarding == null ? departureDay : boarding.getDepartureDay(),
                alighting == null ? arrival : alighting.getArrival(),
                alighting == null ? arrivalDay : alighting.getArrivalDay(),
                durationMinutes, distanceKm, totalHaltsBetween,
                runDays == null ? schedule.getRunDays() : runDays, classes, classSource,
                origin, terminus, boarding == null ? null : boarding.getStation(),
                alighting == null ? null : alighting.getStation(), true, boarding != null, alighting != null);
    }
}
