package com.railreserve.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.railreserve.R;
import com.railreserve.live.LiveTrain;
import java.util.ArrayList;
import java.util.List;

public final class LiveTrainAdapter extends ArrayAdapter<LiveTrain> {
    public interface DetailsClickListener { void onViewDetails(LiveTrain train); }
    private static final String[] DAY_LABELS = {"M", "T", "W", "T", "F", "S", "S"};
    private static final String[] DAY_NAMES = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
    private static final String[] DAY_CODES = {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"};
    private final LayoutInflater inflater;
    private final DetailsClickListener detailsClickListener;
    private final String searchedSource;
    private final String searchedDestination;

    public LiveTrainAdapter(Context context, DetailsClickListener listener) {
        this(context, null, null, null, null, listener);
    }

    public LiveTrainAdapter(Context context, String sourceCode, String sourceName, String destinationCode,
                            String destinationName, DetailsClickListener listener) {
        super(context, 0, new ArrayList<>());
        inflater = LayoutInflater.from(context);
        detailsClickListener = listener;
        searchedSource = stationLabel(sourceCode, sourceName);
        searchedDestination = stationLabel(destinationCode, destinationName);
    }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView == null ? inflater.inflate(R.layout.item_live_train, parent, false) : convertView;
        LiveTrain train = getItem(position);
        if (train == null) return row;
        ((TextView) row.findViewById(R.id.liveTrainName)).setText(value(train.getName()));
        ((TextView) row.findViewById(R.id.liveTrainType)).setText("Train " + value(train.getNumber())
                + (train.getType() == null || train.getType().trim().isEmpty() ? "" : "  ·  " + train.getType()));
        ((TextView) row.findViewById(R.id.liveTrainDeparture)).setText(value(train.getDeparture()));
        ((TextView) row.findViewById(R.id.liveTrainSource)).setText(label(train.getSource()));
        ((TextView) row.findViewById(R.id.liveTrainDepartureDay)).setText(day(train.getDepartureDay()));
        ((TextView) row.findViewById(R.id.liveTrainArrival)).setText(value(train.getArrival()));
        ((TextView) row.findViewById(R.id.liveTrainDestination)).setText(label(train.getDestination()));
        ((TextView) row.findViewById(R.id.liveTrainArrivalDay)).setText(day(train.getArrivalDay()));
        TextView stationNotice = row.findViewById(R.id.liveTrainStationNotice);
        stationNotice.setVisibility(train.isRouteVerified() ? View.VISIBLE : View.GONE);
        stationNotice.setText(stationNotice(train));
        ((TextView) row.findViewById(R.id.liveTrainDistance)).setText(
                train.getDistanceKm() != null
                        ? String.format(java.util.Locale.getDefault(), "%.1f km", train.getDistanceKm())
                        : "Not provided");
        ((TextView) row.findViewById(R.id.liveTrainDuration)).setText(
                duration(train.getDurationMinutes()));
        bindRunningDays(row, train);
        row.findViewById(R.id.liveTrainDetailsButton).setOnClickListener(view -> detailsClickListener.onViewDetails(train));
        row.setOnClickListener(view -> detailsClickListener.onViewDetails(train));
        row.setContentDescription(value(train.getName()) + ", Train " + value(train.getNumber()) + ". "
                + label(train.getSource()) + " to " + label(train.getDestination()) + ". "
                + runningDaysDescription(train) + ". View train details and classes.");
        return row;
    }

    private void bindRunningDays(View row, LiveTrain train) {
        LinearLayout days = row.findViewById(R.id.liveTrainRunningDays);
        if (days.getChildCount() == 0) {
            for (int i = 0; i < DAY_LABELS.length; i++) {
                TextView chip = new TextView(getContext());
                chip.setGravity(android.view.Gravity.CENTER);
                chip.setTextSize(12);
                chip.setPadding(0, dp(5), 0, dp(5));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                params.setMargins(dp(2), 0, dp(2), 0);
                days.addView(chip, params);
            }
        }
        boolean[] active = activeDays(train.getRunDays());
        for (int i = 0; i < days.getChildCount(); i++) {
            TextView chip = (TextView) days.getChildAt(i);
            chip.setText(DAY_LABELS[i]);
            chip.setContentDescription(DAY_NAMES[i] + (active[i] ? ", train operates" : ", train does not operate"));
            chip.setBackgroundResource(active[i] ? R.drawable.chip_day_active : R.drawable.chip_day_inactive);
            chip.setTextColor(getContext().getColor(active[i] ? R.color.brand_dark : R.color.muted));
        }
        int count = 0;
        for (boolean operates : active) if (operates) count++;
        ((TextView) row.findViewById(R.id.liveTrainRunningSummary)).setText(
                train.getRunDays() == null || train.getRunDays().isEmpty()
                        ? "Not provided" : count + (count == 1 ? " day" : " days"));
    }

    private boolean[] activeDays(List<String> runningDays) {
        boolean[] active = new boolean[DAY_CODES.length];
        if (runningDays == null) return active;
        for (String value : runningDays) {
            if (value == null) continue;
            String[] tokens = value.toUpperCase(java.util.Locale.ROOT).split("[^A-Z]+");
            for (String token : tokens) {
                for (int i = 0; i < DAY_CODES.length; i++) {
                    if (token.equals(DAY_CODES[i]) || token.equals(DAY_NAMES[i].toUpperCase(java.util.Locale.ROOT))) {
                        active[i] = true;
                    }
                }
            }
        }
        return active;
    }

    private String runningDaysDescription(LiveTrain train) {
        if (train.getRunDays() == null || train.getRunDays().isEmpty()) return "Running days not provided";
        boolean[] active = activeDays(train.getRunDays());
        List<String> names = new ArrayList<>();
        for (int i = 0; i < active.length; i++) if (active[i]) names.add(DAY_NAMES[i]);
        return names.isEmpty() ? "Running days not provided" : "Runs on " + android.text.TextUtils.join(", ", names);
    }

    private String stationNotice(LiveTrain train) {
        StringBuilder text = new StringBuilder();
        text.append("Searched departure: ").append(searchedSource == null ? label(train.getSource()) : searchedSource);
        if (train.isBoardingStopVerified()) {
            text.append("\nBoard at: ").append(label(train.getBoardingStation()));
            if (train.getActualOrigin() != null && !sameCode(train.getActualOrigin(), train.getBoardingStation())) {
                text.append(" · Train origin: ").append(label(train.getActualOrigin()));
            }
        } else {
            text.append(train.isScheduleVerified() ? "\nNo scheduled boarding stop verified"
                    : "\nBoarding stop unverified");
            if (train.getActualOrigin() != null && !sameCode(train.getActualOrigin(), train.getSource())) {
                text.append(" · Alternative origin (distance unverified): ").append(label(train.getActualOrigin()));
            } else if (train.getActualOrigin() != null) {
                text.append(" · Train origin: ").append(label(train.getActualOrigin()));
            }
        }
        text.append("\nSearched destination: ")
                .append(searchedDestination == null ? label(train.getDestination()) : searchedDestination);
        if (train.isDestinationStopVerified()) {
            text.append("\nAlight at: ").append(label(train.getAlightingStation()));
            if (train.getActualDestination() != null && !sameCode(train.getActualDestination(), train.getAlightingStation())) {
                text.append(" · Train destination: ").append(label(train.getActualDestination()));
            }
        } else {
            text.append(train.isScheduleVerified() ? "\nNo scheduled destination stop verified"
                    : "\nDestination stop unverified");
            if (train.getActualDestination() != null && !sameCode(train.getActualDestination(), train.getDestination())) {
                text.append(" · Alternative terminus (distance unverified): ").append(label(train.getActualDestination()));
            } else if (train.getActualDestination() != null) {
                text.append(" · Train destination: ").append(label(train.getActualDestination()));
            }
        }
        return text.toString();
    }

    private String duration(Integer minutes) {
        if (minutes == null || minutes < 0) return "Not provided";
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }

    private String label(com.railreserve.live.LiveStation station) {
        return station == null ? "Not provided" : station.getLabel();
    }

    private boolean sameCode(com.railreserve.live.LiveStation first, com.railreserve.live.LiveStation second) {
        return first != null && second != null && first.getCode() != null
                && first.getCode().equalsIgnoreCase(second.getCode());
    }

    private String stationLabel(String code, String name) {
        if (code == null || code.trim().isEmpty()) return null;
        return name == null || name.trim().isEmpty() ? code : name + " (" + code + ")";
    }

    private int dp(int value) {
        return Math.round(value * getContext().getResources().getDisplayMetrics().density);
    }

    private String day(Integer value) {
        return value == null ? "Day offset: Not provided" : "Day offset: " + value;
    }

    private String value(Object value) {
        return value == null || value.toString().trim().isEmpty() ? "Not provided" : value.toString();
    }
}
