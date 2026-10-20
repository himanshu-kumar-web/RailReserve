package com.railreserve.service;

import com.railreserve.model.Booking;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pure in-memory search, status filtering, and journey-date sorting for booking history. */
public final class BookingHistoryOrganizer {
    public enum StatusFilter { ALL, UPCOMING, COMPLETED, CANCELLED }
    public enum JourneySort { NEAREST_FIRST, MOST_RECENT_FIRST }

    private BookingHistoryOrganizer() { }

    public static List<Booking> organize(List<Booking> bookings, String query,
                                         StatusFilter filter, JourneySort sort, LocalDate today) {
        List<Booking> visible = new ArrayList<>();
        if (bookings == null) return visible;
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        StatusFilter selectedFilter = filter == null ? StatusFilter.ALL : filter;
        JourneySort selectedSort = sort == null ? JourneySort.NEAREST_FIRST : sort;
        LocalDate referenceDate = today == null ? LocalDate.now() : today;

        for (Booking booking : bookings) {
            if (booking == null || !matchesStatus(booking, selectedFilter, referenceDate)
                    || !matchesSearch(booking, normalizedQuery)) continue;
            visible.add(booking);
        }
        // List.sort is stable, so bookings with equal dates preserve their input order.
        visible.sort((first, second) -> {
            LocalDate firstDate = parseJourneyDate(first.getJourneyDate());
            LocalDate secondDate = parseJourneyDate(second.getJourneyDate());
            if (firstDate == null) return secondDate == null ? 0 : 1;
            if (secondDate == null) return -1;
            int comparison = firstDate.compareTo(secondDate);
            return selectedSort == JourneySort.MOST_RECENT_FIRST ? -comparison : comparison;
        });
        return visible;
    }

    public static LocalDate parseJourneyDate(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static boolean matchesStatus(Booking booking, StatusFilter filter, LocalDate today) {
        boolean cancelled = isCancelled(booking);
        if (filter == StatusFilter.ALL) return true;
        if (filter == StatusFilter.CANCELLED) return cancelled;
        if (cancelled) return false;

        LocalDate journeyDate = parseJourneyDate(booking.getJourneyDate());
        if (journeyDate == null) return false;
        if (filter == StatusFilter.UPCOMING) return !journeyDate.isBefore(today);
        return journeyDate.isBefore(today);
    }

    private static boolean matchesSearch(Booking booking, String query) {
        if (query.isEmpty()) return true;
        return contains(booking.getPnr(), query)
                || contains(booking.getTrainName(), query)
                || contains(booking.getTrainNumber(), query)
                || contains(booking.getSource(), query)
                || contains(booking.getDestination(), query);
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private static boolean isCancelled(Booking booking) {
        return booking.getStatus() != null
                && "CANCELLED".equalsIgnoreCase(booking.getStatus().trim());
    }
}
