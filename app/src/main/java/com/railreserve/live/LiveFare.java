package com.railreserve.live;

public final class LiveFare {
    private final boolean known;
    private final Double total;
    private final String source, retrievedAt;

    public LiveFare(boolean known, Double total) {
        this(known, total, null, null);
    }
    public LiveFare(boolean known, Double total, String source, String retrievedAt) {
        this.known = known;
        this.total = total;
        this.source = source; this.retrievedAt = retrievedAt;
    }

    public boolean isKnown() { return known; }
    public Double getTotal() { return total; }
    public String getSource() { return source; }
    public String getRetrievedAt() { return retrievedAt; }
}
