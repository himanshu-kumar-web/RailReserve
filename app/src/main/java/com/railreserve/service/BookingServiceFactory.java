package com.railreserve.service;
import android.content.Context;
import com.railreserve.dao.SQLiteBookingDao;
import com.railreserve.database.DatabaseHelper;
public final class BookingServiceFactory {private BookingServiceFactory(){} public static BookingService create(Context context){DatabaseHelper helper=new DatabaseHelper(context.getApplicationContext());return new BookingServiceImpl(new SQLiteBookingDao(helper));}}
