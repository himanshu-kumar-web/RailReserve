package com.railreserve.model;

import com.railreserve.live.BackendException;
import com.railreserve.live.EnquiryContext;
import java.time.Instant;
import org.junit.Test;
import static org.junit.Assert.*;

public class FareSnapshotTest {
    private FareSnapshot quote(long amount,String retrieved) {
        return new FareSnapshot("15004","GKP","PRYJ","2026-11-15","SL","GN",amount,"RailCore fare estimate",retrieved,
                "GNWL107/WL62","WAITLIST","RailCore availability",retrieved);
    }
    @Test public void capturesActualEstimateAndWaitlistWithoutMakingRailwayReservation() {
        FareSnapshot snapshot = quote(24500,Instant.now().toString());
        snapshot.requireContext("15004","GKP","PRYJ","2026-11-15","SL","GN"); snapshot.requireFresh();
        assertEquals(245,snapshot.getFareRupees()); assertEquals(24500,snapshot.getAmountPaise());
        assertEquals("GNWL107/WL62",snapshot.getAvailability()); assertEquals("WAITLIST",snapshot.getStatusType());
    }
    @Test public void rejectsFractionalNegativeAndOverflowAmountsWithoutRounding() {
        for (long value : new long[]{24501,-100,214748364800L}) assertThrows(IllegalArgumentException.class,()->quote(value,Instant.now().toString()));
    }
    @Test public void expiredQuoteCannotStartCheckoutButRemainsReadableForReceipts() {
        FareSnapshot snapshot = quote(24500,Instant.now().minusSeconds(120).toString());
        assertThrows(IllegalArgumentException.class,snapshot::requireFresh);
        snapshot.validate(); assertEquals(24500,snapshot.getAmountPaise());
    }
    @Test public void mismatchedRequestIdentityIsRejected() {
        FareSnapshot snapshot=quote(24500,Instant.now().toString());
        assertThrows(IllegalArgumentException.class,()->snapshot.requireContext("15005","GKP","PRYJ","2026-11-15","SL","GN"));
        assertThrows(IllegalArgumentException.class,()->snapshot.requireContext("15004","GKP","PRYJ","2026-11-15","3A","GN"));
        assertThrows(IllegalArgumentException.class,()->snapshot.requireContext("15004","GKP","PRYJ","2026-11-16","SL","GN"));
    }
    @Test public void backendContextMustIdentifyTrainRouteDateClassAndQuota() throws Exception {
        EnquiryContext context=new EnquiryContext("15004","GKP","PRYJ","2026-11-15","SL","GN");
        context.requireMatch("15004","GKP","PRYJ","2026-11-15","SL","GN");
        assertThrows(BackendException.class,()->context.requireMatch("15004","GKP","PRYJ","2026-11-15","SL","TQ"));
        assertThrows(BackendException.class,()->new EnquiryContext(null,null,null,null,null,null).requireMatch("15004","GKP","PRYJ","2026-11-15","SL","GN"));
    }
}
