package com.railreserve.ui;

import com.railreserve.model.Booking;
import com.railreserve.model.DemoPayment;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

public final class PaymentReceiptText {
    private PaymentReceiptText() { }
    public static String amount(long paise) {
        return NumberFormat.getCurrencyInstance(new Locale("en", "IN")).format(BigDecimal.valueOf(paise, 2));
    }
    public static String quote(com.railreserve.model.FareSnapshot quote) {
        return "Captured fare estimate: " + amount(quote.getAmountPaise()) + " | " + quote.getClassCode() + "/" + quote.getQuota()
                + "\n" + quote.getSource() + " | Retrieved: " + quote.getRetrievedAt()
                + "\nProvider availability snapshot: " + (quote.getAvailability() == null ? "not checked / unavailable" : quote.getAvailability() + " (" + quote.getStatusType() + ") | " + quote.getAvailabilityRetrievedAt())
                + "\nEstimate only; not a guaranteed date-specific railway ticket price. Local demo reservation only.";
    }
    public static String receipt(Booking booking) {
        DemoPayment payment = booking.getPayment();
        if (payment == null) return "Legacy demo booking — no payment record.\nSimulation Ticket — Not Valid for Travel.";
        return (booking.getFareSnapshot() == null ? "" : quote(booking.getFareSnapshot()) + "\n") + "Payment method: Demo UPI\nDemo transaction: " + payment.getTransactionReference()
                + "\nPayment status: " + payment.getStatus() + " · " + amount(payment.getAmountPaise())
                + ("SIMULATED_REFUNDED".equals(payment.getRefundStatus())
                ? "\nSimulated refund recorded. No real money moved." : "\nDemo Payment — No real money was deducted.")
                + "\nSimulation Ticket — Not Valid for Travel.";
    }
}
