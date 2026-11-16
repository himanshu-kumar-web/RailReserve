package com.railreserve.ui;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.railreserve.BuildConfig;
import com.railreserve.live.*;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Retains requests across rotation. Optional enquiries are manual and expire after one minute. */
public final class TrainDetailsViewModel extends ViewModel {
    static final class Check {
        LiveFare fare;
        LiveAvailability availability;
        boolean expired;
        boolean loading;
        String text;
        long expiresAt;
    }
    final MutableLiveData<Integer> changes = new MutableLiveData<>(0);
    LiveSchedule schedule;
    boolean loading;
    String error;
    private boolean started, cleared;
    private final Map<String, Check> checks = new HashMap<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LiveDataRepository repository = LiveDataRepository.getInstance();

    void loadSchedule(String number, boolean retry) {
        if (loading || (started && !retry)) return;
        started = true; loading = true; error = null; changed();
        worker.execute(() -> {
            try {
                LiveSchedule result = repository.getSchedule(number, retry);
                if (!number.equals(result.getNumber())) throw new BackendException("INVALID_RESPONSE", "The schedule did not match the selected train.");
                main.post(() -> { if (!cleared) { schedule = result; loading = false; changed(); } });
            } catch (Exception failure) {
                main.post(() -> { if (!cleared) { loading = false; error = message(failure); changed(); } });
            }
        });
    }

    Check getCheck(String code, boolean fare) {
        String key = code + (fare ? ":fare" : ":availability");
        Check check = checks.get(key);
        if (check != null && !check.loading && check.expiresAt > 0 && SystemClock.elapsedRealtime() >= check.expiresAt) {
            expire(check, fare);
        }
        if (check != null && check.fare != null && check.fare.isKnown()) {
            long age = java.time.Duration.between(Instant.parse(check.fare.getRetrievedAt()),Instant.now()).toMillis();
            if (age > 60_000 || age < -300_000) expire(check,true);
        }
        return check;
    }

    void check(LiveTrain train, List<String> verified, String from, String to, String date, String code, boolean fare) {
        if (verified == null || !verified.contains(code) || (fare ? !BuildConfig.LIVE_FARE_ENABLED : !BuildConfig.LIVE_AVAILABILITY_ENABLED)) return;
        Check existing = getCheck(code, fare);
        if (existing != null && existing.loading) return;
        String key = code + (fare ? ":fare" : ":availability");
        Check check = new Check(); check.loading = true; checks.put(key, check); changed();
        worker.execute(() -> {
            String text;
            LiveFare loadedFare = null;
            LiveAvailability loadedAvailability = null;
            try {
                if (fare) {
                    LiveFare result = repository.getFare(train.getNumber(), from, to, date, code, "GN");
                    loadedFare = result;
                    text = (result.isKnown() ? "Fare estimate: " + NumberFormat.getCurrencyInstance(new Locale("en", "IN")).format(result.getTotal())
                            : "Fare not provided for this class.") + provenance(result.getSource(), result.getRetrievedAt());
                } else {
                    LiveAvailability result = repository.getAvailability(train.getNumber(), from, to, date, code, "GN");
                    if (!date.equals(result.getJourneyDate())) throw new BackendException("INVALID_RESPONSE", "Availability date did not match this journey.");
                    loadedAvailability = result;
                    text = "Availability: " + result.getStatus() + "\nStatus: " + result.getStatusType() + " | " + date + " | GN"
                            + provenance(result.getSource(), result.getRetrievedAt())
                            + (result.getInventoryUpdatedAt() == null ? "" : "\nInventory updated: " + result.getInventoryUpdatedAt());
                }
            } catch (Exception failure) { text = message(failure); }
            final String display = text;
            final LiveFare fareResult = loadedFare;
            final LiveAvailability availabilityResult = loadedAvailability;
            main.post(() -> {
                if (cleared || checks.get(key) != check) return;
                boolean expirable = (fareResult != null && fareResult.isKnown()) || availabilityResult != null;
                check.loading = false; check.text = display; check.fare = fareResult; check.availability = availabilityResult;
                check.expiresAt = expirable ? SystemClock.elapsedRealtime() + 60_000 : 0;
                changed();
                if (expirable) main.postDelayed(() -> { if (!cleared && checks.get(key) == check) { expire(check, fare); changed(); } }, 60_000);
            });
        });
    }

    private void expire(Check check, boolean fare) {
        check.expired = true; check.expiresAt = 0; check.fare = null; check.availability = null;
        check.text = fare ? "Fare quote expired. Check fare again to simulate booking." : "Availability expired. Check availability again.";
    }

    com.railreserve.model.FareSnapshot capture(LiveTrain train,String from,String to,String date,String code) {
        Check price = getCheck(code,true), seats = getCheck(code,false);
        if (price != null && price.expired) throw new IllegalArgumentException("Fare quote expired. Check fare again to simulate booking.");
        if (price == null || price.loading || price.fare == null || !price.fare.isKnown()) throw new IllegalArgumentException("Check a provider fare before demo booking.");
        LiveFare fare = price.fare;
        try { fare.getContext().requireMatch(train.getNumber(),from,to,date,code,"GN"); }
        catch (BackendException invalid) { throw new IllegalArgumentException(invalid.getMessage()); }
        if (!BuildConfig.LIVE_AVAILABILITY_ENABLED) {
            throw new IllegalArgumentException("Live availability is disabled for this build. No demo booking was created.");
        }
        if (seats == null || seats.loading || seats.expired || seats.availability == null) {
            throw new IllegalArgumentException("Check live availability before continuing. No demo booking was created.");
        }
        LiveAvailability availability = seats.availability;
        try { availability.getContext().requireMatch(train.getNumber(),from,to,date,code,"GN"); }
        catch (BackendException invalid) { throw new IllegalArgumentException(invalid.getMessage()); }
        long paise;
        try { paise = java.math.BigDecimal.valueOf(fare.getTotal()).movePointRight(2).longValueExact(); }
        catch (RuntimeException e) { throw new IllegalArgumentException("The provider fare cannot be represented exactly."); }
        com.railreserve.model.FareSnapshot snapshot = new com.railreserve.model.FareSnapshot(train.getNumber(),from,to,date,code,"GN",paise,
                fare.getSource(),fare.getRetrievedAt(),availability.getStatus(),availability.getStatusType(),
                availability.getSource(),availability.getRetrievedAt());
        snapshot.requireFresh();
        snapshot.requireAvailableForBooking();
        return snapshot;
    }
    private String provenance(String source, String retrieved) {
        return "\n" + (source == null ? "Provider not identified" : source) + " | Checked: " + value(retrieved);
    }
    private String value(String text) { return text == null ? "Not provided" : text; }
    private String message(Exception failure) { return failure instanceof BackendException ? failure.getMessage() : "Could not load data. Please retry."; }
    private void changed() { changes.setValue(changes.getValue() + 1); }
    @Override protected void onCleared() { cleared = true; main.removeCallbacksAndMessages(null); worker.shutdownNow(); }
}
