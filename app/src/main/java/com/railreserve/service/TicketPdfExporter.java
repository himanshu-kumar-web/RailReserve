package com.railreserve.service;

import android.content.ContentResolver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import com.railreserve.model.Booking;
import com.railreserve.model.DemoPayment;
import java.io.IOException;
import java.io.OutputStream;
import java.text.NumberFormat;
import java.util.Locale;

public final class TicketPdfExporter {
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final int NAVY = Color.rgb(19, 35, 63);
    private static final int TEAL = Color.rgb(15, 118, 110);
    private static final int INK = Color.rgb(20, 33, 61);
    private static final int MUTED = Color.rgb(83, 97, 116);
    private static final int SURFACE = Color.rgb(243, 246, 250);
    private static final int BORDER = Color.rgb(220, 227, 235);

    private TicketPdfExporter() { }

    public static void write(ContentResolver resolver, Uri destination, Booking booking) throws IOException {
        try (OutputStream output = resolver.openOutputStream(destination, "w")) {
            if (output == null) throw new IOException("Could not open the selected PDF destination.");
            write(output, booking);
        }
    }

    static void write(OutputStream output, Booking booking) throws IOException {
        PdfDocument document = new PdfDocument();
        try {
            PdfDocument.Page page = document.startPage(
                    new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create());
            drawTicket(page.getCanvas(), booking);
            document.finishPage(page);
            document.writeTo(output);
        } finally {
            document.close();
        }
    }

    private static void drawTicket(Canvas canvas, Booking booking) {
        canvas.drawColor(Color.WHITE);
        Paint fill = paint(NAVY, false);
        canvas.drawRect(0, 0, PAGE_WIDTH, 170, fill);

        drawText(canvas, "RAILRESERVE", 44, 52, 260, 22, Color.WHITE, true);
        drawText(canvas, "DEMO JOURNEY TICKET", 44, 83, 350, 16, Color.rgb(209, 229, 239), true);
        drawText(canvas, "NOT VALID FOR TRAVEL", 365, 52, 186, 12, Color.WHITE, true);

        roundedBox(canvas, 34, 112, 561, 220, 14, Color.rgb(244, 248, 255));
        drawText(canvas, "LOCAL DEMO BOOKING ID", 54, 145, 300, 11, MUTED, true);
        drawText(canvas, value(booking.getPnr()), 54, 184, 320, 28, NAVY, true);
        drawText(canvas, "Status: " + value(booking.getStatus())
                + ("WAITING_LIST".equals(booking.getStatus()) ? " · WL " + booking.getWaitlistNumber() : ""),
                54, 207, 280, 12, TEAL, true);
        drawText(canvas, "This ID is not an Indian Railways PNR.", 330, 193, 205, 11, MUTED, false);

        drawText(canvas, "JOURNEY", 44, 268, 200, 12, TEAL, true);
        drawText(canvas, value(booking.getTrainName()), 44, 296, 500, 20, INK, true);
        drawText(canvas, "Train " + value(booking.getTrainNumber()), 44, 317, 500, 12, MUTED, false);

        roundedBox(canvas, 34, 334, 561, 458, 12, SURFACE);
        drawText(canvas, "FROM", 54, 362, 220, 10, MUTED, true);
        drawText(canvas, "TO", 320, 362, 220, 10, MUTED, true);
        drawText(canvas, value(booking.getSource()), 54, 392, 235, 17, NAVY, true);
        drawText(canvas, value(booking.getDestination()), 320, 392, 220, 17, NAVY, true);
        drawText(canvas, value(booking.getDeparture()), 54, 420, 230, 15, INK, true);
        drawText(canvas, value(booking.getArrival())
                + (booking.getArrivalDayOffset() > 0 ? " (+1 day)" : ""), 320, 420, 220, 15, INK, true);
        drawText(canvas, "JOURNEY DATE", 54, 445, 200, 10, MUTED, true);
        drawText(canvas, value(booking.getJourneyDate()), 155, 445, 375, 12, INK, false);

        drawText(canvas, booking.getFareSnapshot() == null ? "PASSENGER & SEAT" : "PASSENGER & DEMO AVAILABILITY",
                44, 496, 300, 12, TEAL, true);
        roundedBox(canvas, 34, 510, 561, 632, 12, Color.WHITE);
        drawText(canvas, value(booking.getPassengerName()), 54, 542, 300, 16, INK, true);
        drawText(canvas, "Age " + booking.getAge() + "  |  " + value(booking.getGender()),
                355, 542, 185, 12, MUTED, false);
        String classLabel = booking.getTravelClass() == null ? "Not specified" : booking.getTravelClass().getLabel();
        if ("WAITING_LIST".equals(booking.getStatus())) {
            drawText(canvas, "Class  |  " + classLabel, 54, 575, 480, 12, INK, true);
            drawText(canvas, "Waiting list: WL " + booking.getWaitlistNumber(), 54, 592, 480, 12, TEAL, true);
            drawText(canvas, "No seat allocated while this local booking is waitlisted.",
                    54, 608, 480, 10, MUTED, false);
            drawText(canvas, "Local simulation only; not a railway confirmation.",
                    54, 624, 480, 10, MUTED, false);
        } else if (booking.getFareSnapshot() == null) {
            drawText(canvas, "Coach " + value(booking.getCoach()) + "  |  Seat " + booking.getSeatNumber(),
                    54, 575, 280, 13, INK, true);
            drawText(canvas, "Class  |  " + classLabel, 355, 575, 185, 12, MUTED, false);
            drawText(canvas, "Booked: " + value(booking.getBookedAt()), 54, 596, 480, 10, MUTED, false);
        } else {
            drawText(canvas, "Class  |  " + classLabel, 54, 575, 480, 12, INK, true);
            drawText(canvas, "Provider status: " + value(booking.getFareSnapshot().getStatusType()),
                    54, 592, 480, 12, TEAL, true);
            drawText(canvas, value(booking.getFareSnapshot().getAvailability()), 54, 608, 480, 10, MUTED, false);
            drawText(canvas, "Local demo seat: " + value(booking.getCoach()) + " " + booking.getSeatNumber()
                    + " · no railway seat reserved.", 54, 624, 480, 10, MUTED, false);
        }

        roundedBox(canvas, 34, 640, 561, 710, 12, Color.rgb(231, 244, 242));
        drawText(canvas, "DEMO FARE", 54, 667, 200, 10, MUTED, true);
        drawText(canvas, formatFare(booking.getFare()), 54, 695, 240, 20, TEAL, true);
        DemoPayment payment = booking.getPayment();
        drawText(canvas, payment == null ? "No demo payment record"
                : "Demo payment: " + value(payment.getStatus()), 310, 691, 230, 12, INK, true);

        drawText(canvas, "DEMO ONLY. Not issued by IRCTC or a railway authority.",
                44, 748, 510, 12, NAVY, true);
        drawText(canvas, "This local simulation ticket is not valid for travel. No real payment was processed.",
                44, 770, 510, 10, MUTED, false);
    }

    private static void roundedBox(Canvas canvas, float left, float top, float right, float bottom,
                                   float radius, int color) {
        canvas.drawRoundRect(new RectF(left, top, right, bottom), radius, radius, paint(color, false));
    }

    private static void drawText(Canvas canvas, String value, float x, float y, float maxWidth,
                                 float size, int color, boolean bold) {
        Paint text = paint(color, bold);
        text.setTextSize(size);
        String fitted = value == null ? "" : value;
        if (text.measureText(fitted) > maxWidth) {
            while (fitted.length() > 1 && text.measureText(fitted + "...") > maxWidth) {
                fitted = fitted.substring(0, fitted.length() - 1);
            }
            fitted += "...";
        }
        canvas.drawText(fitted, x, y, text);
    }

    private static Paint paint(int color, boolean bold) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return paint;
    }

    private static String value(String text) {
        return text == null || text.trim().isEmpty() ? "Not provided" : text.trim();
    }

    private static String formatFare(int amount) {
        return NumberFormat.getCurrencyInstance(new Locale("en", "IN")).format(amount);
    }
}
