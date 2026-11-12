package com.railreserve.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.railreserve.database.DatabaseHelper;
import com.railreserve.model.Booking;
import com.railreserve.model.BookingRequest;
import com.railreserve.model.DemoPayment;
import com.railreserve.model.Seat;
import com.railreserve.model.SeatStatus;
import com.railreserve.model.TravelClass;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SQLiteBookingDao implements BookingDao {
    private final DatabaseHelper helper;
    private final SecureRandom random = new SecureRandom();
    public SQLiteBookingDao(DatabaseHelper helper) { this.helper = helper; }

    @Override public List<Seat> getAvailableSeats(long journeyId, String travelClass) {
        List<Seat> seats = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT seat_id,journey_id,travel_class,coach,seat_number,fare,status FROM seats WHERE journey_id=? AND travel_class=? AND status='AVAILABLE' ORDER BY coach,seat_number",
                new String[]{Long.toString(journeyId), travelClass})) {
            while (c.moveToNext()) seats.add(new Seat(c.getLong(0), c.getLong(1), TravelClass.fromCode(c.getString(2)),
                    c.getString(3), c.getInt(4), c.getInt(5), SeatStatus.valueOf(c.getString(6))));
        }
        return seats;
    }

    @Override public Booking createBooking(BookingRequest request) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            if (request.getCheckoutReference() != null) {
                Booking existing = findCheckout(db, request.getCheckoutReference());
                if (existing != null) {
                    if (!"CONFIRMED".equals(existing.getStatus())) {
                        throw new IllegalStateException("This checkout was already cancelled. Start a new reservation.");
                    }
                    db.setTransactionSuccessful();
                    return existing;
                }
            }
            // One shared allocation implementation for legacy requests and the new checkout.
            Booking booking = insertBooking(db, request);
            if (request.getCheckoutReference() != null) {
                long amountPaise = Math.multiplyExact((long) booking.getFare(), 100L);
                if (request.getExpectedAmountPaise() == null || request.getExpectedAmountPaise() != amountPaise) {
                    throw new IllegalStateException("The demo fare changed. Return to passenger details and review again. No payment was completed.");
                }
                ContentValues payment = new ContentValues();
                payment.put("transaction_reference", "DEMO-TXN-" + UUID.randomUUID());
                payment.put("checkout_reference", request.getCheckoutReference());
                payment.put("booking_id", booking.getId());
                payment.put("payment_method", "DEMO_UPI");
                payment.put("amount_paise", amountPaise);
                payment.put("payment_status", "SUCCESS");
                payment.put("created_at", Instant.now().toString());
                db.insertOrThrow("demo_payments", null, payment);
                booking = readBooking(db, "b.booking_id=?", new String[]{Long.toString(booking.getId())});
            }
            db.setTransactionSuccessful();
            return booking;
        } finally {
            // A return is delivered to the caller only after endTransaction commits successfully.
            db.endTransaction();
        }
    }

    /** Runs inside createBooking's transaction; never starts a nested transaction. */
    private Booking insertBooking(SQLiteDatabase db, BookingRequest request) {
        long journeyId;
        String clazz;
        int fare;
        try (Cursor c = db.rawQuery("SELECT s.journey_id,s.travel_class,s.fare FROM seats s JOIN journeys j ON j.journey_id=s.journey_id WHERE s.seat_id=? AND j.journey_id=?",
                new String[]{Long.toString(request.getSeatId()), Long.toString(request.getJourneyId())})) {
            if (!c.moveToFirst()) throw new IllegalStateException("That seat does not belong to this journey.");
            journeyId = c.getLong(0); clazz = c.getString(1); fare = c.getInt(2);
        }
        if (!clazz.equals(request.getTravelClass().getCode())) throw new IllegalStateException("That seat is not in the selected class.");
        ContentValues seatUpdate = new ContentValues();
        seatUpdate.put("status", "BOOKED");
        int changed = db.update("seats", seatUpdate,
                "seat_id=? AND journey_id=? AND travel_class=? AND status='AVAILABLE'",
                new String[]{Long.toString(request.getSeatId()), Long.toString(journeyId), clazz});
        if (changed != 1) throw new IllegalStateException("This seat is no longer available. Please choose another seat. No payment was completed.");
        String pnr = generatePnr(db);
        ContentValues values = new ContentValues();
        values.put("pnr", pnr); values.put("seat_id", request.getSeatId());
        values.put("passenger_name", request.getPassengerName().trim()); values.put("passenger_age", request.getAge());
        values.put("passenger_gender", request.getGender()); values.put("travel_class", clazz); values.put("fare", fare);
        values.put("booking_status", "CONFIRMED"); values.put("booked_at", Instant.now().toString());
        long id = db.insertOrThrow("bookings", null, values);
        return readBooking(db, "b.booking_id=?", new String[]{Long.toString(id)});
    }

    private String generatePnr(SQLiteDatabase db) {
        for (int attempt = 0; attempt < 12; attempt++) {
            StringBuilder digits = new StringBuilder(10);
            digits.append(1 + random.nextInt(9));
            for (int i = 0; i < 9; i++) digits.append(random.nextInt(10));
            String candidate = digits.toString();
            try (Cursor c = db.rawQuery("SELECT 1 FROM bookings WHERE pnr=?", new String[]{candidate})) {
                if (!c.moveToFirst()) return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique demo PNR. Please try again.");
    }

    @Override public List<Booking> getBookings() {
        List<Booking> bookings = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().rawQuery(bookingSelect() + " ORDER BY b.booked_at DESC,b.booking_id DESC", null)) {
            while (c.moveToNext()) bookings.add(readBooking(c));
        }
        return bookings;
    }

    @Override public Booking findBookingByPnr(String pnr) {
        return find(helper.getReadableDatabase(), "b.pnr=?", pnr);
    }

    @Override public Booking findBookingByCheckoutReference(String checkoutReference) {
        return findCheckout(helper.getReadableDatabase(), checkoutReference);
    }

    private Booking findCheckout(SQLiteDatabase db, String reference) { return find(db, "p.checkout_reference=?", reference); }

    private Booking find(SQLiteDatabase db, String where, String value) {
        try (Cursor c = db.rawQuery(bookingSelect() + " WHERE " + where, new String[]{value})) {
            return c.moveToFirst() ? readBooking(c) : null;
        }
    }

    @Override public void cancelBooking(long bookingId) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            long seatId;
            try (Cursor c = db.rawQuery("SELECT seat_id FROM bookings WHERE booking_id=? AND booking_status='CONFIRMED'", new String[]{Long.toString(bookingId)})) {
                if (!c.moveToFirst()) throw new IllegalStateException("This booking is already cancelled or no longer exists.");
                seatId = c.getLong(0);
            }
            String cancelledAt = Instant.now().toString();
            ContentValues booking = new ContentValues();
            booking.put("booking_status", "CANCELLED"); booking.put("cancelled_at", cancelledAt);
            if (db.update("bookings", booking, "booking_id=? AND booking_status='CONFIRMED'", new String[]{Long.toString(bookingId)}) != 1) {
                throw new IllegalStateException("This booking has already been cancelled.");
            }
            ContentValues seat = new ContentValues(); seat.put("status", "AVAILABLE");
            if (db.update("seats", seat, "seat_id=? AND status='BOOKED'", new String[]{Long.toString(seatId)}) != 1) {
                throw new IllegalStateException("The booked seat could not be released; cancellation was rolled back.");
            }
            ContentValues refund = new ContentValues();
            refund.put("refund_status", "SIMULATED_REFUNDED"); refund.put("refunded_at", cancelledAt);
            // Zero affected rows is valid for legacy bookings. The original SUCCESS receipt is retained.
            db.update("demo_payments", refund, "booking_id=? AND payment_status='SUCCESS' AND refund_status='NONE'",
                    new String[]{Long.toString(bookingId)});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    private Booking readBooking(SQLiteDatabase db, String where, String[] args) {
        try (Cursor c = db.rawQuery(bookingSelect() + " WHERE " + where, args)) {
            if (!c.moveToFirst()) throw new IllegalStateException("Saved booking could not be loaded.");
            return readBooking(c);
        }
    }

    private Booking readBooking(Cursor c) {
        DemoPayment payment = c.isNull(22) ? null : new DemoPayment(c.getString(22), c.getString(23), c.getLong(0),
                c.getLong(24), c.getString(25), c.getString(26), c.getString(27), c.isNull(28) ? null : c.getString(28));
        return new Booking(c.getLong(0),c.getString(1),c.getLong(2),c.getLong(3),c.getString(4),c.getString(5),c.getString(6),
                c.getString(7),c.getString(8),c.getString(9),c.getString(10),c.getInt(11),c.getString(12),c.getInt(13),
                c.getString(14),c.getString(15),c.getInt(16),TravelClass.fromCode(c.getString(17)),c.getInt(18),
                c.getString(19),c.getString(20),c.isNull(21)?null:c.getString(21),payment);
    }

    private String bookingSelect() {
        return "SELECT b.booking_id,b.pnr,b.seat_id,j.journey_id,COALESCE(sc.display_train_number,t.train_number),t.name,t.source_station,t.destination_station,j.journey_date,t.departure_time,t.arrival_time,t.arrival_day_offset,b.passenger_name,b.passenger_age,b.passenger_gender,s.coach,s.seat_number,b.travel_class,b.fare,b.booking_status,b.booked_at,b.cancelled_at,"
                + "p.transaction_reference,p.checkout_reference,p.amount_paise,p.payment_status,p.created_at,p.refund_status,p.refunded_at "
                + "FROM bookings b JOIN seats s ON s.seat_id=b.seat_id JOIN journeys j ON j.journey_id=s.journey_id JOIN trains t ON t.train_id=j.train_id "
                + "LEFT JOIN demo_payments p ON p.booking_id=b.booking_id "
                + "LEFT JOIN simulation_scenarios sc ON sc.train_id=t.train_id";
    }
}
