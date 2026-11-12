package com.railreserve.ui;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.view.View;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.railreserve.MainActivity;
import com.railreserve.R;
import com.railreserve.model.Booking;
import com.railreserve.model.TrainSearchResult;
import com.railreserve.service.BookingService;
import com.railreserve.service.BookingServiceFactory;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Explicit opt-in: creates and cancels one named LOCAL demo booking; never calls a payment/provider API. */
@RunWith(AndroidJUnit4.class)
public class DemoCheckoutJourneyTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final List<Activity> opened = new ArrayList<>();
    private final List<Instrumentation.ActivityMonitor> monitors = new ArrayList<>();

    @Test public void demoTrainToPaymentPnrLookupCancellationAndSeatRelease() throws Exception {
        assumeTrue("Opt in to creating a local demonstration record", "true".equals(
                InstrumentationRegistry.getArguments().getString("runDemoCheckoutJourney")));
        BookingService service = BookingServiceFactory.create(instrumentation.getTargetContext());
        String checkout = null;
        try {
            Activity home = start(MainActivity.class);
            waitFor(() -> ((android.widget.AutoCompleteTextView) home.findViewById(R.id.sourceInput)).getAdapter() != null);
            Instrumentation.ActivityMonitor resultsMonitor = monitor(TrainResultsActivity.class);
            main(() -> {
                ((TextView) home.findViewById(R.id.sourceInput)).setText("Prayagraj");
                ((TextView) home.findViewById(R.id.destinationInput)).setText("New Delhi");
                home.findViewById(R.id.searchButton).performClick();
                return null;
            });
            Activity results = arrived(resultsMonitor);
            waitFor(() -> results.findViewById(R.id.resultsProgress).getVisibility() == View.GONE);
            Instrumentation.ActivityMonitor passengerMonitor = monitor(BookingActivity.class);
            main(() -> {
                ListView list = results.findViewById(R.id.resultsList);
                int available = -1;
                for (int i = 0; i < list.getAdapter().getCount(); i++) {
                    if (((TrainSearchResult) list.getAdapter().getItem(i)).getAvailableSeats() > 0) { available = i; break; }
                }
                assertTrue("A demo train must have an available seat", available >= 0);
                list.performItemClick(list.getAdapter().getView(available, null, list), available, list.getAdapter().getItemId(available));
                return null;
            });
            BookingActivity passenger = (BookingActivity) arrived(passengerMonitor);
            waitFor(() -> passenger.findViewById(R.id.confirmBookingButton).isEnabled());
            main(() -> {
                ((TextView) passenger.findViewById(R.id.passengerNameInput)).setText("Demo UPI Verification");
                ((TextView) passenger.findViewById(R.id.passengerAgeInput)).setText("25");
                ((Spinner) passenger.findViewById(R.id.genderSpinner)).setSelection(3);
                return null;
            });
            instrumentation.waitForIdleSync();
            main(() -> { passenger.findViewById(R.id.confirmBookingButton).performClick(); return null; });
            checkout = main(() -> new ViewModelProvider(passenger).get(BookingSubmissionViewModel.class).getCheckoutReference());
            assertNotNull(checkout);
            assertNull(service.findBookingByCheckoutReference(checkout));
            main(() -> {
                assertEquals(View.VISIBLE, passenger.findViewById(R.id.demoCheckoutPanel).getVisibility());
                passenger.findViewById(R.id.demoPayButton).performClick();
                assertFalse(((TextView) passenger.findViewById(R.id.demoPaymentStatus)).getText().toString().contains("SUCCESS"));
                return null;
            });
            waitFor(() -> confirmation(passenger) != null && confirmation(passenger).isShowing());
            String pnr = main(() -> ((TextView) confirmation(passenger).findViewById(R.id.confirmationPnr)).getText().toString());
            Booking booked = service.findBookingByPnr(pnr);
            assertEquals("SUCCESS", booked.getPayment().getStatus());
            main(() -> {
                String receipt = ((TextView) confirmation(passenger).findViewById(R.id.confirmationPayment)).getText().toString();
                assertTrue(receipt.contains("Demo UPI"));
                assertTrue(receipt.contains("Not Valid for Travel"));
                return null;
            });
            Instrumentation.ActivityMonitor bookingsMonitor = monitor(MyBookingsActivity.class);
            main(() -> { confirmation(passenger).getButton(AlertDialog.BUTTON_POSITIVE).performClick(); return null; });
            Activity bookings = arrived(bookingsMonitor);
            waitFor(() -> bookingPosition(bookings, pnr) >= 0);

            Activity lookup = start(FindBookingActivity.class);
            main(() -> {
                ((TextView) lookup.findViewById(R.id.pnrInput)).setText(pnr);
                lookup.findViewById(R.id.lookupButton).performClick();
                return null;
            });
            waitFor(() -> lookup.findViewById(R.id.lookupResultCard).getVisibility() == View.VISIBLE);
            main(() -> {
                assertTrue(((TextView) lookup.findViewById(R.id.lookupPayment)).getText().toString()
                        .contains(booked.getPayment().getTransactionReference()));
                lookup.finish();
                return null;
            });
            waitFor(() -> bookings.findViewById(R.id.bookingsProgress).getVisibility() == View.GONE);
            main(() -> {
                ListView list = bookings.findViewById(R.id.bookingsList);
                int position = bookingPosition(bookings, pnr);
                View row = list.getAdapter().getView(position, null, list);
                row.findViewById(R.id.cancelBookingButton).performClick();
                return null;
            });
            onView(withText("Cancel reservation")).inRoot(isDialog()).perform(new ViewAction() {
                @Override public Matcher<View> getConstraints() { return isAssignableFrom(View.class); }
                @Override public String getDescription() { return "Confirm this local demo cancellation"; }
                @Override public void perform(UiController controller, View view) { view.performClick(); controller.loopMainThreadUntilIdle(); }
            });
            waitFor(() -> {
                int position = bookingPosition(bookings, pnr);
                if (position < 0) return false;
                ListView list = bookings.findViewById(R.id.bookingsList);
                return "CANCELLED".equals(((Booking) list.getAdapter().getItem(position)).getStatus());
            });
            Booking cancelled = service.findBookingByPnr(pnr);
            assertEquals("SIMULATED_REFUNDED", cancelled.getPayment().getRefundStatus());
            assertTrue(service.getAvailableSeats(booked.getJourneyId(), booked.getTravelClass().getCode()).stream()
                    .anyMatch(seat -> seat.getId() == booked.getSeatId()));
        } finally {
            // Only this test's unique checkout can be cancelled here; existing user reservations are never edited.
            if (checkout != null) {
                Booking own = service.findBookingByCheckoutReference(checkout);
                if (own != null && "CONFIRMED".equals(own.getStatus())) service.cancelBooking(own.getId());
            }
            for (Instrumentation.ActivityMonitor monitor : monitors) instrumentation.removeMonitor(monitor);
            main(() -> { for (Activity activity : opened) if (!activity.isFinishing()) activity.finish(); return null; });
        }
    }

    private int bookingPosition(Activity activity, String pnr) {
        ListView list = activity.findViewById(R.id.bookingsList);
        for (int i = 0; i < list.getAdapter().getCount(); i++) if (pnr.equals(((Booking) list.getAdapter().getItem(i)).getPnr())) return i;
        return -1;
    }
    private AlertDialog confirmation(BookingActivity activity) {
        try { Field field = BookingActivity.class.getDeclaredField("confirmationDialog"); field.setAccessible(true); return (AlertDialog) field.get(activity); }
        catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }
    private Instrumentation.ActivityMonitor monitor(Class<? extends Activity> type) {
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(type.getName(), null, false);
        monitors.add(monitor); return monitor;
    }
    private Activity arrived(Instrumentation.ActivityMonitor monitor) {
        Activity activity = instrumentation.waitForMonitorWithTimeout(monitor, 15000);
        assertNotNull("Expected demo workflow screen", activity); opened.add(activity); return activity;
    }
    private Activity start(Class<? extends Activity> type) {
        Activity activity = instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        opened.add(activity); return activity;
    }
    private void waitFor(Supplier<Boolean> condition) throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + 10000;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            if (main(condition)) return;
            Thread.sleep(100);
        }
        fail("Demo journey screen did not reach the expected state");
    }
    private <T> T main(Supplier<T> action) {
        AtomicReference<T> result = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> result.set(action.get())); return result.get();
    }
}
