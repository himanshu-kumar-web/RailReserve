package com.railreserve.live;

import com.railreserve.BuildConfig;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LiveDataRepository {
    private static final LiveDataRepository INSTANCE = new LiveDataRepository();
    private final BackendApiClient api = new BackendApiClient();
    private final Map<String, List<LiveStation>> stationCache = new HashMap<>();
    private final Map<String, LiveSchedule> scheduleCache = new HashMap<>();

    private LiveDataRepository() { }
    public static LiveDataRepository getInstance() { return INSTANCE; }

    public synchronized List<LiveStation> searchStations(String query) throws BackendException {
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        if (stationCache.containsKey(normalized)) return stationCache.get(normalized);
        JSONObject data = api.stationSearch(query);
        List<LiveStation> loaded = Collections.unmodifiableList(new ArrayList<>(LiveDataParser.parseStations(data)));
        // An empty response must remain retryable rather than being cached for the whole session.
        if (!loaded.isEmpty()) stationCache.put(normalized, loaded);
        return loaded;
    }

    public List<LiveTrain> searchTrains(String from, String to, String date) throws BackendException {
        return LiveDataParser.parseTrains(api.trainsBetween(from, to, date));
    }

    public LiveFare getFare(String trainNumber, String from, String to, String date,
                            String classCode, String quotaCode) throws BackendException {
        if (!BuildConfig.LIVE_FARE_ENABLED) throw new BackendException("CAPABILITY_UNAVAILABLE",
                "Fare enquiries are disabled in this build.");
        LiveFare fare = LiveDataParser.parseFare(api.fare(trainNumber, from, to, date, classCode, quotaCode));
        fare.getContext().requireMatch(trainNumber,from,to,date,classCode,quotaCode);
        requireProvenance(fare.getSource(), "RailCore fare estimate", fare.getRetrievedAt());
        return fare;
    }

    public LiveAvailability getAvailability(String trainNumber, String from, String to, String date,
                                            String classCode, String quotaCode) throws BackendException {
        if (!BuildConfig.LIVE_AVAILABILITY_ENABLED) throw new BackendException("CAPABILITY_UNAVAILABLE",
                "Availability enquiries are disabled in this build.");
        LiveAvailability result = LiveDataParser.parseAvailability(api.availability(trainNumber, from, to, date, classCode, quotaCode));
        result.getContext().requireMatch(trainNumber,from,to,date,classCode,quotaCode);
        requireProvenance(result.getSource(), "RailCore availability", result.getRetrievedAt());
        return result;
    }

    private void requireProvenance(String source, String expected, String retrievedAt) throws BackendException {
        try {
            if (!expected.equals(source)) throw new IllegalArgumentException();
            java.time.Instant.parse(retrievedAt);
        } catch (RuntimeException invalid) {
            throw new BackendException("INVALID_RESPONSE", "Provider source or retrieval time could not be verified. Please retry.");
        }
    }

    public synchronized LiveSchedule getSchedule(String trainNumber) throws BackendException {
        return getSchedule(trainNumber, false);
    }

    public synchronized LiveSchedule getSchedule(String trainNumber, boolean refresh) throws BackendException {
        if (refresh) scheduleCache.remove(trainNumber);
        if (scheduleCache.containsKey(trainNumber)) return scheduleCache.get(trainNumber);
        LiveSchedule schedule = LiveDataParser.parseSchedule(api.schedule(trainNumber));
        if (!schedule.getStops().isEmpty()) scheduleCache.put(trainNumber, schedule);
        return schedule;
    }
}
