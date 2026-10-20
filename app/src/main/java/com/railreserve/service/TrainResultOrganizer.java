package com.railreserve.service;

import com.railreserve.model.TrainSearchResult;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure in-memory ordering and availability filtering for the current search results. */
public final class TrainResultOrganizer {
    public enum Sort { ORIGINAL_ORDER, EARLIEST_DEPARTURE, LOWEST_FARE, SHORTEST_JOURNEY }

    private TrainResultOrganizer() { }

    public static List<TrainSearchResult> organize(List<TrainSearchResult> results,
                                                    boolean availableOnly, Sort sort) {
        List<TrainSearchResult> visible = new ArrayList<>();
        if (results != null) {
            for (TrainSearchResult result : results) {
                if (result != null && (!availableOnly || result.getAvailableSeats() > 0)) {
                    visible.add(result);
                }
            }
        }
        if (sort == Sort.EARLIEST_DEPARTURE) {
            visible.sort(Comparator.comparingLong(TrainResultOrganizer::departureMinutes));
        } else if (sort == Sort.LOWEST_FARE) {
            visible.sort(Comparator.comparingInt(result ->
                    result.getFare() < 0 ? Integer.MAX_VALUE : result.getFare()));
        } else if (sort == Sort.SHORTEST_JOURNEY) {
            visible.sort(Comparator.comparingLong(TrainResultOrganizer::journeyDurationMinutes));
        }
        return visible;
    }

    private static long departureMinutes(TrainSearchResult result) {
        try {
            return LocalTime.parse(result.getDeparture()).toSecondOfDay() / 60L;
        } catch (DateTimeParseException | NullPointerException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static long journeyDurationMinutes(TrainSearchResult result) {
        if (result.getArrivalDayOffset() < 0) return Long.MAX_VALUE;
        try {
            LocalTime departure = LocalTime.parse(result.getDeparture());
            LocalTime arrival = LocalTime.parse(result.getArrival());
            long duration = ChronoUnit.MINUTES.between(departure, arrival)
                    + result.getArrivalDayOffset() * 24L * 60L;
            return duration < 0 ? Long.MAX_VALUE : duration;
        } catch (DateTimeParseException | NullPointerException exception) {
            return Long.MAX_VALUE;
        }
    }
}
