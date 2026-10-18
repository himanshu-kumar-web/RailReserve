package com.railreserve.adapter;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;
import com.railreserve.R;
import com.railreserve.model.Booking;
import java.util.ArrayList;
import java.util.List;
public final class BookingAdapter extends BaseAdapter {
    public interface CancelListener { void onCancel(Booking booking); }
    private final LayoutInflater inflater; private final List<Booking> bookings=new ArrayList<>(); private final CancelListener listener;
    public BookingAdapter(Context context,CancelListener listener){inflater=LayoutInflater.from(context);this.listener=listener;}
    public void setBookings(List<Booking> values){bookings.clear();bookings.addAll(values);notifyDataSetChanged();}
    @Override public int getCount(){return bookings.size();}@Override public Booking getItem(int p){return bookings.get(p);}@Override public long getItemId(int p){return getItem(p).getId();}
    @Override public View getView(int p,View convert,ViewGroup parent){View v=convert==null?inflater.inflate(R.layout.item_booking,parent,false):convert;Booking b=getItem(p);((TextView)v.findViewById(R.id.bookingPnr)).setText("Demo PNR  "+b.getPnr());((TextView)v.findViewById(R.id.bookingTrain)).setText("Train "+b.getTrainNumber()+" · "+b.getTrainName());((TextView)v.findViewById(R.id.bookingRoute)).setText(b.getSource()+" → "+b.getDestination()+" · "+b.getJourneyDate());((TextView)v.findViewById(R.id.bookingPassenger)).setText(b.getPassengerName()+" · "+b.getAge()+" · "+b.getCoach()+" Seat "+b.getSeatNumber());((TextView)v.findViewById(R.id.bookingClassFare)).setText(b.getTravelClass().getLabel()+" · ₹"+b.getFare());TextView status=v.findViewById(R.id.bookingStatus);status.setText(b.getStatus());status.setTextColor(b.getStatus().equals("CONFIRMED")?0xff126b59:0xff9a4b3d);Button cancel=v.findViewById(R.id.cancelBookingButton);cancel.setVisibility(b.getStatus().equals("CONFIRMED")?View.VISIBLE:View.GONE);cancel.setOnClickListener(view->listener.onCancel(b));return v;}
}
