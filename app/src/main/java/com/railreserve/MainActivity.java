package com.railreserve;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.railreserve.model.TravelClass;
import com.railreserve.service.TrainSearchService;
import com.railreserve.service.TrainSearchServiceFactory;
import com.railreserve.ui.TrainResultsActivity;
import com.railreserve.ui.MyBookingsActivity;
import com.railreserve.ui.FindBookingActivity;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private final ExecutorService executor=Executors.newSingleThreadExecutor(); private TrainSearchService service; private AutoCompleteTextView source,destination; private Button dateButton,searchButton; private LocalDate selectedDate=LocalDate.now(); private boolean destroyed;
    @Override protected void onCreate(Bundle state){super.onCreate(state);setContentView(R.layout.activity_main);View root=findViewById(R.id.main);ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());v.setPadding(v.getPaddingLeft(),bars.top+16,v.getPaddingRight(),bars.bottom+16);return insets;});service=TrainSearchServiceFactory.create(getApplicationContext());source=findViewById(R.id.sourceInput);destination=findViewById(R.id.destinationInput);dateButton=findViewById(R.id.dateButton);searchButton=findViewById(R.id.searchButton);Spinner spinner=findViewById(R.id.classSpinner);ArrayAdapter<TravelClass> classes=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,TravelClass.values());classes.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinner.setAdapter(classes);updateDateLabel();dateButton.setOnClickListener(v->showDatePicker());findViewById(R.id.swapButton).setOnClickListener(v->{CharSequence old=source.getText();source.setText(destination.getText());destination.setText(old);});findViewById(R.id.myBookingsButton).setOnClickListener(v->startActivity(new android.content.Intent(this,MyBookingsActivity.class)));findViewById(R.id.findBookingButton).setOnClickListener(v->startActivity(new android.content.Intent(this,FindBookingActivity.class)));searchButton.setOnClickListener(v->search(spinner));executor.execute(()->{try{service.ensureDemoData();var stations=service.getStationSuggestions();runOnUiThread(()->{if(destroyed)return;ArrayAdapter<String> suggestions=new ArrayAdapter<>(this,android.R.layout.simple_dropdown_item_1line,stations);source.setAdapter(suggestions);destination.setAdapter(suggestions);source.setThreshold(1);destination.setThreshold(1);});}catch(Exception e){runOnUiThread(()->{if(!destroyed)Toast.makeText(this,"Could not prepare demo schedules.",Toast.LENGTH_LONG).show();});}});}
    private void showDatePicker(){LocalDate today=LocalDate.now();DatePickerDialog picker=new DatePickerDialog(this,(view,year,month,day)->{selectedDate=LocalDate.of(year,month+1,day);updateDateLabel();},selectedDate.getYear(),selectedDate.getMonthValue()-1,selectedDate.getDayOfMonth());picker.getDatePicker().setMinDate(today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());picker.getDatePicker().setMaxDate(today.plusDays(14).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());picker.show();}
    private void updateDateLabel(){dateButton.setText("DATE  |  "+selectedDate.format(DateTimeFormatter.ofPattern("EEE, d MMM",Locale.getDefault())));}
    private void search(Spinner spinner){String from=source.getText().toString().trim(),to=destination.getText().toString().trim();if(from.isEmpty()){source.setError("Enter a source station.");source.requestFocus();return;}if(to.isEmpty()){destination.setError("Enter a destination station.");destination.requestFocus();return;}if(from.equalsIgnoreCase(to)){destination.setError("Choose a different destination.");return;}searchButton.setEnabled(false);searchButton.setText("Searching...");executor.execute(()->{try{service.ensureDemoData();service.search(from,to,selectedDate.toString(),(TravelClass)spinner.getSelectedItem());runOnUiThread(()->{if(destroyed)return;android.content.Intent intent=new android.content.Intent(this,TrainResultsActivity.class);intent.putExtra(TrainResultsActivity.EXTRA_SOURCE,from.trim());intent.putExtra(TrainResultsActivity.EXTRA_DESTINATION,to.trim());intent.putExtra(TrainResultsActivity.EXTRA_DATE,selectedDate.toString());intent.putExtra(TrainResultsActivity.EXTRA_CLASS,((TravelClass)spinner.getSelectedItem()).getCode());startActivity(intent);searchButton.setEnabled(true);searchButton.setText("Search trains");});}catch(Exception e){runOnUiThread(()->{if(destroyed)return;searchButton.setEnabled(true);searchButton.setText("Search trains");Toast.makeText(this,e.getMessage()==null?"Search failed. Please try again.":e.getMessage(),Toast.LENGTH_LONG).show();});}});}
    @Override protected void onDestroy(){destroyed=true;executor.shutdownNow();super.onDestroy();}
}
