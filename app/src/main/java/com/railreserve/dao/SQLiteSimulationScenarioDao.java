package com.railreserve.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.railreserve.database.DatabaseHelper;
import com.railreserve.model.SimulationRequest;
import com.railreserve.model.SimulationScenarioPolicy;
import com.railreserve.model.TrainSearchResult;
import java.util.UUID;

/** Creates inventory only. Reservation, payment and cancellation still belong to SQLiteBookingDao. */
public final class SQLiteSimulationScenarioDao implements SimulationScenarioDao {
    private final DatabaseHelper helper;
    public SQLiteSimulationScenarioDao(DatabaseHelper helper) { this.helper = helper; }

    @Override public TrainSearchResult getOrCreate(SimulationRequest request) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            Long trainId = findTrainId(db, request);
            if (trainId == null) trainId = createInventory(db, request);
            TrainSearchResult scenario = read(db, trainId, request);
            db.setTransactionSuccessful();
            return scenario;
        } finally { db.endTransaction(); }
    }

    private Long findTrainId(SQLiteDatabase db, SimulationRequest request) {
        try (Cursor cursor = db.rawQuery("SELECT train_id FROM simulation_scenarios WHERE namespace=? AND display_train_number=? "
                + "AND source_code=? AND destination_code=? AND journey_date=? AND travel_class=?", new String[]{
                SimulationScenarioPolicy.NAMESPACE, request.getNumber(), request.getSourceCode(), request.getDestinationCode(),
                request.getDate(), request.getTravelClass().getCode()})) {
            return cursor.moveToFirst() ? cursor.getLong(0) : null;
        }
    }

    private long createInventory(SQLiteDatabase db, SimulationRequest request) {
        ContentValues train = new ContentValues();
        // This is an internal key, never a displayed railway number. The mapping preserves the selected number.
        train.put("train_number", "SIM-" + UUID.randomUUID());
        train.put("name", request.getName()); train.put("source_station", request.getSourceLabel());
        train.put("destination_station", request.getDestinationLabel());
        train.put("departure_time", request.getDeparture()); train.put("arrival_time", request.getArrival());
        train.put("arrival_day_offset", request.getArrivalDayOffset());
        long trainId = db.insertOrThrow("trains", null, train);
        ContentValues mapping = new ContentValues();
        mapping.put("namespace", SimulationScenarioPolicy.NAMESPACE); mapping.put("display_train_number", request.getNumber());
        mapping.put("source_code", request.getSourceCode()); mapping.put("destination_code", request.getDestinationCode());
        mapping.put("journey_date", request.getDate()); mapping.put("travel_class", request.getTravelClass().getCode());
        mapping.put("train_id", trainId);
        db.insertOrThrow("simulation_scenarios", null, mapping);
        ContentValues journey = new ContentValues();
        journey.put("train_id", trainId); journey.put("journey_date", request.getDate());
        long journeyId = db.insertOrThrow("journeys", null, journey);
        for (int number = 1; number <= SimulationScenarioPolicy.SEAT_COUNT; number++) {
            ContentValues seat = new ContentValues();
            seat.put("journey_id", journeyId); seat.put("travel_class", request.getTravelClass().getCode());
            seat.put("coach", "SL".equals(request.getTravelClass().getCode()) ? "DEMO-S1" : "3A".equals(request.getTravelClass().getCode()) ? "DEMO-B1" : "DEMO-"+request.getTravelClass().getCode()+"1");
            seat.put("seat_number", number); seat.put("fare", request.getFareSnapshot() == null ? SimulationScenarioPolicy.fareRupees(request.getTravelClass()) : request.getFareSnapshot().getFareRupees());
            seat.put("status", "AVAILABLE");
            db.insertOrThrow("seats", null, seat);
        }
        return trainId;
    }

    private TrainSearchResult read(SQLiteDatabase db, long trainId, SimulationRequest request) {
        try (Cursor c = db.rawQuery("SELECT j.journey_id,t.train_id,sc.display_train_number,t.name,t.source_station,"
                + "t.destination_station,t.departure_time,t.arrival_time,t.arrival_day_offset,j.journey_date,s.fare,"
                + "COUNT(s.seat_id),SUM(CASE WHEN s.status='AVAILABLE' THEN 1 ELSE 0 END) FROM simulation_scenarios sc "
                + "JOIN trains t ON t.train_id=sc.train_id JOIN journeys j ON j.train_id=t.train_id "
                + "JOIN seats s ON s.journey_id=j.journey_id WHERE t.train_id=? AND s.travel_class=? "
                + "GROUP BY j.journey_id,s.travel_class,s.fare", new String[]{Long.toString(trainId), request.getTravelClass().getCode()})) {
            if (!c.moveToFirst()) throw new IllegalStateException("Local simulation inventory is missing. Return to live results and retry.");
            return new TrainSearchResult(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),
                    c.getString(6),c.getString(7),c.getInt(8),c.getString(9),request.getTravelClass(),c.getInt(10),c.getInt(11),c.getInt(12));
        }
    }
}
