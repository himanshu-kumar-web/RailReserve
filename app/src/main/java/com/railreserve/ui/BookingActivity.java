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
import androidx.activity.OnBackPressedCallback;
import androidx.lifecycle.ViewModelProvider;
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
    public static final String EXTRA_FARE_SNAPSHOT = "live_fare_snapshot";
    private com.railreserve.model.FareSnapshot fareSnapshot;
    public static final String EXTRA_LIVE_SIMULATION = "live_derived_simulation";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private BookingService service;
    private boolean destroyed;
    private boolean submitting;
    private BookingSubmissionViewModel submission;
    private AlertDialog confirmationDialog;
    private TextView checkoutSummary, checkoutTotal, paymentStatus;
    private Spinner paymentScenario;
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
        Object quote = getIntent().getSerializableExtra(EXTRA_FARE_SNAPSHOT);
        fareSnapshot = quote instanceof com.railreserve.model.FareSnapshot ? (com.railreserve.model.FareSnapshot) quote : null;
        boolean simulation = getIntent().getBooleanExtra(EXTRA_LIVE_SIMULATION, false);
        findViewById(R.id.bookingSimulationNotice).setVisibility(simulation ? View.VISIBLE : View.GONE);
        findViewById(R.id.bookingSimulationPolicy).setVisibility(simulation ? View.VISIBLE : View.GONE);
        if (simulation) ((TextView) findViewById(R.id.bookingSimulationPolicy)).setText(fareSnapshot == null
                ? "A provider fare is required. Return to Train Details." : UiText.quote(fareSnapshot));
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

        seatAdapter = new ArrayAdapter<Seat>(this, android.R.layout.simple_spinner_item, new ArrayList<>()) {
            private View priced(View view,int position) {
                Seat seat = getItem(position);
                if (fareSnapshot != null && seat != null && view instanceof TextView)
                    ((TextView) view).setText(seat.getCoach() + " - Seat " + seat.getNumber() + " (" + PaymentReceiptText.amount(fareSnapshot.getAmountPaise()) + ")");
                return view;
            }
            @Override public View getView(int position, View view, android.view.ViewGroup parent) { return priced(super.getView(position,view,parent),position); }
            @Override public View getDropDownView(int position, View view, android.view.ViewGroup parent) { return priced(super.getDropDownView(position,view,parent),position); }
        };
        seatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        seatSpinner.setAdapter(seatAdapter);
        seatSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Seat seat = (Seat) parent.getItemAtPosition(position);
                selectedSeatSummary.setText("Selected seat  |  " + seat.getCoach() + " - " + seat.getNumber());
                fareSummary.setText((fareSnapshot == null ? "Local demo fare: " : "Captured estimate: ") + PaymentReceiptText.amount(checkoutAmount(seat)));
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
        submission = new ViewModelProvider(this).get(BookingSubmissionViewModel.class);
        OnBackPressedCallback savingBack = new OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() {
                BookingSubmissionViewModel.State current = submission.getState().getValue();
                if (current != null && current.getStage() == BookingSubmissionViewModel.Stage.PROCESSING) { submission.cancelPayment(); return; }
                if (current != null && !current.isSaving()) { submission.editReview(); loadAvailableSeats(); return; }
                android.widget.Toast.makeText(BookingActivity.this,
                        "Saving your demo booking. Please wait.", android.widget.Toast.LENGTH_SHORT).show();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, savingBack);
        configureCheckout();
        submission.getState().observe(this, result -> {
            submitting = result != null && result.isSaving();
            savingBack.setEnabled(result != null && result.getBooking() == null);
            renderCheckout(result);
            if (result != null && result.getBooking() != null) showConfirmation(result.getBooking());
        });
        submission.recover(service);
        if (journeyId <= 0 || travelClass == null) {
            seatStatus.setText("Invalid journey information. Return to search and choose a train.");
            seatStatus.setVisibility(View.VISIBLE);
        } else if (submission.getState().getValue() == null) {
            loadAvailableSeats();
        }
    }

    private void loadAvailableSeats() {
        progress.setVisibility(View.VISIBLE);
        seatStatus.setText("Loading available seats...");
        seatStatus.setVisibility(View.VISIBLE);
        seatStatus.setClickable(false);
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
                    seatStatus.setText("Could not load demo seats. Tap here to retry.");
                    seatStatus.setOnClickListener(view -> loadAvailableSeats());
                    seatStatus.setClickable(true);
                    seatStatus.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void submitBooking() {
        if (submitting || submission.getState().getValue() != null) return;
        String name = nameInput.getText().toString().trim();
        if (name.length() < 2 || name.length() > 80) {
            nameInput.setError("Enter a name of 2 to 80 characters.");
            nameInput.requestFocus();
            return;
        }
        int age;
        try {
            age = Integer.parseInt(ageInput.getText().toString().trim());
        } catch (Exception exception) {
            ageInput.setError("Enter an age from 1 to 120.");
            ageInput.requestFocus();
            return;
        }
        if (age < 1 || age > 120) {
            ageInput.setError("Enter an age from 1 to 120.");
            ageInput.requestFocus();
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

        if (getIntent().getBooleanExtra(EXTRA_LIVE_SIMULATION,false) && fareSnapshot == null) {
            seatStatus.setText("A matching provider fare is required. Return to Train Details."); seatStatus.setVisibility(View.VISIBLE); return;
        }
        KeyboardHelper.dismiss(this);
        BookingRequest request = new BookingRequest(journeyId, selectedSeat.getId(),
                travelClass, name, age, String.valueOf(genderSpinner.getSelectedItem()), null, null, fareSnapshot);
        String summary = ((TextView) findViewById(R.id.bookingTrainSummary)).getText().toString()
                + "\n\nPassenger: " + name + ", age " + age + ", " + genderSpinner.getSelectedItem()
                + "\nSelected seat: " + selectedSeat.getCoach() + " - " + selectedSeat.getNumber()
                + "\nDemo checkout amount: " + PaymentReceiptText.amount(checkoutAmount(selectedSeat)) + (fareSnapshot == null ? "" : "\n" + UiText.quote(fareSnapshot));
        try { submission.review(request, checkoutAmount(selectedSeat), summary); }
        catch (IllegalArgumentException invalidQuote) {
            seatStatus.setText(invalidQuote.getMessage());
            seatStatus.setVisibility(View.VISIBLE);
        }
    }

    private long checkoutAmount(Seat seat) { return fareSnapshot == null ? Math.multiplyExact((long) seat.getFare(),100L) : fareSnapshot.getAmountPaise(); }

    private void configureCheckout() {
        checkoutSummary = findViewById(R.id.checkoutSummary);
        checkoutTotal = findViewById(R.id.checkoutTotal);
        paymentStatus = findViewById(R.id.demoPaymentStatus);
        paymentScenario = findViewById(R.id.demoPaymentScenario);
        ArrayAdapter<String> scenarios = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[]{"Next demo attempt: success", "Next demo attempt: failure", "Next demo attempt: cancellation"});
        scenarios.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        paymentScenario.setAdapter(scenarios);
        findViewById(R.id.demoPaymentTestToggle).setOnClickListener(view -> paymentScenario.setVisibility(
                paymentScenario.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        findViewById(R.id.demoPayButton).setOnClickListener(view -> {
            BookingSubmissionViewModel.Simulation[] outcomes = BookingSubmissionViewModel.Simulation.values();
            BookingSubmissionViewModel.Simulation outcome = outcomes[paymentScenario.getSelectedItemPosition()];
            paymentScenario.setSelection(0);
            submission.pay(service, outcome);
        });
        findViewById(R.id.cancelDemoPaymentButton).setOnClickListener(view -> submission.cancelPayment());
        findViewById(R.id.editCheckoutButton).setOnClickListener(view -> {
            BookingSubmissionViewModel.State current = submission.getState().getValue();
            if (current == null || current.isSaving() || current.getBooking() != null) return;
            submission.editReview();
            loadAvailableSeats();
        });
    }

    private void renderCheckout(BookingSubmissionViewModel.State result) {
        boolean reviewing = result != null;
        boolean liveForm = !reviewing && getIntent().getBooleanExtra(EXTRA_LIVE_SIMULATION,false);
        findViewById(R.id.bookingSimulationNotice).setVisibility(liveForm ? View.VISIBLE : View.GONE);
        findViewById(R.id.bookingSimulationPolicy).setVisibility(liveForm ? View.VISIBLE : View.GONE);
        findViewById(R.id.bookingForm).setVisibility(reviewing ? View.GONE : View.VISIBLE);
        findViewById(R.id.demoCheckoutPanel).setVisibility(reviewing ? View.VISIBLE : View.GONE);
        progress.setVisibility(submitting ? View.VISIBLE : View.GONE);
        confirmButton.setEnabled(!reviewing && seatAdapter.getCount() > 0);
        if (!reviewing) return;
        boolean complete = result.getBooking() != null;
        checkoutSummary.setText(submission.getReviewSummary());
        checkoutTotal.setText("Total demo fare: " + PaymentReceiptText.amount(submission.getAmountPaise()));
        findViewById(R.id.demoPaymentProgress).setVisibility(submitting ? View.VISIBLE : View.GONE);
        Button pay = findViewById(R.id.demoPayButton);
        pay.setEnabled(!submitting && !complete);
        pay.setText(submitting ? "Please wait..." : complete ? "Checkout completed"
                : (result.getStage() == BookingSubmissionViewModel.Stage.FAILED
                || result.getStage() == BookingSubmissionViewModel.Stage.CANCELLED ? "Retry " : "Pay ")
                + PaymentReceiptText.amount(submission.getAmountPaise()) + " (Demo)");
        findViewById(R.id.editCheckoutButton).setEnabled(!submitting && !complete);
        findViewById(R.id.demoPaymentTestToggle).setEnabled(!submitting && !complete);
        paymentScenario.setEnabled(!submitting && !complete);
        findViewById(R.id.cancelDemoPaymentButton).setVisibility(
                result.getStage() == BookingSubmissionViewModel.Stage.PROCESSING ? View.VISIBLE : View.GONE);
        String message = result.getError();
        if (message == null) {
            switch (result.getStage()) {
                case PROCESSING: message = "Processing Demo UPI simulation... No real money is involved."; break;
                case COMMITTING: message = "Finalizing your demo reservation. Please wait for confirmation."; break;
                case RECOVERING: message = "Checking this checkout for an existing reservation..."; break;
                case COMPLETE: message = "CONFIRMED".equals(result.getBooking().getStatus())
                        ? result.getBooking().getPayment() == null ? "Local demo booking confirmed. No demo-payment record exists."
                        : "Demo payment SUCCESS. Your local booking is confirmed." : "This demo reservation was cancelled."; break;
                default: message = "Review the details, then tap Pay (Demo).";
            }
        }
        paymentStatus.setText(message);
    }

    private void showConfirmation(Booking booking) {
        if (confirmationDialog != null && confirmationDialog.isShowing()) return;
        View confirmation = getLayoutInflater().inflate(R.layout.dialog_booking_confirmation, null);
        ((TextView) confirmation.findViewById(R.id.confirmationStatus)).setText("Local demo: " + booking.getStatus());
        ((TextView) confirmation.findViewById(R.id.confirmationPnr)).setText(booking.getPnr());
        TicketUi.bindCopy(confirmation,booking.getPnr());
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
                "Demo checkout amount: " + PaymentReceiptText.amount(Math.multiplyExact((long) booking.getFare(),100L)));

        ((TextView) confirmation.findViewById(R.id.confirmationPayment)).setText(PaymentReceiptText.receipt(booking));
        confirmationDialog = new AlertDialog.Builder(this)
                .setTitle("CONFIRMED".equals(booking.getStatus()) ? "Local demo booking confirmed" : "Local demo booking cancelled")
                .setView(confirmation)
                .setCancelable(false)
                .setPositiveButton("My bookings", (dialog, which) -> {
                    startActivity(new Intent(this, MyBookingsActivity.class));
                    finish();
                })
                .setNegativeButton("Train search", (dialog, which) -> {
                    Intent intent = new Intent(this, com.railreserve.MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                })
                .show();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (confirmationDialog != null) confirmationDialog.dismiss();

        executor.shutdownNow();
        super.onDestroy();
    }
}
