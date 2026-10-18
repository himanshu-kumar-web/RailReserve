package com.railreserve.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.time.LocalDate;

public final class DemoDataSeeder {
    private static final String[][] TRAINS={
            {"90001","RailReserve Express","Prayagraj","New Delhi","06:30","14:45","0","450","1150"},
            {"90002","Ganga Superfast","Prayagraj","New Delhi","22:10","06:50","1","540","1320"},
            {"90003","Prayag Varanasi Express","Prayagraj","Varanasi","08:15","11:00","0","180","420"},
            {"90004","Kashi Prayag Express","Varanasi","Prayagraj","16:20","19:05","0","180","420"},
            {"90005","Awadh Delhi Express","Lucknow","New Delhi","07:00","15:30","0","480","1250"},
            {"90006","Kanpur Lucknow Passenger","Kanpur","Lucknow","10:00","12:00","0","150","350"},
            {"90007","Delhi Prayag Express","New Delhi","Prayagraj","18:00","03:30","1","500","1280"},
            {"90008","Prayag Lucknow Express","Prayagraj","Lucknow","12:00","17:00","0","250","620"}
    };
    public void ensureSchedule(SQLiteDatabase db){
        db.beginTransaction();
        try {
            for(String[] t:TRAINS){ContentValues v=new ContentValues();v.put("train_number",t[0]);v.put("name",t[1]);v.put("source_station",t[2]);v.put("destination_station",t[3]);v.put("departure_time",t[4]);v.put("arrival_time",t[5]);v.put("arrival_day_offset",Integer.parseInt(t[6]));db.insertWithOnConflict("trains",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
            LocalDate today=LocalDate.now();
            for(int day=0;day<15;day++){String date=today.plusDays(day).toString();for(String[] t:TRAINS){long trainId;
                try(Cursor c=db.rawQuery("SELECT train_id FROM trains WHERE train_number=?",new String[]{t[0]})){if(!c.moveToFirst())continue;trainId=c.getLong(0);}
                ContentValues j=new ContentValues();j.put("train_id",trainId);j.put("journey_date",date);db.insertWithOnConflict("journeys",null,j,SQLiteDatabase.CONFLICT_IGNORE);
                long journeyId;try(Cursor c=db.rawQuery("SELECT journey_id FROM journeys WHERE train_id=? AND journey_date=?",new String[]{Long.toString(trainId),date})){if(!c.moveToFirst())continue;journeyId=c.getLong(0);}
                insertSeats(db,journeyId,"SL",18,Integer.parseInt(t[7]),"S1");insertSeats(db,journeyId,"3A",12,Integer.parseInt(t[8]),"B1");
            }} db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }
    private void insertSeats(SQLiteDatabase db,long journey,String clazz,int count,int fare,String coach){for(int n=1;n<=count;n++){ContentValues s=new ContentValues();s.put("journey_id",journey);s.put("travel_class",clazz);s.put("coach",coach);s.put("seat_number",n);s.put("fare",fare);s.put("status","AVAILABLE");db.insertWithOnConflict("seats",null,s,SQLiteDatabase.CONFLICT_IGNORE);}}
}
