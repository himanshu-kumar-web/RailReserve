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
            checks.remove(key); return null;
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
            try {
                if (fare) {
                    LiveFare result = repository.getFare(train.getNumber(), from, to, date, code, "GN");
                    text = (result.isKnown() ? "Provider fare estimate: " + NumberFormat.getCurrencyInstance(new Locale("en", "IN")).format(result.getTotal())
                            : "Fare not provided for this class.") + provenance(result.getSource(), result.getRetrievedAt());
                } else {
                    LiveAvailability result = repository.getAvailability(train.getNumber(), from, to, date, code, "GN");
                    if (!date.equals(result.getJourneyDate())) throw new BackendException("INVALID_RESPONSE", "Availability date did not match this journey.");
                    text = result.getStatus() + " · " + date + " · General quota"
                            + provenance(result.getSource(), result.getRetrievedAt())
                            + "\nInventory updated: " + value(result.getInventoryUpdatedAt());
                }
            } catch (Exception failure) { text = message(failure); }
            final String display = text;
            main.post(() -> {
                if (cleared) return;
                check.loading = false; check.text = display; check.expiresAt = SystemClock.elapsedRealtime() + 60_000;
                changed();
                main.postDelayed(() -> { if (!cleared && checks.get(key) == check) { checks.remove(key); changed(); } }, 60_000);
            });
        });
    }

    private String provenance(String source, String retrieved) {
        return "\nSource: " + (source == null ? "RailReserve backend (provider not identified)" : source)
                + "\nRetrieved: " + value(retrieved) + "\nChecked on device: " + Instant.now();
    }
    private String value(String text) { return text == null ? "Not provided" : text; }
    private String message(Exception failure) { return failure instanceof BackendException ? failure.getMessage() : "Could not load data. Please retry."; }
    private void changed() { changes.setValue(changes.getValue() + 1); }
    @Override protected void onCleared() { cleared = true; main.removeCallbacksAndMessages(null); worker.shutdownNow(); }
}
