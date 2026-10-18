package com.railreserve.adapter;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import com.railreserve.R;
import com.railreserve.model.TrainSearchResult;
import android.widget.Button;
import java.util.ArrayList;
import java.util.List;
public final class TrainResultAdapter extends BaseAdapter {
    private final LayoutInflater inflater; private final List<TrainSearchResult> results=new ArrayList<>();
    public TrainResultAdapter(Context context){inflater=LayoutInflater.from(context);}
    public void setResults(List<TrainSearchResult> values){results.clear();results.addAll(values);notifyDataSetChanged();}
    @Override public int getCount(){return results.size();} @Override public Object getItem(int position){return results.get(position);} @Override public long getItemId(int position){return position;}
    @Override public View getView(int position,View convertView,ViewGroup parent){View v=convertView==null?inflater.inflate(R.layout.item_train_result,parent,false):convertView;TrainSearchResult r=results.get(position);((TextView)v.findViewById(R.id.trainName)).setText(r.getName());((TextView)v.findViewById(R.id.trainNumber)).setText("Train "+r.getNumber()+"  •  "+r.getTravelClass().getCode());((TextView)v.findViewById(R.id.trainRoute)).setText(r.getSource()+"  →  "+r.getDestination());String arrival=r.getArrival()+(r.getArrivalDayOffset()>0?"  +"+r.getArrivalDayOffset()+" day":"");((TextView)v.findViewById(R.id.trainTimes)).setText(r.getDeparture()+"  —  "+arrival);((TextView)v.findViewById(R.id.trainFare)).setText("₹"+r.getFare());((TextView)v.findViewById(R.id.trainAvailability)).setText(r.getAvailableSeats()==0?"Sold out · 0 / "+r.getTotalSeats()+" seats":r.getAvailableSeats()+" / "+r.getTotalSeats()+" seats available");Button book=v.findViewById(R.id.bookTrainButton);book.setEnabled(r.getAvailableSeats()>0);book.setText(r.getAvailableSeats()>0?"Choose seat":"Sold out");book.setOnClickListener(view->{if(parent instanceof android.widget.ListView)((android.widget.ListView)parent).performItemClick(v,position,getItemId(position));});return v;}
}
