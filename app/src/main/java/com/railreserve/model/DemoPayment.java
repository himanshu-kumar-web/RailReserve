package com.railreserve.model;

/** A local simulation receipt. SUCCESS never represents a bank transaction. */
public final class DemoPayment {
    private final String transactionReference, checkoutReference, status, createdAt, refundStatus, refundedAt;
    private final long bookingId, amountPaise;

    public DemoPayment(String transactionReference, String checkoutReference, long bookingId, long amountPaise,
                       String status, String createdAt, String refundStatus, String refundedAt) {
        this.transactionReference = transactionReference;
        this.checkoutReference = checkoutReference;
        this.bookingId = bookingId;
        this.amountPaise = amountPaise;
        this.status = status;
        this.createdAt = createdAt;
        this.refundStatus = refundStatus;
        this.refundedAt = refundedAt;
    }
    public String getTransactionReference() { return transactionReference; }
    public String getCheckoutReference() { return checkoutReference; }
    public long getBookingId() { return bookingId; }
    public long getAmountPaise() { return amountPaise; }
    public String getMethod() { return "DEMO_UPI"; }
    public String getStatus() { return status; }
    public String getCreatedAt() { return createdAt; }
    public String getRefundStatus() { return refundStatus; }
    public String getRefundedAt() { return refundedAt; }
}
