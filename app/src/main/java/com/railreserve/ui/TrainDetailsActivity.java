package com.railreserve.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import com.railreserve.BuildConfig;
import com.railreserve.R;
import com.railreserve.live.LiveClassCatalog;
import com.railreserve.live.LiveTrain;
import com.railreserve.model.SimulationRequest;
import com.railreserve.model.TravelClass;
import com.railreserve.service.SimulationScenarioService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Provider timetable and offered classes, with a separately labelled local simulation entry. */
public final class TrainDetailsActivity extends AppCompatActivity {
    public static final String EXTRA_TRAIN = "selected_live_train";
    private LiveTrain train;
    private TrainDetailsViewModel model;
    private String sourceCode, sourceName, destinationCode, destinationName, date;
    private boolean opening;
    private final Map<String, View> cards = new HashMap<>();
    private List<String> renderedClasses;
    private boolean demoFallbackRendered;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_train_details);
        View root = findViewById(R.id.trainDetailsRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        findViewById(R.id.detailsBack).setOnClickListener(view -> finish());
        Object selected = getIntent().getSerializableExtra(EXTRA_TRAIN);
        if (!(selected instanceof LiveTrain)) {
            ((TextView) findViewById(R.id.detailsClassesStatus)).setText(R.string.train_details_missing);
            findViewById(R.id.detailsProgress).setVisibility(View.GONE);
            findViewById(R.id.detailsSchedule).setEnabled(false); return;
        }
        train = (LiveTrain) selected;
        sourceCode = getIntent().getStringExtra(LiveSearchActivity.EXTRA_SOURCE_CODE);
        sourceName = getIntent().getStringExtra(LiveSearchActivity.EXTRA_SOURCE_NAME);
        destinationCode = getIntent().getStringExtra(LiveSearchActivity.EXTRA_DESTINATION_CODE);
        destinationName = getIntent().getStringExtra(LiveSearchActivity.EXTRA_DESTINATION_NAME);
        date = getIntent().getStringExtra(LiveSearchActivity.EXTRA_DATE);
        ((TextView) findViewById(R.id.detailsHeading)).setText(value(train.getName()) + " · " + value(train.getNumber()));
        ((TextView) findViewById(R.id.detailsJourney)).setText(label(sourceName, sourceCode) + " → " + label(destinationName, destinationCode)
                + "\nJourney date: " + value(date) + "\nDeparture: " + value(train.getDeparture())
                + "  →  Arrival: " + value(train.getArrival()) + arrivalOffset());
        model = new ViewModelProvider(this).get(TrainDetailsViewModel.class);
        findViewById(R.id.detailsRetry).setOnClickListener(view -> model.loadSchedule(train.getNumber(), true));
        findViewById(R.id.detailsSchedule).setOnClickListener(view -> {
            if (opening) return; opening = true;
            startActivity(new Intent(this, LiveScheduleActivity.class)
                    .putExtra(LiveScheduleActivity.EXTRA_TRAIN_NUMBER, train.getNumber())
                    .putExtra(LiveScheduleActivity.EXTRA_TRAIN_NAME, train.getName())
                    .putExtra(LiveScheduleActivity.EXTRA_DATE, date));
        });
        model.changes.observe(this, ignored -> render());
        model.loadSchedule(train.getNumber(), false);
    }

    private List<String> verifiedClasses() {
        // Schedule is a fallback, never union classes inferred from other trains or train type.
        return train.getClasses() != null ? train.getClasses() : model.schedule == null ? null : model.schedule.getClasses();
    }

    private void render() {
        List<String> classes = verifiedClasses();
        List<String> days = train.getRunDays() != null ? train.getRunDays() : model.schedule == null ? null : model.schedule.getRunDays();
        Integer duration = train.getDurationMinutes();
        ((TextView) findViewById(R.id.detailsTimetable)).setText("Scheduled duration: "
                + (duration == null || duration < 0 ? "Not provided" : duration / 60 + "h " + duration % 60 + "m")
                + "\nOperating days: " + (days == null || days.isEmpty() ? "Not provided" : android.text.TextUtils.join(", ", days))
                + "\nScheduled stops: " + (model.schedule == null ? "Not loaded" : model.schedule.getStops().isEmpty()
                ? "Not provided" : model.schedule.getStops().size() + " — open schedule to view")
                + "\nTimetable source: RailReserve backend");
        findViewById(R.id.detailsProgress).setVisibility(model.loading ? View.VISIBLE : View.GONE);
        boolean incomplete = model.error != null || (model.schedule != null && (model.schedule.getStops().isEmpty() || classes == null));
        findViewById(R.id.detailsRetry).setVisibility(!model.loading && incomplete ? View.VISIBLE : View.GONE);
        String classStatus = classes == null ? getString(model.loading ? R.string.classes_loading : R.string.classes_not_provided)
                : classes.isEmpty() ? getString(R.string.classes_empty) : getString(R.string.classes_verified);
        if (model.error != null) classStatus += "\n" + model.error;
        else if (model.schedule != null && model.schedule.getStops().isEmpty()) classStatus += "\n" + getString(R.string.schedule_not_provided);
        ((TextView) findViewById(R.id.detailsClassesStatus)).setText(classStatus);
        boolean fallback = classes == null && !model.loading;
        if (!java.util.Objects.equals(renderedClasses, classes) || demoFallbackRendered != fallback || cards.isEmpty()) {
            renderedClasses = classes; demoFallbackRendered = fallback; cards.clear();
            LinearLayout verified = findViewById(R.id.detailsClasses); verified.removeAllViews();
            LinearLayout demo = findViewById(R.id.detailsDemoOnlyClasses); demo.removeAllViews();
            findViewById(R.id.detailsDemoOnlyNotice).setVisibility(fallback ? View.VISIBLE : View.GONE);
            if (classes != null) for (String code : classes) addCard(verified, code, true);
            if (fallback) {
                Button localDemo = new Button(this); localDemo.setText(R.string.open_local_demo);
                localDemo.setMinHeight((int) (48 * getResources().getDisplayMetrics().density));
                localDemo.setOnClickListener(view -> { if (!opening) { opening = true; startActivity(new Intent(this,com.railreserve.MainActivity.class)); } });
                demo.addView(localDemo);
            }
        }
        for (Map.Entry<String, View> entry : cards.entrySet()) updateCard(entry.getValue(), entry.getKey(), classes != null);
    }

    private void addCard(LinearLayout parent, String code, boolean verified) {
        View card = getLayoutInflater().inflate(R.layout.item_live_class, parent, false);
        card.setTag(code); cards.put(code, card); parent.addView(card);
        ((TextView) card.findViewById(R.id.classHeading)).setText(LiveClassCatalog.label(code) + " (" + code + ")");
        String source = train.getClasses() != null ? train.getClassSource() : model.schedule == null ? null : model.schedule.getClassSource();
        ((TextView) card.findViewById(R.id.classSource)).setText(verified ? "Offered · "
                + (source == null ? "provider data via RailReserve backend" : source) : getString(R.string.demo_class_not_verified));
        card.findViewById(R.id.classFareCheck).setOnClickListener(view -> model.check(train, verifiedClasses(), sourceCode, destinationCode, date, code, true));
        card.findViewById(R.id.classAvailabilityCheck).setOnClickListener(view -> model.check(train, verifiedClasses(), sourceCode, destinationCode, date, code, false));
        Button book = card.findViewById(R.id.classDemoBook);
        book.setAlpha(1f);
        book.setText(getString(R.string.simulate_class_booking, code));
        book.setOnClickListener(view -> simulate(code));
    }

    private SimulationRequest selection(String code, com.railreserve.model.FareSnapshot quote) {
        return new SimulationRequest(train.getNumber(),train.getName(),sourceCode,sourceName,destinationCode,destinationName,
                date,TravelClass.fromCode(code),train.getDeparture(),train.getArrival(),train.getDepartureDay(),train.getArrivalDay(),quote);
    }

    private String bookingBlockReason(String code, boolean verified) {
        if (!verified) return "This class has not been verified for the selected train.";
        if (!LiveClassCatalog.supportsDemo(code)) return "Demo booking does not support class " + code + ".";
        try {
            SimulationScenarioService.validateJourney(selection(code,null));
            model.capture(train,sourceCode,destinationCode,date,code);
            return null;
        } catch(IllegalArgumentException blocked) { return blocked.getMessage(); }
    }

    private void updateCard(View card, String code, boolean verified) {
        TrainDetailsViewModel.Check fare = model.getCheck(code, true), availability = model.getCheck(code, false);
        boolean fareEnabled = verified && BuildConfig.LIVE_FARE_ENABLED, availabilityEnabled = verified && BuildConfig.LIVE_AVAILABILITY_ENABLED;
        ((TextView) card.findViewById(R.id.classFare)).setText(!fareEnabled ? getString(R.string.fare_plan_unavailable)
                : fare == null ? getString(R.string.fare_not_checked) : fare.loading ? getString(R.string.checking_fare) : UiText.enquiry(fare.text));
        ((TextView) card.findViewById(R.id.classAvailability)).setText(!availabilityEnabled ? getString(R.string.availability_unavailable)
                : availability == null ? getString(R.string.availability_not_checked) : availability.loading ? getString(R.string.checking_availability) : UiText.enquiry(availability.text));
        String blocked = bookingBlockReason(code,verified);
        card.findViewById(R.id.classDemoBook).setEnabled(blocked == null);
        ((TextView) card.findViewById(R.id.classDemoNotice)).setText(blocked == null ? getString(R.string.demo_uses_quote) : blocked);
        card.findViewById(R.id.classDemoNotice).setVisibility(blocked == null ? View.GONE : View.VISIBLE);
        ((Button) card.findViewById(R.id.classFareCheck)).setText(fare != null && fare.loading ? R.string.checking_fare
                : fare != null && fare.expired ? R.string.recheck_expired_fare : R.string.check_fare);
        ((Button) card.findViewById(R.id.classAvailabilityCheck)).setText(availability != null && availability.loading ? R.string.checking_availability
                : availability != null && availability.expired ? R.string.recheck_availability : R.string.check_availability);
        card.findViewById(R.id.classFareCheck).setVisibility(fareEnabled ? View.VISIBLE : View.GONE);
        card.findViewById(R.id.classFareCheck).setEnabled(fare == null || !fare.loading);
        card.findViewById(R.id.classAvailabilityCheck).setVisibility(availabilityEnabled ? View.VISIBLE : View.GONE);
        card.findViewById(R.id.classAvailabilityCheck).setEnabled(availability == null || !availability.loading);
        card.findViewById(R.id.classProgress).setVisibility((fare != null && fare.loading) || (availability != null && availability.loading) ? View.VISIBLE : View.GONE);
    }

    private void simulate(String code) {
        if (opening) return;
        String blocked = bookingBlockReason(code,verifiedClasses()!=null && verifiedClasses().contains(code));
        if (blocked != null) { android.widget.Toast.makeText(this,blocked,android.widget.Toast.LENGTH_LONG).show(); render(); return; }
        try {
            com.railreserve.model.FareSnapshot quote = model.capture(train,sourceCode,destinationCode,date,code);
            opening = true;
            startActivity(new Intent(this,SimulationEntryActivity.class).putExtra(SimulationEntryActivity.EXTRA_REQUEST,selection(code,quote)));
        } catch (IllegalArgumentException expired) {
            opening = false;
            android.widget.Toast.makeText(this,expired.getMessage(),android.widget.Toast.LENGTH_LONG).show();
            render();
        }
    }
    private String arrivalOffset() {
        if (train.getArrivalDay() == null || train.getDepartureDay() == null) return " (day offset not provided)";
        long days = (long) train.getArrivalDay() - train.getDepartureDay();
        return days < 0 ? " (day offset not provided)" : days == 0 ? " (same day)" : " (+" + days + " day" + (days == 1 ? "" : "s") + ")";
    }
    private String value(String text) { return text == null || text.trim().isEmpty() ? "Not provided" : text; }
    private String label(String name, String code) { return name == null || name.trim().isEmpty() ? value(code) : name + " (" + value(code) + ")"; }
    @Override protected void onResume() { super.onResume(); opening = false; if (model != null) render(); }
}
