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
    @Test public void expiredOrMismatchedQuoteCannotStartCheckoutButFreshReenquiryCan() throws Exception {
        BookingSubmissionViewModel model=main(BookingSubmissionViewModel::new);
        ViewModelStore store=main(()->{ ViewModelStore value=new ViewModelStore(); value.put("quote",model); return value; });
        try {
            main(()->{
                for(int invalid=0;invalid<4;invalid++) {
                    com.railreserve.model.FareSnapshot quote=new com.railreserve.model.FareSnapshot("15004","GKP","PRYJ",java.time.LocalDate.now().toString(),
                            invalid==1 ? "1A" : "2A","GN",87500,"RailCore fare estimate",java.time.Instant.now().minusSeconds(invalid==0 ? 61 : 0).toString(),null,null,null,null);
                    com.railreserve.model.BookingRequest draft=new com.railreserve.model.BookingRequest(1,1,com.railreserve.model.TravelClass.AC_TWO_TIER,"Passenger",25,"Other",null,null,quote);
                    long amount=invalid==2 ? 10000 : 87500;
                    assertThrows(IllegalArgumentException.class,()->model.review(draft,amount,"Invalid quote"));
                    assertNull(model.getState().getValue()); assertNull(model.getCheckoutReference());
                }
                String now=java.time.Instant.now().toString();
                for(String status:new String[]{"WAITLIST","RAC"}) {
                    com.railreserve.model.FareSnapshot blocked=new com.railreserve.model.FareSnapshot("15004","GKP","PRYJ",
                            java.time.LocalDate.now().toString(),"2A","GN",87500,"RailCore fare estimate",now,
                            status.equals("RAC") ? "RAC 12" : "GNWL107/WL62",status,"RailCore availability",now);
                    assertThrows(IllegalArgumentException.class,()->model.review(new com.railreserve.model.BookingRequest(1,1,
                            com.railreserve.model.TravelClass.AC_TWO_TIER,"Passenger",25,"Other",null,null,blocked),87500,"Blocked quote"));
                }
                com.railreserve.model.FareSnapshot fresh=new com.railreserve.model.FareSnapshot("15004","GKP","PRYJ",java.time.LocalDate.now().toString(),"2A","GN",87500,"RailCore fare estimate",now,"AVAILABLE 4","AVAILABLE","RailCore availability",now);
                model.review(new com.railreserve.model.BookingRequest(1,1,com.railreserve.model.TravelClass.AC_TWO_TIER,"Passenger",25,"Other",null,null,fresh),87500,"Fresh quote");
                assertEquals(BookingSubmissionViewModel.Stage.REVIEW,model.getState().getValue().getStage()); assertEquals(87500,model.getAmountPaise());
                return null;
            });
        } finally { main(()->{ store.clear(); return null; }); }
    }
    @Test public void savedQuoteSurvivesRecoveryAndExplicitPayUsesTheSameAmount() throws Exception {
        try (DemoPaymentTestDatabase fixture = new DemoPaymentTestDatabase()) {
            String date=java.time.LocalDate.now().plusDays(1).toString(), time=java.time.Instant.now().toString();
            com.railreserve.model.FareSnapshot quote=new com.railreserve.model.FareSnapshot("15004","GKP","PRYJ",date,"2A","GN",24500,"RailCore fare estimate",time,"AVAILABLE 4","AVAILABLE","RailCore availability",time);
            com.railreserve.model.TrainSearchResult scenario=new com.railreserve.service.SimulationScenarioService(new com.railreserve.dao.SQLiteSimulationScenarioDao(fixture.helper))
                    .prepare(new com.railreserve.model.SimulationRequest("15004","TEST FIXTURE","GKP","Gorakhpur","PRYJ","Prayagraj",date,com.railreserve.model.TravelClass.AC_TWO_TIER,"20:00","08:00",0,1,quote));
            com.railreserve.model.BookingRequest draft=new com.railreserve.model.BookingRequest(scenario.getJourneyId(),fixture.service.getAvailableSeats(scenario.getJourneyId(),"2A").get(0).getId(),com.railreserve.model.TravelClass.AC_TWO_TIER,"Passenger",25,"Other",null,null,quote);
            SavedStateHandle saved=main(SavedStateHandle::new);
            BookingSubmissionViewModel first=main(()->new BookingSubmissionViewModel(saved));
            ViewModelStore store=main(()->{ ViewModelStore value=new ViewModelStore(); value.put("first",first); return value; });
            try {
                main(()->{ first.review(draft,24500,"Captured quote fixture"); return null; });
                // Round-trip the Bundle through Parcel, as saved instance state does.
                SavedStateHandle restored=main(()->{
                    Bundle bundle=saved.get("demoCheckoutDraft"); android.os.Parcel parcel=android.os.Parcel.obtain();
                    try { parcel.writeBundle(bundle); parcel.setDataPosition(0); Bundle copy=parcel.readBundle(com.railreserve.model.FareSnapshot.class.getClassLoader());
                        Map<String,Object> data=new HashMap<>(); data.put("demoCheckoutDraft",copy); data.put("demoCheckoutSummary",saved.get("demoCheckoutSummary")); return new SavedStateHandle(data);
                    } finally { parcel.recycle(); }
                });
                BookingSubmissionViewModel next=main(()->new BookingSubmissionViewModel(restored));
                main(()->{ store.put("next",next); next.recover(fixture.service); return null; }); waitStage(next,BookingSubmissionViewModel.Stage.REVIEW);
                assertEquals(24500,next.getAmountPaise()); assertEquals(first.getCheckoutReference(),next.getCheckoutReference());
                main(()->{ next.pay(fixture.service,BookingSubmissionViewModel.Simulation.SUCCESS); return null; }); waitStage(next,BookingSubmissionViewModel.Stage.COMPLETE);
                Booking result=main(()->next.getState().getValue().getBooking()); assertEquals(245,result.getFare());
                assertEquals(24500,result.getPayment().getAmountPaise()); assertEquals("AVAILABLE 4",result.getFareSnapshot().getAvailability());
            } finally { main(()->{ store.clear(); return null; }); }
        }
    }
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
