package com.railreserve.live;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.railreserve.BuildConfig;
import com.railreserve.R;
import com.railreserve.ui.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

@RunWith(AndroidJUnit4.class)
public class TrainDetailsDeviceTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private LocalBackendFixture server;
    private final List<Activity> opened = new ArrayList<>();
    @Before public void before() throws Exception {
        assumeTrue(LocalBackendFixture.BASE_URL.equals(new BackendApiClient().getBaseUrl()));
        server = new LocalBackendFixture();
    }
    @After public void after() throws Exception {
        main(() -> { for (Activity screen : opened) screen.finish(); return null; });
        if (server != null) server.close();
    }

    @Test public void allMatchingTrainsOpenDetailsWithoutGlobalClassSelection() throws Exception {
        String[] offered = {"1A", "2A", "3A", "3E", "SL", "CC", "EC", "2S"};
        server.handler = path -> new LocalBackendFixture.Response(200, path.contains("/between") ? trains("90310", offered) : schedule("90310", offered));
        Activity results = start(route(LiveTrainResultsActivity.class));
        waitFor(() -> ((ListView) results.findViewById(R.id.liveResultsList)).getAdapter().getCount() == 2);
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(TrainDetailsActivity.class.getName(), null, false);
        try {
            main(() -> {
                assertFalse(hasSpinner(results.findViewById(android.R.id.content)));
                ListView list = results.findViewById(R.id.liveResultsList);
                View row = list.getAdapter().getView(0, null, list);
                row.performClick(); row.performClick(); return null;
            });
            Activity details = instrumentation.waitForMonitorWithTimeout(monitor, 5000); assertNotNull(details); opened.add(details);
            waitFor(() -> details.findViewById(R.id.detailsProgress).getVisibility() == View.GONE);
            assertEquals(1, monitor.getHits());
            main(() -> {
                assertTrue(text(details, R.id.detailsHeading).contains("90310"));
                assertTrue(text(details, R.id.detailsJourney).contains("Alpha (AAA) → Beta (BBB)"));
                assertTrue(text(details, R.id.detailsJourney).contains(LocalDate.now().toString()));
                LinearLayout cards = details.findViewById(R.id.detailsClasses); assertEquals(8, cards.getChildCount());
                for (String code : offered) {
                    View card = cards.findViewWithTag(code); assertNotNull(card);
                    assertTrue(((TextView) card.findViewById(R.id.classSource)).getText().toString().contains("RailCore route search"));
                    assertEquals(LiveClassCatalog.supportsDemo(code), card.findViewById(R.id.classDemoBook).isEnabled());
                    if (!BuildConfig.LIVE_FARE_ENABLED) assertEquals(View.GONE, card.findViewById(R.id.classFareCheck).getVisibility());
                }
                return null;
            });
            assertFalse(server.paths.stream().anyMatch(path -> path.contains("/fare") || path.contains("/availability")));
        } finally { instrumentation.removeMonitor(monitor); }
    }

    @Test public void scheduleSuppliesOnlyItsVerifiedClassesAndRotationDoesNotRepeatSlowRequest() throws Exception {
        server.handler = path -> new LocalBackendFixture.Response(200, schedule("90311", new String[]{"CC", "EC", "EV"}), 900);
        try (ActivityScenario<TrainDetailsActivity> scenario = ActivityScenario.launch(detailsIntent("90311", null, 1))) {
            AtomicReference<TrainDetailsActivity> current = new AtomicReference<>(); scenario.onActivity(current::set);
            waitFor(() -> server.requests.get() == 1);
            scenario.recreate(); scenario.onActivity(current::set);
            waitFor(() -> current.get().findViewById(R.id.detailsProgress).getVisibility() == View.GONE);
            scenario.onActivity(activity -> {
                LinearLayout cards = activity.findViewById(R.id.detailsClasses); assertEquals(3, cards.getChildCount());
                assertNull(cards.findViewWithTag("SL")); assertNull(cards.findViewWithTag("3A"));
                assertNotNull(cards.findViewWithTag("EV"));
                assertFalse(cards.findViewWithTag("CC").findViewById(R.id.classDemoBook).isEnabled());
                assertEquals(0, ((LinearLayout) activity.findViewById(R.id.detailsDemoOnlyClasses)).getChildCount());
            });
            assertEquals(1, server.requests.get());
        }
    }

    @Test public void unknownClassesStaySeparateFromDemoAndExplicitEmptyListStaysEmpty() throws Exception {
        server.handler = path -> new LocalBackendFixture.Response(200, schedule(path.contains("90312") ? "90312" : "90313", null));
        Activity unknown = start(detailsIntent("90312", null, 1));
        waitFor(() -> unknown.findViewById(R.id.detailsProgress).getVisibility() == View.GONE);
        main(() -> {
            assertEquals(0, ((LinearLayout) unknown.findViewById(R.id.detailsClasses)).getChildCount());
            assertEquals(2, ((LinearLayout) unknown.findViewById(R.id.detailsDemoOnlyClasses)).getChildCount());
            assertEquals(View.VISIBLE, unknown.findViewById(R.id.detailsDemoOnlyNotice).getVisibility()); return null;
        });
        Activity empty = start(detailsIntent("90313", new String[]{}, 1));
        waitFor(() -> empty.findViewById(R.id.detailsProgress).getVisibility() == View.GONE);
        main(() -> { assertEquals(0, ((LinearLayout) empty.findViewById(R.id.detailsClasses)).getChildCount());
            assertEquals(0, ((LinearLayout) empty.findViewById(R.id.detailsDemoOnlyClasses)).getChildCount()); return null; });
    }

    @Test public void scheduleFailuresTerminateLoadingAndRetryWithoutLosingJourney() throws Exception {
        for (int status : new int[]{403, 429, 503, 200}) {
            String number = status == 200 ? "90314" : "90" + status;
            server.handler = path -> new LocalBackendFixture.Response(status, "invalid-private-json");
            Activity details = start(detailsIntent(number, null, 1));
            waitFor(() -> details.findViewById(R.id.detailsRetry).getVisibility() == View.VISIBLE);
            assertEquals(View.GONE, (int) main(() -> details.findViewById(R.id.detailsProgress).getVisibility()));
            assertFalse(main(() -> text(details, R.id.detailsClassesStatus)).contains("private"));
            server.handler = path -> new LocalBackendFixture.Response(200, schedule(number, new String[]{"SL"}));
            main(() -> { details.findViewById(R.id.detailsRetry).performClick(); return null; });
            waitFor(() -> details.findViewById(R.id.detailsClasses).findViewWithTag("SL") != null);
            assertTrue(main(() -> text(details, R.id.detailsJourney)).contains(LocalDate.now().toString()));
        }
    }

    @Test public void missingTimetableDoesNotEnableSimulationOrInventTimes() throws Exception {
        server.handler = path -> new LocalBackendFixture.Response(200, schedule("90315", new String[]{"3A"}));
        Intent intent = route(TrainDetailsActivity.class).putExtra(TrainDetailsActivity.EXTRA_TRAIN,
                new LiveTrain("90315", "Fixture", null, null, null, null, null, null, null, null, null, null, null));
        Activity details = start(intent);
        waitFor(() -> details.findViewById(R.id.detailsClasses).findViewWithTag("3A") != null);
        main(() -> {
            assertTrue(text(details, R.id.detailsJourney).contains("Departure: Not provided"));
            assertFalse(details.findViewById(R.id.detailsClasses).findViewWithTag("3A").findViewById(R.id.classDemoBook).isEnabled()); return null;
        });
    }

    @Test public void enabledFixtureChecksKeepProvenanceRejectDuplicatesAndExpire() throws Exception {
        assumeTrue(BuildConfig.LIVE_FARE_ENABLED && BuildConfig.LIVE_AVAILABILITY_ENABLED);
        server.handler = path -> new LocalBackendFixture.Response(200, path.contains("/fare")
                ? "{\"success\":true,\"data\":{\"fareKnown\":true,\"totalFare\":875.5,\"providerSource\":\"TEST FIXTURE authorized fare\",\"retrievedAt\":\"2026-11-12T10:00:00Z\"}}"
                : path.contains("/availability") ? availability(LocalDate.now().toString()) : schedule("90316", new String[]{"3A"}));
        try (ActivityScenario<TrainDetailsActivity> scenario = ActivityScenario.launch(detailsIntent("90316", new String[]{"3A"}, 1))) {
            AtomicReference<TrainDetailsActivity> current = new AtomicReference<>(); scenario.onActivity(current::set);
            waitFor(() -> current.get().findViewById(R.id.detailsProgress).getVisibility() == View.GONE);
            scenario.onActivity(activity -> { View card = card(activity); card.findViewById(R.id.classFareCheck).performClick(); card.findViewById(R.id.classFareCheck).performClick();
                card.findViewById(R.id.classAvailabilityCheck).performClick(); card.findViewById(R.id.classAvailabilityCheck).performClick(); });
            waitFor(() -> ((TextView) card(current.get()).findViewById(R.id.classAvailability)).getText().toString().contains("TEST FIXTURE"));
            scenario.recreate(); scenario.onActivity(current::set);
            assertEquals(1, server.paths.stream().filter(path -> path.contains("/fare")).count());
            assertEquals(1, server.paths.stream().filter(path -> path.contains("/availability")).count());
            assertTrue(main(() -> ((TextView) card(current.get()).findViewById(R.id.classFare)).getText().toString()).contains("875.50"));
            // Advance retained result expiry without waiting a minute or changing the phone clock.
            java.lang.reflect.Field field = TrainDetailsViewModel.class.getDeclaredField("checks"); field.setAccessible(true);
            main(() -> {
                try {
                    Object model = new androidx.lifecycle.ViewModelProvider(current.get()).get(TrainDetailsViewModel.class);
                    for (Object check : ((Map<?, ?>) field.get(model)).values()) { java.lang.reflect.Field expiry = check.getClass().getDeclaredField("expiresAt"); expiry.setAccessible(true); expiry.setLong(check, 1); }
                } catch (Exception e) { throw new AssertionError(e); } return null;
            });
            scenario.recreate(); scenario.onActivity(current::set);
            assertTrue(main(() -> ((TextView) card(current.get()).findViewById(R.id.classAvailability)).getText().toString()).contains("not checked"));
            assertEquals(3, server.requests.get());
        }
    }

    @Test public void enabledFixtureMissingFareAndWrongDateRemainRetryable() throws Exception {
        assumeTrue(BuildConfig.LIVE_FARE_ENABLED && BuildConfig.LIVE_AVAILABILITY_ENABLED);
        server.handler = path -> new LocalBackendFixture.Response(200, path.contains("/fare") ? "{\"success\":true,\"data\":{\"fareKnown\":false,\"totalFare\":null}}"
                : path.contains("/availability") ? availability(LocalDate.now().plusDays(1).toString()) : schedule("90317", new String[]{"3A"}));
        Activity details = start(detailsIntent("90317", new String[]{"3A"}, 1));
        waitFor(() -> details.findViewById(R.id.detailsProgress).getVisibility() == View.GONE);
        main(() -> { card(details).findViewById(R.id.classFareCheck).performClick(); card(details).findViewById(R.id.classAvailabilityCheck).performClick(); return null; });
        waitFor(() -> ((TextView) card(details).findViewById(R.id.classAvailability)).getText().toString().contains("did not match"));
        assertTrue(main(() -> ((TextView) card(details).findViewById(R.id.classFare)).getText().toString()).contains("not provided"));
        server.handler = path -> new LocalBackendFixture.Response(503, "invalid");
        main(() -> { card(details).findViewById(R.id.classAvailabilityCheck).performClick(); return null; });
        waitFor(() -> ((TextView) card(details).findViewById(R.id.classAvailability)).getText().toString().contains("temporarily unavailable"));
        server.handler = path -> new LocalBackendFixture.Response(200, availability(LocalDate.now().toString()));
        main(() -> { card(details).findViewById(R.id.classAvailabilityCheck).performClick(); return null; });
        waitFor(() -> ((TextView) card(details).findViewById(R.id.classAvailability)).getText().toString().contains("TEST FIXTURE"));
        assertEquals(View.GONE, (int) main(() -> card(details).findViewById(R.id.classProgress).getVisibility()));
    }

    private String availability(String date) { return "{\"success\":true,\"data\":{\"availabilityStatus\":\"UNKNOWN\",\"statusType\":\"UNKNOWN\",\"availabilityDate\":\"" + date + "\",\"providerSource\":\"TEST FIXTURE availability\"}}"; }
    private View card(Activity activity) { return activity.findViewById(R.id.detailsClasses).findViewWithTag("3A"); }
    private Intent route(Class<?> type) { return new Intent(instrumentation.getTargetContext(), type)
            .putExtra(LiveSearchActivity.EXTRA_SOURCE_CODE, "AAA").putExtra(LiveSearchActivity.EXTRA_SOURCE_NAME, "Alpha")
            .putExtra(LiveSearchActivity.EXTRA_DESTINATION_CODE, "BBB").putExtra(LiveSearchActivity.EXTRA_DESTINATION_NAME, "Beta")
            .putExtra(LiveSearchActivity.EXTRA_DATE, LocalDate.now().toString()); }
    private Intent detailsIntent(String number, String[] classes, int arrivalDay) {
        return route(TrainDetailsActivity.class).putExtra(TrainDetailsActivity.EXTRA_TRAIN,
                new LiveTrain(number, "TEST FIXTURE " + number, "Express", null, null, "22:00", 0, "06:00", arrivalDay, 480, null, null,
                        Arrays.asList("MON"), classes == null ? null : Arrays.asList(classes), "RailCore route search"));
    }
    private String schedule(String number, String[] classes) {
        try { JSONObject train = new JSONObject().put("number", number).put("name", "TEST FIXTURE");
            if (classes != null) train.put("classes", new JSONArray(Arrays.asList(classes))).put("classSource", "RailCore schedule");
            return new JSONObject().put("success", true).put("data", new JSONObject().put("train", train).put("stops", new JSONArray("[{\"station\":{\"code\":\"AAA\"},\"sequence\":1}]"))).toString();
        } catch (Exception e) { throw new AssertionError(e); }
    }
    private String trains(String number, String[] classes) {
        try { JSONObject train = new JSONObject().put("number", number).put("name", "TEST FIXTURE").put("classes", new JSONArray(Arrays.asList(classes)))
                    .put("classSource", "RailCore route search").put("from", new JSONObject().put("departure", "22:00").put("day", 0))
                    .put("to", new JSONObject().put("arrival", "06:00").put("day", 1));
            return new JSONObject().put("success", true).put("data", new JSONObject().put("from", new JSONObject().put("code", "AAA"))
                    .put("to", new JSONObject().put("code", "BBB")).put("count", 2).put("trains", new JSONArray().put(train).put(new JSONObject(train.toString()).put("number", "90319")))).toString();
        } catch (Exception e) { throw new AssertionError(e); }
    }
    private boolean hasSpinner(View view) { if (view instanceof Spinner) return true; if (view instanceof android.view.ViewGroup) {
        android.view.ViewGroup group = (android.view.ViewGroup) view; for (int i = 0; i < group.getChildCount(); i++) if (hasSpinner(group.getChildAt(i))) return true; } return false; }
    // Each direct launch owns a fresh test task. NEW_TASK alone may reuse its root on Android 16.
    private Activity start(Intent intent) { Activity screen = instrumentation.startActivitySync(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)); opened.add(screen); return screen; }
    private String text(Activity activity, int id) { return ((TextView) activity.findViewById(id)).getText().toString(); }
    private void waitFor(Supplier<Boolean> condition) throws Exception { long end = android.os.SystemClock.elapsedRealtime() + 12000;
        while (android.os.SystemClock.elapsedRealtime() < end) { if (main(condition)) return; Thread.sleep(50); } fail("Details state did not settle"); }
    private <T> T main(Supplier<T> action) { AtomicReference<T> result = new AtomicReference<>(); instrumentation.runOnMainSync(() -> result.set(action.get())); return result.get(); }
}
