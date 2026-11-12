package com.railreserve.live;

import org.json.JSONObject;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class TrainDetailsContractTest {
    @Test public void offeredClassesAreNotInferredFromCategoryOrExampleList() throws Exception {
        JSONObject body = new JSONObject("{\"from\":{\"code\":\"AAA\"},\"to\":{\"code\":\"BBB\"},\"count\":1,\"trains\":[{\"number\":\"12345\",\"name\":\"Fixture\",\"type\":\"Express\",\"from\":{},\"to\":{}}]}");
        assertNull(LiveDataParser.parseTrains(body).get(0).getClasses());
        body.getJSONArray("trains").getJSONObject(0).put("classes", new org.json.JSONArray("[\"CC\",\"EC\",\"CC\",null,5,\"invalid\"]")).put("classSource", "RailCore route search");
        LiveTrain train = LiveDataParser.parseTrains(body).get(0);
        assertEquals(Arrays.asList("CC", "EC"), train.getClasses());
        assertEquals("RailCore route search", train.getClassSource());
        assertFalse(LiveClassCatalog.supportsDemo("CC"));
        assertTrue(LiveClassCatalog.supportsDemo("SL"));
    }
    @Test public void scheduleCarriesAllVerifiedClassesIncludingUnknownCodes() throws Exception {
        LiveSchedule result = LiveDataParser.parseSchedule(new JSONObject("{\"train\":{\"number\":\"12345\",\"name\":\"Fixture\",\"classes\":[\"1A\",\"2A\",\"3A\",\"3E\",\"SL\",\"CC\",\"EC\",\"2S\",\"EV\"],\"classSource\":\"RailCore schedule\"},\"stops\":[]}"));
        assertEquals(9, result.getClasses().size());
        assertEquals("RailCore schedule", result.getClassSource());
        assertEquals("Provider class", LiveClassCatalog.label("EV"));
    }
    @Test public void supportedFareFixturePreservesRealResponseValueAndProvenance() throws Exception {
        LiveFare fare = LiveDataParser.parseFare(new JSONObject("{\"fareKnown\":true,\"totalFare\":875.5,\"providerSource\":\"RailCore fare estimate\",\"retrievedAt\":\"2026-11-12T12:00:00Z\"}"));
        assertTrue(fare.isKnown()); assertEquals(875.5, fare.getTotal(), 0);
        assertEquals("RailCore fare estimate", fare.getSource());
        assertEquals("2026-11-12T12:00:00Z", fare.getRetrievedAt());
        LiveFare missing = LiveDataParser.parseFare(new JSONObject("{\"fareKnown\":false,\"totalFare\":999}"));
        assertNull(missing.getTotal());
    }
    @Test public void availabilityPreservesSourceAgeAndDateWithoutInventingCounts() throws Exception {
        LiveAvailability result = LiveDataParser.parseAvailability(new JSONObject("{\"availabilityStatus\":\"UNKNOWN\",\"statusType\":\"UNKNOWN\",\"availabilityDate\":\"2026-11-13\",\"providerSource\":\"RailCore availability\",\"retrievedAt\":\"2026-11-12T12:00:00Z\",\"inventoryUpdatedAt\":null}"));
        assertEquals("UNKNOWN", result.getStatus()); assertNull(result.getInventoryUpdatedAt());
        assertEquals("2026-11-13", result.getJourneyDate());
        assertEquals("RailCore availability", result.getSource());
    }
}
