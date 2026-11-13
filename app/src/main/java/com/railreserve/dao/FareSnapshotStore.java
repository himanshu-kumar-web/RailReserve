package com.railreserve.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.railreserve.model.FareSnapshot;

/** Uses the caller's booking transaction; never starts an independent commit. */
final class FareSnapshotStore {
    private FareSnapshotStore() { }
    static void insert(SQLiteDatabase db, long bookingId, FareSnapshot quote) {
        quote.validate();
        ContentValues values = new ContentValues();
        values.put("booking_id", bookingId); values.put("train_number", quote.getNumber());
        values.put("source_code", quote.getFrom()); values.put("destination_code", quote.getTo());
        values.put("journey_date", quote.getDate()); values.put("class_code", quote.getClassCode());
        values.put("quota_code", quote.getQuota()); values.put("amount_paise", quote.getAmountPaise());
        values.put("provider_source", quote.getSource()); values.put("retrieved_at", quote.getRetrievedAt());
        values.put("availability_text", quote.getAvailability()); values.put("status_type", quote.getStatusType());
        values.put("availability_source", quote.getAvailabilitySource()); values.put("availability_retrieved_at", quote.getAvailabilityRetrievedAt());
        db.insertOrThrow("booking_fare_snapshots", null, values);
    }
    static FareSnapshot read(SQLiteDatabase db, long bookingId) {
        try (Cursor c = db.rawQuery("SELECT train_number,source_code,destination_code,journey_date,class_code,quota_code,"
                + "amount_paise,provider_source,retrieved_at,availability_text,status_type,availability_source,availability_retrieved_at "
                + "FROM booking_fare_snapshots WHERE booking_id=?", new String[]{Long.toString(bookingId)})) {
            return !c.moveToFirst() ? null : new FareSnapshot(c.getString(0),c.getString(1),c.getString(2),c.getString(3),
                    c.getString(4),c.getString(5),c.getLong(6),c.getString(7),c.getString(8),c.getString(9),c.getString(10),c.getString(11),c.getString(12));
        }
    }
}
