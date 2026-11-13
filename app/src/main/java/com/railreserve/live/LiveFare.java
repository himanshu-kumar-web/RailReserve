package com.railreserve.live;

public final class LiveFare {
    private final EnquiryContext context;
    private final boolean known;
    private final Double total;
    private final String source, retrievedAt;

    public LiveFare(boolean known, Double total) {
        this(known, total, null, null);
    }
    public LiveFare(boolean known, Double total, String source, String retrievedAt) {
        this(known, total, source, retrievedAt, null);
    }
    public LiveFare(boolean known, Double total, String source, String retrievedAt, EnquiryContext context) {
        this.known = known;
        this.total = total;
        this.context = context;
        this.source = source; this.retrievedAt = retrievedAt;
    }

    public boolean isKnown() { return known; }
    public Double getTotal() { return total; }
    public EnquiryContext getContext() { return context; }
    public String getSource() { return source; }
    public String getRetrievedAt() { return retrievedAt; }
}
