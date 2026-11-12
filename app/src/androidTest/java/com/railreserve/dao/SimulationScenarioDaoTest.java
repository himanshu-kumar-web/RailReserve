package com.railreserve.dao;

import android.database.DatabaseUtils;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.railreserve.database.DemoDataSeeder;
import com.railreserve.model.Booking;
import com.railreserve.model.BookingRequest;
import com.railreserve.model.Seat;
import com.railreserve.model.SimulationRequest;
import com.railreserve.model.TrainSearchResult;
import com.railreserve.model.TravelClass;
import com.railreserve.service.SimulationScenarioService;
import com.railreserve.testing.DemoPaymentTestDatabase;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SimulationScenarioDaoTest {
    private SimulationRequest selection(String number, String from, String to, LocalDate date, TravelClass clazz) {
        return new SimulationRequest(number, "Selected Test Train", from, "Origin", to, "Destination", date.toString(), clazz,
                "22:00", "06:00", 1, 2);
    }
    private TrainSearchResult prepare(DemoPaymentTestDatabase f, SimulationRequest request) {
        return new SimulationScenarioService(new SQLiteSimulationScenarioDao(f.helper)).prepare(request);
    }
    private BookingRequest checkout(DemoPaymentTestDatabase f, TrainSearchResult scenario) {
        Seat seat = f.service.getAvailableSeats(scenario.getJourneyId(), scenario.getTravelClass().getCode()).get(0);
        return new BookingRequest(scenario.getJourneyId(), seat.getId(), scenario.getTravelClass(), "Scenario Passenger", 25, "Other",
                "DEMO-CHK-" + UUID.randomUUID(), scenario.getFare() * 100L);
    }

    @Test public void reentryReusesInventoryAndTicketMetadataSurvivesReopening() {
        try (DemoPaymentTestDatabase f = new DemoPaymentTestDatabase()) {
            SimulationRequest selected = selection("12345", "AAA", "BBB", LocalDate.now().plusDays(1), TravelClass.AC_THREE_TIER);
            TrainSearchResult scenario = prepare(f, selected);
            assertEquals(6, scenario.getAvailableSeats());
            assertEquals(250, scenario.getFare());
            BookingRequest request = checkout(f, scenario);
            Booking booked = f.service.createBooking(request);
            assertEquals(booked.getId(), f.service.createBooking(request).getId());
            TrainSearchResult again = prepare(f, selected);
            assertEquals(scenario.getJourneyId(), again.getJourneyId());
            assertEquals(6, again.getTotalSeats());
            assertEquals(5, again.getAvailableSeats());
            f.helper.close();
            Booking restored = f.service.findBookingByPnr(booked.getPnr());
            assertEquals("12345", restored.getTrainNumber());
            assertEquals("Selected Test Train", restored.getTrainName());
            assertEquals("Origin (AAA)", restored.getSource());
            assertEquals("Destination (BBB)", restored.getDestination());
            assertEquals(selected.getDate(), restored.getJourneyDate());
            assertEquals(TravelClass.AC_THREE_TIER, restored.getTravelClass());
            assertEquals(1, restored.getArrivalDayOffset());
            assertEquals(25000L, restored.getPayment().getAmountPaise());
            f.service.cancelBooking(booked.getId());
            assertEquals("SIMULATED_REFUNDED", f.service.findBookingByPnr(booked.getPnr()).getPayment().getRefundStatus());
            assertEquals(6, prepare(f, selected).getAvailableSeats());
            assertEquals(1, DatabaseUtils.queryNumEntries(f.helper.getReadableDatabase(), "simulation_scenarios"));
        }
    }

    @Test public void trainRouteDateAndClassEachHaveDisjointSeats() {
        try (DemoPaymentTestDatabase f = new DemoPaymentTestDatabase()) {
            LocalDate today = LocalDate.now();
            SimulationRequest[] requests = {
                    selection("12345", "AAA", "BBB", today, TravelClass.SLEEPER),
                    selection("12345", "AAA", "BBB", today.plusDays(1), TravelClass.SLEEPER),
                    selection("12345", "AAA", "BBB", today, TravelClass.AC_THREE_TIER),
                    selection("12345", "AAA", "CCC", today, TravelClass.SLEEPER),
                    selection("12345", "BBB", "AAA", today, TravelClass.SLEEPER),
                    selection("54321", "AAA", "BBB", today, TravelClass.SLEEPER)};
            Set<Long> journeys = new HashSet<>(), seats = new HashSet<>();
            for (SimulationRequest request : requests) {
                TrainSearchResult result = prepare(f, request);
                assertTrue(journeys.add(result.getJourneyId()));
                for (Seat seat : f.service.getAvailableSeats(result.getJourneyId(), result.getTravelClass().getCode())) assertTrue(seats.add(seat.getId()));
            }
            assertEquals(36, seats.size());
        }
    }

    @Test public void seededTrainWithSameNumberIsNeitherOverwrittenNorMixedIntoSimulation() {
        try (DemoPaymentTestDatabase f = new DemoPaymentTestDatabase()) {
            new DemoDataSeeder().ensureSchedule(f.helper.getWritableDatabase());
            SQLiteTrainDao demo = new SQLiteTrainDao(f.helper);
            String date = LocalDate.now().toString();
            int before = demo.search("Prayagraj", "New Delhi", date, TravelClass.SLEEPER).size();
            TrainSearchResult scenario = prepare(f, selection("90001", "AAA", "BBB", LocalDate.now(), TravelClass.SLEEPER));
            assertNotEquals(1L, scenario.getTrainId());
            assertEquals(100, scenario.getFare());
            new DemoDataSeeder().ensureSchedule(f.helper.getWritableDatabase());
            assertEquals(before, demo.search("Prayagraj", "New Delhi", date, TravelClass.SLEEPER).size());
            assertTrue(demo.search("Origin (AAA)", "Destination (BBB)", date, TravelClass.SLEEPER).isEmpty());
            assertFalse(demo.getStations().contains("Origin (AAA)"));
            assertEquals("TEST FIXTURE", DatabaseUtils.stringForQuery(f.helper.getReadableDatabase(), "SELECT name FROM trains WHERE train_number='90001'", null));
            assertEquals(6, f.service.getAvailableSeats(scenario.getJourneyId(), "SL").size());
        }
    }

    @Test public void concurrentScenarioPreparationCreatesOnePool() throws Exception {
        try (DemoPaymentTestDatabase f = new DemoPaymentTestDatabase()) {
            SimulationRequest request = selection("12345", "AAA", "BBB", LocalDate.now(), TravelClass.SLEEPER);
            ExecutorService workers = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            try {
                Future<TrainSearchResult> a = workers.submit(() -> { start.await(); return prepare(f, request); });
                Future<TrainSearchResult> b = workers.submit(() -> { start.await(); return prepare(f, request); });
                start.countDown();
                assertEquals(a.get(10, TimeUnit.SECONDS).getJourneyId(), b.get(10, TimeUnit.SECONDS).getJourneyId());
                assertEquals(1, DatabaseUtils.queryNumEntries(f.helper.getReadableDatabase(), "simulation_scenarios"));
            } finally { workers.shutdownNow(); }
        }
    }

    @Test public void competingCheckoutsReserveExactlyOneSimulationSeat() throws Exception {
        try (DemoPaymentTestDatabase f = new DemoPaymentTestDatabase()) {
            TrainSearchResult scenario = prepare(f, selection("12345", "AAA", "BBB", LocalDate.now(), TravelClass.SLEEPER));
            BookingRequest first = checkout(f, scenario), second = checkout(f, scenario);
            ExecutorService workers = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            try {
                Future<Boolean> a = workers.submit(() -> { start.await(); return attempt(f, first); });
                Future<Boolean> b = workers.submit(() -> { start.await(); return attempt(f, second); });
                start.countDown();
                assertNotEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
                assertEquals(1, f.dao.getBookings().size());
                assertEquals(1, DatabaseUtils.queryNumEntries(f.helper.getReadableDatabase(), "demo_payments"));
                assertEquals(5, f.service.getAvailableSeats(scenario.getJourneyId(), "SL").size());
            } finally { workers.shutdownNow(); }
        }
    }

    @Test public void paymentFailureRollsBackSimulationAllocation() {
        try (DemoPaymentTestDatabase f = new DemoPaymentTestDatabase()) {
            SimulationRequest selected = selection("12345", "AAA", "BBB", LocalDate.now(), TravelClass.SLEEPER);
            TrainSearchResult scenario = prepare(f, selected);
            f.helper.getWritableDatabase().execSQL("CREATE TRIGGER reject_scenario_payment BEFORE INSERT ON demo_payments BEGIN SELECT RAISE(ABORT,'TEST payment failure'); END");
            assertThrows(RuntimeException.class, () -> f.service.createBooking(checkout(f, scenario)));
            assertTrue(f.dao.getBookings().isEmpty());
            assertEquals(0, DatabaseUtils.queryNumEntries(f.helper.getReadableDatabase(), "demo_payments"));
            assertEquals(6, prepare(f, selected).getAvailableSeats());
        }
    }

    private boolean attempt(DemoPaymentTestDatabase f, BookingRequest request) {
        try { f.service.createBooking(request); return true; }
        catch (IllegalStateException conflict) { return false; }
    }
}
