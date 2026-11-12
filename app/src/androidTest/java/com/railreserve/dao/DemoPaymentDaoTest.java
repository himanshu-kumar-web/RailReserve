package com.railreserve.dao;

import android.content.ContentValues;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.railreserve.model.Booking;
import com.railreserve.model.BookingRequest;
import com.railreserve.model.TravelClass;
import com.railreserve.testing.DemoPaymentTestDatabase;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DemoPaymentDaoTest {
    @Test public void successDuplicateReferenceAndReopeningReturnOneReceipt() {
        try (DemoPaymentTestDatabase fixture = new DemoPaymentTestDatabase()) {
            BookingRequest request = fixture.checkout(1);
            Booking first = fixture.service.createBooking(request);
            assertEquals("SUCCESS", first.getPayment().getStatus());
            assertEquals("DEMO_UPI", first.getPayment().getMethod());
            assertEquals(10000L, first.getPayment().getAmountPaise());
            assertEquals(first.getId(), first.getPayment().getBookingId());
            assertEquals(request.getCheckoutReference(), first.getPayment().getCheckoutReference());
            assertTrue(first.getPayment().getTransactionReference().startsWith("DEMO-TXN-"));
            assertEquals(first.getPnr(), fixture.service.createBooking(request).getPnr());
            fixture.helper.close();
            Booking restored = fixture.service.findBookingByCheckoutReference(request.getCheckoutReference());
            assertEquals(first.getId(), restored.getId());
            assertEquals(first.getPayment().getTransactionReference(), restored.getPayment().getTransactionReference());
            assertEquals(1, fixture.dao.getBookings().size());
            assertEquals(1, count(fixture, "demo_payments"));
            assertEquals(19, fixture.dao.getAvailableSeats(1, "SL").size());
        }
    }

    @Test public void concurrentDuplicateCheckoutsAllocateOnlyOnce() throws Exception {
        try (DemoPaymentTestDatabase fixture = new DemoPaymentTestDatabase()) {
            BookingRequest request = fixture.checkout(1);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService workers = Executors.newFixedThreadPool(2);
            try {
                Future<Booking> a = workers.submit(() -> { start.await(); return fixture.service.createBooking(request); });
                Future<Booking> b = workers.submit(() -> { start.await(); return fixture.service.createBooking(request); });
                start.countDown();
                assertEquals(a.get(10, TimeUnit.SECONDS).getPnr(), b.get(10, TimeUnit.SECONDS).getPnr());
                assertEquals(1, count(fixture, "bookings"));
                assertEquals(1, count(fixture, "demo_payments"));
            } finally { workers.shutdownNow(); }
        }
    }

    @Test public void seatConflictCreatesNoNewBookingOrPayment() {
        try (DemoPaymentTestDatabase fixture = new DemoPaymentTestDatabase()) {
            Booking legacy = fixture.service.createBooking(new BookingRequest(1, 1, TravelClass.SLEEPER, "Legacy Passenger", 25, "Other"));
            assertNull(legacy.getPayment());
            assertThrows(IllegalStateException.class, () -> fixture.service.createBooking(fixture.checkout(1)));
            assertEquals(1, count(fixture, "bookings"));
            assertEquals(0, count(fixture, "demo_payments"));
            fixture.service.cancelBooking(legacy.getId());
            assertEquals(20, fixture.dao.getAvailableSeats(1, "SL").size());
        }
    }

    @Test public void paymentInsertFailureRollsBackSeatPnrAndBooking() {
        try (DemoPaymentTestDatabase fixture = new DemoPaymentTestDatabase()) {
            fixture.helper.getWritableDatabase().execSQL("CREATE TRIGGER reject_test_payment BEFORE INSERT ON demo_payments BEGIN SELECT RAISE(ABORT,'TEST payment insert failure'); END");
            assertThrows(RuntimeException.class, () -> fixture.service.createBooking(fixture.checkout(1)));
            assertEquals(0, count(fixture, "bookings"));
            assertEquals(0, count(fixture, "demo_payments"));
            assertEquals(20, fixture.dao.getAvailableSeats(1, "SL").size());
        }
    }

    @Test public void changedFareRollsBackRatherThanClaimingPaymentSuccess() {
        try (DemoPaymentTestDatabase fixture = new DemoPaymentTestDatabase()) {
            BookingRequest original = fixture.checkout(1);
            BookingRequest wrongAmount = new BookingRequest(1, 1, TravelClass.SLEEPER, "Test Passenger", 25, "Other",
                    original.getCheckoutReference(), 9999L);
            assertThrows(IllegalStateException.class, () -> fixture.service.createBooking(wrongAmount));
            assertEquals(0, count(fixture, "bookings"));
            assertEquals(0, count(fixture, "demo_payments"));
            assertEquals(20, fixture.dao.getAvailableSeats(1, "SL").size());
        }
    }

    @Test public void pnrsAndTransactionReferencesAreUniqueAcrossSeats() {
        try (DemoPaymentTestDatabase fixture = new DemoPaymentTestDatabase()) {
            Set<String> pnrs = new HashSet<>();
            Set<String> transactions = new HashSet<>();
            for (int seat = 1; seat <= 20; seat++) {
                Booking booking = fixture.service.createBooking(fixture.checkout(seat));
                assertTrue(booking.getPnr().matches("[1-9][0-9]{9}"));
                assertTrue(pnrs.add(booking.getPnr()));
                assertTrue(transactions.add(booking.getPayment().getTransactionReference()));
            }
            assertEquals(20, count(fixture, "bookings"));
            assertEquals(20, count(fixture, "demo_payments"));
        }
    }

    @Test public void cancellationRetainsReceiptAndSimulatesRefundExactlyOnce() {
        try (DemoPaymentTestDatabase fixture = new DemoPaymentTestDatabase()) {
            BookingRequest request = fixture.checkout(1);
            Booking booking = fixture.service.createBooking(request);
            fixture.service.cancelBooking(booking.getId());
            fixture.helper.close();
            Booking cancelled = fixture.service.findBookingByPnr(booking.getPnr());
            assertEquals("CANCELLED", cancelled.getStatus());
            assertEquals("SUCCESS", cancelled.getPayment().getStatus());
            assertEquals("SIMULATED_REFUNDED", cancelled.getPayment().getRefundStatus());
            assertNotNull(cancelled.getPayment().getRefundedAt());
            assertEquals(booking.getPayment().getTransactionReference(), cancelled.getPayment().getTransactionReference());
            assertThrows(IllegalStateException.class, () -> fixture.service.cancelBooking(booking.getId()));
            assertThrows(IllegalStateException.class, () -> fixture.service.createBooking(request));
            assertEquals(1, count(fixture, "demo_payments"));
            assertEquals(20, fixture.dao.getAvailableSeats(1, "SL").size());
            assertNotEquals(booking.getPnr(), fixture.service.createBooking(fixture.checkout(1)).getPnr());
        }
    }

    @Test public void refundWriteFailureRollsBackCancellationAndSeatRelease() {
        try (DemoPaymentTestDatabase fixture = new DemoPaymentTestDatabase()) {
            Booking booking = fixture.service.createBooking(fixture.checkout(1));
            fixture.helper.getWritableDatabase().execSQL("CREATE TRIGGER reject_test_refund BEFORE UPDATE OF refund_status ON demo_payments BEGIN SELECT RAISE(ABORT,'TEST refund failure'); END");
            assertThrows(RuntimeException.class, () -> fixture.service.cancelBooking(booking.getId()));
            Booking restored = fixture.service.findBookingByPnr(booking.getPnr());
            assertEquals("CONFIRMED", restored.getStatus());
            assertEquals("NONE", restored.getPayment().getRefundStatus());
            assertEquals(19, fixture.dao.getAvailableSeats(1, "SL").size());
        }
    }

    @Test public void paymentTableRejectsUnsupportedMethodStatusAndFractionalPaise() {
        try (DemoPaymentTestDatabase fixture = new DemoPaymentTestDatabase()) {
            Booking legacy = fixture.service.createBooking(new BookingRequest(1, 1, TravelClass.SLEEPER, "Legacy Passenger", 25, "Other"));
            ContentValues values = new ContentValues();
            values.put("transaction_reference", "TEST"); values.put("checkout_reference", "TEST");
            values.put("booking_id", legacy.getId()); values.put("payment_method", "REAL_UPI");
            values.put("amount_paise", 10000L); values.put("payment_status", "SUCCESS"); values.put("created_at", "TEST");
            SQLiteDatabase db = fixture.helper.getWritableDatabase();
            assertThrows(RuntimeException.class, () -> db.insertOrThrow("demo_payments", null, values));
            values.put("payment_method", "DEMO_UPI"); values.put("payment_status", "PENDING");
            assertThrows(RuntimeException.class, () -> db.insertOrThrow("demo_payments", null, values));
            values.put("payment_status", "SUCCESS"); values.put("amount_paise", 100.5);
            assertThrows(RuntimeException.class, () -> db.insertOrThrow("demo_payments", null, values));
            assertEquals(0, count(fixture, "demo_payments"));
        }
    }

    private long count(DemoPaymentTestDatabase fixture, String table) {
        return DatabaseUtils.queryNumEntries(fixture.helper.getReadableDatabase(), table);
    }
}
