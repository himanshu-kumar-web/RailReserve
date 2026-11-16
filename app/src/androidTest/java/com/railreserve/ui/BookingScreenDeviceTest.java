package com.railreserve.ui;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.appcompat.app.AlertDialog;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.railreserve.R;
import com.railreserve.dao.SQLiteBookingDao;
import com.railreserve.database.DatabaseHelper;
import com.railreserve.model.Booking;
import com.railreserve.model.BookingRequest;
import com.railreserve.model.Seat;
import com.railreserve.service.BookingService;
import com.railreserve.service.BookingServiceImpl;
import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class BookingScreenDeviceTest {
    @Test public void savingAndConfirmationSurviveRecreationWithoutDuplicateSeatAllocation() throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File file = new File(target.getCacheDir(), "phase2-booking-ui-" + UUID.randomUUID() + ".db");
        Context isolated = new ContextWrapper(target) {
            @Override public Context getApplicationContext() { return this; }
            @Override public File getDatabasePath(String name) { return file; }
            @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory) {
                return SQLiteDatabase.openOrCreateDatabase(file, factory);
            }
            @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode,
                    SQLiteDatabase.CursorFactory factory, DatabaseErrorHandler handler) {
                return SQLiteDatabase.openOrCreateDatabase(file.getPath(), factory, handler);
            }
        };
        DatabaseHelper helper = new DatabaseHelper(isolated);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        AtomicInteger writes = new AtomicInteger();
        ActivityScenario<BookingActivity> scenario = null;
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.execSQL("INSERT INTO trains VALUES (1,'90001','TEST FIXTURE','From','To','08:00','12:00',0)");
            db.execSQL("INSERT INTO journeys VALUES (1,1,'2026-11-12')");
            db.execSQL("INSERT INTO seats VALUES (1,1,'SL','S1',1,100,'AVAILABLE')");
            SQLiteBookingDao dao = new SQLiteBookingDao(helper);
            BookingServiceImpl actual = new BookingServiceImpl(dao);
            BookingService delayed = new BookingService() {
                @Override public Booking findBookingByCheckoutReference(String reference) { return actual.findBookingByCheckoutReference(reference); }
                @Override public List<Seat> getAvailableSeats(long id, String code) { return actual.getAvailableSeats(id, code); }
                @Override public Booking createBooking(BookingRequest request) {
                    writes.incrementAndGet(); started.countDown();
                    try { if (!release.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Test timeout"); }
                    catch (InterruptedException e) { throw new IllegalStateException(e); }
                    return actual.createBooking(request);
                }
                @Override public List<Booking> getBookings() { return actual.getBookings(); }
                @Override public List<Booking> getVisibleBookings() { return actual.getVisibleBookings(); }
                @Override public Booking findBookingByPnr(String pnr) { return actual.findBookingByPnr(pnr); }
                @Override public void cancelBooking(long id) { actual.cancelBooking(id); }
                @Override public void hideBookingFromList(long id) { actual.hideBookingFromList(id); }
            };
            scenario = ActivityScenario.launch(new Intent(target, BookingActivity.class)
                    .putExtra(BookingActivity.EXTRA_JOURNEY_ID, 1L).putExtra(BookingActivity.EXTRA_CLASS, "SL")
                    .putExtra(BookingActivity.EXTRA_TRAIN_NAME, "TEST FIXTURE")
                    .putExtra(BookingActivity.EXTRA_TRAIN_NUMBER, "90001")
                    .putExtra(BookingActivity.EXTRA_SOURCE, "From").putExtra(BookingActivity.EXTRA_DESTINATION, "To")
                    .putExtra(BookingActivity.EXTRA_DATE, "2026-11-12")
                    .putExtra(BookingActivity.EXTRA_DEPARTURE, "08:00").putExtra(BookingActivity.EXTRA_ARRIVAL, "12:00"));
            ActivityScenario<BookingActivity> active = scenario;
            waitFor(active, false);
            Field service = BookingActivity.class.getDeclaredField("service");
            service.setAccessible(true);
            List<Seat> seats = dao.getAvailableSeats(1, "SL");
            active.onActivity(activity -> {
                try { service.set(activity, delayed); } catch (IllegalAccessException e) { throw new AssertionError(e); }
                Spinner spinner = activity.findViewById(R.id.seatSpinner);
                spinner.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, seats));
                activity.findViewById(R.id.confirmBookingButton).setEnabled(true);
                activity.findViewById(R.id.confirmBookingButton).performClick();
                assertNotNull(((TextView) activity.findViewById(R.id.passengerNameInput)).getError());
                ((TextView) activity.findViewById(R.id.passengerNameInput)).setText("Test Passenger");
                ((TextView) activity.findViewById(R.id.passengerAgeInput)).setText("25");
                ((Spinner) activity.findViewById(R.id.genderSpinner)).setSelection(3);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            active.onActivity(activity -> {
                activity.findViewById(R.id.confirmBookingButton).performClick();
                activity.findViewById(R.id.confirmBookingButton).performClick();
                assertEquals(View.VISIBLE, activity.findViewById(R.id.demoCheckoutPanel).getVisibility());
                assertTrue(dao.getBookings().isEmpty());
                activity.findViewById(R.id.demoPayButton).performClick();
                activity.findViewById(R.id.demoPayButton).performClick();
            });
            AtomicReference<String> checkoutReference = new AtomicReference<>();
            active.onActivity(activity -> checkoutReference.set(new androidx.lifecycle.ViewModelProvider(activity)
                    .get(BookingSubmissionViewModel.class).getCheckoutReference()));
            active.recreate(); // Rotation during the simulated processing interval.
            active.onActivity(activity -> assertEquals(checkoutReference.get(), new androidx.lifecycle.ViewModelProvider(activity)
                    .get(BookingSubmissionViewModel.class).getCheckoutReference()));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            active.recreate();
            active.onActivity(activity -> {
                assertFalse(activity.findViewById(R.id.confirmBookingButton).isEnabled());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.seatProgress).getVisibility());
            });
            release.countDown();
            waitFor(active, true);
            AtomicReference<String> pnr = new AtomicReference<>();
            active.onActivity(activity -> {
                TextView view = confirmation(activity).findViewById(R.id.confirmationPnr);
                pnr.set(view.getText().toString());
                assertTrue(pnr.get().matches("[1-9][0-9]{9}"));
                assertTrue(view.isTextSelectable());
            });
            active.recreate();
            waitFor(active, true);
            active.onActivity(activity -> {
                TextView view = confirmation(activity).findViewById(R.id.confirmationPnr);
                assertEquals(pnr.get(), view.getText().toString());
            });
            assertEquals(1, writes.get());
            assertEquals(1, dao.getBookings().size());
            assertEquals("CONFIRMED", dao.findBookingByPnr(pnr.get()).getStatus());
            assertNotNull(dao.findBookingByPnr(pnr.get()).getPayment());
            assertEquals("SUCCESS", dao.findBookingByPnr(pnr.get()).getPayment().getStatus());
        } finally {
            release.countDown();
            if (scenario != null) scenario.close();
            helper.close();
            SQLiteDatabase.deleteDatabase(file);
        }
    }

    private void waitFor(ActivityScenario<BookingActivity> scenario, boolean confirmation) throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + 7000;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            AtomicReference<Boolean> ready = new AtomicReference<>(false);
            scenario.onActivity(activity -> ready.set(confirmation
                    ? confirmation(activity) != null && confirmation(activity).isShowing()
                    : activity.findViewById(R.id.seatProgress).getVisibility() == View.GONE));
            if (ready.get()) {
                if (!confirmation) return;
                // State observer displays the confirmation in the same main-thread callback.
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                return;
            }
            Thread.sleep(50);
        }
        fail("Booking screen did not settle");
    }

    private AlertDialog confirmation(BookingActivity activity) {
        try {
            Field dialog = BookingActivity.class.getDeclaredField("confirmationDialog");
            dialog.setAccessible(true);
            return (AlertDialog) dialog.get(activity);
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }
}
