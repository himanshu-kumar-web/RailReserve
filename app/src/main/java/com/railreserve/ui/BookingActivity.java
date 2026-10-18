package com.railreserve.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.railreserve.R;
import com.railreserve.model.Booking;
import com.railreserve.model.BookingRequest;
import com.railreserve.model.Seat;
import com.railreserve.model.TravelClass;
import com.railreserve.service.BookingService;
import com.railreserve.service.BookingServiceFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BookingActivity extends AppCompatActivity {
    public static final String EXTRA_JOURNEY_ID = "journey_id";
    public static final String EXTRA_TRAIN_ID = "train_id";
    public static final String EXTRA_TRAIN_NUMBER = "train_number";
    public static final String EXTRA_TRAIN_NAME = "train_name";
    public static final String EXTRA_SOURCE = "source";
    public static final String EXTRA_DESTINATION = "destination";
    public static final String EXTRA_DATE = "date";
    public static final String EXTRA_DEPARTURE = "departure";
    public static final String EXTRA_ARRIVAL = "arrival";
    public static final String EXTRA_ARRIVAL_OFFSET = "arrival_offset";
    public static final String EXTRA_CLASS = "travel_class";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private BookingService service;
    private boolean destroyed;
    private boolean submitting;
    private long journeyId;
    private TravelClass travelClass;
    private Spinner seatSpinner;
    private Spinner genderSpinner;
    private EditText nameInput;
    private EditText ageInput;
    private Button confirmButton;
    private ProgressBar progress;
    private TextView seatStatus;
    private TextView selectedSeatSummary;
    private TextView fareSummary;
    private TextView genderError;
    private ArrayAdapter<Seat> seatAdapter;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_booking);
        View root = findViewById(R.id.bookingRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(view.getPaddingLeft(), bars.top, view.getPaddingRight(), bars.bottom);
            return insets;
        });

        service = BookingServiceFactory.create(getApplicationContext());
        journeyId = getIntent().getLongExtra(EXTRA_JOURNEY_ID, -1);
        try {
            travelClass = TravelClass.fromCode(getIntent().getStringExtra(EXTRA_CLASS));
        } catch (Exception exception) {
            travelClass = null;
        }

        String trainName = getIntent().getStringExtra(EXTRA_TRAIN_NAME);
        String trainNumber = getIntent().getStringExtra(EXTRA_TRAIN_NUMBER);
        String source = getIntent().getStringExtra(EXTRA_SOURCE);
        String destination = getIntent().getStringExtra(EXTRA_DESTINATION);
        String date = getIntent().getStringExtra(EXTRA_DATE);
        String departure = getIntent().getStringExtra(EXTRA_DEPARTURE);
        String arrival = getIntent().getStringExtra(EXTRA_ARRIVAL);
        String classLabel = travelClass == null ? "Invalid class" : travelClass.getLabel();
        String overnight = getIntent().getIntExtra(EXTRA_ARRIVAL_OFFSET, 0) > 0 ? " (+1 day)" : "";
        ((TextView) findViewById(R.id.bookingTrainSummary)).setText(
                trainName + "  |  Train " + trainNumber + "\n"
                        + source + "  ->  " + destination + "\n"
                        + date + "  |  " + departure + " - " + arrival + overnight + "\n"
                        + classLabel);

        seatSpinner = findViewById(R.id.seatSpinner);
        genderSpinner = findViewById(R.id.genderSpinner);
        nameInput = findViewById(R.id.passengerNameInput);
        ageInput = findViewById(R.id.passengerAgeInput);
        confirmButton = findViewById(R.id.confirmBookingButton);
        progress = findViewById(R.id.seatProgress);
        seatStatus = findViewById(R.id.seatStatus);
        selectedSeatSummary = findViewById(R.id.bookingSelectedSeat);
        fareSummary = findViewById(R.id.bookingFareSummary);
        genderError = findViewById(R.id.genderError);

        seatAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>());
        seatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        seatSpinner.setAdapter(seatAdapter);
        seatSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Seat seat = (Seat) parent.getItemAtPosition(position);
                selectedSeatSummary.setText("Selected seat  |  " + seat.getCoach() + " - " + seat.getNumber());
                fareSummary.setText("Fare  |  \u20B9 " + seat.getFare());
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {
                selectedSeatSummary.setText("Select an available seat below");
                fareSummary.setText("");
            }
        });

        ArrayAdapter<String> genders = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"Select gender", "Female", "Male", "Other"});
        genders.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        genderSpinner.setAdapter(genders);
        genderSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) genderError.setVisibility(View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        confirmButton.setEnabled(false);
        confirmButton.setOnClickListener(view -> submitBooking());
        if (journeyId <= 0 || travelClass == null) {
            seatStatus.setText("Invalid journey information. Return to search and choose a train.");
            seatStatus.setVisibility(View.VISIBLE);
        } else {
            loadAvailableSeats();
        }
    }

    private void loadAvailableSeats() {
        progress.setVisibility(View.VISIBLE);
        seatStatus.setText("Loading available seats...");
        seatStatus.setVisibility(View.VISIBLE);
        confirmButton.setEnabled(false);
        executor.execute(() -> {
            try {
                List<Seat> seats = service.getAvailableSeats(journeyId, travelClass.getCode());
                runOnUiThread(() -> {
                    if (destroyed) return;
                    progress.setVisibility(View.GONE);
                    seatAdapter.clear();
                    seatAdapter.addAll(seats);
                    confirmButton.setEnabled(!seats.isEmpty());
                    if (seats.isEmpty()) {
                        seatStatus.setText("No seats remain in this class for this journey.");
                        seatStatus.setVisibility(View.VISIBLE);
                    } else {
                        seatStatus.setVisibility(View.GONE);
                    }
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    if (destroyed) return;
                    progress.setVisibility(View.GONE);
                    seatStatus.setText(exception.getMessage() == null
                            ? "Could not load seats." : exception.getMessage());
                    seatStatus.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void submitBooking() {
        if (submitting) return;
        String name = nameInput.getText().toString().trim();
        if (name.length() < 2 || name.length() > 80) {
            nameInput.setError("Enter a name of 2 to 80 characters.");
            return;
        }
        int age;
        try {
            age = Integer.parseInt(ageInput.getText().toString().trim());
        } catch (Exception exception) {
            ageInput.setError("Enter an age from 1 to 120.");
            return;
        }
        if (age < 1 || age > 120) {
            ageInput.setError("Enter an age from 1 to 120.");
            return;
        }
        if (genderSpinner.getSelectedItemPosition() == 0) {
            genderError.setVisibility(View.VISIBLE);
            return;
        }
        Seat selectedSeat = (Seat) seatSpinner.getSelectedItem();
        if (selectedSeat == null) {
            seatStatus.setText("Choose an available seat.");
            seatStatus.setVisibility(View.VISIBLE);
            return;
        }

        submitting = true;
        confirmButton.setEnabled(false);
        confirmButton.setText("Confirming booking...");
        progress.setVisibility(View.VISIBLE);
        BookingRequest request = new BookingRequest(journeyId, selectedSeat.getId(),
                travelClass, name, age, String.valueOf(genderSpinner.getSelectedItem()));
        executor.execute(() -> {
            try {
                Booking booking = service.createBooking(request);
                runOnUiThread(() -> {
                    if (destroyed) return;
                    progress.setVisibility(View.GONE);
                    submitting = false;
                    confirmButton.setText("Confirm booking");
                    showConfirmation(booking);
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    if (destroyed) return;
                    progress.setVisibility(View.GONE);
                    submitting = false;
                    confirmButton.setText("Confirm booking");
                    confirmButton.setEnabled(false);
                    new AlertDialog.Builder(this)
                            .setTitle("Booking not completed")
                            .setMessage(exception.getMessage() == null
                                    ? "Please refresh the available seats and try again."
                                    : exception.getMessage())
                            .setPositiveButton("Refresh seats", (dialog, which) -> loadAvailableSeats())
                            .setNegativeButton("Back to results", (dialog, which) -> finish())
                            .show();
                });
            }
        });
    }

    private void showConfirmation(Booking booking) {
        View confirmation = getLayoutInflater().inflate(R.layout.dialog_booking_confirmation, null);
        ((TextView) confirmation.findViewById(R.id.confirmationStatus)).setText(booking.getStatus());
        ((TextView) confirmation.findViewById(R.id.confirmationPnr)).setText(booking.getPnr());
        ((TextView) confirmation.findViewById(R.id.confirmationTrain)).setText(
                "Train " + booking.getTrainNumber() + "  |  " + booking.getTrainName());
        ((TextView) confirmation.findViewById(R.id.confirmationRoute)).setText(
                booking.getSource() + "  ->  " + booking.getDestination());
        ((TextView) confirmation.findViewById(R.id.confirmationJourney)).setText(
                booking.getJourneyDate() + "  |  " + booking.getDeparture() + " - "
                        + booking.getArrival() + (booking.getArrivalDayOffset() > 0 ? " (+1 day)" : ""));
        ((TextView) confirmation.findViewById(R.id.confirmationPassenger)).setText(
                "Passenger  |  " + booking.getPassengerName() + ", age " + booking.getAge());
        ((TextView) confirmation.findViewById(R.id.confirmationSeatClass)).setText(
                "Seat  |  " + booking.getCoach() + " - " + booking.getSeatNumber()
                        + "     Class  |  " + booking.getTravelClass().getLabel());
        ((TextView) confirmation.findViewById(R.id.confirmationFare)).setText(
                "Total fare  |  \u20B9 " + booking.getFare());

        new AlertDialog.Builder(this)
                .setView(confirmation)
                .setCancelable(false)
                .setPositiveButton("My bookings", (dialog, which) -> {
                    startActivity(new Intent(this, MyBookingsActivity.class));
                    finish();
                })
                .setNegativeButton("Train search", (dialog, which) -> {
                    finishAffinity();
                    startActivity(new Intent(this, com.railreserve.MainActivity.class));
                })
                .show();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        executor.shutdownNow();
        super.onDestroy();
    }
}
