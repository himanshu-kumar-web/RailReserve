package com.railreserve.live;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Provider-free checks of the same envelopes consumed by the Android screens. */
public class LiveDataContractTest {
    @Test public void stationEnvelopeSupportsPrayagrajAndEmptyResults() throws Exception {
        JSONObject data = LiveDataParser.successData("{\"success\":true,\"data\":{\"stations\":["
                + "{\"code\":\"PRYJ\",\"name\":\"Prayagraj Junction\",\"city\":\"Prayagraj\"}]}}");
        LiveStation station = LiveDataParser.parseStations(data).get(0);
        assertEquals("PRYJ", station.getCode());
        assertTrue(StationSuggestionMatcher.matches(station, "Prayagraj"));
        assertEquals("Prayagraj Junction (PRYJ)", station.toString());
        assertTrue(LiveDataParser.parseStations(LiveDataParser.successData(
                "{\"success\":true,\"data\":{\"stations\":[]}}")).isEmpty());
    }

    @Test public void rejectsMalformedEnvelopesAndIncompleteStations() {
        assertThrows(BackendException.class, () -> LiveDataParser.successData("<html>Unavailable</html>"));
        assertThrows(BackendException.class, () -> LiveDataParser.successData("{\"success\":true}"));
        assertThrows(BackendException.class, () -> LiveDataParser.parseStations(new JSONObject("{}")));
        assertThrows(BackendException.class, () -> LiveDataParser.parseStations(
                new JSONObject("{\"stations\":[{\"code\":\"PRYJ\"}]}")));
    }

    @Test public void backendFailuresUseSafeMessages() {
        for (String code : new String[]{"PROVIDER_TIMEOUT", "PROVIDER_ACCESS_DENIED", "RATE_LIMITED"}) {
            BackendException error = assertThrows(BackendException.class, () -> LiveDataParser.successData(
                    "{\"success\":false,\"error\":{\"code\":\"" + code + "\",\"message\":\"private-provider-text\"}}"));
            assertEquals(code, error.getCode());
            assertEquals(LiveDataParser.userMessage(code), error.getMessage());
            assertFalse(error.getMessage().contains("private-provider-text"));
        }
        assertEquals("The provider returned data that could not be verified for this request. Retry the check; no booking was made.",
                LiveDataParser.userMessage("INVALID_PROVIDER_RESPONSE"));
    }

    @Test public void trainSearchAllowsEmptyResultsAndPreservesUnknownFields() throws Exception {
        JSONObject data = new JSONObject("{\"from\":{\"code\":\"PRYJ\"},\"to\":{\"code\":\"NDLS\"},\"count\":0,\"trains\":[]}");
        assertTrue(LiveDataParser.parseTrains(data).isEmpty());
        data.put("count", 1).getJSONArray("trains").put(new JSONObject(
                "{\"number\":\"12345\",\"name\":\"Test fixture\",\"from\":{\"departure\":\"22:00\",\"day\":0},"
                        + "\"to\":{\"arrival\":\"08:00\",\"day\":1}}"));
        LiveTrain train = LiveDataParser.parseTrains(data).get(0);
        assertEquals(Integer.valueOf(1), train.getArrivalDay());
        assertNull(train.getDurationMinutes());
        assertNull(train.getDistanceKm());
        assertNull(train.getRunDays());
    }

    @Test public void scheduleKeepsStopOrderAndMissingEndpointTimes() throws Exception {
        LiveSchedule schedule = LiveDataParser.parseSchedule(new JSONObject(
                "{\"train\":{\"number\":\"12345\",\"name\":\"Test fixture\"},\"stops\":["
                        + "{\"sequence\":1,\"station\":{\"code\":\"PRYJ\"},\"arrival\":null,\"departure\":\"22:00\"},"
                        + "{\"sequence\":2,\"station\":{\"code\":\"NDLS\"},\"arrival\":\"08:00\",\"arrivalDay\":1,\"departure\":null}]}"));
        assertEquals("PRYJ", schedule.getStops().get(0).getStation().getCode());
        assertNull(schedule.getStops().get(0).getArrival());
        assertEquals("NDLS", schedule.getStops().get(1).getStation().getCode());
        assertNull(schedule.getStops().get(1).getDeparture());
        assertEquals(Integer.valueOf(1), schedule.getStops().get(1).getArrivalDay());
    }

    @Test public void missingFareAndAvailabilityAreNeverInvented() throws Exception {
        LiveFare fare = LiveDataParser.parseFare(new JSONObject("{\"fareKnown\":false,\"totalFare\":null}"));
        assertFalse(fare.isKnown());
        assertNull(fare.getTotal());
        assertThrows(BackendException.class, () -> LiveDataParser.parseFare(new JSONObject("{\"fareKnown\":true}")));
        assertThrows(BackendException.class, () -> LiveDataParser.parseAvailability(new JSONObject("{}")));
        LiveAvailability unavailableClass = LiveDataParser.parseAvailability(
                new JSONObject("{\"availabilityStatus\":null,\"statusType\":\"UNKNOWN\"}"));
        assertEquals("Availability not provided for this class", unavailableClass.getStatus());
        assertEquals("UNKNOWN", unavailableClass.getStatusType());
    }
}
