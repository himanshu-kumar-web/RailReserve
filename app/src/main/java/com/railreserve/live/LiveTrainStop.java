package com.railreserve.live;

public final class LiveTrainStop {
    private final Integer sequence;
    private final LiveStation station;
    private final String arrival, departure, platform;
    private final Integer arrivalDay, departureDay;
    private final Double distanceKm;
    private final Boolean halt;

    public LiveTrainStop(Integer sequence, LiveStation station, String arrival, String departure,
                         Integer arrivalDay, Integer departureDay, Double distanceKm, Boolean halt, String platform) {
        this.sequence = sequence;
        this.station = station;
        this.arrival = arrival;
        this.departure = departure;
        this.arrivalDay = arrivalDay;
        this.departureDay = departureDay;
        this.distanceKm = distanceKm;
        this.halt = halt;
        this.platform = platform;
    }

    public Integer getSequence() { return sequence; }
    public LiveStation getStation() { return station; }
    public String getArrival() { return arrival; }
    public String getDeparture() { return departure; }
    public Integer getArrivalDay() { return arrivalDay; }
    public Integer getDepartureDay() { return departureDay; }
    public Double getDistanceKm() { return distanceKm; }
    public Boolean isHalt() { return halt; }
    public String getPlatform() { return platform; }
}
