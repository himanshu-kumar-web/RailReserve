package com.railreserve.live;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class TrainScheduleMatcherTest {
    private LiveTrain searchedTrain(String from, String to) throws Exception {
        JSONObject data = new JSONObject("{\"from\":{\"code\":\"" + from + "\"},\"to\":{\"code\":\"" + to
                + "\"},\"count\":1,\"trains\":[{\"number\":\"11401\",\"name\":\"PUNE SUPAUL EXP\","
                + "\"from\":{\"departure\":\"19:47\",\"day\":0},\"to\":{\"arrival\":\"22:58\",\"day\":0},"
                + "\"durationMinutes\":191,\"distanceKm\":144}]}");
        return LiveDataParser.parseTrains(data).get(0);
    }

    @Test public void nearbyOriginDoesNotMakeAnUnscheduledSearchedStationBoardable() throws Exception {
        LiveSchedule schedule = LiveDataParser.parseSchedule(new JSONObject(
                "{\"train\":{\"number\":\"11401\",\"name\":\"PUNE SUPAUL EXP\","
                        + "\"source\":{\"code\":\"PCOI\",\"name\":\"Prayagraj Chheoki\"},"
                        + "\"destination\":{\"code\":\"DDU\",\"name\":\"Pt. Deen Dayal Upadhyaya Jn.\"}},"
                        + "\"stops\":["
                        + "{\"sequence\":1,\"station\":{\"code\":\"PCOI\",\"name\":\"Prayagraj Chheoki\"},\"departure\":\"19:30\",\"departureDay\":0,\"isHalt\":true},"
                        + "{\"sequence\":2,\"station\":{\"code\":\"DDU\",\"name\":\"Pt. Deen Dayal Upadhyaya Jn.\"},\"arrival\":\"22:58\",\"arrivalDay\":0,\"isHalt\":true}]}"));
        LiveTrain result = TrainScheduleMatcher.apply(searchedTrain("PRYJ", "DDU"), schedule);
        assertTrue(result.isScheduleVerified());
        assertEquals("PCOI", result.getActualOrigin().getCode());
        assertFalse(result.isBoardingStopVerified());
        assertTrue(result.isDestinationStopVerified());
        assertFalse(result.isRouteVerified());
        assertEquals("19:47", result.getDeparture());
        assertEquals("22:58", result.getArrival());
        TrainEnquiryRoute enquiry = TrainScheduleMatcher.resolveEnquiryRoute(searchedTrain("PRYJ", "DDU"), schedule);
        assertNotNull(enquiry);
        assertEquals("PCOI", enquiry.getBoardingStop().getStation().getCode());
        assertEquals("DDU", enquiry.getAlightingStop().getStation().getCode());
        assertEquals("19:30", enquiry.getBoardingStop().getDeparture());
        assertFalse(enquiry.usesSearchedSource());
        assertTrue(enquiry.usesSearchedDestination());
    }

    @Test public void exactScheduledStopsUseTheirOwnTimesForTheSearchedRoute() throws Exception {
        LiveSchedule schedule = LiveDataParser.parseSchedule(new JSONObject(
                "{\"train\":{\"number\":\"11401\",\"name\":\"PUNE SUPAUL EXP\","
                        + "\"source\":{\"code\":\"PCOI\",\"name\":\"Prayagraj Chheoki\"},"
                        + "\"destination\":{\"code\":\"DDU\",\"name\":\"Pt. Deen Dayal Upadhyaya Jn.\"}},"
                        + "\"stops\":["
                        + "{\"sequence\":1,\"station\":{\"code\":\"PCOI\",\"name\":\"Prayagraj Chheoki\"},\"departure\":\"19:30\",\"departureDay\":0,\"isHalt\":true},"
                        + "{\"sequence\":2,\"station\":{\"code\":\"PRYJ\",\"name\":\"Prayagraj Junction\"},\"departure\":\"19:47\",\"departureDay\":0,\"isHalt\":true},"
                        + "{\"sequence\":3,\"station\":{\"code\":\"DDU\",\"name\":\"Pt. Deen Dayal Upadhyaya Jn.\"},\"arrival\":\"22:58\",\"arrivalDay\":0,\"isHalt\":true}]}"));
        LiveTrain result = TrainScheduleMatcher.apply(searchedTrain("PRYJ", "DDU"), schedule);
        assertTrue(result.isRouteVerified());
        assertEquals("PRYJ", result.getBoardingStation().getCode());
        assertEquals("19:47", result.getDeparture());
        assertEquals("22:58", result.getArrival());
        assertEquals("PCOI", result.getActualOrigin().getCode());
    }

    @Test public void passThroughStationIsNotTreatedAsABoardingStop() throws Exception {
        LiveSchedule schedule = LiveDataParser.parseSchedule(new JSONObject(
                "{\"train\":{\"number\":\"11401\",\"name\":\"PUNE SUPAUL EXP\"},\"stops\":["
                        + "{\"sequence\":1,\"station\":{\"code\":\"PCOI\",\"name\":\"Chheoki\"},\"departure\":\"19:30\",\"isHalt\":true},"
                        + "{\"sequence\":2,\"station\":{\"code\":\"PRYJ\",\"name\":\"Prayagraj Junction\"},\"arrival\":\"19:40\",\"isHalt\":false},"
                        + "{\"sequence\":3,\"station\":{\"code\":\"DDU\",\"name\":\"DDU\"},\"arrival\":\"22:58\",\"isHalt\":true}]}"));
        LiveTrain result = TrainScheduleMatcher.apply(searchedTrain("PRYJ", "DDU"), schedule);
        assertFalse(result.isBoardingStopVerified());
        assertFalse(result.isRouteVerified());
    }

    @Test public void nearbyRouteUsesOnlyAuthoritativeScheduleEndpointsAndNeverReversesTrainDirection() throws Exception {
        LiveSchedule schedule = LiveDataParser.parseSchedule(new JSONObject(
                "{\"train\":{\"number\":\"11401\",\"name\":\"PUNE SUPAUL EXP\","
                        + "\"source\":{\"code\":\"PCOI\",\"name\":\"Prayagraj Chheoki\"},"
                        + "\"destination\":{\"code\":\"DDU\",\"name\":\"Pt. Deen Dayal Upadhyaya Jn.\"}},"
                        + "\"stops\":["
                        + "{\"sequence\":1,\"station\":{\"code\":\"PCOI\",\"name\":\"Prayagraj Chheoki\"},\"departure\":\"19:30\",\"departureDay\":0,\"isHalt\":true},"
                        + "{\"sequence\":2,\"station\":{\"code\":\"DDU\",\"name\":\"Pt. Deen Dayal Upadhyaya Jn.\"},\"arrival\":\"22:58\",\"arrivalDay\":0,\"isHalt\":true}]}"));
        TrainEnquiryRoute enquiry = TrainScheduleMatcher.resolveEnquiryRoute(searchedTrain("PRYJ", "NDLS"), schedule);
        assertNotNull(enquiry);
        assertEquals("PCOI", enquiry.getBoardingStop().getStation().getCode());
        assertEquals("DDU", enquiry.getAlightingStop().getStation().getCode());
        assertFalse(enquiry.usesSearchedSource());
        assertFalse(enquiry.usesSearchedDestination());
        assertNull(TrainScheduleMatcher.resolveEnquiryRoute(searchedTrain("DDU", "PRYJ"), schedule));
    }
}
