package com.railreserve.service;

import com.railreserve.model.TrainSearchResult;
import com.railreserve.model.TravelClass;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class TrainResultOrganizerTest {
    @Test public void sortsTimesFaresAndOvernightDurationsSafely() {
        TrainSearchResult daytime = result(1, "10:00", "18:50", 0, 500, 3);
        TrainSearchResult overnight = result(2, "22:10", "06:50", 1, 700, 2);
        TrainSearchResult invalidTime = result(3, "bad", "09:00", 0, 100, 2);
        List<TrainSearchResult> source = Arrays.asList(daytime, overnight, invalidTime);

        assertEquals(Arrays.asList(daytime, overnight, invalidTime), TrainResultOrganizer.organize(
                source, false, TrainResultOrganizer.Sort.EARLIEST_DEPARTURE));
        assertEquals(Arrays.asList(invalidTime, daytime, overnight), TrainResultOrganizer.organize(
                source, false, TrainResultOrganizer.Sort.LOWEST_FARE));
        assertEquals(Arrays.asList(overnight, daytime, invalidTime), TrainResultOrganizer.organize(
                source, false, TrainResultOrganizer.Sort.SHORTEST_JOURNEY));
        assertEquals(source, TrainResultOrganizer.organize(
                source, false, TrainResultOrganizer.Sort.ORIGINAL_ORDER));
    }

    @Test public void availabilityFilterRecomputesFromOriginalCollection() {
        TrainSearchResult full = result(1, "07:00", "08:00", 0, 100, 0);
        TrainSearchResult available = result(2, "08:00", "09:00", 0, 200, 1);
        List<TrainSearchResult> source = Arrays.asList(full, available);

        assertEquals(Arrays.asList(available), TrainResultOrganizer.organize(
                source, true, TrainResultOrganizer.Sort.ORIGINAL_ORDER));
        assertEquals(source, TrainResultOrganizer.organize(
                source, false, TrainResultOrganizer.Sort.ORIGINAL_ORDER));
        assertSame(full, source.get(0));
    }

    private TrainSearchResult result(long journeyId, String departure, String arrival,
                                     int offset, int fare, int available) {
        return new TrainSearchResult(journeyId, journeyId, "T" + journeyId, "Train " + journeyId,
                "Origin", "Destination", departure, arrival, offset, "2026-10-20",
                TravelClass.SLEEPER, fare, 5, available);
    }
}
