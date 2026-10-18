package com.railreserve.service;
import android.content.Context;
import com.railreserve.dao.SQLiteTrainDao;
import com.railreserve.database.DatabaseHelper;
import com.railreserve.database.DemoDataSeeder;
public final class TrainSearchServiceFactory { private TrainSearchServiceFactory(){} public static TrainSearchService create(Context context){DatabaseHelper helper=new DatabaseHelper(context.getApplicationContext());return new TrainSearchServiceImpl(new SQLiteTrainDao(helper),helper,new DemoDataSeeder());} }
