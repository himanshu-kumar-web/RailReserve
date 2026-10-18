package com.railreserve.ui;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.railreserve.R;
import com.railreserve.adapter.BookingAdapter;
import com.railreserve.model.Booking;
import com.railreserve.service.BookingService;
import com.railreserve.service.BookingServiceFactory;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
public class MyBookingsActivity extends AppCompatActivity implements BookingAdapter.CancelListener {
    private final ExecutorService executor=Executors.newSingleThreadExecutor();private BookingService service;private BookingAdapter adapter;private ListView list;private ProgressBar progress;private TextView status;private boolean destroyed;
    @Override protected void onCreate(Bundle state){super.onCreate(state);setContentView(R.layout.activity_my_bookings);View root=findViewById(R.id.bookingsRoot);ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());v.setPadding(20+bars.left,20+bars.top,20+bars.right,20+bars.bottom);return insets;});service=BookingServiceFactory.create(getApplicationContext());list=findViewById(R.id.bookingsList);progress=findViewById(R.id.bookingsProgress);status=findViewById(R.id.bookingsStatus);adapter=new BookingAdapter(this,this);list.setAdapter(adapter);}
    @Override protected void onResume(){super.onResume();loadBookings();}
    private void loadBookings(){progress.setVisibility(View.VISIBLE);status.setVisibility(View.GONE);executor.execute(()->{try{java.util.List<Booking> bookings=service.getBookings();runOnUiThread(()->{if(destroyed)return;progress.setVisibility(View.GONE);adapter.setBookings(bookings);if(bookings.isEmpty()){status.setText("No bookings yet. Search for a train to get started.");status.setVisibility(View.VISIBLE);}});}catch(Exception e){runOnUiThread(()->{if(destroyed)return;progress.setVisibility(View.GONE);status.setText(e.getMessage()==null?"Could not load bookings.":e.getMessage());status.setVisibility(View.VISIBLE);});}});}
    @Override public void onCancel(Booking booking){new AlertDialog.Builder(this).setTitle("Cancel this booking?").setMessage("Cancel demo PNR "+booking.getPnr()+" and release "+booking.getCoach()+" Seat "+booking.getSeatNumber()+"?").setNegativeButton("Keep booking",null).setPositiveButton("Cancel booking",(dialog,which)->{progress.setVisibility(View.VISIBLE);executor.execute(()->{try{service.cancelBooking(booking.getId());runOnUiThread(()->{if(destroyed)return;progress.setVisibility(View.GONE);android.widget.Toast.makeText(this,"Booking cancelled and seat released.",android.widget.Toast.LENGTH_SHORT).show();loadBookings();});}catch(Exception e){runOnUiThread(()->{if(destroyed)return;progress.setVisibility(View.GONE);new AlertDialog.Builder(this).setMessage(e.getMessage()==null?"Cancellation failed.":e.getMessage()).setPositiveButton("OK",null).show();loadBookings();});}});}).show();}
    @Override protected void onDestroy(){destroyed=true;executor.shutdownNow();super.onDestroy();}
}
