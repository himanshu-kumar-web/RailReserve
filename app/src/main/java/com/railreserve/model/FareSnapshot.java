package com.railreserve.model;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** Immutable enquiry snapshot. Availability is informational and never allocates railway seats. */
public final class FareSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String number, from, to, date, classCode, quota, source, retrievedAt;
    private final long amountPaise;
    private final String availability, statusType, availabilitySource, availabilityRetrievedAt;

    public FareSnapshot(String number, String from, String to, String date, String classCode, String quota,
                        long amountPaise, String source, String retrievedAt, String availability,
                        String statusType, String availabilitySource, String availabilityRetrievedAt) {
        this.number = number; this.from = from; this.to = to; this.date = date; this.classCode = classCode;
        this.quota = quota; this.amountPaise = amountPaise; this.source = source; this.retrievedAt = retrievedAt;
        this.availability = availability; this.statusType = statusType; this.availabilitySource = availabilitySource;
        this.availabilityRetrievedAt = availabilityRetrievedAt;
        validate();
    }
    public void validate() {
        if (number == null || !number.matches("[0-9]{5}") || from == null || !from.matches("[A-Z0-9]{2,6}")
                || to == null || !to.matches("[A-Z0-9]{2,6}") || from.equals(to) || classCode == null
                || !classCode.matches("[A-Z0-9]{1,4}") || !"GN".equals(quota)
                || !"RailCore fare estimate".equals(source) || amountPaise < 0 || amountPaise % 100 != 0
                || amountPaise / 100 > Integer.MAX_VALUE) throw new IllegalArgumentException("A matching whole-INR RailCore quote is required for demo checkout.");
        try { LocalDate.parse(date); Instant.parse(retrievedAt); }
        catch (RuntimeException e) { throw new IllegalArgumentException("The quote is missing a valid journey date or retrieval time."); }
        boolean hasAvailability = availability != null;
        if (hasAvailability != (statusType != null) || hasAvailability != (availabilitySource != null)
                || hasAvailability != (availabilityRetrievedAt != null)) throw new IllegalArgumentException("Availability snapshot is incomplete.");
        if (hasAvailability) {
            if (availability.trim().isEmpty() || statusType.trim().isEmpty() || !"RailCore availability".equals(availabilitySource))
                throw new IllegalArgumentException("Availability snapshot is invalid.");
            try { Instant.parse(availabilityRetrievedAt); } catch (RuntimeException e) { throw new IllegalArgumentException("Availability retrieval time is invalid."); }
        }
    }
    public void requireFresh() {
        validate();
        long age = java.time.Duration.between(Instant.parse(retrievedAt), Instant.now()).toMillis();
        if (age > 60_000 || age < -300_000) throw new IllegalArgumentException("The fare quote expired. Return to Train Details and check the fare again.");
    }
    public void requireAvailableForBooking() {
        validate();
        if (availability == null) {
            throw new IllegalArgumentException("Check live availability before continuing. No demo booking was created.");
        }
        long age = java.time.Duration.between(Instant.parse(availabilityRetrievedAt), Instant.now()).toMillis();
        if (age > 60_000 || age < -300_000) {
            throw new IllegalArgumentException("Live availability expired. Check availability again. No demo booking was created.");
        }
        if (!"AVAILABLE".equalsIgnoreCase(statusType.trim())) {
            throw new IllegalArgumentException("The provider reports " + statusType + " (" + availability
                    + "). Booking is blocked; no demo booking was created.");
        }
    }
    public void requireContext(String number, String from, String to, String date, String clazz, String quota) {
        validate();
        if (!Objects.equals(this.number, number) || !Objects.equals(this.from, from) || !Objects.equals(this.to, to)
                || !Objects.equals(this.date, date) || !Objects.equals(classCode, clazz) || !Objects.equals(this.quota, quota))
            throw new IllegalArgumentException("The fare quote does not match the selected train, route, date, class and quota.");
    }
    public String getNumber() { return number; }
    public String getFrom() { return from; }
    public String getTo() { return to; }
    public String getDate() { return date; }
    public String getClassCode() { return classCode; }
    public String getQuota() { return quota; }
    public long getAmountPaise() { return amountPaise; }
    public int getFareRupees() { return Math.toIntExact(amountPaise / 100); }
    public String getSource() { return source; }
    public String getRetrievedAt() { return retrievedAt; }
    public String getAvailability() { return availability; }
    public String getStatusType() { return statusType; }
    public String getAvailabilitySource() { return availabilitySource; }
    public String getAvailabilityRetrievedAt() { return availabilityRetrievedAt; }
}
