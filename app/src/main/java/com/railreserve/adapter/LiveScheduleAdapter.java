package com.railreserve.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import com.railreserve.R;
import com.railreserve.live.LiveTrainStop;
import java.util.ArrayList;

public final class LiveScheduleAdapter extends ArrayAdapter<LiveTrainStop> {
    private final LayoutInflater inflater;

    public LiveScheduleAdapter(Context context) {
        super(context, 0, new ArrayList<>());
        inflater = LayoutInflater.from(context);
    }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView == null ? inflater.inflate(R.layout.item_live_stop, parent, false) : convertView;
        LiveTrainStop stop = getItem(position);
        if (stop == null) return row;
        ((TextView) row.findViewById(R.id.liveStopStation)).setText(
                stop.getStation() == null ? "Not provided" : stop.getStation().getLabel());
        ((TextView) row.findViewById(R.id.liveStopSequence)).setText(
                stop.getSequence() == null ? "Stop sequence: Not provided" : "Stop " + stop.getSequence());
        String arrival = time(stop.getArrival(), stop.getArrivalDay());
        String departure = time(stop.getDeparture(), stop.getDepartureDay());
        ((TextView) row.findViewById(R.id.liveStopTimes)).setText("Arrival  " + arrival + "\nDeparture  " + departure);
        StringBuilder details = new StringBuilder();
        details.append("Distance: ").append(stop.getDistanceKm() == null ? "Not provided" : stop.getDistanceKm() + " km");
        if (stop.getPlatform() != null) details.append("  ·  Platform: ").append(stop.getPlatform());
        if (stop.isHalt() != null) details.append("  ·  ").append(stop.isHalt() ? "Halting stop" : "Pass-through stop");
        ((TextView) row.findViewById(R.id.liveStopDetails)).setText(details);
        return row;
    }

    private String time(String time, Integer day) {
        if (time == null) return "Not provided";
        return day == null ? time + " (day offset not provided)" : time + " (day offset " + day + ")";
    }
}
