package com.railreserve.dao;

import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.railreserve.model.*;
import com.railreserve.service.SimulationScenarioService;
import com.railreserve.testing.DemoPaymentTestDatabase;
import com.railreserve.testing.VersionFiveFixture;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ExpandedClassBookingTest {
    private FareSnapshot quote(String number,String from,String to,String date,TravelClass clazz,int fare) {
        return new FareSnapshot(number,from,to,date,clazz.getCode(),"GN",fare*100L,"RailCore fare estimate",Instant.now().toString(),null,null,null,null);
    }
    private TrainSearchResult prepare(DemoPaymentTestDatabase f,FareSnapshot q) {
        return new SimulationScenarioService(new SQLiteSimulationScenarioDao(f.helper)).prepare(new SimulationRequest(q.getNumber(),"TEST FIXTURE Expanded Classes",q.getFrom(),q.getFrom(),q.getTo(),q.getTo(),q.getDate(),TravelClass.fromCode(q.getClassCode()),"22:00","06:00",0,1,q));
    }
    private BookingRequest checkout(DemoPaymentTestDatabase f,TrainSearchResult scenario,FareSnapshot q) {
        return new BookingRequest(scenario.getJourneyId(),f.service.getAvailableSeats(scenario.getJourneyId(),q.getClassCode()).get(0).getId(),scenario.getTravelClass(),"Class Passenger",25,"Other","DEMO-CHK-"+UUID.randomUUID(),q.getAmountPaise(),q);
    }
    @Test public void everyRecognizedClassBooksAtItsQuoteAndRefundSurvivesReopen() {
        try(DemoPaymentTestDatabase f=new DemoPaymentTestDatabase()) {
            Set<Long> journeys=new HashSet<>(); Set<String> pnrs=new HashSet<>(); int fare=245;
            for(TravelClass clazz:TravelClass.values()) {
                FareSnapshot q=quote("15004","GKP","PRYJ",LocalDate.now().plusDays(1).toString(),clazz,fare++);
                TrainSearchResult scenario=prepare(f,q); assertTrue(journeys.add(scenario.getJourneyId())); assertEquals(6,scenario.getAvailableSeats());
                BookingRequest request=checkout(f,scenario,q); Booking booked=f.service.createBooking(request);
                assertEquals(clazz,booked.getTravelClass()); assertEquals(q.getFareRupees(),booked.getFare());
                assertEquals(q.getAmountPaise(),booked.getPayment().getAmountPaise()); assertTrue(pnrs.add(booked.getPnr()));
                assertEquals(booked.getId(),f.service.createBooking(request).getId());
                assertEquals(5,prepare(f,q).getAvailableSeats());
                f.helper.close(); Booking reopened=f.service.findBookingByPnr(booked.getPnr());
                assertEquals(clazz,reopened.getTravelClass()); assertEquals(q.getRetrievedAt(),reopened.getFareSnapshot().getRetrievedAt());
                f.service.cancelBooking(booked.getId()); Booking cancelled=f.service.findBookingByPnr(booked.getPnr());
                assertEquals("SIMULATED_REFUNDED",cancelled.getPayment().getRefundStatus());
                assertEquals(6,f.service.getAvailableSeats(scenario.getJourneyId(),clazz.getCode()).size());
                assertEquals(q.getAmountPaise(),cancelled.getPayment().getAmountPaise());
            }
            assertEquals(8,pnrs.size());
        }
    }
    @Test public void expandedClassesRemainIsolatedByRouteDateAndDirection() {
        try(DemoPaymentTestDatabase f=new DemoPaymentTestDatabase()) {
            String date=LocalDate.now().plusDays(1).toString(); Set<Long> pools=new HashSet<>();
            FareSnapshot[] quotes={quote("15004","GKP","PRYJ",date,TravelClass.FIRST_AC,1200),
                    quote("15004","GKP","PRYJ",date,TravelClass.AC_TWO_TIER,900),
                    quote("15004","GKP","PRYJ",LocalDate.now().plusDays(2).toString(),TravelClass.FIRST_AC,1200),
                    quote("15004","PRYJ","GKP",date,TravelClass.FIRST_AC,1200),quote("15004","GKP","NDLS",date,TravelClass.FIRST_AC,1200)};
            for(FareSnapshot q:quotes) { TrainSearchResult scenario=prepare(f,q); assertTrue(pools.add(scenario.getJourneyId())); assertEquals(6,scenario.getAvailableSeats()); }
            assertEquals(5,pools.size());
        }
    }
    @Test public void versionFiveMigrationPreservesAllRecordsIndexesTriggersViewsAndSequence() {
        try(DemoPaymentTestDatabase f=new DemoPaymentTestDatabase(true)) {
            String[] tables={"trains","journeys","seats","bookings","demo_payments","simulation_scenarios","booking_fare_snapshots"};
            Map<String,List<String>> rows=new HashMap<>();
            try(SQLiteDatabase old=SQLiteDatabase.openOrCreateDatabase(f.file,null)) {
                VersionFiveFixture.create(old);
                old.execSQL("INSERT INTO trains VALUES(1,'SIM-old','TEST FIXTURE','GKP','PRYJ','22:00','06:00',1)");
                old.execSQL("INSERT INTO journeys VALUES(1,1,'2026-11-15')");
                old.execSQL("INSERT INTO seats VALUES(1,1,'SL','DEMO-S1',1,245,'BOOKED')");
                old.execSQL("INSERT INTO seats VALUES(2,1,'SL','DEMO-S1',2,245,'AVAILABLE')");
                old.execSQL("INSERT INTO simulation_scenarios VALUES(1,'SELECTED_LIVE_V1','15004','GKP','PRYJ','2026-11-15','SL',1)");
                old.execSQL("INSERT INTO bookings VALUES(101,'1234567890',1,'Saved Passenger',25,'Other','SL',245,'CONFIRMED','2026-11-13T00:00:00Z',NULL)");
                old.execSQL("INSERT INTO bookings VALUES(102,'2234567890',2,'Cancelled Passenger',25,'Other','SL',245,'CANCELLED','2026-11-13T00:00:00Z','2026-11-13T01:00:00Z')");
                old.execSQL("INSERT INTO demo_payments(transaction_reference,checkout_reference,booking_id,payment_method,amount_paise,payment_status,created_at) VALUES('DEMO-TXN-old','DEMO-CHK-12345678-1234-1234-1234-123456789abc',101,'DEMO_UPI',24500,'SUCCESS','2026-11-13T00:00:00Z')");
                old.execSQL("INSERT INTO booking_fare_snapshots(booking_id,train_number,source_code,destination_code,journey_date,class_code,quota_code,amount_paise,provider_source,retrieved_at) VALUES(101,'15004','GKP','PRYJ','2026-11-15','SL','GN',24500,'RailCore fare estimate','2026-11-13T00:00:00Z')");
                old.execSQL("UPDATE sqlite_sequence SET seq=1000 WHERE name='bookings'");
                old.execSQL("CREATE INDEX fixture_fare_index ON seats(fare)");
                old.execSQL("CREATE VIEW fixture_confirmed AS SELECT booking_id FROM bookings WHERE booking_status='CONFIRMED'");
                for(String table:tables) rows.put(table,rows(old,table));
            }
            SQLiteDatabase upgraded=f.helper.getWritableDatabase(); assertEquals(6,upgraded.getVersion());
            for(String table:tables) assertEquals(table,rows.get(table),rows(upgraded,table));
            assertEquals(1,DatabaseUtils.longForQuery(upgraded,"PRAGMA foreign_keys",null));
            try(Cursor c=upgraded.rawQuery("PRAGMA foreign_key_check",null)) { assertFalse(c.moveToFirst()); }
            assertEquals(1,DatabaseUtils.longForQuery(upgraded,"SELECT COUNT(*) FROM fixture_confirmed",null));
            assertEquals(1,DatabaseUtils.longForQuery(upgraded,"SELECT COUNT(*) FROM sqlite_master WHERE name='fixture_fare_index'",null));
            assertThrows(RuntimeException.class,()->upgraded.execSQL("UPDATE booking_fare_snapshots SET amount_paise=10000 WHERE booking_id=101"));
            assertThrows(RuntimeException.class,()->upgraded.execSQL("INSERT INTO bookings(pnr,seat_id,passenger_name,passenger_age,passenger_gender,travel_class,fare,booking_status,booked_at) VALUES('3234567890',999,'Invalid FK',25,'Other','2A',100,'CONFIRMED','2026-11-13T00:00:00Z')"));
            assertThrows(RuntimeException.class,()->upgraded.execSQL("UPDATE seats SET travel_class='EV' WHERE seat_id=2"));
            Booking next=f.service.createBooking(new BookingRequest(1,2,TravelClass.SLEEPER,"New Passenger",25,"Other"));
            assertTrue(next.getId()>1000); f.service.cancelBooking(101);
            assertEquals("SIMULATED_REFUNDED",f.service.findBookingByPnr("1234567890").getPayment().getRefundStatus());
            assertEquals(24500,f.service.findBookingByPnr("1234567890").getFareSnapshot().getAmountPaise());
        }
    }
    private List<String> rows(SQLiteDatabase db,String table) {
        List<String> result=new ArrayList<>();
        try(Cursor c=db.rawQuery("SELECT * FROM "+table+" ORDER BY 1",null)) {
            while(c.moveToNext()) { StringBuilder row=new StringBuilder(); for(int i=0;i<c.getColumnCount();i++) row.append(c.isNull(i)?"<null>":c.getString(i)).append('|'); result.add(row.toString()); }
        }
        return result;
    }

    @Test public void failedForeignKeyValidationRollsBackTheWholeUpgrade() {
        try(DemoPaymentTestDatabase f=new DemoPaymentTestDatabase(true)) {
            try(SQLiteDatabase old=SQLiteDatabase.openOrCreateDatabase(f.file,null)) {
                VersionFiveFixture.create(old);
                // A deliberately corrupt historical reference must cause rollback, never partial migration.
                old.execSQL("INSERT INTO journeys VALUES(1,999,'2026-11-15')");
            }
            assertThrows(RuntimeException.class,()->f.helper.getWritableDatabase());
            f.helper.close();
            try(SQLiteDatabase old=SQLiteDatabase.openOrCreateDatabase(f.file,null)) {
                assertEquals(5,old.getVersion());
                assertEquals(1,DatabaseUtils.queryNumEntries(old,"journeys"));
                assertEquals(0,DatabaseUtils.longForQuery(old,"SELECT COUNT(*) FROM sqlite_master WHERE name LIKE 'class_upgrade_%'",null));
                assertTrue(DatabaseUtils.stringForQuery(old,"SELECT sql FROM sqlite_master WHERE name='seats'",null).contains("travel_class IN ('SL','3A')"));
            }
        }
    }
}
