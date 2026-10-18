package com.railreserve.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import com.railreserve.R;
import com.railreserve.model.TrainSearchResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TrainResultAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final List<TrainSearchResult> results = new ArrayList<>();

    public TrainResultAdapter(Context context) {
        inflater = LayoutInflater.from(context);
    }

    public void setResults(List<TrainSearchResult> values) {
        results.clear();
        results.addAll(values);
        notifyDataSetChanged();
    }

    @Override public int getCount() { return results.size(); }
    @Override public Object getItem(int position) { return results.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView == null
                ? inflater.inflate(R.layout.item_train_result, parent, false)
                : convertView;
        TrainSearchResult result = results.get(position);
        ((TextView) view.findViewById(R.id.trainName)).setText(result.getName());
        ((TextView) view.findViewById(R.id.trainNumber)).setText(
                "TRAIN " + result.getNumber() + "  |  "
                        + result.getTravelClass().getLabel().toUpperCase(Locale.ROOT));
        ((TextView) view.findViewById(R.id.trainRoute)).setText(
                result.getSource() + "  ->  " + result.getDestination());
        String arrival = result.getArrival()
                + (result.getArrivalDayOffset() > 0 ? " (+1 day)" : "");
        ((TextView) view.findViewById(R.id.trainTimes)).setText(
                result.getDeparture() + "  -  " + arrival);
        ((TextView) view.findViewById(R.id.trainFare)).setText(
                "\u20B9 " + result.getFare());
        ((TextView) view.findViewById(R.id.trainAvailability)).setText(
                result.getAvailableSeats() == 0
                        ? "Sold out\n0 / " + result.getTotalSeats() + " seats"
                        : result.getAvailableSeats() + " / " + result.getTotalSeats() + " seats available");
        Button bookButton = view.findViewById(R.id.bookTrainButton);
        bookButton.setEnabled(result.getAvailableSeats() > 0);
        bookButton.setText(result.getAvailableSeats() > 0 ? "Select seat" : "Sold out");
        bookButton.setOnClickListener(v -> {
            if (parent instanceof ListView) {
                ListView list = (ListView) parent;
                int currentPosition = list.getPositionForView(view);
                if (currentPosition != ListView.INVALID_POSITION) {
                    list.performItemClick(view, currentPosition, getItemId(currentPosition));
                }
            }
        });
        return view;
    }
}
