package com.railreserve.ui;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStore;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.railreserve.R;
import com.railreserve.model.Booking;
import com.railreserve.model.BookingRequest;
import com.railreserve.model.Seat;
import com.railreserve.model.TravelClass;
import com.railreserve.service.BookingService;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ReliabilityDeviceTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

    @Test public void bookingSubmissionSurvivesObserverRecreationAndRejectsDuplicates() throws Exception {
        ViewModelStore store = new ViewModelStore();
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        Booking expected = new Booking(1, "1234567890", 1, 1, "90001", "TEST FIXTURE", "From", "To",
                "2026-11-12", "08:00", "12:00", 0, "Test Passenger", 25, "Other", "S1", 1,
                TravelClass.SLEEPER, 100, "CONFIRMED", "2026-11-12T00:00:00Z", null);
        BookingService service = new StubService() {
            @Override public Booking createBooking(BookingRequest request) {
                calls.incrementAndGet();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test timeout"); }
                catch (InterruptedException exception) { throw new IllegalStateException(exception); }
                return expected;
            }
        };
        BookingRequest request = new BookingRequest(1, 1, TravelClass.SLEEPER, "Test Passenger", 25, "Other");
        try {
            BookingSubmissionViewModel first = main(() -> new ViewModelProvider(store,
                    new ViewModelProvider.NewInstanceFactory()).get(BookingSubmissionViewModel.class));
            main(() -> { first.submit(service, request); first.submit(service, request); return null; });
            waitFor(() -> calls.get() == 1);
            BookingSubmissionViewModel recreated = main(() -> new ViewModelProvider(store,
                    new ViewModelProvider.NewInstanceFactory()).get(BookingSubmissionViewModel.class));
            assertSame(first, recreated);
            assertTrue(main(() -> recreated.getState().getValue().isSaving()));
            release.countDown();
            waitFor(() -> recreated.getState().getValue().getBooking() != null);
            assertSame(expected, main(() -> recreated.getState().getValue().getBooking()));
            main(() -> { recreated.submit(service, request); return null; });
            assertEquals(1, calls.get());
        } finally { release.countDown(); main(() -> { store.clear(); return null; }); }
    }

    @Test public void editedPnrRejectsStaleLookupAndDuplicateRequests() throws Exception {
        Activity activity = start(FindBookingActivity.class);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch returned = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        BookingService fake = new StubService() {
            @Override public Booking findBookingByPnr(String pnr) {
                calls.incrementAndGet();
                try { release.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                returned.countDown();
                return null;
            }
        };
        try {
            // Test-only replacement avoids touching the user's SQLite records or adding production hooks.
            Field field = FindBookingActivity.class.getDeclaredField("service");
            field.setAccessible(true);
            main(() -> {
                try { field.set(activity, fake); } catch (IllegalAccessException e) { throw new AssertionError(e); }
                ((TextView) activity.findViewById(R.id.pnrInput)).setText("1234567890");
                activity.findViewById(R.id.lookupButton).performClick();
                activity.findViewById(R.id.lookupButton).performClick();
                return null;
            });
            waitFor(() -> calls.get() == 1);
            main(() -> { ((TextView) activity.findViewById(R.id.pnrInput)).setText("9999999999"); return null; });
            release.countDown();
            assertTrue(returned.await(5, TimeUnit.SECONDS));
            instrumentation.waitForIdleSync();
            // The worker posts its UI update just after returning from the fake service.
            Thread.sleep(200);
            main(() -> {
                assertEquals(View.GONE, activity.findViewById(R.id.lookupMessage).getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.lookupProgress).getVisibility());
                assertTrue(activity.findViewById(R.id.lookupButton).isEnabled());
                assertEquals(View.GONE, activity.findViewById(R.id.lookupResultCard).getVisibility());
                return null;
            });
            assertEquals(1, calls.get());
        } finally { release.countDown(); main(() -> { activity.finish(); return null; }); }
    }

    @Test public void profileAndAboutHideTicketContent() throws Exception {
        Activity activity = start(AppSectionActivity.class);
        try {
            for (int nav : new int[]{R.id.navProfile, R.id.navAbout}) {
                main(() -> { activity.findViewById(nav).performClick(); return null; });
                instrumentation.waitForIdleSync();
                main(() -> {
                    assertEquals(View.GONE, activity.findViewById(R.id.ticketCards).getVisibility());
                    assertEquals(View.GONE, activity.findViewById(R.id.ticketStatus).getVisibility());
                    assertEquals(View.GONE, activity.findViewById(R.id.ticketProgress).getVisibility());
                    assertEquals(View.GONE, activity.findViewById(R.id.ticketsActions).getVisibility());
                    return null;
                });
            }
        } finally { main(() -> { activity.finish(); return null; }); }
    }

    private Activity start(Class<? extends Activity> type) {
        return instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), type)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void waitFor(Supplier<Boolean> condition) throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + 5000;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            if (main(condition)) return;
            Thread.sleep(50);
        }
        fail("State did not settle within five seconds");
    }

    private <T> T main(Supplier<T> action) {
        AtomicReference<T> result = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> result.set(action.get()));
        return result.get();
    }

    private static class StubService implements BookingService {
        @Override public Booking findBookingByCheckoutReference(String reference) { return null; }
        @Override public List<Seat> getAvailableSeats(long id, String travelClass) { return Collections.emptyList(); }
        @Override public Booking createBooking(BookingRequest request) { throw new UnsupportedOperationException(); }
        @Override public List<Booking> getBookings() { return Collections.emptyList(); }
        @Override public List<Booking> getVisibleBookings() { return Collections.emptyList(); }
        @Override public Booking findBookingByPnr(String pnr) { return null; }
        @Override public void cancelBooking(long id) { }
        @Override public void hideBookingFromList(long id) { }
    }
}
