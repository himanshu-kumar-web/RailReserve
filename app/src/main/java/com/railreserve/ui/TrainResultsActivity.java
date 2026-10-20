package com.railreserve.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.railreserve.R;
import com.railreserve.adapter.TrainResultAdapter;
import com.railreserve.model.TrainSearchResult;
import com.railreserve.model.TravelClass;
import com.railreserve.service.TrainSearchService;
import com.railreserve.service.TrainSearchServiceFactory;
import com.railreserve.service.TrainResultOrganizer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TrainResultsActivity extends AppCompatActivity {
    public static final String EXTRA_SOURCE = "search_source";
    public static final String EXTRA_DESTINATION = "search_destination";
    public static final String EXTRA_DATE = "search_date";
    public static final String EXTRA_CLASS = "search_class";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<TrainSearchResult> originalResults = new ArrayList<>();
    private TrainSearchService service;
    private boolean destroyed;
    private boolean firstResume = true;
    private boolean resultsLoaded;
    private TrainResultAdapter adapter;
    private ProgressBar progress;
    private TextView status;
    private ListView list;
    private Spinner sortSpinner;
    private CheckBox availableOnly;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_train_results);
        View root = findViewById(R.id.resultsRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(20 + bars.left, 20 + bars.top, 20 + bars.right, 20 + bars.bottom);
            return insets;
        });

        service = TrainSearchServiceFactory.create(getApplicationContext());
        progress = findViewById(R.id.resultsProgress);
        status = findViewById(R.id.resultsStatus);
        list = findViewById(R.id.resultsList);
        sortSpinner = findViewById(R.id.resultsSortSpinner);
        availableOnly = findViewById(R.id.availableOnlyCheckBox);
        adapter = new TrainResultAdapter(this);
        list.setAdapter(adapter);

        String from = getIntent().getStringExtra(EXTRA_SOURCE);
        String to = getIntent().getStringExtra(EXTRA_DESTINATION);
        String date = getIntent().getStringExtra(EXTRA_DATE);
        String clazz = getIntent().getStringExtra(EXTRA_CLASS);
        ((TextView) findViewById(R.id.resultsHeading)).setText(from + "  ->  " + to);
        String classLabel = "SL".equals(clazz) ? "Sleeper"
                : "3A".equals(clazz) ? "AC 3 Tier" : clazz;
        ((TextView) findViewById(R.id.resultsSubtitle)).setText(date + "  |  " + classLabel);

        configureControls();
        list.setOnItemClickListener((parent, view, position, id) -> openBooking(position));
        loadInitialResults(from, to, date, clazz);
    }

    private void configureControls() {
        String[] options = {"Original order", "Earliest departure", "Lowest fare", "Shortest journey"};
        ArrayAdapter<String> choices = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, options);
        choices.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sortSpinner.setAdapter(choices);
        sortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (resultsLoaded) applyControls();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        availableOnly.setOnCheckedChangeListener((button, checked) -> {
            if (resultsLoaded) applyControls();
        });
    }

    private void loadInitialResults(String from, String to, String date, String clazz) {
        executor.execute(() -> {
            try {
                service.ensureDemoData();
                List<TrainSearchResult> rows = service.search(from, to, date, TravelClass.fromCode(clazz));
                runOnUiThread(() -> {
                    if (destroyed) return;
                    progress.setVisibility(View.GONE);
                    replaceOriginalResults(rows);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (destroyed) return;
                    progress.setVisibility(View.GONE);
                    showStatus(e.getMessage() == null
                            ? "Could not load trains. Please try again." : e.getMessage());
                });
            }
        });
    }

    private void replaceOriginalResults(List<TrainSearchResult> rows) {
        originalResults.clear();
        originalResults.addAll(rows);
        resultsLoaded = true;
        applyControls();
    }

    private void applyControls() {
        int selectedSort = sortSpinner.getSelectedItemPosition();
        TrainResultOrganizer.Sort[] sorts = TrainResultOrganizer.Sort.values();
        TrainResultOrganizer.Sort sort = selectedSort >= 0 && selectedSort < sorts.length
                ? sorts[selectedSort] : TrainResultOrganizer.Sort.ORIGINAL_ORDER;
        List<TrainSearchResult> visible = TrainResultOrganizer.organize(
                originalResults, availableOnly.isChecked(), sort);

        adapter.setResults(visible);
        if (originalResults.isEmpty()) {
            showStatus("No direct trains found for this route and class. Try another date or route.");
        } else if (visible.isEmpty() && availableOnly.isChecked()) {
            showStatus("No trains have available seats for this search. Turn off ‘Available seats only’ to view all trains.");
        } else {
            status.setVisibility(View.GONE);
        }
    }

    private void showStatus(String message) {
        status.setText(message);
        status.setVisibility(View.VISIBLE);
    }

    private void openBooking(int position) {
        if (position < 0 || position >= adapter.getCount()) return;
        TrainSearchResult row = (TrainSearchResult) adapter.getItem(position);
        if (row.getAvailableSeats() <= 0) return;
        Intent intent = new Intent(this, BookingActivity.class);
        intent.putExtra(BookingActivity.EXTRA_JOURNEY_ID, row.getJourneyId());
        intent.putExtra(BookingActivity.EXTRA_TRAIN_ID, row.getTrainId());
        intent.putExtra(BookingActivity.EXTRA_TRAIN_NUMBER, row.getNumber());
        intent.putExtra(BookingActivity.EXTRA_TRAIN_NAME, row.getName());
        intent.putExtra(BookingActivity.EXTRA_SOURCE, row.getSource());
        intent.putExtra(BookingActivity.EXTRA_DESTINATION, row.getDestination());
        intent.putExtra(BookingActivity.EXTRA_DATE, row.getDate());
        intent.putExtra(BookingActivity.EXTRA_DEPARTURE, row.getDeparture());
        intent.putExtra(BookingActivity.EXTRA_ARRIVAL, row.getArrival());
        intent.putExtra(BookingActivity.EXTRA_ARRIVAL_OFFSET, row.getArrivalDayOffset());
        intent.putExtra(BookingActivity.EXTRA_CLASS, row.getTravelClass().getCode());
        startActivity(intent);
    }

    @Override protected void onResume() {
        super.onResume();
        if (firstResume) {
            firstResume = false;
            return;
        }
        refreshResults();
    }

    private void refreshResults() {
        String from = getIntent().getStringExtra(EXTRA_SOURCE);
        String to = getIntent().getStringExtra(EXTRA_DESTINATION);
        String date = getIntent().getStringExtra(EXTRA_DATE);
        String clazz = getIntent().getStringExtra(EXTRA_CLASS);
        progress.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            try {
                List<TrainSearchResult> rows = service.search(from, to, date, TravelClass.fromCode(clazz));
                runOnUiThread(() -> {
                    if (destroyed) return;
                    progress.setVisibility(View.GONE);
                    replaceOriginalResults(rows);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (destroyed) return;
                    progress.setVisibility(View.GONE);
                    showStatus(e.getMessage() == null
                            ? "Could not refresh train availability." : e.getMessage());
                });
            }
        });
    }

    @Override protected void onDestroy() {
        destroyed = true;
        executor.shutdownNow();
        super.onDestroy();
    }
}
