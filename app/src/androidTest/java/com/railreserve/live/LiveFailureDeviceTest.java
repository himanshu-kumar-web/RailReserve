package com.railreserve.live;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.ListView;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.railreserve.BuildConfig;
import com.railreserve.R;
import com.railreserve.ui.LiveScheduleActivity;
import com.railreserve.ui.LiveSearchActivity;
import com.railreserve.ui.LiveTrainResultsActivity;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Run only with a localhost fixture APK; the HTTPS application never runs these requests. */
@RunWith(AndroidJUnit4.class)
public class LiveFailureDeviceTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final List<Activity> activities = new ArrayList<>();
    private LocalBackendFixture server;
    private static final String TRAINS = "{\"success\":true,\"data\":{\"from\":{\"code\":\"AAA\"},"
            + "\"to\":{\"code\":\"BBB\"},\"count\":1,\"trains\":[{\"number\":\"99999\",\"name\":\"TEST FIXTURE\",\"from\":{},\"to\":{}}]}}";
    private static final String STOPS = "{\"success\":true,\"data\":{\"train\":{\"number\":\"99999\",\"name\":\"TEST FIXTURE\"},"
            + "\"stops\":[{\"station\":{\"code\":\"AAA\"},\"sequence\":1}]}}";

    @Before public void setUp() throws Exception {
        assumeTrue("Requires the local fixture build", LocalBackendFixture.BASE_URL.equals(new BackendApiClient().getBaseUrl()));
        server = new LocalBackendFixture();
    }

    @After public void tearDown() throws Exception {
        main(() -> { for (Activity activity : activities) activity.finish(); return null; });
        if (server != null) server.close();
    }

    @Test public void emptyStationsCanRetryWithoutAnEmptyCacheTrap() throws Exception {
        server.handler = path -> new LocalBackendFixture.Response(200, "{\"success\":true,\"data\":{\"stations\":[]}}");
        Activity activity = stationQuery("AlphaEmpty");
        waitFor(() -> text(activity, R.id.liveSourceStatus).startsWith("No station matches"));
        assertTrue(main(() -> activity.findViewById(R.id.liveSourceStatus).isClickable()));
        recoverStation(activity, "AlphaEmpty");
    }

    @Test public void selectedLiveCardReusesCheckoutWithFailureCancellationAndRetry() throws Exception {
        assumeTrue(BuildConfig.LIVE_FARE_ENABLED && BuildConfig.LIVE_AVAILABILITY_ENABLED);
        server.handler = path -> new LocalBackendFixture.Response(200,
                path.contains("/fare") || path.contains("/availability") ? LocalBackendFixture.enquiry("12345","AAA","BBB",LocalDate.now().toString(),"2A",path.contains("/fare"),245.0)
                : path.contains("/schedule") ? "{\"success\":true,\"data\":{\"train\":{\"number\":\"12345\",\"name\":\"Fixture\",\"classes\":[\"2A\"]},\"stops\":[]}}"
                : "{\"success\":true,\"data\":{\"from\":{\"code\":\"AAA\"},\"to\":{\"code\":\"BBB\"},\"count\":1,\"trains\":["
                        + "{\"number\":\"12345\",\"name\":\"TEST FIXTURE Selected Express\",\"from\":{\"departure\":\"22:00\",\"day\":0},"
                        + "\"to\":{\"arrival\":\"06:00\",\"day\":1}}]}}");
        android.content.Intent intent = new android.content.Intent(instrumentation.getTargetContext(), LiveTrainResultsActivity.class)
                .putExtra(LiveSearchActivity.EXTRA_SOURCE_CODE, "AAA").putExtra(LiveSearchActivity.EXTRA_DESTINATION_CODE, "BBB")
                .putExtra(LiveSearchActivity.EXTRA_DATE, LocalDate.now().toString());
        androidx.test.core.app.ActivityScenario<LiveTrainResultsActivity> screen = androidx.test.core.app.ActivityScenario.launch(intent);
        com.railreserve.service.BookingService bookingService = com.railreserve.service.BookingServiceFactory.create(instrumentation.getTargetContext());
        String checkout = null;
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(com.railreserve.ui.BookingActivity.class.getName(), null, false);
        try {
            java.util.concurrent.atomic.AtomicReference<LiveTrainResultsActivity> current = new java.util.concurrent.atomic.AtomicReference<>();
            screen.onActivity(current::set);
            waitFor(() -> ((ListView) current.get().findViewById(R.id.liveResultsList)).getAdapter().getCount() == 1);
            screen.recreate();
            screen.onActivity(current::set);
            waitFor(() -> ((ListView) current.get().findViewById(R.id.liveResultsList)).getAdapter().getCount() == 1);
            Instrumentation.ActivityMonitor detailsMonitor = instrumentation.addMonitor(com.railreserve.ui.TrainDetailsActivity.class.getName(), null, false);
            screen.onActivity(activity -> {
                ListView list = activity.findViewById(R.id.liveResultsList);
                View row = list.getAdapter().getView(0, null, list);
                row.performClick(); row.performClick();
            });
            Activity details = instrumentation.waitForMonitorWithTimeout(detailsMonitor, 10000);
            instrumentation.removeMonitor(detailsMonitor);
            assertNotNull(details); activities.add(details);
            assertEquals(1, detailsMonitor.getHits());
            waitFor(() -> details.findViewById(R.id.detailsClasses).findViewWithTag("2A") != null);
            main(() -> {
                View card = details.findViewById(R.id.detailsClasses).findViewWithTag("2A");
                assertFalse(card.findViewById(R.id.classDemoBook).isEnabled());
                card.findViewById(R.id.classFareCheck).performClick();
                card.findViewById(R.id.classAvailabilityCheck).performClick();
                return null;
            });
            waitFor(() -> details.findViewById(R.id.detailsClasses).findViewWithTag("2A").findViewById(R.id.classDemoBook).isEnabled() && ((TextView) details.findViewById(R.id.detailsClasses).findViewWithTag("2A").findViewById(R.id.classAvailability)).getText().toString().contains("GNWL107/WL62"));
            main(() -> {
                View card = details.findViewById(R.id.detailsClasses).findViewWithTag("2A");
                card.findViewById(R.id.classDemoBook).performClick(); card.findViewById(R.id.classDemoBook).performClick();
                return null;
            });
            com.railreserve.ui.BookingActivity booking = (com.railreserve.ui.BookingActivity) instrumentation.waitForMonitorWithTimeout(monitor, 10000);
            assertNotNull("Selected train must reach the existing passenger form", booking);
            activities.add(booking);
            assertEquals(1, monitor.getHits());
            waitFor(() -> booking.findViewById(R.id.confirmBookingButton).isEnabled());
            assertEquals("12345", booking.getIntent().getStringExtra(com.railreserve.ui.BookingActivity.EXTRA_TRAIN_NUMBER));
            assertTrue(main(() -> text(booking,R.id.bookingFareSummary)).contains("245.00"));
            assertEquals("2A", booking.getIntent().getStringExtra(com.railreserve.ui.BookingActivity.EXTRA_CLASS));
            assertEquals(LocalDate.now().toString(), booking.getIntent().getStringExtra(com.railreserve.ui.BookingActivity.EXTRA_DATE));
            main(() -> {
                assertEquals(View.VISIBLE, booking.findViewById(R.id.bookingSimulationNotice).getVisibility());
                ((TextView) booking.findViewById(R.id.passengerNameInput)).setText("Simulation UI Verification");
                ((TextView) booking.findViewById(R.id.passengerAgeInput)).setText("25");
                ((android.widget.Spinner) booking.findViewById(R.id.genderSpinner)).setSelection(3);
                return null;
            });
            instrumentation.waitForIdleSync();
            main(() -> { booking.findViewById(R.id.confirmBookingButton).performClick(); return null; });
            assertTrue(main(() -> text(booking,R.id.checkoutTotal)).contains("245.00"));
            checkout = main(() -> new androidx.lifecycle.ViewModelProvider(booking)
                    .get(com.railreserve.ui.BookingSubmissionViewModel.class).getCheckoutReference());
            main(() -> {
                ((android.widget.Spinner) booking.findViewById(R.id.demoPaymentScenario)).setSelection(1);
                return null;
            });
            instrumentation.waitForIdleSync();
            main(() -> { booking.findViewById(R.id.demoPayButton).performClick(); return null; });
            waitFor(() -> text(booking, R.id.demoPaymentStatus).contains("Demo payment failed"));
            assertNull(bookingService.findBookingByCheckoutReference(checkout));
            main(() -> { ((android.widget.Spinner) booking.findViewById(R.id.demoPaymentScenario)).setSelection(2); return null; });
            instrumentation.waitForIdleSync();
            main(() -> { booking.findViewById(R.id.demoPayButton).performClick(); return null; });
            waitFor(() -> text(booking, R.id.demoPaymentStatus).contains("Demo payment cancelled"));
            assertNull(bookingService.findBookingByCheckoutReference(checkout));
            main(() -> { booking.findViewById(R.id.demoPayButton).performClick(); booking.findViewById(R.id.demoPayButton).performClick(); return null; });
            waitFor(() -> new androidx.lifecycle.ViewModelProvider(booking).get(com.railreserve.ui.BookingSubmissionViewModel.class)
                    .getState().getValue().getBooking() != null);
            com.railreserve.model.Booking ticket = bookingService.findBookingByCheckoutReference(checkout);
            assertEquals("12345", ticket.getTrainNumber());
            assertEquals("TEST FIXTURE Selected Express", ticket.getTrainName());
            assertEquals("AAA", ticket.getSource()); assertEquals("BBB", ticket.getDestination());
            assertEquals(com.railreserve.model.TravelClass.AC_TWO_TIER, ticket.getTravelClass());
            assertEquals(24500L, ticket.getPayment().getAmountPaise());
            assertEquals(245,ticket.getFare());
            assertNotNull(ticket.getFareSnapshot());
            assertTrue(com.railreserve.ui.PaymentReceiptText.receipt(ticket).contains("245.00"));
            assertTrue(com.railreserve.ui.PaymentReceiptText.receipt(ticket).contains("WAITLIST"));
            assertEquals("GNWL107/WL62",ticket.getFareSnapshot().getAvailability());
            assertEquals(ticket.getId(), bookingService.findBookingByPnr(ticket.getPnr()).getId());
            bookingService.cancelBooking(ticket.getId());
            assertEquals("SIMULATED_REFUNDED", bookingService.findBookingByPnr(ticket.getPnr()).getPayment().getRefundStatus());
            assertEquals(6, bookingService.getAvailableSeats(ticket.getJourneyId(), "2A").size());
            assertEquals(1,server.paths.stream().filter(path -> path.contains("/fare")).count());
            assertEquals(1,server.paths.stream().filter(path -> path.contains("/availability")).count());
        } finally {
            if (checkout != null) {
                com.railreserve.model.Booking own = bookingService.findBookingByCheckoutReference(checkout);
                if (own != null && "CONFIRMED".equals(own.getStatus())) bookingService.cancelBooking(own.getId());
            }
            instrumentation.removeMonitor(monitor);
            screen.close();
        }
    }

    @Test public void stationHttpFailuresInvalidJsonAndDisconnectRecover() throws Exception {
        int[] statuses = {403, 429, 503, 200, -1};
        for (int status : statuses) {
            server.handler = path -> status == -1 ? null : new LocalBackendFixture.Response(status, "invalid-json-private-text");
            String query = "AlphaError" + status;
            Activity activity = stationQuery(query);
            waitFor(() -> activity.findViewById(R.id.liveSourceStatus).isClickable());
            String message = main(() -> text(activity, R.id.liveSourceStatus));
            assertFalse(message.contains("invalid-json-private-text"));
            assertFalse(message.contains("Searching live"));
            recoverStation(activity, query);
            main(() -> { activity.finish(); return null; });
        }
    }

    @Test public void actualReadTimeoutEndsLoadingAndAllowsRetry() throws Exception {
        server.handler = path -> new LocalBackendFixture.Response(200, stationBody("AlphaTimeout"), 28000);
        Activity activity = stationQuery("AlphaTimeout");
        waitFor(() -> text(activity, R.id.liveSourceStatus).contains("timed out"));
        assertTrue(main(() -> activity.findViewById(R.id.liveSourceStatus).isClickable()));
        recoverStation(activity, "AlphaTimeout");
    }

    @Test public void slowOldQueryCannotOverwriteLatestQueryOrOtherField() throws Exception {
        server.handler = path -> new LocalBackendFixture.Response(200,
                stationBody(path.contains("Old") ? "AlphaOld" : "AlphaLatest"), path.contains("Old") ? 1200 : 0);
        int before = server.requests.get();
        Activity activity = stationQuery("AlphaOld");
        waitFor(() -> server.requests.get() > before);
        main(() -> { ((TextView) activity.findViewById(R.id.liveSourceInput)).setText("AlphaLatest"); return null; });
        waitFor(() -> text(activity, R.id.liveSourceStatus).startsWith("Select a station"));
        assertTrue(main(() -> ((AutoCompleteTextView) activity.findViewById(R.id.liveSourceInput))
                .getAdapter().getItem(0).toString().contains("AlphaLatest")));
        assertTrue(main(() -> text(activity, R.id.liveDestinationStatus).startsWith("Type at least")));
    }

    @Test public void emptyTrainsRetryAndCapabilitiesCannotMakeRequests() throws Exception {
        assumeTrue(!BuildConfig.LIVE_FARE_ENABLED && !BuildConfig.LIVE_AVAILABILITY_ENABLED);
        server.handler = path -> new LocalBackendFixture.Response(200,
                "{\"success\":true,\"data\":{\"from\":{\"code\":\"AAA\"},\"to\":{\"code\":\"BBB\"},\"count\":0,\"trains\":[]}}");
        Activity activity = results();
        waitFor(() -> activity.findViewById(R.id.liveResultsRetry).getVisibility() == View.VISIBLE);
        assertTrue(main(() -> text(activity, R.id.liveResultsStatus).startsWith("No trains")));
        recoverTrains(activity);
        assertFalse(BuildConfig.LIVE_FARE_ENABLED);
        assertFalse(BuildConfig.LIVE_AVAILABILITY_ENABLED);
        assumeTrue(!BuildConfig.LIVE_FARE_ENABLED && !BuildConfig.LIVE_AVAILABILITY_ENABLED);
        int before = server.requests.get();
        BackendException fare = assertThrows(BackendException.class, () -> LiveDataRepository.getInstance()
                .getFare("99999", "AAA", "BBB", LocalDate.now().toString(), "3A", "GN"));
        assertEquals("CAPABILITY_UNAVAILABLE", fare.getCode());
        assertThrows(BackendException.class, () -> LiveDataRepository.getInstance()
                .getAvailability("99999", "AAA", "BBB", LocalDate.now().toString(), "3A", "GN"));
        assertEquals(before, server.requests.get());
    }

    @Test public void trainHttpFailuresInvalidJsonAndDisconnectRecover() throws Exception {
        for (int status : new int[]{403, 429, 503, 200, -1}) {
            server.handler = path -> status == -1 ? null : new LocalBackendFixture.Response(status, "invalid-json");
            Activity activity = results();
            waitFor(() -> activity.findViewById(R.id.liveResultsRetry).getVisibility() == View.VISIBLE);
            assertEquals(View.GONE, (int) main(() -> activity.findViewById(R.id.liveResultsProgress).getVisibility()));
            recoverTrains(activity);
            main(() -> { activity.finish(); return null; });
        }
    }

    @Test public void scheduleHttpFailuresInvalidJsonAndDisconnectRecover() throws Exception {
        for (int status : new int[]{403, 429, 503, 200, -1}) {
            server.handler = path -> status == -1 ? null : new LocalBackendFixture.Response(status, "invalid-json");
            Activity activity = start(new Intent(instrumentation.getTargetContext(), LiveScheduleActivity.class)
                    .putExtra(LiveScheduleActivity.EXTRA_TRAIN_NUMBER, status == -1 ? "99901" : "99" + status));
            waitFor(() -> activity.findViewById(R.id.liveScheduleRetry).getVisibility() == View.VISIBLE);
            assertEquals(View.GONE, (int) main(() -> activity.findViewById(R.id.liveScheduleProgress).getVisibility()));
            server.handler = path -> new LocalBackendFixture.Response(200, STOPS);
            main(() -> { activity.findViewById(R.id.liveScheduleRetry).performClick(); return null; });
            waitFor(() -> ((ListView) activity.findViewById(R.id.liveScheduleList)).getAdapter().getCount() == 1);
            main(() -> { activity.finish(); return null; });
        }
    }

    @Test public void refusedConnectionCanRetryAfterServerRestart() throws Exception {
        server.close();
        Activity activity = stationQuery("AlphaRefused");
        waitFor(() -> activity.findViewById(R.id.liveSourceStatus).isClickable());
        assertTrue(main(() -> text(activity, R.id.liveSourceStatus).contains("Unable to connect")));
        server = new LocalBackendFixture();
        recoverStation(activity, "AlphaRefused");
    }

    @Test public void emptyAndMissingSchedulesCanRetry() throws Exception {
        for (String body : new String[]{
                "{\"success\":true,\"data\":{\"train\":{\"number\":\"99997\",\"name\":\"TEST FIXTURE\"},\"stops\":[]}}",
                "{\"success\":true,\"data\":{}}"}) {
            server.handler = path -> new LocalBackendFixture.Response(200, body);
            Activity activity = start(new Intent(instrumentation.getTargetContext(), LiveScheduleActivity.class)
                    .putExtra(LiveScheduleActivity.EXTRA_TRAIN_NUMBER, body.contains("99997") ? "99997" : "99998"));
            waitFor(() -> activity.findViewById(R.id.liveScheduleRetry).getVisibility() == View.VISIBLE);
            assertEquals(View.GONE, (int) main(() -> activity.findViewById(R.id.liveScheduleProgress).getVisibility()));
            server.handler = path -> new LocalBackendFixture.Response(200, STOPS);
            main(() -> { activity.findViewById(R.id.liveScheduleRetry).performClick(); return null; });
            waitFor(() -> ((ListView) activity.findViewById(R.id.liveScheduleList)).getAdapter().getCount() == 1);
            main(() -> { activity.finish(); return null; });
        }
    }

    private Activity stationQuery(String query) {
        Activity activity = start(new Intent(instrumentation.getTargetContext(), LiveSearchActivity.class));
        main(() -> {
            activity.findViewById(R.id.liveSourceInput).requestFocus();
            ((TextView) activity.findViewById(R.id.liveSourceInput)).setText(query);
            return null;
        });
        return activity;
    }

    private void recoverStation(Activity activity, String query) throws Exception {
        server.handler = path -> new LocalBackendFixture.Response(200, stationBody(query));
        main(() -> { activity.findViewById(R.id.liveSourceStatus).performClick(); return null; });
        waitFor(() -> text(activity, R.id.liveSourceStatus).startsWith("Select a station"));
        assertTrue(main(() -> ((AutoCompleteTextView) activity.findViewById(R.id.liveSourceInput)).getAdapter().getCount() > 0));
    }

    private Activity results() {
        return start(new Intent(instrumentation.getTargetContext(), LiveTrainResultsActivity.class)
                .putExtra(LiveSearchActivity.EXTRA_SOURCE_CODE, "AAA")
                .putExtra(LiveSearchActivity.EXTRA_DESTINATION_CODE, "BBB")
                .putExtra(LiveSearchActivity.EXTRA_DATE, LocalDate.now().toString()));
    }

    private void recoverTrains(Activity activity) throws Exception {
        server.handler = path -> new LocalBackendFixture.Response(200, TRAINS);
        main(() -> { activity.findViewById(R.id.liveResultsRetry).performClick(); return null; });
        waitFor(() -> ((ListView) activity.findViewById(R.id.liveResultsList)).getAdapter().getCount() == 1);
        main(() -> {
            ListView list = activity.findViewById(R.id.liveResultsList);
            View row = list.getAdapter().getView(0, null, list);
            assertTrue(row.findViewById(R.id.liveTrainDetailsButton).isEnabled());
            assertEquals(activity.getString(R.string.live_results_details_notice), text(activity, R.id.liveCapabilityNotice));
            return null;
        });
    }

    private String stationBody(String query) {
        return "{\"success\":true,\"data\":{\"stations\":[{\"code\":\"TST\",\"name\":\"TEST FIXTURE " + query + "\"}]}}";
    }

    private Activity start(Intent intent) {
        Activity activity = instrumentation.startActivitySync(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        activities.add(activity);
        return activity;
    }

    private String text(Activity activity, int id) { return ((TextView) activity.findViewById(id)).getText().toString(); }

    private void waitFor(Supplier<Boolean> condition) throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + 38000;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            if (main(condition)) return;
            Thread.sleep(100);
        }
        fail("Expected UI state did not appear within 38 seconds");
    }

    private <T> T main(Supplier<T> action) {
        AtomicReference<T> result = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> result.set(action.get()));
        return result.get();
    }
}
