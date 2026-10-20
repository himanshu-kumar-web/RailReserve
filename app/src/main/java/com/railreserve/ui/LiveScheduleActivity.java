package com.railreserve.ui;

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
import com.railreserve.adapter.LiveScheduleAdapter;
import com.railreserve.live.BackendException;
import com.railreserve.live.LiveDataRepository;
import com.railreserve.live.LiveSchedule;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class LiveScheduleActivity extends AppCompatActivity {
    public static final String EXTRA_TRAIN_NUMBER = "live_schedule_train_number";
    public static final String EXTRA_TRAIN_NAME = "live_schedule_train_name";
    public static final String EXTRA_DATE = "live_schedule_journey_date";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicInteger requestVersion = new AtomicInteger();
    private final LiveDataRepository repository = LiveDataRepository.getInstance();
    private String trainNumber, trainName, journeyDate;
    private boolean destroyed;
    private ProgressBar progress;
    private TextView status;
    private Button retry;
    private LiveScheduleAdapter adapter;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_live_schedule);
        View root = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(18 + bars.left, bars.top + 12, 18 + bars.right, bars.bottom + 12);
            return insets;
        });
        trainNumber = getIntent().getStringExtra(EXTRA_TRAIN_NUMBER);
        trainName = getIntent().getStringExtra(EXTRA_TRAIN_NAME);
        journeyDate = getIntent().getStringExtra(EXTRA_DATE);
        ((TextView) findViewById(R.id.liveScheduleHeading)).setText(
                (trainName == null ? "Train schedule" : trainName) + (trainNumber == null ? "" : "  ·  " + trainNumber));
        ((TextView) findViewById(R.id.liveScheduleSubtitle)).setText(
                "Scheduled timetable  ·  Journey date " + (journeyDate == null ? "Not provided" : journeyDate));
        progress = findViewById(R.id.liveScheduleProgress);
        status = findViewById(R.id.liveScheduleStatus);
        retry = findViewById(R.id.liveScheduleRetry);
        retry.setOnClickListener(view -> loadSchedule());
        adapter = new LiveScheduleAdapter(this);
        ((ListView) findViewById(R.id.liveScheduleList)).setAdapter(adapter);
        if (trainNumber == null || trainNumber.trim().isEmpty()) {
            progress.setVisibility(View.GONE);
            showStatus("Train number is missing. Return to live results and choose a train.", false);
        } else {
            loadSchedule();
        }
    }

    private void loadSchedule() {
        int version = requestVersion.incrementAndGet();
        progress.setVisibility(View.VISIBLE);
        status.setVisibility(View.GONE);
        retry.setVisibility(View.GONE);
        executor.execute(() -> {
            try {
                LiveSchedule schedule = repository.getSchedule(trainNumber);
                runOnUiThread(() -> {
                    if (destroyed || requestVersion.get() != version) return;
                    progress.setVisibility(View.GONE);
                    adapter.clear();
                    adapter.addAll(schedule.getStops());
                    if (schedule.getStops().isEmpty()) showStatus("No schedule stops were returned for this train.", false);
                    else status.setVisibility(View.GONE);
                });
            } catch (BackendException exception) {
                runOnUiThread(() -> {
                    if (destroyed || requestVersion.get() != version) return;
                    progress.setVisibility(View.GONE);
                    showStatus(exception.getMessage(), true);
                });
            }
        });
    }

    private void showStatus(String message, boolean canRetry) {
        status.setText(message == null || message.trim().isEmpty()
                ? "Unable to connect to the train-data service. Check your connection and try again." : message);
        status.setVisibility(View.VISIBLE);
        retry.setVisibility(canRetry ? View.VISIBLE : View.GONE);
    }

    @Override protected void onDestroy() {
        destroyed = true;
        requestVersion.incrementAndGet();
        executor.shutdownNow();
        super.onDestroy();
    }
}
