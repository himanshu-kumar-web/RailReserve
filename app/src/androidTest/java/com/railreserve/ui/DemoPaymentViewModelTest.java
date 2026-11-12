package com.railreserve.ui;

import android.os.Bundle;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModelStore;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.railreserve.model.Booking;
import com.railreserve.testing.DemoPaymentTestDatabase;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DemoPaymentViewModelTest {
    @Test public void failedAndCancelledSimulationsWriteNothingAndRetrySucceeds() throws Exception {
        try (DemoPaymentTestDatabase fixture = new DemoPaymentTestDatabase()) {
            BookingSubmissionViewModel model = main(BookingSubmissionViewModel::new);
            ViewModelStore store = main(() -> { ViewModelStore value = new ViewModelStore(); value.put("test", model); return value; });
            try {
                main(() -> { model.review(fixture.checkout(1), 10000L, "TEST FIXTURE"); return null; });
                String reference = model.getCheckoutReference();
                assertTrue(fixture.dao.getBookings().isEmpty());
                main(() -> { model.pay(fixture.service, BookingSubmissionViewModel.Simulation.FAILURE); return null; });
                waitStage(model, BookingSubmissionViewModel.Stage.FAILED);
                assertTrue(fixture.dao.getBookings().isEmpty());
                main(() -> { model.pay(fixture.service, BookingSubmissionViewModel.Simulation.CANCEL); return null; });
                waitStage(model, BookingSubmissionViewModel.Stage.CANCELLED);
                assertTrue(fixture.dao.getBookings().isEmpty());
                main(() -> {
                    model.pay(fixture.service, BookingSubmissionViewModel.Simulation.SUCCESS);
                    model.pay(fixture.service, BookingSubmissionViewModel.Simulation.SUCCESS);
                    return null;
                });
                waitStage(model, BookingSubmissionViewModel.Stage.COMPLETE);
                assertEquals(reference, model.getCheckoutReference());
                Booking result = main(() -> model.getState().getValue().getBooking());
                assertEquals("SUCCESS", result.getPayment().getStatus());
                assertEquals(1, fixture.dao.getBookings().size());
            } finally { main(() -> { store.clear(); return null; }); }
        }
    }

    @Test public void cancellingWhileProcessingCannotCreateABookingLater() throws Exception {
        try (DemoPaymentTestDatabase fixture = new DemoPaymentTestDatabase()) {
            BookingSubmissionViewModel model = main(BookingSubmissionViewModel::new);
            ViewModelStore store = main(() -> { ViewModelStore value = new ViewModelStore(); value.put("test", model); return value; });
            try {
                main(() -> {
                    model.review(fixture.checkout(1), 10000L, "TEST FIXTURE");
                    model.pay(fixture.service, BookingSubmissionViewModel.Simulation.SUCCESS);
                    model.cancelPayment();
                    return null;
                });
                Thread.sleep(1500);
                waitStage(model, BookingSubmissionViewModel.Stage.CANCELLED);
                assertTrue(fixture.dao.getBookings().isEmpty());
                assertEquals(20, fixture.dao.getAvailableSeats(1, "SL").size());
            } finally { main(() -> { store.clear(); return null; }); }
        }
    }

    @Test public void savedCheckoutRecoversCommittedPaymentWithoutAnotherPnr() throws Exception {
        try (DemoPaymentTestDatabase fixture = new DemoPaymentTestDatabase()) {
            SavedStateHandle saved = main(SavedStateHandle::new);
            BookingSubmissionViewModel first = main(() -> new BookingSubmissionViewModel(saved));
            ViewModelStore store = main(() -> { ViewModelStore value = new ViewModelStore(); value.put("first", first); return value; });
            try {
                main(() -> { first.review(fixture.checkout(1), 10000L, "TEST FIXTURE"); first.pay(fixture.service, BookingSubmissionViewModel.Simulation.SUCCESS); return null; });
                waitStage(first, BookingSubmissionViewModel.Stage.COMPLETE);
                String pnr = main(() -> first.getState().getValue().getBooking().getPnr());
                SavedStateHandle restored = main(() -> copySavedDraft(saved));
                fixture.helper.close();
                BookingSubmissionViewModel recovered = main(() -> new BookingSubmissionViewModel(restored));
                main(() -> { store.put("recovered", recovered); recovered.recover(fixture.service); return null; });
                waitStage(recovered, BookingSubmissionViewModel.Stage.COMPLETE);
                assertEquals(pnr, main(() -> recovered.getState().getValue().getBooking().getPnr()));
                assertEquals(first.getCheckoutReference(), recovered.getCheckoutReference());
                assertEquals(1, fixture.dao.getBookings().size());
            } finally { main(() -> { store.clear(); return null; }); }
        }
    }

    @Test public void uncommittedSavedCheckoutRequiresExplicitPayAfterRecovery() throws Exception {
        try (DemoPaymentTestDatabase fixture = new DemoPaymentTestDatabase()) {
            SavedStateHandle saved = main(SavedStateHandle::new);
            BookingSubmissionViewModel first = main(() -> new BookingSubmissionViewModel(saved));
            ViewModelStore store = main(() -> { ViewModelStore value = new ViewModelStore(); value.put("first", first); return value; });
            try {
                main(() -> { first.review(fixture.checkout(1), 10000L, "TEST FIXTURE"); return null; });
                BookingSubmissionViewModel recovered = main(() -> new BookingSubmissionViewModel(copySavedDraft(saved)));
                main(() -> { store.put("recovered", recovered); recovered.recover(fixture.service); return null; });
                waitStage(recovered, BookingSubmissionViewModel.Stage.REVIEW);
                assertEquals(first.getCheckoutReference(), recovered.getCheckoutReference());
                assertTrue(fixture.dao.getBookings().isEmpty());
                main(() -> { recovered.pay(fixture.service, BookingSubmissionViewModel.Simulation.SUCCESS); return null; });
                waitStage(recovered, BookingSubmissionViewModel.Stage.COMPLETE);
                assertEquals(1, fixture.dao.getBookings().size());
            } finally { main(() -> { store.clear(); return null; }); }
        }
    }

    private SavedStateHandle copySavedDraft(SavedStateHandle saved) {
        Map<String, Object> values = new HashMap<>();
        Bundle draft = saved.get("demoCheckoutDraft");
        values.put("demoCheckoutDraft", new Bundle(draft));
        values.put("demoCheckoutSummary", saved.get("demoCheckoutSummary"));
        return new SavedStateHandle(values);
    }

    private void waitStage(BookingSubmissionViewModel model, BookingSubmissionViewModel.Stage stage) throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + 7000;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            if (main(() -> model.getState().getValue() != null && model.getState().getValue().getStage() == stage)) return;
            Thread.sleep(50);
        }
        fail("Expected checkout state " + stage);
    }

    private <T> T main(Supplier<T> action) {
        AtomicReference<T> result = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> result.set(action.get()));
        return result.get();
    }
}
