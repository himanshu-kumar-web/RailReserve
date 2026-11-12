package com.railreserve.live;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.view.View;
import android.widget.DatePicker;
import android.widget.ListView;
import android.widget.TextView;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.railreserve.R;
import com.railreserve.BuildConfig;
import com.railreserve.ui.LiveScheduleActivity;
import com.railreserve.ui.LiveSearchActivity;
import com.railreserve.ui.LiveTrainResultsActivity;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

@RunWith(AndroidJUnit4.class)
public class LiveJourneyDeviceTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

    @Test public void datePickerRestrictsRangeAndUpdatesSelectedDate() {
        Activity activity = start(new Intent(instrumentation.getTargetContext(), LiveSearchActivity.class));
        try {
            onMain(() -> { activity.findViewById(R.id.liveDateButton).performClick(); return null; });
            LocalDate today = LocalDate.now();
            LocalDate tomorrow = today.plusDays(1);
            onView(isAssignableFrom(DatePicker.class)).perform(action(view -> {
                DatePicker picker = (DatePicker) view;
                assertEquals(today, Instant.ofEpochMilli(picker.getMinDate()).atZone(ZoneId.systemDefault()).toLocalDate());
                assertEquals(today.plusDays(14), Instant.ofEpochMilli(picker.getMaxDate()).atZone(ZoneId.systemDefault()).toLocalDate());
                picker.updateDate(tomorrow.getYear(), tomorrow.getMonthValue() - 1, tomorrow.getDayOfMonth());
            }));
            onView(withId(android.R.id.button1)).perform(action(View::performClick));
            assertTrue(onMain(() -> ((TextView) activity.findViewById(R.id.liveDateButton)).getText().toString())
                    .contains(tomorrow.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault()))));
        } finally { onMain(() -> { activity.finish(); return null; }); }
    }

    /** Opt-in: one live route search and, if trains exist, one schedule. No fare/seat requests. */
    @Test public void readOnlyTrainResultsAndScheduleLoadOnPhone() throws Exception {
        assumeTrue("Explicit opt-in required for provider requests", "true".equals(
                InstrumentationRegistry.getArguments().getString("runLiveTrainChecks")));
        Activity results = start(new Intent(instrumentation.getTargetContext(), LiveTrainResultsActivity.class)
                .putExtra(LiveSearchActivity.EXTRA_SOURCE_CODE, "PRYJ")
                .putExtra(LiveSearchActivity.EXTRA_SOURCE_NAME, "Prayagraj Junction")
                .putExtra(LiveSearchActivity.EXTRA_DESTINATION_CODE, "NDLS")
                .putExtra(LiveSearchActivity.EXTRA_DESTINATION_NAME, "New Delhi")
                .putExtra(LiveSearchActivity.EXTRA_DATE, LocalDate.now().toString()));
        Activity schedule = null;
        Activity details = null;
        Instrumentation.ActivityMonitor monitor = null;
        try {
            waitUntil(() -> results.findViewById(R.id.liveResultsProgress).getVisibility() != View.VISIBLE);
            int count = onMain(() -> ((ListView) results.findViewById(R.id.liveResultsList)).getAdapter().getCount());
            if (count == 0) {
                assertEquals("No trains were returned for this route and date.",
                        onMain(() -> ((TextView) results.findViewById(R.id.liveResultsStatus)).getText().toString()));
                return; // Empty live results are valid; this branch does not verify a schedule.
            }
            monitor = instrumentation.addMonitor(com.railreserve.ui.TrainDetailsActivity.class.getName(), null, false);
            onMain(() -> {
                ListView list = results.findViewById(R.id.liveResultsList);
                View row = list.getAdapter().getView(0, null, list);
                row.performClick();
                return null;
            });
            details = instrumentation.waitForMonitorWithTimeout(monitor, 5000);
            assertNotNull("Train card must open details", details);
            instrumentation.removeMonitor(monitor);
            monitor = instrumentation.addMonitor(LiveScheduleActivity.class.getName(), null, false);
            Activity trainDetails = details;
            onMain(() -> { trainDetails.findViewById(R.id.detailsSchedule).performClick(); return null; });
            schedule = instrumentation.waitForMonitorWithTimeout(monitor, 5000);
            assertNotNull("Schedule action must open its activity", schedule);
            Activity opened = schedule;
            waitUntil(() -> opened.findViewById(R.id.liveScheduleProgress).getVisibility() != View.VISIBLE);
            int stops = onMain(() -> ((ListView) opened.findViewById(R.id.liveScheduleList)).getAdapter().getCount());
            assertTrue("Schedule failed: " + onMain(() -> ((TextView) opened.findViewById(R.id.liveScheduleStatus))
                    .getText().toString()), stops > 0);
        } finally {
            if (monitor != null) instrumentation.removeMonitor(monitor);
            Activity opened = schedule;
            Activity detailScreen = details;
            onMain(() -> { if (opened != null) opened.finish(); if (detailScreen != null) detailScreen.finish(); results.finish(); return null; });
        }
    }

    private Activity start(Intent intent) {
        return instrumentation.startActivitySync(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void waitUntil(Supplier<Boolean> condition) throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + 45000;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            if (onMain(condition)) return;
            Thread.sleep(100);
        }
        fail("Loading did not finish within 45 seconds");
    }

    private ViewAction action(Consumer<View> callback) {
        return new ViewAction() {
            @Override public Matcher<View> getConstraints() { return isAssignableFrom(View.class); }
            @Override public String getDescription() { return "Invoke this app's control on the UI thread"; }
            @Override public void perform(UiController controller, View view) {
                callback.accept(view);
                controller.loopMainThreadUntilIdle();
            }
        };
    }

    private <T> T onMain(Supplier<T> action) {
        AtomicReference<T> result = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> result.set(action.get()));
        return result.get();
    }
}
