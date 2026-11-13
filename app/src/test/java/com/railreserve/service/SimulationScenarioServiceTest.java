package com.railreserve.service;

import com.railreserve.model.SimulationRequest;
import com.railreserve.model.SimulationScenarioPolicy;
import com.railreserve.model.TravelClass;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class SimulationScenarioServiceTest {
    private SimulationRequest request(String number, String from, String to, String date, TravelClass clazz, Integer arrival) {
        return new SimulationRequest(number, "Selected Train", from, "From", to, "To", date, clazz, "08:00", "12:00", 0, arrival);
    }

    @Test public void validSelectionNormalizesCodesAndUsesFictionalPolicy() {
        AtomicInteger calls = new AtomicInteger();
        SimulationScenarioService service = new SimulationScenarioService(selection -> {
            assertEquals("PRYJ", selection.getSourceCode());
            assertEquals("NDLS", selection.getDestinationCode());
            calls.incrementAndGet(); return null;
        });
        service.prepare(request("12345", " pryj ", "ndls", LocalDate.now().toString(), TravelClass.SLEEPER, 1));
        assertEquals(1, calls.get());
        assertEquals(100, SimulationScenarioPolicy.fareRupees(TravelClass.SLEEPER));
        assertEquals(250, SimulationScenarioPolicy.fareRupees(TravelClass.AC_THREE_TIER));
        assertEquals(6, SimulationScenarioPolicy.SEAT_COUNT);
    }

    @Test public void invalidSelectionNeverCreatesInventory() {
        AtomicInteger calls = new AtomicInteger();
        SimulationScenarioService service = new SimulationScenarioService(selection -> { calls.incrementAndGet(); return null; });
        String today = LocalDate.now().toString();
        assertThrows(IllegalArgumentException.class, () -> service.prepare(null));
        assertThrows(IllegalArgumentException.class, () -> service.prepare(request("invalid", "AAA", "BBB", today, TravelClass.SLEEPER, 0)));
        assertThrows(IllegalArgumentException.class, () -> service.prepare(request("12345", "AAA", "AAA", today, TravelClass.SLEEPER, 0)));
        assertThrows(IllegalArgumentException.class, () -> service.prepare(request("12345", "AAA", "BBB", "bad-date", TravelClass.SLEEPER, 0)));
        assertThrows(IllegalArgumentException.class, () -> service.prepare(request("12345", "AAA", "BBB", LocalDate.now().minusDays(1).toString(), TravelClass.SLEEPER, 0)));
        assertThrows(IllegalArgumentException.class, () -> service.prepare(request("12345", "AAA", "BBB", LocalDate.now().plusDays(15).toString(), TravelClass.SLEEPER, 0)));
        assertThrows(IllegalArgumentException.class, () -> service.prepare(request("12345", "AAA", "BBB", today, null, 0)));
        assertThrows(IllegalArgumentException.class, () -> service.prepare(request("12345", "AAA", "BBB", today, TravelClass.SLEEPER, 2)));
        assertEquals(0, calls.get());
    }

    @Test public void unrepresentableTimetablesAreRejectedWithoutInventingDayOffsets() {
        assertTrue(SimulationScenarioService.supportsTimetable(2, 3));
        assertTrue(SimulationScenarioService.supportsTimetable(2, 2));
        assertFalse(SimulationScenarioService.supportsTimetable(null, 1));
        assertFalse(SimulationScenarioService.supportsTimetable(0, null));
        assertFalse(SimulationScenarioService.supportsTimetable(0, 2));
        assertFalse(SimulationScenarioService.supportsTimetable(1, 0));
        assertFalse(SimulationScenarioService.supportsTimetable(-1, 0));
    }

    @Test public void allEightClassesPreserveTheirQuoteWithoutUsingLegacyFares() {
        AtomicInteger calls = new AtomicInteger();
        for (TravelClass clazz : TravelClass.values()) {
            assertTrue(com.railreserve.live.LiveClassCatalog.supportsDemo(clazz.getCode()));
            com.railreserve.model.FareSnapshot quote = new com.railreserve.model.FareSnapshot("12345","AAA","BBB",
                    LocalDate.now().toString(),clazz.getCode(),"GN",87500,"RailCore fare estimate",java.time.Instant.now().toString(),null,null,null,null);
            SimulationRequest selection = new SimulationRequest("12345","Selected Train","AAA","From","BBB","To",
                    quote.getDate(),clazz,"08:00","12:00",0,0,quote);
            new SimulationScenarioService(actual -> {
                assertSame(quote,actual.getFareSnapshot());
                assertEquals(875,actual.getFareSnapshot().getFareRupees()); calls.incrementAndGet(); return null;
            }).prepare(selection);
        }
        assertEquals(8,calls.get());
        assertFalse(com.railreserve.live.LiveClassCatalog.supportsDemo("EV"));
    }

    @Test public void additionalClassesRequireQuotesAndStaleOrMismatchedQuotesNeverCreateInventory() {
        SimulationScenarioService service = new SimulationScenarioService(selection -> { fail("Invalid quote reached inventory"); return null; });
        String today = LocalDate.now().toString();
        assertThrows(IllegalArgumentException.class, () -> service.prepare(request("12345","AAA","BBB",today,TravelClass.FIRST_AC,0)));
        for (boolean stale : new boolean[]{true,false}) {
            com.railreserve.model.FareSnapshot quote = new com.railreserve.model.FareSnapshot("12345","AAA","BBB",today,
                    "1A","GN",87500,"RailCore fare estimate",java.time.Instant.now().minusSeconds(stale ? 61 : 0).toString(),null,null,null,null);
            // The first quote is expired; the second deliberately has a different route.
            assertThrows(IllegalArgumentException.class, () -> service.prepare(new SimulationRequest("12345","Selected Train","AAA","From",
                    stale ? "BBB" : "CCC","To",today,TravelClass.FIRST_AC,"08:00","12:00",0,0,quote)));
        }
    }
}
