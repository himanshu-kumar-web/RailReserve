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

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_live_train_results);
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
        journeyDate = getIntent().getStringExtra(LiveSearchActivity.EXTRA_DATE);
        ((TextView) findViewById(R.id.liveResultsHeading)).setText(label(sourceName, sourceCode) + "  →  " + label(destinationName, destinationCode));
        ((TextView) findViewById(R.id.liveResultsSubtitle)).setText(value(journeyDate) + "  ·  RailRadar via RailReserve backend");
        progress = findViewById(R.id.liveResultsProgress);
        status = findViewById(R.id.liveResultsStatus);
        retry = findViewById(R.id.liveResultsRetry);
        retry.setOnClickListener(view -> loadTrains());
        adapter = new LiveTrainAdapter(this, this::openSchedule);
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
        progress.setVisibility(View.VISIBLE);
        status.setVisibility(View.GONE);
        retry.setVisibility(View.GONE);
        executor.execute(() -> {
            try {
                List<LiveTrain> trains = repository.searchTrains(sourceCode, destinationCode, journeyDate);
                runOnUiThread(() -> {
                    if (destroyed || requestVersion.get() != version) return;
                    progress.setVisibility(View.GONE);
                    adapter.clear();
                    adapter.addAll(trains);
                    if (trains.isEmpty()) showError("No trains were returned for this route and date.", false);
                    else status.setVisibility(View.GONE);
                });
            } catch (BackendException exception) {
                runOnUiThread(() -> {
                    if (destroyed || requestVersion.get() != version) return;
                    progress.setVisibility(View.GONE);
                    showError(exception.getMessage(), true);
                });
            }
        });
    }

    private void openSchedule(LiveTrain train) {
        if (train == null || destroyed) return;
        Intent intent = new Intent(this, LiveScheduleActivity.class);
        intent.putExtra(LiveScheduleActivity.EXTRA_TRAIN_NUMBER, train.getNumber());
        intent.putExtra(LiveScheduleActivity.EXTRA_TRAIN_NAME, train.getName());
        intent.putExtra(LiveScheduleActivity.EXTRA_DATE, journeyDate);
        startActivity(intent);
    }

    private void showError(String message, boolean canRetry) {
        status.setText(message == null || message.trim().isEmpty()
                ? "Unable to connect to the train-data service. Check your connection and try again." : message);
        status.setVisibility(View.VISIBLE);
        retry.setVisibility(canRetry ? View.VISIBLE : View.GONE);
    }

    private String label(String name, String code) {
        if (!nonEmpty(name)) return value(code);
        return nonEmpty(code) ? name + " (" + code + ")" : name;
    }

    private String value(String value) { return nonEmpty(value) ? value : "Not provided"; }
    private boolean nonEmpty(String value) { return value != null && !value.trim().isEmpty(); }

    @Override protected void onDestroy() {
        destroyed = true;
        requestVersion.incrementAndGet();
        executor.shutdownNow();
        super.onDestroy();
    }
}
