package com.railreserve.live;

public final class LiveAvailability {
    private final String status;
    private final String statusType;
    private final String journeyDate;
    private final String source, retrievedAt, inventoryUpdatedAt;

    public LiveAvailability(String status, String statusType, String journeyDate) {
        this(status, statusType, journeyDate, null, null, null);
    }
    public LiveAvailability(String status, String statusType, String journeyDate,
                            String source, String retrievedAt, String inventoryUpdatedAt) {
        this.status = status;
        this.statusType = statusType;
        this.journeyDate = journeyDate;
        this.source = source; this.retrievedAt = retrievedAt; this.inventoryUpdatedAt = inventoryUpdatedAt;
    }

    public String getStatus() { return status; }
    public String getStatusType() { return statusType; }
    public String getJourneyDate() { return journeyDate; }
    public String getSource() { return source; }
    public String getRetrievedAt() { return retrievedAt; }
    public String getInventoryUpdatedAt() { return inventoryUpdatedAt; }
}
