package com.railreserve.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
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
import android.widget.Toast;

public final class AppSectionActivity extends AppCompatActivity {
    public static final String EXTRA_SECTION = "app_section";
    public static final String SECTION_TICKETS = "tickets";
    public static final String SECTION_PROFILE = "profile";
    public static final String SECTION_ABOUT = "about";

    private TextView heading;
    private TextView intro;
    private TextView details;
    private LinearLayout ticketActions;
    private LinearLayout ticketCards;
    private TextView ticketStatus;
    private ProgressBar ticketProgress;
    private BookingService bookingService;
    private BookingAdapter ticketAdapter;
    private final ExecutorService ticketExecutor = Executors.newSingleThreadExecutor();
    private String currentSection = SECTION_TICKETS;
    private boolean initialResume = true;
    private boolean destroyed;
    private int ticketGeneration;
    private boolean openingChild;
    private boolean hidingTicket;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_app_section);
        BackNavigation.bind(this);
        View root = findViewById(R.id.appSectionRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(view.getPaddingLeft(), bars.top, view.getPaddingRight(), bars.bottom);
            return insets;
        });

        heading = findViewById(R.id.sectionHeading);
        intro = findViewById(R.id.sectionIntro);
        details = findViewById(R.id.sectionDetails);
        ticketActions = findViewById(R.id.ticketsActions);
        ticketCards = findViewById(R.id.ticketCards);
        ticketStatus = findViewById(R.id.ticketStatus);
        ticketProgress = findViewById(R.id.ticketProgress);
        bookingService = BookingServiceFactory.create(getApplicationContext());
        ticketAdapter = new BookingAdapter(this, null, this::confirmHideTicket);
        findViewById(R.id.openMyBookings).setOnClickListener(view ->
                openChild(MyBookingsActivity.class));
        findViewById(R.id.openFindBooking).setOnClickListener(view ->
                openChild(FindBookingActivity.class));
        findViewById(R.id.openDemoWorkflow).setOnClickListener(view ->
                openChild(com.railreserve.MainActivity.class));

        String section = state == null
                ? getIntent().getStringExtra(EXTRA_SECTION)
                : state.getString(EXTRA_SECTION, SECTION_TICKETS);
        showSection(section == null ? SECTION_TICKETS : section);
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { AppNavigation.navigate(AppSectionActivity.this, "home"); }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        AppNavigation.onResume(this);
        openingChild = false;
        if (initialResume) {
            initialResume = false;
        } else if (SECTION_TICKETS.equals(currentSection)) {
            loadTicketBookings();
        }
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString(EXTRA_SECTION, currentSection);
        super.onSaveInstanceState(out);
    }

    private void openChild(Class<?> destination) {
        if (openingChild || destroyed) return;
        openingChild = true;
        startActivity(new Intent(this, destination));
    }

    void showSection(String section) {
        if (!SECTION_PROFILE.equals(section) && !SECTION_ABOUT.equals(section)) section = SECTION_TICKETS;
        ticketGeneration++;
        currentSection = section;
        getIntent().putExtra(EXTRA_SECTION, currentSection);
        ticketCards.setVisibility(View.GONE);
        ticketStatus.setVisibility(View.GONE);
        ticketProgress.setVisibility(View.GONE);
        if (SECTION_PROFILE.equals(section)) {
            heading.setText(R.string.app_section_profile);
            intro.setText(R.string.profile_intro);
            details.setText(R.string.profile_details);
            details.setVisibility(View.VISIBLE);
            ticketActions.setVisibility(View.GONE);
        } else if (SECTION_ABOUT.equals(section)) {
            heading.setText(R.string.app_section_about);
            intro.setText(R.string.about_intro);
            details.setText(R.string.about_details);
            details.setVisibility(View.VISIBLE);
            ticketActions.setVisibility(View.GONE);
        } else {
            heading.setText(R.string.app_section_tickets);
            intro.setText(R.string.tickets_intro);
            details.setVisibility(View.GONE);
            ticketActions.setVisibility(View.VISIBLE);
            loadTicketBookings();
        }
        AppNavigation.bind(this, section);
    }

    private void loadTicketBookings() {
        int generation = ++ticketGeneration;
        ticketProgress.setVisibility(View.VISIBLE);
        ticketStatus.setVisibility(View.VISIBLE);
        ticketStatus.setText(R.string.tickets_loading);
        ticketExecutor.execute(() -> {
            try {
                List<Booking> bookings = bookingService.getVisibleBookings();
                runOnUiThread(() -> {
                    if (destroyed || generation != ticketGeneration || !SECTION_TICKETS.equals(currentSection)) return;
                    ticketProgress.setVisibility(View.GONE);
                    ticketCards.removeAllViews();
                    ticketAdapter.setBookings(bookings);
                    if (bookings.isEmpty()) {
                        ticketCards.setVisibility(View.GONE);
                        ticketStatus.setText(R.string.empty_demo_bookings);
                        ticketStatus.setVisibility(View.VISIBLE);
                        return;
                    }
                    ticketStatus.setText(getString(R.string.tickets_booking_count, bookings.size()));
                    ticketStatus.setVisibility(View.VISIBLE);
                    for (int i = 0; i < ticketAdapter.getCount(); i++) {
                        View card = ticketAdapter.getView(i, null, ticketCards);
                        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        if (i > 0) params.topMargin = (int) (12 * getResources().getDisplayMetrics().density);
                        ticketCards.addView(card, params);
                    }
                    ticketCards.setVisibility(View.VISIBLE);
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    if (destroyed || generation != ticketGeneration || !SECTION_TICKETS.equals(currentSection)) return;
                    ticketProgress.setVisibility(View.GONE);
                    ticketCards.removeAllViews();
                    ticketCards.setVisibility(View.GONE);
                    ticketStatus.setText(R.string.tickets_load_failed);
                    ticketStatus.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void confirmHideTicket(Booking booking) {
        if (hidingTicket || destroyed) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.remove_ticket_title)
                .setMessage(R.string.remove_ticket_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.remove_ticket_from_list, (dialog, which) -> hideTicket(booking))
                .show();
    }

    private void hideTicket(Booking booking) {
        if (hidingTicket || destroyed) return;
        hidingTicket = true;
        ticketExecutor.execute(() -> {
            try {
                bookingService.hideBookingFromList(booking.getId());
                runOnUiThread(() -> {
                    if (destroyed) return;
                    hidingTicket = false;
                    Toast.makeText(this, R.string.ticket_hidden, Toast.LENGTH_SHORT).show();
                    loadTicketBookings();
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    if (destroyed) return;
                    hidingTicket = false;
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.remove_ticket_failed)
                            .setMessage(exception.getMessage() == null
                                    ? getString(R.string.remove_ticket_failed_message) : exception.getMessage())
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                });
            }
        });
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String section = intent.getStringExtra(EXTRA_SECTION);
        showSection(section == null ? SECTION_TICKETS : section);
    }

    @Override protected void onDestroy() {
        destroyed = true;
        ticketExecutor.shutdownNow();
        super.onDestroy();
    }
}
