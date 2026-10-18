package com.railreserve.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MyBookingsActivity extends AppCompatActivity implements BookingAdapter.CancelListener {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private BookingService service;
    private BookingAdapter adapter;
    private ProgressBar progress;
    private TextView status;
    private Button emptySearchButton;
    private boolean destroyed;
    private boolean cancellationInProgress;
    private boolean cancellationDialogShowing;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_my_bookings);
        View root = findViewById(R.id.bookingsRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(20 + bars.left, 20 + bars.top, 20 + bars.right, 20 + bars.bottom);
            return insets;
        });
        service = BookingServiceFactory.create(getApplicationContext());
        ListView list = findViewById(R.id.bookingsList);
        progress = findViewById(R.id.bookingsProgress);
        status = findViewById(R.id.bookingsStatus);
        emptySearchButton = findViewById(R.id.emptySearchButton);
        adapter = new BookingAdapter(this, this);
        list.setAdapter(adapter);
        emptySearchButton.setOnClickListener(view -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadBookings();
    }

    private void loadBookings() {
        progress.setVisibility(View.VISIBLE);
        status.setVisibility(View.GONE);
        emptySearchButton.setVisibility(View.GONE);
        executor.execute(() -> {
            try {
                List<Booking> bookings = service.getBookings();
                runOnUiThread(() -> {
                    if (destroyed) return;
                    progress.setVisibility(View.GONE);
                    adapter.setBookings(bookings);
                    if (bookings.isEmpty()) {
                        status.setText("No reservations yet. Search for a train to plan your first journey.");
                        status.setVisibility(View.VISIBLE);
                        emptySearchButton.setVisibility(View.VISIBLE);
                    } else {
                        status.setVisibility(View.GONE);
                    }
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    if (destroyed) return;
                    progress.setVisibility(View.GONE);
                    emptySearchButton.setVisibility(View.GONE);
                    status.setText(exception.getMessage() == null
                            ? "Could not load your bookings. Please try again."
                            : exception.getMessage());
                    status.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    @Override
    public void onCancel(Booking booking) {
        if (cancellationInProgress || cancellationDialogShowing) return;
        cancellationDialogShowing = true;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Cancel this reservation?")
                .setMessage("Demo PNR " + booking.getPnr() + "\n"
                        + booking.getTrainName() + "\n"
                        + booking.getJourneyDate() + "  |  "
                        + booking.getCoach() + " Seat " + booking.getSeatNumber()
                        + "\nThe seat will be released if cancellation succeeds.")
                .setNegativeButton("Keep booking", null)
                .setPositiveButton("Cancel reservation", null)
                .create();
        dialog.setOnDismissListener(ignored -> cancellationDialogShowing = false);
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    if (cancellationInProgress) return;
                    dialog.dismiss();
                    performCancellation(booking);
                }));
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xff9a3328);
    }

    private void performCancellation(Booking booking) {
        cancellationInProgress = true;
        adapter.setCancellationInProgress(true);
        progress.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            try {
                service.cancelBooking(booking.getId());
                runOnUiThread(() -> {
                    if (destroyed) return;
                    cancellationInProgress = false;
                    adapter.setCancellationInProgress(false);
                    progress.setVisibility(View.GONE);
                    Toast.makeText(this, "Booking cancelled. The seat is available again.",
                            Toast.LENGTH_SHORT).show();
                    loadBookings();
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    if (destroyed) return;
                    cancellationInProgress = false;
                    adapter.setCancellationInProgress(false);
                    progress.setVisibility(View.GONE);
                    new AlertDialog.Builder(this)
                            .setTitle("Cancellation not completed")
                            .setMessage(exception.getMessage() == null
                                    ? "The booking could not be cancelled."
                                    : exception.getMessage())
                            .setPositiveButton("OK", null)
                            .show();
                    loadBookings();
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        executor.shutdownNow();
        super.onDestroy();
    }
}
