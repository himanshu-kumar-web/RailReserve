package com.railreserve.live;

import java.util.List;

/** Matches searched stops exactly and exposes schedule endpoints for clearly labelled enquiries. */
public final class TrainScheduleMatcher {
    private TrainScheduleMatcher() { }

    public static LiveTrain apply(LiveTrain train, LiveSchedule schedule) {
        if (train == null || schedule == null || train.getNumber() == null
                || !train.getNumber().equals(schedule.getNumber()) || schedule.getStops().isEmpty()) {
            return train;
        }
        List<LiveTrainStop> stops = schedule.getStops();
        int sourceIndex = findBoardingStop(stops, train.getSource());
        int destinationIndex = findAlightingStop(stops, train.getDestination(), sourceIndex);
        LiveTrainStop boarding = sourceIndex < 0 ? null : stops.get(sourceIndex);
        LiveTrainStop alighting = destinationIndex < 0 ? null : stops.get(destinationIndex);
        LiveStation origin = schedule.getSource() != null ? schedule.getSource() : stops.get(0).getStation();
        LiveStation terminus = schedule.getDestination() != null
                ? schedule.getDestination() : stops.get(stops.size() - 1).getStation();
        return train.withSchedule(schedule, boarding, alighting, origin, terminus);
    }

    public static TrainEnquiryRoute resolveEnquiryRoute(LiveTrain train, LiveSchedule schedule) {
        if (train == null || schedule == null || train.getNumber() == null
                || !train.getNumber().equals(schedule.getNumber()) || schedule.getStops().isEmpty()) return null;
        List<LiveTrainStop> stops = schedule.getStops();
        int searchedSource = findBoardingStop(stops, train.getSource());
        int boarding = searchedSource >= 0 ? searchedSource : findEndpoint(stops, schedule.getSource(), true);
        if (boarding < 0) return null;

        // A route may visit the same station more than once. Match the destination
        // after the selected boarding stop, rather than its first occurrence.
        int searchedDestination = findAlightingStop(stops, train.getDestination(), boarding);
        // If the searched destination occurs only before boarding, the requested
        // direction is invalid; do not silently substitute a different terminus.
        if (searchedDestination < 0 && findBoardingStop(stops, train.getDestination()) >= 0) return null;
        int alighting = searchedDestination >= 0
                ? searchedDestination : findEndpoint(stops, schedule.getDestination(), false);
        if (alighting <= boarding) return null;
        return new TrainEnquiryRoute(stops.get(boarding), stops.get(alighting),
                searchedSource >= 0, searchedDestination > boarding);
    }

    private static int findEndpoint(List<LiveTrainStop> stops, LiveStation endpoint, boolean first) {
        if (endpoint != null && endpoint.getCode() != null) {
            for (int i = 0; i < stops.size(); i++) {
                if (sameCode(stops.get(i).getStation(), endpoint) && Boolean.TRUE.equals(stops.get(i).isHalt())) {
                    if (first) return i;
                    int last = i;
                    for (int j = i + 1; j < stops.size(); j++) {
                        if (sameCode(stops.get(j).getStation(), endpoint) && Boolean.TRUE.equals(stops.get(j).isHalt())) last = j;
                    }
                    return last;
                }
            }
        }
        if (endpoint == null && !stops.isEmpty()) {
            int index = first ? 0 : stops.size() - 1;
            return Boolean.TRUE.equals(stops.get(index).isHalt()) ? index : -1;
        }
        return -1;
    }

    private static int findBoardingStop(List<LiveTrainStop> stops, LiveStation searched) {
        if (searched == null || searched.getCode() == null) return -1;
        for (int i = 0; i < stops.size(); i++) {
            LiveTrainStop stop = stops.get(i);
            if (sameCode(stop.getStation(), searched) && Boolean.TRUE.equals(stop.isHalt())) return i;
        }
        return -1;
    }

    private static int findAlightingStop(List<LiveTrainStop> stops, LiveStation searched, int afterIndex) {
        if (searched == null || searched.getCode() == null) return -1;
        int firstPossibleIndex = afterIndex < 0 ? 0 : afterIndex + 1;
        for (int i = firstPossibleIndex; i < stops.size(); i++) {
            LiveTrainStop stop = stops.get(i);
            if (sameCode(stop.getStation(), searched) && Boolean.TRUE.equals(stop.isHalt())) return i;
        }
        return -1;
    }

    private static boolean sameCode(LiveStation first, LiveStation second) {
        return first != null && first.getCode() != null && second != null
                && first.getCode().equalsIgnoreCase(second.getCode());
    }
}
