package com.railreserve.live;

import android.app.Instrumentation;
import android.content.Intent;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.railreserve.R;
import com.railreserve.ui.LiveSearchActivity;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Opt-in: at most two provider station queries; no bookings, fares, or availability requests. */
@RunWith(AndroidJUnit4.class)
public class LiveStationDeviceTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

    @Test public void bothStationFieldsLoadSelectAndValidateOnDevice() throws Exception {
        assumeTrue("Explicit opt-in required for provider requests", "true".equals(
                InstrumentationRegistry.getArguments().getString("runLiveStationChecks")));
        LiveSearchActivity activity = (LiveSearchActivity) instrumentation.startActivitySync(
                new Intent(instrumentation.getTargetContext(), LiveSearchActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        try {
            onMain(() -> { activity.findViewById(R.id.liveSearchButton).performClick(); return null; });
            assertNotNull(onMain(() -> ((TextView) activity.findViewById(R.id.liveSourceInput)).getError()));
            LiveStation source = select(activity, R.id.liveSourceInput, R.id.liveSourceStatus, "Prayagraj");
            // Reuses the station cache, so identical-route validation costs no extra provider request.
            select(activity, R.id.liveDestinationInput, R.id.liveDestinationStatus, "Prayagraj");
            onMain(() -> { activity.findViewById(R.id.liveSearchButton).performClick(); return null; });
            assertEquals("Choose a different destination station.", onMain(() ->
                    ((TextView) activity.findViewById(R.id.liveDestinationInput)).getError().toString()));
            LiveStation destination = select(activity, R.id.liveDestinationInput, R.id.liveDestinationStatus, "Delhi");
            assertNotEquals(source.getCode(), destination.getCode());
        } finally {
            onMain(() -> { activity.finish(); return null; });
        }
    }

    private LiveStation select(LiveSearchActivity activity, int inputId, int statusId, String query) throws Exception {
        onMain(() -> {
            AutoCompleteTextView input = activity.findViewById(inputId);
            input.requestFocus();
            input.setText(query);
            return null;
        });
        long deadline = android.os.SystemClock.elapsedRealtime() + 45000;
        boolean ready = false;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            ready = onMain(() -> {
                AutoCompleteTextView input = activity.findViewById(inputId);
                return input.getAdapter().getCount() > 0 && ((TextView) activity.findViewById(statusId))
                        .getText().toString().startsWith("Select a station");
            });
            if (ready) break;
            String status = onMain(() -> ((TextView) activity.findViewById(statusId)).getText().toString());
            if (status.contains("Tap here to retry") || status.startsWith("No station matches")) break;
            Thread.sleep(100);
        }
        assertTrue("Station suggestions failed: " + onMain(() ->
                ((TextView) activity.findViewById(statusId)).getText().toString()), ready);
        return onMain(() -> {
            AutoCompleteTextView input = activity.findViewById(inputId);
            assertTrue("Suggestions should be visible for the focused field", input.isPopupShowing());
            LiveStation station = (LiveStation) input.getAdapter().getItem(0);
            input.getOnItemClickListener().onItemClick(null, null, 0, 0);
            assertEquals(station.getLabel(), input.getText().toString());
            assertEquals("Selected station code: " + station.getCode(),
                    ((TextView) activity.findViewById(statusId)).getText().toString());
            return station;
        });
    }

    private <T> T onMain(Supplier<T> action) {
        AtomicReference<T> result = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> result.set(action.get()));
        return result.get();
    }
}
