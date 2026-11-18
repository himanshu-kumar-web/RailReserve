package com.railreserve.ui;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.railreserve.R;
import com.railreserve.dao.SQLiteSimulationScenarioDao;
import com.railreserve.model.*;
import com.railreserve.service.SimulationScenarioService;
import com.railreserve.testing.DemoPaymentTestDatabase;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Wait-list lifecycle and real screens, with every reservation in a private fixture database. */
@RunWith(AndroidJUnit4.class)
public class WaitlistLifecycleDeviceTest {
    private FareSnapshot quote(TravelClass clazz) {
        String now = Instant.now().toString();
        return new FareSnapshot("90339", "AAA", "BBB", LocalDate.now().plusDays(1).toString(),
                clazz.getCode(), "GN", 24500L, "RailCore fare estimate", now,
                "GNWL107/WL62", "WAITLIST", "RailCore availability", now);
    }

    private TrainSearchResult prepare(DemoPaymentTestDatabase f, FareSnapshot q) {
        return new SimulationScenarioService(new SQLiteSimulationScenarioDao(f.helper)).prepare(
                new SimulationRequest(q.getNumber(), "Waitlist Fixture", "AAA", "Alpha", "BBB", "Beta",
                        q.getDate(), TravelClass.fromCode(q.getClassCode()), "08:00", "12:00", 0, 0, q));
    }

    private BookingRequest request(TrainSearchResult journey, FareSnapshot q) {
        return new BookingRequest(journey.getJourneyId(), 0, TravelClass.fromCode(q.getClassCode()),
                "Waitlist Fixture Passenger", 25, "Other", "DEMO-CHK-" + UUID.randomUUID(), q.getAmountPaise(), q);
    }

    @Test public void allEightClassesKeepWaitlistNumbersPaymentsAndRefundsAcrossReopen() {
        try (DemoPaymentTestDatabase f = new DemoPaymentTestDatabase()) {
            for (TravelClass clazz : TravelClass.values()) {
                FareSnapshot q = quote(clazz);
                TrainSearchResult journey = prepare(f, q);
                BookingRequest firstRequest = request(journey, q);
                Booking first = f.service.createBooking(firstRequest);
                Booking second = f.service.createBooking(request(journey, q));
                assertEquals(Integer.valueOf(1), first.getWaitlistNumber());
                assertEquals(Integer.valueOf(2), second.getWaitlistNumber());
                assertEquals(first.getId(), f.service.createBooking(firstRequest).getId());
                assertEquals(6, f.service.getAvailableSeats(journey.getJourneyId(), clazz.getCode()).size());
                f.service.cancelBooking(first.getId());
                assertThrows(IllegalStateException.class, () -> f.service.confirmWaitingList(first.getId()));
                assertThrows(IllegalStateException.class, () -> f.service.createBooking(firstRequest));
                Booking third = f.service.createBooking(request(journey, q));
                assertEquals(Integer.valueOf(3), third.getWaitlistNumber());
                Booking confirmed = f.service.confirmWaitingList(second.getId());
                assertEquals("CONFIRMED", confirmed.getStatus());
                assertTrue(confirmed.getSeatId() > 0);
                assertNotNull(confirmed.getCoach());
                assertNull(confirmed.getWaitlistNumber());
                assertEquals(clazz, confirmed.getTravelClass());
                assertEquals(5, f.service.getAvailableSeats(journey.getJourneyId(), clazz.getCode()).size());
                assertThrows(IllegalStateException.class, () -> f.service.confirmWaitingList(second.getId()));
                f.service.cancelBooking(second.getId());
                f.service.cancelBooking(third.getId());
                assertEquals(6, f.service.getAvailableSeats(journey.getJourneyId(), clazz.getCode()).size());
                f.helper.close();
                Booking cancelled = f.service.findBookingByPnr(first.getPnr());
                assertEquals("CANCELLED", cancelled.getStatus());
                assertEquals(0, cancelled.getSeatId());
                assertEquals(Integer.valueOf(1), cancelled.getWaitlistNumber());
                assertEquals("SUCCESS", cancelled.getPayment().getStatus());
                assertEquals("SIMULATED_REFUNDED", cancelled.getPayment().getRefundStatus());
                assertEquals(24500L, cancelled.getPayment().getAmountPaise());
                assertThrows(IllegalStateException.class, () -> f.service.cancelBooking(first.getId()));
            }
            assertEquals(24, f.service.getBookings().size());
        }
    }

    @Test public void cancelledWaitlistIsSeatlessInMyBookingsAndActualPnrLookup() throws Exception {
        try (DemoPaymentTestDatabase f = new DemoPaymentTestDatabase()) {
            FareSnapshot q = quote(TravelClass.SLEEPER);
            Booking first = f.service.createBooking(request(prepare(f, q), q));
            f.service.cancelBooking(first.getId());
            try (ActivityScenario<MyBookingsActivity> screen = ActivityScenario.launch(intent(MyBookingsActivity.class))) {
                await(screen, a -> a.findViewById(R.id.bookingsProgress).getVisibility() == View.GONE);
                screen.onActivity(a -> {
                    inject(a, f);
                    invoke(a, "loadBookings");
                });
                await(screen, a -> a.findViewById(R.id.bookingsProgress).getVisibility() == View.GONE);
                screen.onActivity(a -> {
                    ListView list = a.findViewById(R.id.bookingsList);
                    assertEquals(1, list.getAdapter().getCount());
                    View row = list.getAdapter().getView(0, null, list);
                    String seat = ((TextView) row.findViewById(R.id.bookingClassFare)).getText().toString();
                    assertTrue(seat, seat.contains("WL 1"));
                    assertTrue(seat, seat.contains("Cancelled wait-list booking; no seat was allocated."));
                    assertFalse(seat, seat.contains("null") || seat.contains("Seat 0"));
                    assertEquals(View.GONE, row.findViewById(R.id.confirmWaitingListButton).getVisibility());
                    assertEquals(View.GONE, row.findViewById(R.id.cancelBookingButton).getVisibility());
                });
            }
            try (ActivityScenario<FindBookingActivity> screen = ActivityScenario.launch(intent(FindBookingActivity.class))) {
                screen.onActivity(a -> {
                    inject(a, f);
                    ((TextView) a.findViewById(R.id.pnrInput)).setText(first.getPnr());
                    a.findViewById(R.id.lookupButton).performClick();
                });
                await(screen, a -> a.findViewById(R.id.lookupResultCard).getVisibility() == View.VISIBLE);
                screen.onActivity(a -> {
                    String seat = ((TextView) a.findViewById(R.id.lookupSeat)).getText().toString();
                    assertTrue(seat, seat.contains("WL 1"));
                    assertTrue(seat, seat.contains("Cancelled wait-list booking; no seat was allocated."));
                    assertFalse(seat, seat.contains("null") || seat.contains("Seat 0"));
                    assertTrue(((TextView) a.findViewById(R.id.lookupResultStatus)).getText().toString().contains("CANCELLED"));
                    assertTrue(((TextView) a.findViewById(R.id.lookupPayment)).getText().toString().contains("Simulated refund recorded"));
                });
            }
        }
    }

    @Test public void recoveredCancelledWaitlistConfirmationDoesNotInventASeat() throws Exception {
        try (DemoPaymentTestDatabase f = new DemoPaymentTestDatabase()) {
            FareSnapshot q = quote(TravelClass.SLEEPER);
            Booking waiting = f.service.createBooking(request(prepare(f, q), q));
            f.service.cancelBooking(waiting.getId());
            Booking cancelled = f.service.findBookingByPnr(waiting.getPnr());
            try (ActivityScenario<BookingActivity> screen = ActivityScenario.launch(intent(BookingActivity.class))) {
                screen.onActivity(a -> {
                    try {
                        Method method = BookingActivity.class.getDeclaredMethod("showConfirmation", Booking.class);
                        method.setAccessible(true); method.invoke(a, cancelled);
                        Field field = BookingActivity.class.getDeclaredField("confirmationDialog");
                        field.setAccessible(true);
                        androidx.appcompat.app.AlertDialog dialog = (androidx.appcompat.app.AlertDialog) field.get(a);
                        String description = ((TextView) dialog.findViewById(R.id.confirmationSeatClass)).getText().toString();
                        assertTrue(description, description.contains("WL 1"));
                        assertTrue(description, description.contains("Cancelled wait-list booking; no seat was allocated."));
                        assertFalse(description, description.contains("null") || description.contains(" - 0"));
                        assertTrue(((TextView) dialog.findViewById(R.id.confirmationStatus)).getText().toString().contains("CANCELLED"));
                    } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                });
            }
        }
    }
    private Intent intent(Class<? extends Activity> type) {
        return new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(), type);
    }
    private void inject(Activity activity, DemoPaymentTestDatabase f) {
        try {
            Field service = activity.getClass().getDeclaredField("service");
            service.setAccessible(true); service.set(activity, f.service);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private void invoke(Activity activity, String name) {
        try {
            Method method = activity.getClass().getDeclaredMethod(name);
            method.setAccessible(true); method.invoke(activity);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private <T extends Activity> void await(ActivityScenario<T> screen, Predicate<T> condition) throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + 10000;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            AtomicBoolean ready = new AtomicBoolean(); screen.onActivity(a -> ready.set(condition.test(a)));
            if (ready.get()) return;
            Thread.sleep(50);
        }
        fail("Wait-list screen did not reach its expected state");
    }
}
