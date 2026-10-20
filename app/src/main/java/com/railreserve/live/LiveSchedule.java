package com.railreserve.live;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LiveSchedule {
    private final String number, name, type, category;
    private final LiveStation source, destination;
    private final List<String> runDays;
    private final List<LiveTrainStop> stops;

    public LiveSchedule(String number, String name, String type, String category, LiveStation source,
                        LiveStation destination, List<String> runDays, List<LiveTrainStop> stops) {
        this.number = number;
        this.name = name;
        this.type = type;
        this.category = category;
        this.source = source;
        this.destination = destination;
        this.runDays = runDays == null ? null : Collections.unmodifiableList(new ArrayList<>(runDays));
        this.stops = Collections.unmodifiableList(new ArrayList<>(stops));
    }

    public String getNumber() { return number; }
    public String getName() { return name; }
    public String getType() { return type; }
    public String getCategory() { return category; }
    public LiveStation getSource() { return source; }
    public LiveStation getDestination() { return destination; }
    public List<String> getRunDays() { return runDays; }
    public List<LiveTrainStop> getStops() { return stops; }
}
