package com.railreserve.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.railreserve.R;
import com.railreserve.dao.SQLiteSimulationScenarioDao;
import com.railreserve.database.DatabaseHelper;
import com.railreserve.model.SimulationRequest;
import com.railreserve.model.TrainSearchResult;
import com.railreserve.service.SimulationScenarioService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Converts a timetable selection into local inventory, then opens the unchanged checkout engine. */
public final class SimulationEntryActivity extends AppCompatActivity {
    public static final String EXTRA_REQUEST = "simulation_request";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SimulationRequest request;
    private boolean preparing, destroyed;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_simulation_entry);
        View root = findViewById(R.id.simulationEntryRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(view.getPaddingLeft(), bars.top + 20, view.getPaddingRight(), bars.bottom + 20);
            return insets;
        });
        Object extra = getIntent().getSerializableExtra(EXTRA_REQUEST);
        request = extra instanceof SimulationRequest ? (SimulationRequest) extra : null;
        TextView policy = findViewById(R.id.simulationEntryPolicy);
        policy.setText(request != null && request.getFareSnapshot() != null
                ? R.string.quoted_simulation_policy : R.string.local_search_demo_policy);
        findViewById(R.id.simulationEntryRetry).setOnClickListener(view -> prepare());
        findViewById(R.id.simulationEntryBack).setOnClickListener(view -> finish());
        findViewById(R.id.simulationEntryDemoFallback).setOnClickListener(view -> {
            startActivity(new Intent(this, com.railreserve.MainActivity.class));
            finish();
        });
        prepare();
    }

    private void prepare() {
        if (preparing || destroyed) return;
        preparing = true;
        findViewById(R.id.simulationEntryProgress).setVisibility(View.VISIBLE);
        findViewById(R.id.simulationEntryRetry).setVisibility(View.GONE);
        ((TextView) findViewById(R.id.simulationEntryStatus)).setText(R.string.simulation_preparing);
        executor.execute(() -> {
            DatabaseHelper helper = new DatabaseHelper(getApplicationContext());
            try {
                if (request == null) throw new IllegalArgumentException("Return to train details and choose a train class before starting this simulation.");
                TrainSearchResult scenario = new SimulationScenarioService(new SQLiteSimulationScenarioDao(helper)).prepare(request);
                runOnUiThread(() -> {
                    if (destroyed) return;
                    Intent intent = bookingIntent(scenario);
                    startActivity(intent);
                    finish();
                });
            } catch (Exception exception) {
                String error = exception instanceof IllegalArgumentException || exception instanceof IllegalStateException
                        ? exception.getMessage() : getString(R.string.simulation_prepare_failed);
                runOnUiThread(() -> {
                    if (destroyed) return;
                    preparing = false;
                    findViewById(R.id.simulationEntryProgress).setVisibility(View.GONE);
                    boolean expired = false;
                    if (request != null && request.getFareSnapshot() != null) {
                        try { request.getFareSnapshot().requireFresh(); } catch (IllegalArgumentException stale) { expired = true; }
                    }
                    findViewById(R.id.simulationEntryRetry).setVisibility(request == null || expired ? View.GONE : View.VISIBLE);
                    ((TextView) findViewById(R.id.simulationEntryStatus)).setText(error);
                });
            } finally { helper.close(); }
        });
    }

    private Intent bookingIntent(TrainSearchResult scenario) {
        return new Intent(this, BookingActivity.class)
                .putExtra(BookingActivity.EXTRA_JOURNEY_ID, scenario.getJourneyId())
                .putExtra(BookingActivity.EXTRA_TRAIN_ID, scenario.getTrainId())
                .putExtra(BookingActivity.EXTRA_TRAIN_NUMBER, scenario.getNumber())
                .putExtra(BookingActivity.EXTRA_TRAIN_NAME, scenario.getName())
                .putExtra(BookingActivity.EXTRA_SOURCE, scenario.getSource())
                .putExtra(BookingActivity.EXTRA_DESTINATION, scenario.getDestination())
                .putExtra(BookingActivity.EXTRA_DATE, scenario.getDate())
                .putExtra(BookingActivity.EXTRA_CLASS, scenario.getTravelClass().getCode())
                .putExtra(BookingActivity.EXTRA_DEPARTURE, scenario.getDeparture())
                .putExtra(BookingActivity.EXTRA_ARRIVAL, scenario.getArrival())
                .putExtra(BookingActivity.EXTRA_ARRIVAL_OFFSET, scenario.getArrivalDayOffset())
                .putExtra(BookingActivity.EXTRA_LIVE_SIMULATION, request.getFareSnapshot() != null)
                .putExtra(BookingActivity.EXTRA_FARE_SNAPSHOT, request.getFareSnapshot());
    }

    @Override protected void onDestroy() {
        destroyed = true;
        executor.shutdownNow();
        super.onDestroy();
    }
}
