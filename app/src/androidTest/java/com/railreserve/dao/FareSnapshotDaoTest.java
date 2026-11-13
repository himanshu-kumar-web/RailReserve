package com.railreserve.dao;

import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.railreserve.model.*;
import com.railreserve.service.SimulationScenarioService;
import com.railreserve.testing.DemoPaymentTestDatabase;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class FareSnapshotDaoTest {
    @Test public void populatedVersionFourUpgradePreservesOldSchemasAndReceipts() {
        try(DemoPaymentTestDatabase f=new DemoPaymentTestDatabase()) {
            Booking old=f.service.createBooking(f.checkout(1));
            java.util.Map<String,String> schemas=new java.util.HashMap<>();
            String[] tables={"trains","journeys","seats","bookings","demo_payments","simulation_scenarios"};
            SQLiteDatabase current=f.helper.getReadableDatabase();
            for(String table:tables) schemas.put(table,DatabaseUtils.stringForQuery(current,"SELECT sql FROM sqlite_master WHERE name=?",new String[]{table}));
            f.helper.close();
            // Only the isolated UUID fixture is converted to the exact pre-snapshot schema.
            try(SQLiteDatabase versionFour=SQLiteDatabase.openOrCreateDatabase(f.file,null)) {
                versionFour.execSQL("DROP TABLE booking_fare_snapshots"); versionFour.setVersion(4);
            }
            SQLiteDatabase upgraded=f.helper.getWritableDatabase(); assertEquals(6,upgraded.getVersion());
            for(String table:tables) assertEquals(expanded(schemas.get(table)),expanded(DatabaseUtils.stringForQuery(upgraded,"SELECT sql FROM sqlite_master WHERE name=?",new String[]{table})));
            assertEquals(0,DatabaseUtils.queryNumEntries(upgraded,"booking_fare_snapshots"));
            Booking preserved=f.service.findBookingByPnr(old.getPnr()); assertEquals(old.getId(),preserved.getId()); assertEquals(10000,preserved.getPayment().getAmountPaise());
            assertNull(preserved.getFareSnapshot()); f.service.cancelBooking(preserved.getId());
            assertEquals("SIMULATED_REFUNDED",f.service.findBookingByPnr(old.getPnr()).getPayment().getRefundStatus());
        }
    }
    private FareSnapshot quote(int fare) { return new FareSnapshot("15004","GKP","PRYJ",LocalDate.now().plusDays(1).toString(),"SL","GN",fare*100L,
            "RailCore fare estimate",Instant.now().toString(),"GNWL107/WL62","WAITLIST","RailCore availability",Instant.now().toString()); }
    private TrainSearchResult scenario(DemoPaymentTestDatabase f,FareSnapshot quote) {
        return new SimulationScenarioService(new SQLiteSimulationScenarioDao(f.helper)).prepare(new SimulationRequest("15004","TEST FIXTURE Chaurichaura","GKP","Gorakhpur","PRYJ","Prayagraj",
                quote.getDate(),TravelClass.SLEEPER,"20:00","08:00",0,1,quote));
    }
    private BookingRequest request(DemoPaymentTestDatabase f,TrainSearchResult scenario,FareSnapshot quote) {
        return new BookingRequest(scenario.getJourneyId(),f.service.getAvailableSeats(scenario.getJourneyId(),"SL").get(0).getId(),TravelClass.SLEEPER,"Quote Passenger",25,"Other","DEMO-CHK-"+UUID.randomUUID(),quote.getAmountPaise(),quote);
    }
    @Test public void quotePersistsAcrossReopenPnrHistoryCancellationAndDuplicateCheckout() {
        try(DemoPaymentTestDatabase f=new DemoPaymentTestDatabase()) {
            FareSnapshot quote=quote(245); TrainSearchResult scenario=scenario(f,quote); BookingRequest request=request(f,scenario,quote);
            Booking booking=f.service.createBooking(request); assertEquals(245,booking.getFare()); assertEquals(24500,booking.getPayment().getAmountPaise());
            assertEquals("GNWL107/WL62",booking.getFareSnapshot().getAvailability()); assertEquals("CONFIRMED",booking.getStatus());
            assertEquals(booking.getId(),f.service.createBooking(request).getId());
            FareSnapshot different=quote(260);
            assertThrows(IllegalArgumentException.class,()->f.service.createBooking(new BookingRequest(request.getJourneyId(),request.getSeatId(),TravelClass.SLEEPER,
                    "Quote Passenger",25,"Other",request.getCheckoutReference(),different.getAmountPaise(),different)));
            f.helper.close(); Booking reopened=f.service.findBookingByPnr(booking.getPnr());
            assertEquals(quote.getRetrievedAt(),reopened.getFareSnapshot().getRetrievedAt()); assertEquals(245,reopened.getFare());
            assertTrue(f.service.getBookings().stream().anyMatch(b->b.getId()==booking.getId()&&b.getFareSnapshot()!=null));
            f.service.cancelBooking(booking.getId()); Booking cancelled=f.service.findBookingByPnr(booking.getPnr());
            assertEquals("SIMULATED_REFUNDED",cancelled.getPayment().getRefundStatus()); assertEquals(24500,cancelled.getPayment().getAmountPaise());
            assertEquals("GNWL107/WL62",cancelled.getFareSnapshot().getAvailability()); assertEquals(6,f.service.getAvailableSeats(scenario.getJourneyId(),"SL").size());
        }
    }
    @Test public void laterQuoteDoesNotChangeOldBookingOrRefillScenario() {
        try(DemoPaymentTestDatabase f=new DemoPaymentTestDatabase()) {
            FareSnapshot first=quote(245); TrainSearchResult one=scenario(f,first); Booking old=f.service.createBooking(request(f,one,first));
            FareSnapshot next=quote(260); TrainSearchResult two=scenario(f,next); assertEquals(one.getJourneyId(),two.getJourneyId()); assertEquals(5,two.getAvailableSeats());
            Booking newer=f.service.createBooking(request(f,two,next)); assertEquals(260,newer.getFare()); assertEquals(245,f.service.findBookingByPnr(old.getPnr()).getFare());
            assertEquals(4,f.service.getAvailableSeats(one.getJourneyId(),"SL").size());
        }
    }
    @Test public void snapshotInsertionFailureRollsBackSeatBookingAndPayment() {
        try(DemoPaymentTestDatabase f=new DemoPaymentTestDatabase()) {
            FareSnapshot quote=quote(245); TrainSearchResult scenario=scenario(f,quote); SQLiteDatabase db=f.helper.getWritableDatabase();
            db.execSQL("CREATE TRIGGER fail_quote BEFORE INSERT ON booking_fare_snapshots BEGIN SELECT RAISE(ABORT,'fixture'); END");
            assertThrows(RuntimeException.class,()->f.service.createBooking(request(f,scenario,quote)));
            assertEquals(0,DatabaseUtils.queryNumEntries(db,"bookings")); assertEquals(0,DatabaseUtils.queryNumEntries(db,"demo_payments"));
            assertEquals(6,f.service.getAvailableSeats(scenario.getJourneyId(),"SL").size());
        }
    }
    @Test public void wrongQuoteContextOrReviewAmountCannotAllocateSeat() {
        try(DemoPaymentTestDatabase f=new DemoPaymentTestDatabase()) {
            FareSnapshot quote=quote(245); TrainSearchResult scenario=scenario(f,quote); BookingRequest valid=request(f,scenario,quote);
            FareSnapshot wrong=new FareSnapshot("15005","GKP","PRYJ",quote.getDate(),"SL","GN",24500,"RailCore fare estimate",quote.getRetrievedAt(),null,null,null,null);
            assertThrows(IllegalArgumentException.class,()->f.service.createBooking(new BookingRequest(valid.getJourneyId(),valid.getSeatId(),TravelClass.SLEEPER,"Passenger",25,"Other",valid.getCheckoutReference(),24500L,wrong)));
            assertThrows(IllegalArgumentException.class,()->f.service.createBooking(new BookingRequest(valid.getJourneyId(),valid.getSeatId(),TravelClass.SLEEPER,"Passenger",25,"Other",valid.getCheckoutReference(),10000L,quote)));
            assertEquals(6,f.service.getAvailableSeats(scenario.getJourneyId(),"SL").size());
        }
    }
    @Test public void immutableSnapshotAndOriginalSeededFaresRemainIntact() {
        try(DemoPaymentTestDatabase f=new DemoPaymentTestDatabase()) {
            FareSnapshot quote=quote(245); TrainSearchResult scenario=scenario(f,quote); Booking booking=f.service.createBooking(request(f,scenario,quote));
            assertThrows(RuntimeException.class,()->f.helper.getWritableDatabase().execSQL("UPDATE booking_fare_snapshots SET amount_paise=10000 WHERE booking_id=?",new Object[]{booking.getId()}));
            assertEquals(100,f.service.createBooking(f.checkout(1)).getFare());
            assertNull(f.service.findBookingByPnr(f.service.getBookings().stream().filter(b->b.getJourneyId()==1).findFirst().get().getPnr()).getFareSnapshot());
        }
    }
    private String expanded(String sql) { return sql.replace("\"", "").replace("travel_class IN ('SL','3A')", "travel_class IN ('SL','3A','2A','1A','3E','CC','EC','2S')"); }

}
