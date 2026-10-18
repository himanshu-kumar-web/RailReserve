package com.railreserve.ui;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.AdapterView;
import android.content.Intent;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.railreserve.R;
import com.railreserve.adapter.TrainResultAdapter;
import com.railreserve.model.TravelClass;
import com.railreserve.service.TrainSearchService;
import com.railreserve.service.TrainSearchServiceFactory;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TrainResultsActivity extends AppCompatActivity {
    public static final String EXTRA_SOURCE="search_source",EXTRA_DESTINATION="search_destination",EXTRA_DATE="search_date",EXTRA_CLASS="search_class";
    private final ExecutorService executor=Executors.newSingleThreadExecutor(); private TrainSearchService service; private boolean destroyed,firstResume=true; private TrainResultAdapter adapter; private ProgressBar progress; private TextView status; private ListView list;
    @Override protected void onCreate(Bundle state){super.onCreate(state);setContentView(R.layout.activity_train_results);View root=findViewById(R.id.resultsRoot);ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());v.setPadding(20+bars.left,20+bars.top,20+bars.right,20+bars.bottom);return insets;});service=TrainSearchServiceFactory.create(getApplicationContext());progress=findViewById(R.id.resultsProgress);status=findViewById(R.id.resultsStatus);list=findViewById(R.id.resultsList);adapter=new TrainResultAdapter(this);list.setAdapter(adapter);String from=getIntent().getStringExtra(EXTRA_SOURCE),to=getIntent().getStringExtra(EXTRA_DESTINATION),date=getIntent().getStringExtra(EXTRA_DATE),clazz=getIntent().getStringExtra(EXTRA_CLASS);((TextView)findViewById(R.id.resultsHeading)).setText(from+"  →  "+to);((TextView)findViewById(R.id.resultsSubtitle)).setText(date+"  ·  "+clazz);list.setOnItemClickListener((parent,view,position,id)->{com.railreserve.model.TrainSearchResult row=(com.railreserve.model.TrainSearchResult)adapter.getItem(position);if(row.getAvailableSeats()==0)return;Intent intent=new Intent(this,BookingActivity.class);intent.putExtra(BookingActivity.EXTRA_JOURNEY_ID,row.getJourneyId());intent.putExtra(BookingActivity.EXTRA_TRAIN_ID,row.getTrainId());intent.putExtra(BookingActivity.EXTRA_TRAIN_NUMBER,row.getNumber());intent.putExtra(BookingActivity.EXTRA_TRAIN_NAME,row.getName());intent.putExtra(BookingActivity.EXTRA_SOURCE,row.getSource());intent.putExtra(BookingActivity.EXTRA_DESTINATION,row.getDestination());intent.putExtra(BookingActivity.EXTRA_DATE,row.getDate());intent.putExtra(BookingActivity.EXTRA_DEPARTURE,row.getDeparture());intent.putExtra(BookingActivity.EXTRA_ARRIVAL,row.getArrival());intent.putExtra(BookingActivity.EXTRA_ARRIVAL_OFFSET,row.getArrivalDayOffset());intent.putExtra(BookingActivity.EXTRA_CLASS,row.getTravelClass().getCode());startActivity(intent);});executor.execute(()->{try{service.ensureDemoData();var rows=service.search(from,to,date,TravelClass.fromCode(clazz));runOnUiThread(()->{if(destroyed)return;progress.setVisibility(View.GONE);if(rows.isEmpty()){status.setText("No direct trains found for this route and class. Try another date or route.");status.setVisibility(View.VISIBLE);}else{status.setVisibility(View.GONE);adapter.setResults(rows);}});}catch(Exception e){runOnUiThread(()->{if(destroyed)return;progress.setVisibility(View.GONE);status.setText(e.getMessage()==null?"Could not load trains. Please try again.":e.getMessage());status.setVisibility(View.VISIBLE);});}});}
    @Override protected void onDestroy(){destroyed=true;executor.shutdownNow();super.onDestroy();}
    @Override protected void onResume(){super.onResume();if(firstResume){firstResume=false;return;}refreshResults();}
    private void refreshResults(){String from=getIntent().getStringExtra(EXTRA_SOURCE),to=getIntent().getStringExtra(EXTRA_DESTINATION),date=getIntent().getStringExtra(EXTRA_DATE),clazz=getIntent().getStringExtra(EXTRA_CLASS);progress.setVisibility(View.VISIBLE);executor.execute(()->{try{var rows=service.search(from,to,date,TravelClass.fromCode(clazz));runOnUiThread(()->{if(destroyed)return;progress.setVisibility(View.GONE);adapter.setResults(rows);status.setText(rows.isEmpty()?"No direct trains found for this route and class. Try another date or route.":"");status.setVisibility(rows.isEmpty()?View.VISIBLE:View.GONE);});}catch(Exception e){runOnUiThread(()->{if(destroyed)return;progress.setVisibility(View.GONE);status.setText(e.getMessage()==null?"Could not refresh train availability.":e.getMessage());status.setVisibility(View.VISIBLE);});}});}
}
