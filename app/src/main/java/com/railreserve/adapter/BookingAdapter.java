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
import com.railreserve.ui.PaymentReceiptText;
import com.railreserve.ui.TicketUi;
import java.util.ArrayList;
import java.util.List;

public final class BookingAdapter extends BaseAdapter {
    public interface CancelListener {
        void onCancel(Booking booking);
    }

    public interface RemoveListener {
        void onRemove(Booking booking);
    }

    public interface WaitingListListener {
        void onConfirmWaitingList(Booking booking);
    }

    private final LayoutInflater inflater;
    private final List<Booking> bookings = new ArrayList<>();
    private final CancelListener listener;
    private final RemoveListener removeListener;
    private final WaitingListListener waitingListListener;
    private boolean cancellationInProgress;
    private boolean confirmationInProgress;
    private final java.util.Set<Long> expandedReceipts = new java.util.HashSet<>();

    public BookingAdapter(Context context, CancelListener listener) {
        this(context, listener, null, null);
    }

    public BookingAdapter(Context context, CancelListener listener, RemoveListener removeListener) {
        this(context, listener, removeListener, null);
    }

    public BookingAdapter(Context context, CancelListener listener, RemoveListener removeListener,
                          WaitingListListener waitingListListener) {
        inflater = LayoutInflater.from(context);
        this.listener = listener;
        this.removeListener = removeListener;
        this.waitingListListener = waitingListListener;
    }

    public void setBookings(List<Booking> values) {
        bookings.clear();
        bookings.addAll(values);
        notifyDataSetChanged();
    }

    public void setCancellationInProgress(boolean inProgress) {
        cancellationInProgress = inProgress;
        notifyDataSetChanged();
    }

    public void setConfirmationInProgress(boolean inProgress) {
        confirmationInProgress = inProgress;
        notifyDataSetChanged();
    }

    @Override public int getCount() { return bookings.size(); }
    @Override public Booking getItem(int position) { return bookings.get(position); }
    @Override public long getItemId(int position) { return getItem(position).getId(); }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView == null
                ? inflater.inflate(R.layout.item_booking, parent, false)
                : convertView;
        Booking booking = getItem(position);
        ((TextView) view.findViewById(R.id.bookingPayment)).setText(PaymentReceiptText.receipt(booking));
        boolean confirmed = "CONFIRMED".equals(booking.getStatus());

        ((TextView) view.findViewById(R.id.bookingPnr)).setText("DEMO PNR\n" + booking.getPnr());
        TicketUi.bindCopy(view,booking.getPnr());
        boolean expanded = expandedReceipts.contains(booking.getId());
        view.findViewById(R.id.bookingPayment).setVisibility(expanded ? View.VISIBLE : View.GONE);
        Button receipt = view.findViewById(R.id.bookingReceiptToggle);
        receipt.setText(expanded ? R.string.hide_receipt : R.string.show_receipt);
        receipt.setOnClickListener(clicked -> {
            if (!expandedReceipts.remove(booking.getId())) expandedReceipts.add(booking.getId());
            notifyDataSetChanged();
        });
        ((TextView) view.findViewById(R.id.bookingTrain)).setText(
                "Train " + booking.getTrainNumber() + "  |  " + booking.getTrainName());
        ((TextView) view.findViewById(R.id.bookingRoute)).setText(
                booking.getSource() + "  ->  " + booking.getDestination());
        ((TextView) view.findViewById(R.id.bookingTimes)).setText(
                booking.getJourneyDate() + "  |  " + booking.getDeparture() + " - "
                        + booking.getArrival() + (booking.getArrivalDayOffset() > 0 ? " (+1 day)" : ""));
        ((TextView) view.findViewById(R.id.bookingPassenger)).setText(
                "Passenger  |  " + booking.getPassengerName() + ", age " + booking.getAge());
        String classFare = booking.getTravelClass().getLabel();
        if ("WAITING_LIST".equals(booking.getStatus())) {
            classFare += "  |  WL " + booking.getWaitlistNumber() + "  |  No seat allocated while waitlisted  |  Local demo";
        } else if (booking.getFareSnapshot() == null) {
            classFare += "  |  " + booking.getCoach() + " Seat " + booking.getSeatNumber()
                    + "  |  Local demo fare ";
        } else {
            classFare += "  |  Local demo seat: " + booking.getCoach() + " " + booking.getSeatNumber()
                    + "  |  Live status: " + booking.getFareSnapshot().getStatusType()
                    + " (" + booking.getFareSnapshot().getAvailability() + ")  |  No railway seat reserved  |  Captured estimate ";
        }
        ((TextView) view.findViewById(R.id.bookingClassFare)).setText(
                classFare + PaymentReceiptText.amount(booking.getFare()*100L));

        TextView status = view.findViewById(R.id.bookingStatus);
        boolean waitingList = "WAITING_LIST".equals(booking.getStatus());
        status.setText("Local demo: " + booking.getStatus()
                + (waitingList ? " · WL " + booking.getWaitlistNumber() : ""));
        status.setBackgroundResource(confirmed ? R.drawable.status_confirmed
                : waitingList ? R.drawable.status_waiting_list : R.drawable.status_cancelled);
        status.setTextColor(confirmed ? 0xff20624e : waitingList ? 0xff9a5c00 : 0xff9a3e31);

        Button cancelButton = view.findViewById(R.id.cancelBookingButton);
        cancelButton.setVisibility((confirmed || waitingList) && listener != null ? View.VISIBLE : View.GONE);
        cancelButton.setEnabled((confirmed || waitingList) && listener != null && !cancellationInProgress);
        cancelButton.setText(cancellationInProgress ? "Cancelling..." : "Cancel booking");
        cancelButton.setOnClickListener(listener == null ? null : clicked -> listener.onCancel(booking));
        Button confirmWaitingList = view.findViewById(R.id.confirmWaitingListButton);
        confirmWaitingList.setVisibility(waitingList && waitingListListener != null ? View.VISIBLE : View.GONE);
        confirmWaitingList.setEnabled(waitingList && !confirmationInProgress);
        confirmWaitingList.setText(confirmationInProgress ? "Confirming locally..." : "Simulate local confirmation");
        confirmWaitingList.setOnClickListener(waitingListListener == null ? null
                : clicked -> waitingListListener.onConfirmWaitingList(booking));
        Button removeButton = view.findViewById(R.id.removeBookingButton);
        removeButton.setVisibility(removeListener == null ? View.GONE : View.VISIBLE);
        removeButton.setOnClickListener(removeListener == null ? null : clicked -> removeListener.onRemove(booking));
        return view;
    }
}
