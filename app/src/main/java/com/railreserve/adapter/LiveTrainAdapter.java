package com.railreserve.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import com.railreserve.R;
import com.railreserve.live.LiveTrain;
import java.util.ArrayList;
import java.util.List;

public final class LiveTrainAdapter extends ArrayAdapter<LiveTrain> {
    public interface ScheduleClickListener { void onViewSchedule(LiveTrain train); }
    private final LayoutInflater inflater;
    private final ScheduleClickListener scheduleClickListener;

    public LiveTrainAdapter(Context context, ScheduleClickListener listener) {
        super(context, 0, new ArrayList<>());
        inflater = LayoutInflater.from(context);
        scheduleClickListener = listener;
    }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView == null ? inflater.inflate(R.layout.item_live_train, parent, false) : convertView;
        LiveTrain train = getItem(position);
        if (train == null) return row;
        ((TextView) row.findViewById(R.id.liveTrainName)).setText(value(train.getName()) + "  ·  " + value(train.getNumber()));
        ((TextView) row.findViewById(R.id.liveTrainType)).setText("Train " + value(train.getNumber()) + "  ·  " + value(train.getType()));
        ((TextView) row.findViewById(R.id.liveTrainDeparture)).setText(value(train.getDeparture()));
        ((TextView) row.findViewById(R.id.liveTrainSource)).setText(label(train.getSource()));
        ((TextView) row.findViewById(R.id.liveTrainDepartureDay)).setText(day(train.getDepartureDay()));
        ((TextView) row.findViewById(R.id.liveTrainArrival)).setText(value(train.getArrival()));
        ((TextView) row.findViewById(R.id.liveTrainDestination)).setText(label(train.getDestination()));
        ((TextView) row.findViewById(R.id.liveTrainArrivalDay)).setText(day(train.getArrivalDay()));
        ((TextView) row.findViewById(R.id.liveTrainDetails)).setText(details(train));
        Button schedule = row.findViewById(R.id.liveTrainScheduleButton);
        schedule.setOnClickListener(view -> scheduleClickListener.onViewSchedule(train));
        row.setOnClickListener(view -> scheduleClickListener.onViewSchedule(train));
        return row;
    }

    private String details(LiveTrain train) {
        List<String> parts = new ArrayList<>();
        parts.add("Duration: " + duration(train.getDurationMinutes()));
        parts.add("Distance: " + (train.getDistanceKm() == null ? "Not provided" : train.getDistanceKm() + " km"));
        parts.add("Halts: " + value(train.getTotalHaltsBetween()));
        if (train.getRunDays() != null && !train.getRunDays().isEmpty()) {
            parts.add("Runs: " + android.text.TextUtils.join(", ", train.getRunDays()));
        }
        return android.text.TextUtils.join("  ·  ", parts);
    }

    private String duration(Integer minutes) {
        if (minutes == null || minutes < 0) return "Not provided";
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }

    private String label(com.railreserve.live.LiveStation station) {
        return station == null ? "Not provided" : station.getLabel();
    }

    private String day(Integer value) {
        return value == null ? "Day offset: Not provided" : "Day offset: " + value;
    }

    private String value(Object value) {
        return value == null || value.toString().trim().isEmpty() ? "Not provided" : value.toString();
    }
}
