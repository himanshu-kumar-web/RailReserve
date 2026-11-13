package com.railreserve.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import com.railreserve.model.Booking;
import com.railreserve.model.BookingRequest;
import com.railreserve.model.TravelClass;
import com.railreserve.service.BookingService;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Retains a single checkout across rotation; saved draft/reference allow database recovery after process recreation. */
public final class BookingSubmissionViewModel extends ViewModel {
    public enum Stage { REVIEW, PROCESSING, COMMITTING, FAILED, CANCELLED, COMPLETE, RECOVERING }
    public enum Simulation { SUCCESS, FAILURE, CANCEL }

    public static final class State {
        private final Stage stage;
        private final Booking booking;
        private final String error;
        private State(Stage stage, Booking booking, String error) {
            this.stage = stage; this.booking = booking; this.error = error;
        }
        public Stage getStage() { return stage; }
        public boolean isSaving() { return stage == Stage.PROCESSING || stage == Stage.COMMITTING || stage == Stage.RECOVERING; }
        public Booking getBooking() { return booking; }
        public String getError() { return error; }
    }

    private final MutableLiveData<State> state = new MutableLiveData<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SavedStateHandle saved;
    private BookingRequest draft;
    private int attempt;
    private boolean recoveryStarted;
    private boolean cleared;

    public BookingSubmissionViewModel() { this(new SavedStateHandle()); }
    public BookingSubmissionViewModel(SavedStateHandle saved) {
        this.saved = saved;
        Bundle bundle = saved.get("demoCheckoutDraft");
        if (bundle != null) {
            try {
                String reference = bundle.getString("reference");
                if (reference == null || !reference.matches("DEMO-CHK-[a-fA-F0-9-]{36}")
                        || bundle.getLong("journey") <= 0 || bundle.getLong("seat") <= 0
                        || !bundle.containsKey("amount") || bundle.getLong("amount") < 0) {
                    throw new IllegalArgumentException("Invalid saved checkout");
                }
                draft = new BookingRequest(bundle.getLong("journey"), bundle.getLong("seat"),
                        TravelClass.fromCode(bundle.getString("class")), bundle.getString("name"),
                        bundle.getInt("age"), bundle.getString("gender"), bundle.getString("reference"), bundle.getLong("amount"), (com.railreserve.model.FareSnapshot) bundle.getSerializable("fareSnapshot"));
                state.setValue(new State(Stage.RECOVERING, null, null));
            } catch (RuntimeException exception) { draft = null; saved.remove("demoCheckoutDraft"); saved.remove("demoCheckoutSummary"); }
        }
    }

    public LiveData<State> getState() { return state; }
    public String getCheckoutReference() { return draft == null ? null : draft.getCheckoutReference(); }
    public long getAmountPaise() { return draft == null ? 0 : draft.getExpectedAmountPaise(); }
    public String getReviewSummary() { String summary = saved.get("demoCheckoutSummary"); return summary == null ? "" : summary; }

    public void review(BookingRequest request, long amountPaise, String summary) {
        State current = state.getValue();
        if (current != null && (current.isSaving() || current.getBooking() != null)) return;
        if (amountPaise < 0) throw new IllegalArgumentException("Invalid demo fare.");
        if (request.getFareSnapshot() != null) {
            request.getFareSnapshot().requireFresh();
            if (amountPaise != request.getFareSnapshot().getAmountPaise() || request.getTravelClass() == null
                    || !request.getTravelClass().getCode().equals(request.getFareSnapshot().getClassCode()))
                throw new IllegalArgumentException("Review the matching class and captured provider fare before payment.");
        }
        // Once reviewed, this immutable amount belongs to the local checkout, including retries/recovery.
        draft = new BookingRequest(request.getJourneyId(), request.getSeatId(), request.getTravelClass(),
                request.getPassengerName(), request.getAge(), request.getGender(), "DEMO-CHK-" + UUID.randomUUID(), amountPaise, request.getFareSnapshot());
        Bundle bundle = new Bundle();
        bundle.putLong("journey", draft.getJourneyId()); bundle.putLong("seat", draft.getSeatId());
        bundle.putString("class", draft.getTravelClass().getCode()); bundle.putString("name", draft.getPassengerName());
        bundle.putInt("age", draft.getAge()); bundle.putString("gender", draft.getGender());
        bundle.putString("reference", draft.getCheckoutReference()); bundle.putLong("amount", amountPaise);
        bundle.putSerializable("fareSnapshot", draft.getFareSnapshot());
        saved.set("demoCheckoutDraft", bundle); saved.set("demoCheckoutSummary", summary);
        state.setValue(new State(Stage.REVIEW, null, null));
    }

    public void pay(BookingService service, Simulation simulation) {
        State current = state.getValue();
        if (draft == null || current == null || (current.getStage() != Stage.REVIEW
                && current.getStage() != Stage.FAILED && current.getStage() != Stage.CANCELLED)) return;
        int version = ++attempt;
        BookingRequest request = draft;
        state.setValue(new State(Stage.PROCESSING, null, null));
        executor.execute(() -> {
            try { Thread.sleep(1200); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); return; }
            main.post(() -> {
                if (cleared || attempt != version) return;
                if (simulation == Simulation.FAILURE) {
                    state.setValue(new State(Stage.FAILED, null, "Demo payment failed. No booking was created and no money moved. You can retry."));
                } else if (simulation == Simulation.CANCEL) {
                    state.setValue(new State(Stage.CANCELLED, null, "Demo payment cancelled. No booking was created and no money moved."));
                } else {
                    commit(service, request);
                }
            });
        });
    }

    public void cancelPayment() {
        State current = state.getValue();
        if (current != null && current.getStage() == Stage.PROCESSING) {
            attempt++;
            state.setValue(new State(Stage.CANCELLED, null, "Demo payment cancelled. No booking was created and no money moved."));
        }
    }

    // Keeps the existing submission API for legacy callers; the passenger UI uses review() then pay().
    public void submit(BookingService service, BookingRequest request) {
        State current = state.getValue();
        if (current != null && (current.isSaving() || current.getBooking() != null)) return;
        commit(service, request);
    }

    private void commit(BookingService service, BookingRequest request) {
        state.setValue(new State(Stage.COMMITTING, null, null));
        executor.execute(() -> {
            try {
                Booking booking = service.createBooking(request);
                if (booking == null || (request.getCheckoutReference() != null && booking.getPayment() == null)) {
                    throw new IllegalStateException("The checkout receipt could not be verified. Check saved bookings before retrying.");
                }
                main.post(() -> { if (!cleared) state.setValue(new State(Stage.COMPLETE, booking, null)); });
            } catch (Exception exception) {
                String message = exception instanceof IllegalStateException || exception instanceof IllegalArgumentException
                        ? exception.getMessage() : "The reservation could not be saved. No payment was completed. Return to details or retry.";
                main.post(() -> { if (!cleared) state.setValue(new State(Stage.FAILED, null, message)); });
            }
        });
    }

    public void recover(BookingService service) {
        State current = state.getValue();
        if (draft == null || current == null || current.getStage() != Stage.RECOVERING || recoveryStarted) return;
        recoveryStarted = true;
        String reference = draft.getCheckoutReference();
        executor.execute(() -> {
            try {
                Booking booking = service.findBookingByCheckoutReference(reference);
                main.post(() -> {
                    if (!cleared) state.setValue(booking == null
                            ? new State(Stage.REVIEW, null, "No completed checkout was found. Review and retry the demo payment.")
                            : new State(Stage.COMPLETE, booking, null));
                });
            } catch (Exception exception) {
                main.post(() -> { if (!cleared) state.setValue(new State(Stage.FAILED, null,
                        "Could not recover this checkout. Retrying uses the same reference to prevent duplicate bookings.")); });
            }
        });
    }

    public void editReview() {
        State current = state.getValue();
        if (current != null && (current.isSaving() || current.getBooking() != null)) return;
        attempt++;
        draft = null;
        saved.remove("demoCheckoutDraft"); saved.remove("demoCheckoutSummary");
        state.setValue(null);
    }

    public void clearFailure() { editReview(); }

    @Override protected void onCleared() {
        cleared = true;
        attempt++;
        main.removeCallbacksAndMessages(null);
        executor.shutdown();
    }
}
