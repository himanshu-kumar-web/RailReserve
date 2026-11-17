package com.railreserve.live;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.railreserve.BuildConfig;
import com.railreserve.R;
import com.railreserve.adapter.BookingAdapter;
import com.railreserve.model.Booking;
import com.railreserve.testing.DemoPaymentTestDatabase;
import com.railreserve.ui.*;
import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Screenshots use labelled fixture data. No provider calls or device font/settings changes. */
@RunWith(AndroidJUnit4.class)
public class UiRefinementDeviceTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();

    @Test public void fixtureJourneyRemainsOnDemandAndCapturesRefinedScreens() throws Exception {
        assumeTrue(LocalBackendFixture.BASE_URL.equals(new BackendApiClient().getBaseUrl()));
        assumeTrue(BuildConfig.LIVE_FARE_ENABLED && BuildConfig.LIVE_AVAILABILITY_ENABLED);
        List<Activity> opened=new ArrayList<>();
        try(LocalBackendFixture server=new LocalBackendFixture()) {
            server.handler=path->new LocalBackendFixture.Response(200,path.contains("/fare")
                    ? LocalBackendFixture.enquiry("90390","AAA","BBB",LocalDate.now().toString(),"2A",true,875.0)
                    : path.contains("/availability") ? LocalBackendFixture.availableEnquiry("90390","AAA","BBB",LocalDate.now().toString(),"2A")
                    : path.contains("/schedule") ? "{\"success\":true,\"data\":{\"train\":{\"number\":\"90390\",\"name\":\"TEST FIXTURE Express\",\"classes\":[\"2A\",\"1A\"]},\"stops\":["
                            + "{\"station\":{\"code\":\"AAA\",\"name\":\"Alpha\"},\"sequence\":1,\"departure\":\"22:00\",\"departureDay\":0,\"isHalt\":true},"
                            + "{\"station\":{\"code\":\"BBB\",\"name\":\"Beta\"},\"sequence\":2,\"arrival\":\"06:00\",\"arrivalDay\":1,\"isHalt\":true}]}}"
                    : "{\"success\":true,\"data\":{\"from\":{\"code\":\"AAA\"},\"to\":{\"code\":\"BBB\"},\"count\":1,\"trains\":[{\"number\":\"90390\",\"name\":\"TEST FIXTURE Express\",\"classes\":[\"2A\",\"1A\"],\"from\":{\"departure\":\"22:00\",\"day\":0},\"to\":{\"arrival\":\"06:00\",\"day\":1},\"durationMinutes\":480}]}}");
            Activity home=start(new Intent(instrumentation.getTargetContext(),LiveSearchActivity.class));opened.add(home);
            screenshot("home"); assertEquals(0,server.requests.get());
            Activity results=start(route(LiveTrainResultsActivity.class));opened.add(results);
            waitFor(()->{ListView list=results.findViewById(R.id.liveResultsList);return list.getAdapter().getCount()-list.getHeaderViewsCount()==1;});screenshot("results");
            Instrumentation.ActivityMonitor detailsMonitor=instrumentation.addMonitor(TrainDetailsActivity.class.getName(),null,false);
            Activity details;
            try {
                main(()->{ ListView list=results.findViewById(R.id.liveResultsList);list.getAdapter().getView(list.getHeaderViewsCount(),null,list).performClick();return null; });
                details=instrumentation.waitForMonitorWithTimeout(detailsMonitor,10000);assertNotNull(details);opened.add(details);
            } finally { instrumentation.removeMonitor(detailsMonitor); }
            waitFor(()->details.findViewById(R.id.detailsProgress).getVisibility()==View.GONE);
            assertEquals(0,server.paths.stream().filter(path->path.contains("/fare") || path.contains("/availability")).count());
            main(()->{ View card=details.findViewById(R.id.detailsClasses).findViewWithTag("2A");card.findViewById(R.id.classFareCheck).performClick();card.findViewById(R.id.classAvailabilityCheck).performClick();return null; });
            waitFor(()->((TextView)details.findViewById(R.id.detailsClasses).findViewWithTag("2A").findViewById(R.id.classAvailability)).getText().toString().contains("AVAILABLE 4"));
            screenshot("details");
            main(()->{ ((ScrollView)details.findViewById(R.id.trainDetailsRoot)).scrollTo(0,details.findViewById(R.id.detailsClasses).getTop());return null; });screenshot("classes");
            Instrumentation.ActivityMonitor bookingMonitor=instrumentation.addMonitor(BookingActivity.class.getName(),null,false);
            Activity booking;
            try {
                main(()->{ details.findViewById(R.id.detailsClasses).findViewWithTag("2A").findViewById(R.id.classDemoBook).performClick();return null; });
                booking=instrumentation.waitForMonitorWithTimeout(bookingMonitor,10000);assertNotNull(booking);opened.add(booking);
            } finally { instrumentation.removeMonitor(bookingMonitor); }
            waitFor(()->booking.findViewById(R.id.confirmBookingButton).isEnabled());screenshot("passenger");
            main(()->{ ((TextView)booking.findViewById(R.id.passengerNameInput)).setText("UI Fixture Passenger");
                ((TextView)booking.findViewById(R.id.passengerAgeInput)).setText("25");((Spinner)booking.findViewById(R.id.genderSpinner)).setSelection(3);return null; });
            instrumentation.waitForIdleSync();
            main(()->{ booking.findViewById(R.id.confirmBookingButton).performClick();((ScrollView)booking.findViewById(R.id.bookingRoot)).scrollTo(0,0);return null; });
            assertEquals(View.VISIBLE,(int)main(()->booking.findViewById(R.id.demoCheckoutPanel).getVisibility()));
            assertTrue(main(()->((TextView)booking.findViewById(R.id.checkoutTotal)).getText().toString()).contains("875.00"));screenshot("checkout");
            assertEquals(1,server.paths.stream().filter(path->path.contains("/fare")).count());
            assertEquals(1,server.paths.stream().filter(path->path.contains("/availability")).count());
            // Deliberately stop at review. This visual test allocates no seat and creates no PNR.
        } finally { main(()->{ for(Activity a:opened)a.finish();return null; }); }
    }

    @Test public void ticketCopyAndReceiptExpansionRemainCorrectWhenCardsAreRecycled() throws Exception {
        try(DemoPaymentTestDatabase db=new DemoPaymentTestDatabase()) {
            Booking first=db.service.createBooking(db.checkout(1)),second=db.service.createBooking(db.checkout(2));db.service.cancelBooking(second.getId());
            second=db.service.findBookingByPnr(second.getPnr());final Booking cancelled=second;
            Activity host=start(new Intent(instrumentation.getTargetContext(),LiveSearchActivity.class));
            try {
                main(()->{
                    BookingAdapter adapter=new BookingAdapter(host,null);adapter.setBookings(Arrays.asList(first,cancelled));
                    LinearLayout parent=new LinearLayout(host);parent.setOrientation(LinearLayout.VERTICAL);
                    androidx.core.view.WindowInsetsCompat insets=androidx.core.view.ViewCompat.getRootWindowInsets(host.getWindow().getDecorView());
                    parent.setPadding(24,(insets==null ? 0 : insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).top)+24,24,24);
                    View row=adapter.getView(0,null,parent);parent.addView(row);host.setContentView(parent);
                    assertEquals(View.GONE,row.findViewById(R.id.bookingPayment).getVisibility());
                    row.findViewById(R.id.copyPnrButton).performClick();
                    ClipboardManager clipboard=(ClipboardManager)host.getSystemService(Context.CLIPBOARD_SERVICE);
                    assertEquals(first.getPnr(),clipboard.getPrimaryClip().getItemAt(0).getText().toString());
                    row.findViewById(R.id.bookingReceiptToggle).performClick();adapter.getView(0,row,parent);
                    assertEquals(View.VISIBLE,row.findViewById(R.id.bookingPayment).getVisibility());
                    assertTrue(((TextView)row.findViewById(R.id.bookingPayment)).getText().toString().contains("Not Valid for Travel"));
                    adapter.getView(1,row,parent);assertEquals(View.GONE,row.findViewById(R.id.bookingPayment).getVisibility());
                    assertTrue(((TextView)row.findViewById(R.id.bookingStatus)).getText().toString().contains("CANCELLED"));
                    adapter.getView(0,row,parent);assertEquals(View.VISIBLE,row.findViewById(R.id.bookingPayment).getVisibility());
                    row.findViewById(R.id.bookingReceiptToggle).performClick();adapter.getView(0,row,parent);
                    assertEquals(View.GONE,row.findViewById(R.id.bookingPayment).getVisibility());return null;
                });screenshot("ticket-fixture");
            } finally { main(()->{ host.finish();return null; }); }
        }
    }

    @Test public void liveDerivedTicketLabelsItsLocalSeatAndShowsProviderStatus() {
        Activity host=start(new Intent(instrumentation.getTargetContext(),LiveSearchActivity.class));
        try {
            main(()->{
                String retrieved=java.time.Instant.now().toString();
                com.railreserve.model.FareSnapshot snapshot=new com.railreserve.model.FareSnapshot(
                        "90390","AAA","BBB",LocalDate.now().plusDays(1).toString(),"2A","GN",24500,
                        "RailCore fare estimate",retrieved,"AVAILABLE 4","AVAILABLE",
                        "RailCore availability",retrieved);
                Booking booking=new Booking(1,"1234567890",1,1,"90390","TEST FIXTURE Express","AAA","BBB",
                        snapshot.getDate(),"08:00","12:00",0,"UI Fixture Passenger",25,"Other","SIM-S1",99,
                        com.railreserve.model.TravelClass.AC_TWO_TIER,245,"CONFIRMED",retrieved,null,null,snapshot);
                BookingAdapter adapter=new BookingAdapter(host,null);
                adapter.setBookings(Collections.singletonList(booking));
                View row=adapter.getView(0,null,new LinearLayout(host));
                String details=((TextView)row.findViewById(R.id.bookingClassFare)).getText().toString();
                assertTrue(details.contains("AVAILABLE"));
                assertTrue(details.contains("Local demo seat: SIM-S1 99"));
                assertTrue(details.contains("No railway seat reserved"));
                return null;
            });
        } finally { main(()->{ host.finish();return null; }); }
    }

    @Test public void largeFontAndSmallViewportKeepControlsReadableAndScrollable() {
        main(()->{
            Configuration configuration=new Configuration(instrumentation.getTargetContext().getResources().getConfiguration());
            configuration.fontScale=1.6f;configuration.screenWidthDp=320;
            Context context=new ContextThemeWrapper(instrumentation.getTargetContext().createConfigurationContext(configuration),R.style.Theme_MyApplication);
            int width=(int)(320*context.getResources().getDisplayMetrics().density),height=(int)(568*context.getResources().getDisplayMetrics().density);
            for(int resource:new int[]{R.layout.activity_live_search,R.layout.activity_train_details,R.layout.activity_booking,R.layout.activity_find_booking,R.layout.item_live_class,R.layout.item_booking,R.layout.view_demo_checkout}) {
                View root=LayoutInflater.from(context).inflate(resource,null,false);root.setVisibility(View.VISIBLE);
                if(resource==R.layout.item_live_class) {
                    ((TextView)root.findViewById(R.id.classHeading)).setText("AC 3 Tier Economy (3E)");
                    ((TextView)root.findViewById(R.id.classFare)).setText("Fare estimate: ₹875.00\nRailCore fare estimate · Checked 13 Nov, 07:00:00 IST");
                    ((TextView)root.findViewById(R.id.classAvailability)).setText("Availability: GNWL107/WL62\nStatus: WAITLIST | 2026-11-15 | GN");
                }
                // Items and checkout are children of a scrolling container in the real UI.
                boolean viewport=root instanceof ScrollView || resource==R.layout.activity_live_search;
                root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(viewport ? height : 0,viewport ? View.MeasureSpec.EXACTLY : View.MeasureSpec.UNSPECIFIED));
                root.layout(0,0,width,root.getMeasuredHeight());verifyTextAndTargets(root,context.getResources().getDisplayMetrics().density);
                if(root instanceof ScrollView) assertTrue(((ScrollView)root).getChildAt(0).getHeight()>=root.getHeight()-root.getPaddingTop()-root.getPaddingBottom());
            }
            return null;
        });
    }

    @Test public void resultListsKeepAScrollableViewportInLandscape() {
        main(()->{
            Context context=new ContextThemeWrapper(instrumentation.getTargetContext(),R.style.Theme_MyApplication);
            float density=context.getResources().getDisplayMetrics().density;
            int width=(int)(600*density),height=(int)(280*density);
            for(int[] screen:new int[][]{{R.layout.activity_live_train_results,R.id.liveResultsList},{R.layout.activity_live_schedule,R.id.liveScheduleList},{R.layout.activity_my_bookings,R.id.bookingsList},{R.layout.activity_train_results,R.id.resultsList}}) {
                View root=LayoutInflater.from(context).inflate(screen[0],null,false);
                ((ListView)root.findViewById(screen[1])).setAdapter(new ArrayAdapter<>(context,android.R.layout.simple_list_item_1,Collections.singletonList("TEST FIXTURE")));
                root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));root.layout(0,0,width,height);
                assertTrue(context.getResources().getResourceEntryName(screen[0])+" list viewport="+root.findViewById(screen[1]).getHeight()/density,root.findViewById(screen[1]).getHeight()>=80*density);
            }
            return null;
        });
    }

    private void verifyTextAndTargets(View view,float density) {
        if(view.getVisibility()==View.GONE)return;
        if(view instanceof Button || view instanceof ImageButton || view instanceof EditText || view instanceof Spinner)assertTrue("48dp target: "+view.getId(),view.getHeight()>=48*density-1);
        if(view instanceof TextView && !(view instanceof EditText)) {
            TextView text=(TextView)view;
            if(text.getLayout()!=null)assertTrue("Text clipped: "+text.getText(),text.getLayout().getHeight()<=text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom()+1);
        }
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)verifyTextAndTargets(((ViewGroup)view).getChildAt(i),density);
    }
    private Intent route(Class<?> type) { return new Intent(instrumentation.getTargetContext(),type)
            .putExtra(LiveSearchActivity.EXTRA_SOURCE_CODE,"AAA").putExtra(LiveSearchActivity.EXTRA_SOURCE_NAME,"Alpha")
            .putExtra(LiveSearchActivity.EXTRA_DESTINATION_CODE,"BBB").putExtra(LiveSearchActivity.EXTRA_DESTINATION_NAME,"Beta")
            .putExtra(LiveSearchActivity.EXTRA_DATE,LocalDate.now().toString()); }
    private Activity start(Intent intent) { return instrumentation.startActivitySync(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK)); }
    private void screenshot(String name) throws Exception {
        instrumentation.waitForIdleSync();Thread.sleep(500);
        Bitmap bitmap=instrumentation.getUiAutomation().takeScreenshot();assertNotNull(bitmap);
        File directory=new File(instrumentation.getTargetContext().getCacheDir(),"ui-refinement");assertTrue(directory.isDirectory() || directory.mkdirs());
        try(FileOutputStream stream=new FileOutputStream(new File(directory,"after-"+name+".png"))) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,stream)); }finally { bitmap.recycle(); }
    }
    private void waitFor(Supplier<Boolean> condition) throws Exception { long until=android.os.SystemClock.elapsedRealtime()+12000;
        while(android.os.SystemClock.elapsedRealtime()<until){if(main(condition))return;Thread.sleep(50);}fail("UI did not settle"); }
    private <T>T main(Supplier<T> action) { AtomicReference<T> value=new AtomicReference<>();instrumentation.runOnMainSync(()->value.set(action.get()));return value.get(); }
}
