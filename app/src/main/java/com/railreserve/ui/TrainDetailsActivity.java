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
import com.railreserve.live.LiveTrainStop;
import com.railreserve.live.TrainEnquiryRoute;
import com.railreserve.live.TrainScheduleMatcher;
import com.railreserve.model.SimulationRequest;
import com.railreserve.model.SimulationScenarioPolicy;
import com.railreserve.model.TravelClass;
import com.railreserve.service.SimulationScenarioService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Provider timetable and offered classes, with a separately labelled local simulation entry. */
public final class TrainDetailsActivity extends AppCompatActivity {
    public static final String EXTRA_TRAIN = "selected_live_train";
    private LiveTrain train;
    private LiveTrain routeResult;
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
        routeResult = train;
        sourceCode = getIntent().getStringExtra(LiveSearchActivity.EXTRA_SOURCE_CODE);
        sourceName = getIntent().getStringExtra(LiveSearchActivity.EXTRA_SOURCE_NAME);
        destinationCode = getIntent().getStringExtra(LiveSearchActivity.EXTRA_DESTINATION_CODE);
        destinationName = getIntent().getStringExtra(LiveSearchActivity.EXTRA_DESTINATION_NAME);
        date = getIntent().getStringExtra(LiveSearchActivity.EXTRA_DATE);
        ((TextView) findViewById(R.id.detailsHeading)).setText(value(train.getName()) + " · " + value(train.getNumber()));
        model = new ViewModelProvider(this).get(TrainDetailsViewModel.class);
        updateJourneySummary();
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
        train = model.schedule == null ? routeResult : TrainScheduleMatcher.apply(routeResult, model.schedule);
        updateJourneySummary();
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
        boolean hasLocalClass = classes != null && classes.stream().anyMatch(SimulationScenarioPolicy::supportsCode);
        boolean fallback = classes == null || classes.isEmpty()
                || (!hasLocalClass && (!train.isRouteVerified()
                || !BuildConfig.LIVE_FARE_ENABLED || !BuildConfig.LIVE_AVAILABILITY_ENABLED));
        if (!java.util.Objects.equals(renderedClasses, classes) || demoFallbackRendered != fallback || cards.isEmpty()) {
            renderedClasses = classes; demoFallbackRendered = fallback; cards.clear();
            LinearLayout verified = findViewById(R.id.detailsClasses); verified.removeAllViews();
            LinearLayout demo = findViewById(R.id.detailsDemoOnlyClasses); demo.removeAllViews();
            findViewById(R.id.detailsDemoOnlyNotice).setVisibility(fallback ? View.VISIBLE : View.GONE);
            if (classes != null) for (String code : classes) addCard(verified, code, true);
            if (fallback) {
                addCard(demo, TravelClass.SLEEPER.getCode(), false);
                addCard(demo, TravelClass.AC_THREE_TIER.getCode(), false);
            }
        }
        for (Map.Entry<String, View> entry : cards.entrySet()) {
            updateCard(entry.getValue(), entry.getKey(), classes != null && classes.contains(entry.getKey()));
        }
    }

    private void addCard(LinearLayout parent, String code, boolean verified) {
        View card = getLayoutInflater().inflate(R.layout.item_live_class, parent, false);
        card.setTag(code); cards.put(code, card); parent.addView(card);
        ((TextView) card.findViewById(R.id.classHeading)).setText(LiveClassCatalog.label(code) + " (" + code + ")");
        String source = train.getClasses() != null ? train.getClassSource() : model.schedule == null ? null : model.schedule.getClassSource();
        ((TextView) card.findViewById(R.id.classSource)).setText(verified ? "Offered · "
                + (source == null ? "provider data via RailReserve backend" : source) : getString(R.string.demo_class_not_verified));
        card.findViewById(R.id.classFareCheck).setOnClickListener(view -> checkClass(code, true));
        card.findViewById(R.id.classAvailabilityCheck).setOnClickListener(view -> checkClass(code, false));
        Button book = card.findViewById(R.id.classDemoBook);
        book.setAlpha(1f);
        book.setText(getString(R.string.simulate_class_booking, code));
        book.setOnClickListener(view -> simulate(code));
    }

    private SimulationRequest selection(String code, com.railreserve.model.FareSnapshot quote) {
        TrainEnquiryRoute route = enquiryRoute();
        if (route == null) {
            return new SimulationRequest(train.getNumber(),train.getName(),sourceCode,sourceName,destinationCode,destinationName,
                    date,TravelClass.fromCode(code),train.getDeparture(),train.getArrival(),train.getDepartureDay(),train.getArrivalDay(),quote);
        }
        LiveTrainStop boarding = route.getBoardingStop(), alighting = route.getAlightingStop();
        return new SimulationRequest(train.getNumber(),train.getName(),
                boarding.getStation().getCode(),boarding.getStation().getName(),
                alighting.getStation().getCode(),alighting.getStation().getName(),
                date,TravelClass.fromCode(code),boarding.getDeparture(),alighting.getArrival(),
                boarding.getDepartureDay(),alighting.getArrivalDay(),quote);
    }

    private TrainEnquiryRoute enquiryRoute() {
        return TrainScheduleMatcher.resolveEnquiryRoute(routeResult, model.schedule);
    }

    private void checkClass(String code, boolean fare) {
        TrainEnquiryRoute route = enquiryRoute();
        if (route == null) {
            android.widget.Toast.makeText(this,
                    "A verified scheduled boarding and destination route is required for live checks.",
                    android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        model.check(train, verifiedClasses(),
                route.getBoardingStop().getStation().getCode(),
                route.getAlightingStop().getStation().getCode(),date,code,fare);
    }

    private String bookingBlockReason(String code, boolean verified) {
        if (!verified) return "This travel class is not verified for the selected train.";
        if (!BuildConfig.LIVE_FARE_ENABLED || !BuildConfig.LIVE_AVAILABILITY_ENABLED) {
            return "Fare and availability checks are disabled in this build.";
        }
        TrainEnquiryRoute route = enquiryRoute();
        if (route == null) return "The schedule does not provide a valid boarding-to-destination route for live checks.";
        if (!LiveClassCatalog.supportsDemo(code)) return "Demo booking does not support class " + code + ".";
        try {
            SimulationScenarioService.validateJourney(selection(code,null));
            model.capture(train,route.getBoardingStop().getStation().getCode(),
                    route.getAlightingStop().getStation().getCode(),date,code);
            return null;
        } catch(IllegalArgumentException blocked) { return blocked.getMessage(); }
    }

    private void updateCard(View card, String code, boolean verified) {
        TrainDetailsViewModel.Check fare = model.getCheck(code, true), availability = model.getCheck(code, false);
        boolean routeAvailable = enquiryRoute() != null;
        boolean fareEnabled = verified && routeAvailable && BuildConfig.LIVE_FARE_ENABLED,
                availabilityEnabled = verified && routeAvailable && BuildConfig.LIVE_AVAILABILITY_ENABLED;
        ((TextView) card.findViewById(R.id.classFare)).setText(!fareEnabled
                ? getString(!verified ? R.string.live_class_unverified
                        : !routeAvailable ? R.string.live_check_route_unavailable : R.string.fare_plan_unavailable)
                : fare == null ? getString(R.string.fare_not_checked) : fare.loading ? getString(R.string.checking_fare) : UiText.enquiry(fare.text));
        ((TextView) card.findViewById(R.id.classAvailability)).setText(!availabilityEnabled
                ? getString(!verified ? R.string.live_class_unverified
                        : !routeAvailable ? R.string.live_check_route_unavailable : R.string.availability_unavailable)
                : availability == null ? getString(R.string.availability_not_checked) : availability.loading ? getString(R.string.checking_availability) : UiText.enquiry(availability.text));
        String blocked = bookingBlockReason(code,verified);
        card.findViewById(R.id.classDemoBook).setEnabled(blocked == null);
        TextView notice = card.findViewById(R.id.classDemoNotice);
        notice.setText(blocked != null ? blocked : getString(train.isRouteVerified()
                ? R.string.local_demo_booking_notice : R.string.local_unverified_route_booking_notice));
        notice.setVisibility(blocked != null || !train.isRouteVerified() ? View.VISIBLE : View.GONE);
        ((Button) card.findViewById(R.id.classFareCheck)).setText(fare != null && fare.loading ? R.string.checking_fare
                : fare != null && fare.expired ? R.string.recheck_expired_fare : R.string.check_fare);
        ((Button) card.findViewById(R.id.classAvailabilityCheck)).setText(availability != null && availability.loading ? R.string.checking_availability
                : availability != null && availability.expired ? R.string.recheck_availability : R.string.check_availability);
        card.findViewById(R.id.classFareCheck).setVisibility(verified && BuildConfig.LIVE_FARE_ENABLED ? View.VISIBLE : View.GONE);
        card.findViewById(R.id.classFareCheck).setEnabled(fareEnabled && (fare == null || !fare.loading));
        card.findViewById(R.id.classAvailabilityCheck).setVisibility(verified && BuildConfig.LIVE_AVAILABILITY_ENABLED ? View.VISIBLE : View.GONE);
        card.findViewById(R.id.classAvailabilityCheck).setEnabled(availabilityEnabled && (availability == null || !availability.loading));
        card.findViewById(R.id.classProgress).setVisibility((fare != null && fare.loading) || (availability != null && availability.loading) ? View.VISIBLE : View.GONE);
    }

    private void simulate(String code) {
        if (opening) return;
        List<String> classes = verifiedClasses();
        boolean verified = classes != null && classes.contains(code);
        String blocked = bookingBlockReason(code, verified);
        if (blocked != null) { android.widget.Toast.makeText(this,blocked,android.widget.Toast.LENGTH_LONG).show(); render(); return; }
        try {
            TrainEnquiryRoute route = enquiryRoute();
            com.railreserve.model.FareSnapshot quote = model.capture(train,
                    route.getBoardingStop().getStation().getCode(),
                    route.getAlightingStop().getStation().getCode(),date,code);
            opening = true;
            startActivity(new Intent(this,SimulationEntryActivity.class).putExtra(SimulationEntryActivity.EXTRA_REQUEST,selection(code,quote)));
        } catch (IllegalArgumentException expired) {
            opening = false;
            android.widget.Toast.makeText(this,expired.getMessage(),android.widget.Toast.LENGTH_LONG).show();
            render();
        }
    }
    private String arrivalOffset() {
        if (!train.isBoardingStopVerified() || !train.isDestinationStopVerified()) return " (day offset unverified)";
        if (train.getArrivalDay() == null || train.getDepartureDay() == null) return " (day offset not provided)";
        long days = (long) train.getArrivalDay() - train.getDepartureDay();
        return days < 0 ? " (day offset not provided)" : days == 0 ? " (same day)" : " (+" + days + " day" + (days == 1 ? "" : "s") + ")";
    }
    private void updateJourneySummary() {
        String boarding = train.isBoardingStopVerified()
                ? label(train.getBoardingStation().getName(), train.getBoardingStation().getCode())
                : "Not verified at " + label(sourceName, sourceCode);
        String alighting = train.isDestinationStopVerified()
                ? label(train.getAlightingStation().getName(), train.getAlightingStation().getCode())
                : "Not verified at " + label(destinationName, destinationCode);
        String origin = train.getActualOrigin() == null ? "Not provided" : train.getActualOrigin().getLabel();
        String terminus = train.getActualDestination() == null ? "Not provided" : train.getActualDestination().getLabel();
        TrainEnquiryRoute enquiry = enquiryRoute();
        String checkRoute = enquiry == null
                ? "\nFare / availability checks and booking: no valid scheduled route"
                : "\nFare / availability checks and local demo booking: "
                        + label(enquiry.getBoardingStop().getStation().getName(), enquiry.getBoardingStop().getStation().getCode())
                        + " at " + value(enquiry.getBoardingStop().getDeparture())
                        + " → "
                        + label(enquiry.getAlightingStop().getStation().getName(), enquiry.getAlightingStop().getStation().getCode())
                        + " at " + value(enquiry.getAlightingStop().getArrival());
        ((TextView) findViewById(R.id.detailsJourney)).setText(
                "Searched: " + label(sourceName, sourceCode) + " → " + label(destinationName, destinationCode)
                        + "\nTrain origin: " + origin + " · Train destination: " + terminus
                        + "\nBoarding: " + boarding + " at " + value(train.isBoardingStopVerified() ? train.getDeparture() : null)
                        + " → Alight: " + alighting + " at " + value(train.isDestinationStopVerified() ? train.getArrival() : null)
                        + arrivalOffset() + "\nJourney date: " + value(date) + checkRoute);
    }
    private String value(String text) { return text == null || text.trim().isEmpty() ? "Not provided" : text; }
    private String label(String name, String code) { return name == null || name.trim().isEmpty() ? value(code) : name + " (" + value(code) + ")"; }
    @Override protected void onResume() { super.onResume(); opening = false; if (model != null) render(); }
}
