package com.railreserve.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.railreserve.R;
import com.railreserve.adapter.LiveTrainAdapter;
import com.railreserve.live.BackendException;
import com.railreserve.live.LiveDataRepository;
import com.railreserve.live.LiveTrain;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class LiveTrainResultsActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicInteger requestVersion = new AtomicInteger();
    private final LiveDataRepository repository = LiveDataRepository.getInstance();
    private String sourceCode, sourceName, destinationCode, destinationName, journeyDate;
    private boolean destroyed;
    private ProgressBar progress;
    private TextView status;
    private Button retry;
    private LiveTrainAdapter adapter;
    private boolean openingDetails;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_live_train_results);
        BackNavigation.bind(this);
        View root = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(18 + bars.left, bars.top + 12, 18 + bars.right, bars.bottom + 12);
            return insets;
        });
        sourceCode = getIntent().getStringExtra(LiveSearchActivity.EXTRA_SOURCE_CODE);
        sourceName = getIntent().getStringExtra(LiveSearchActivity.EXTRA_SOURCE_NAME);
        destinationCode = getIntent().getStringExtra(LiveSearchActivity.EXTRA_DESTINATION_CODE);
        destinationName = getIntent().getStringExtra(LiveSearchActivity.EXTRA_DESTINATION_NAME);
        journeyDate = state == null ? getIntent().getStringExtra(LiveSearchActivity.EXTRA_DATE)
                : state.getString(LiveSearchActivity.EXTRA_DATE, getIntent().getStringExtra(LiveSearchActivity.EXTRA_DATE));
        updateRouteHeader();
        renderDateTabs();
        progress = findViewById(R.id.liveResultsProgress);
        status = findViewById(R.id.liveResultsStatus);
        retry = findViewById(R.id.liveResultsRetry);
        retry.setOnClickListener(view -> loadTrains());
        adapter = new LiveTrainAdapter(this, this::openDetails);
        ((ListView) findViewById(R.id.liveResultsList)).setAdapter(adapter);
        if (!validSearch()) {
            progress.setVisibility(View.GONE);
            showError("Search details are incomplete. Return to Live Train Search and choose both stations.", false);
        } else {
            loadTrains();
        }
    }

    private boolean validSearch() {
        return nonEmpty(sourceCode) && nonEmpty(destinationCode) && nonEmpty(journeyDate)
                && !sourceCode.equalsIgnoreCase(destinationCode);
    }

    private void loadTrains() {
        int version = requestVersion.incrementAndGet();
        String requestedSource = sourceCode;
        String requestedDestination = destinationCode;
        String requestedDate = journeyDate;
        progress.setVisibility(View.VISIBLE);
        status.setVisibility(View.GONE);
        retry.setVisibility(View.GONE);
        adapter.clear();
        executor.execute(() -> {
            try {
                List<LiveTrain> trains = repository.searchTrains(requestedSource, requestedDestination, requestedDate);
                runOnUiThread(() -> {
                    if (destroyed || requestVersion.get() != version) return;
                    progress.setVisibility(View.GONE);
                    adapter.clear();
                    adapter.addAll(trains);
                    if (trains.isEmpty()) showError("No trains were returned for this route and date.", true);
                    else status.setVisibility(View.GONE);
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    if (destroyed || requestVersion.get() != version) return;
                    progress.setVisibility(View.GONE);
                    showError(exception instanceof BackendException ? exception.getMessage()
                            : "Could not load trains. Please retry.", true);
                });
            }
        });
    }

    private void updateRouteHeader() {
        ((TextView) findViewById(R.id.liveResultsHeading)).setText(
                routeLabel(sourceCode, sourceName) + "  →  " + routeLabel(destinationCode, destinationName));
        ((TextView) findViewById(R.id.liveResultsSubtitle)).setText(formatJourneyDate(journeyDate));
    }

    private void renderDateTabs() {
        android.widget.LinearLayout tabs = findViewById(R.id.liveDateTabs);
        tabs.removeAllViews();
        LocalDate selected;
        try {
            selected = LocalDate.parse(journeyDate);
        } catch (DateTimeParseException exception) {
            findViewById(R.id.liveDateScroller).setVisibility(View.GONE);
            return;
        }

        LocalDate minimum = LocalDate.now();
        LocalDate maximum = minimum.plusDays(14);
        if (selected.isBefore(minimum) || selected.isAfter(maximum)) {
            findViewById(R.id.liveDateScroller).setVisibility(View.GONE);
            return;
        }
        findViewById(R.id.liveDateScroller).setVisibility(View.VISIBLE);
        LocalDate firstDate = selected.minusDays(2);
        if (firstDate.isBefore(minimum)) firstDate = minimum;
        LocalDate lastDate = firstDate.plusDays(4);
        if (lastDate.isAfter(maximum)) {
            lastDate = maximum;
            firstDate = lastDate.minusDays(4);
        }

        DateTimeFormatter weekdayFormat = DateTimeFormatter.ofPattern("EEE", Locale.getDefault());
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault());
        for (LocalDate date = firstDate; !date.isAfter(lastDate); date = date.plusDays(1)) {
            View tab = getLayoutInflater().inflate(R.layout.item_journey_date, tabs, false);
            boolean isSelected = date.equals(selected);
            ((TextView) tab.findViewById(R.id.dateTabWeekday)).setText(date.format(weekdayFormat));
            ((TextView) tab.findViewById(R.id.dateTabDate)).setText(date.format(dateFormat));
            ((TextView) tab.findViewById(R.id.dateTabWeekday)).setTextColor(
                    getColor(isSelected ? R.color.brand : R.color.muted));
            ((TextView) tab.findViewById(R.id.dateTabDate)).setTextColor(
                    getColor(isSelected ? R.color.brand_dark : R.color.ink));
            tab.findViewById(R.id.dateTabIndicator).setVisibility(isSelected ? View.VISIBLE : View.INVISIBLE);
            tab.setContentDescription(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
                    + (isSelected ? ", selected" : ""));
            LocalDate tabDate = date;
            tab.setOnClickListener(view -> selectDate(tabDate));
            tabs.addView(tab);
        }
    }

    private void selectDate(LocalDate date) {
        if (date.toString().equals(journeyDate)) return;
        journeyDate = date.toString();
        getIntent().putExtra(LiveSearchActivity.EXTRA_DATE, journeyDate);
        updateRouteHeader();
        renderDateTabs();
        if (validSearch()) loadTrains();
    }

    private String formatJourneyDate(String date) {
        try {
            return LocalDate.parse(date).format(DateTimeFormatter.ofPattern("d MMM, EEE", Locale.getDefault()));
        } catch (DateTimeParseException exception) {
            return value(date);
        }
    }

    private void openDetails(LiveTrain train) {
        if (train == null || destroyed || openingDetails) return;
        openingDetails = true;
        Intent intent = new Intent(this, TrainDetailsActivity.class);
        intent.putExtras(getIntent());
        intent.putExtra(TrainDetailsActivity.EXTRA_TRAIN, train);
        startActivity(intent);
    }

    private void showError(String message, boolean canRetry) {
        status.setText(message == null || message.trim().isEmpty()
                ? "Unable to connect to the train-data service. Check your connection and try again." : message);
        status.setVisibility(View.VISIBLE);
        retry.setVisibility(canRetry ? View.VISIBLE : View.GONE);
    }

    private String routeLabel(String code, String name) {
        if (!nonEmpty(name)) return value(code);
        return nonEmpty(code) ? code + " - " + name : name;
    }

    private String value(String value) { return nonEmpty(value) ? value : "Not provided"; }
    private boolean nonEmpty(String value) { return value != null && !value.trim().isEmpty(); }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putString(LiveSearchActivity.EXTRA_DATE, journeyDate);
        super.onSaveInstanceState(outState);
    }

    @Override protected void onResume() {
        super.onResume();
        openingDetails = false;
    }

    @Override protected void onDestroy() {
        destroyed = true;
        requestVersion.incrementAndGet();
        executor.shutdownNow();
        super.onDestroy();
    }
}
