package com.railreserve.ui;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.view.View;
import androidx.activity.ComponentActivity;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import com.railreserve.R;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NavigationPolicyDeviceTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private static final int[] NAV = {R.id.navHome, R.id.navTickets, R.id.navProfile, R.id.navAbout};

    @Test public void everyTopLevelPairHasCorrectSelectionAndReusesItsSectionHost() throws Exception {
        try (ActivityScenario<LiveSearchActivity> homeScenario = ActivityScenario.launch(root(LiveSearchActivity.class))) {
            AtomicReference<LiveSearchActivity> root = new AtomicReference<>(); homeScenario.onActivity(root::set);
            for (int source = 0; source < NAV.length; source++) {
                for (int destination = 0; destination < NAV.length; destination++) {
                    Activity active = foreground();
                    click(active, R.id.navHome); waitFor(() -> foreground() instanceof LiveSearchActivity);
                    assertSame(root.get(), foreground());
                    if (source != 0) { click(foreground(), NAV[source]); waitFor(() -> foreground() instanceof AppSectionActivity); }
                    Activity before = foreground();
                    click(before, NAV[destination]);
                    final int target = destination;
                    waitFor(() -> foreground() != null && foreground().findViewById(NAV[target]) != null
                            && foreground().findViewById(NAV[target]).isSelected());
                    Activity after = foreground();
                    if (source != 0 && destination != 0) assertSame(before, after);
                    if (destination == 0) assertSame(root.get(), after);
                    assertState(after, destination);
                    if (after instanceof AppSectionActivity) {
                        assertTrue((after.getIntent().getFlags() & Intent.FLAG_ACTIVITY_NO_ANIMATION) != 0);
                        java.lang.reflect.Field generation = AppSectionActivity.class.getDeclaredField("ticketGeneration"); generation.setAccessible(true);
                        int old = main(() -> { try { return generation.getInt(after); } catch (Exception e) { throw new AssertionError(e); } });
                        click(after, NAV[destination]);
                        assertEquals(old, (int) main(() -> { try { return generation.getInt(after); } catch (Exception e) { throw new AssertionError(e); } }));
                    }
                    click(after, R.id.navHome); waitFor(() -> foreground() == root.get());
                }
            }
        }
    }

    @Test public void sectionRecreationKeepsSelectionAndBackReturnsHomeWithoutSectionHistory() throws Exception {
        Intent intent = root(AppSectionActivity.class).putExtra(AppSectionActivity.EXTRA_SECTION, AppSectionActivity.SECTION_PROFILE);
        try (ActivityScenario<AppSectionActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.recreate();
            scenario.onActivity(activity -> { assertState(activity, 2); activity.findViewById(R.id.navAbout).performClick(); });
            scenario.recreate();
            scenario.onActivity(activity -> { assertState(activity, 3); activity.getOnBackPressedDispatcher().onBackPressed(); });
            waitFor(() -> foreground() instanceof LiveSearchActivity);
            assertState(foreground(), 0);
            main(() -> { foreground().finish(); return null; });
        }
    }

    @Test public void ticketsChildBackReturnsToSameSectionAndSectionBackReturnsHome() throws Exception {
        try (ActivityScenario<LiveSearchActivity> scenario = ActivityScenario.launch(root(LiveSearchActivity.class))) {
            Activity home = foreground(); click(home, R.id.navTickets); waitFor(() -> foreground() instanceof AppSectionActivity);
            Activity section = foreground(); click(section, R.id.openMyBookings); waitFor(() -> foreground() instanceof MyBookingsActivity);
            main(() -> { ((ComponentActivity) foreground()).getOnBackPressedDispatcher().onBackPressed(); return null; });
            waitFor(() -> foreground() == section); assertState(section, 1);
            main(() -> { ((AppSectionActivity) section).getOnBackPressedDispatcher().onBackPressed(); return null; });
            waitFor(() -> foreground() == home); assertState(home, 0);
        }
    }

    private Intent root(Class<?> type) { return new Intent(instrumentation.getTargetContext(), type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK); }
    private void assertState(Activity screen, int selected) {
        main(() -> {
            for (int i = 0; i < NAV.length; i++) assertEquals(i == selected, screen.findViewById(NAV[i]).isSelected());
            if (selected >= 2) for (int id : new int[]{R.id.ticketCards, R.id.ticketStatus, R.id.ticketProgress, R.id.ticketsActions}) assertEquals(View.GONE, screen.findViewById(id).getVisibility());
            return null;
        });
    }
    private void click(Activity screen, int id) { main(() -> { screen.findViewById(id).performClick(); screen.findViewById(id).performClick(); return null; }); instrumentation.waitForIdleSync(); }
    private Activity foreground() {
        return main(() -> { for (Activity screen : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))
            if (screen instanceof LiveSearchActivity || screen instanceof AppSectionActivity || screen instanceof MyBookingsActivity) return screen; return null; });
    }
    private void waitFor(Supplier<Boolean> condition) throws Exception { long end = android.os.SystemClock.elapsedRealtime() + 8000;
        while (android.os.SystemClock.elapsedRealtime() < end) { if (condition.get()) return; Thread.sleep(50); } fail("Navigation did not settle"); }
    private <T> T main(Supplier<T> action) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) return action.get();
        AtomicReference<T> result = new AtomicReference<>(); instrumentation.runOnMainSync(() -> result.set(action.get())); return result.get();
    }
}
