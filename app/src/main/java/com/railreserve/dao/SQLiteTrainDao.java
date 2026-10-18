package com.railreserve.dao;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.railreserve.database.DatabaseHelper;
import com.railreserve.model.TrainSearchResult;
import com.railreserve.model.TravelClass;
import java.util.ArrayList;
import java.util.List;

public final class SQLiteTrainDao implements TrainDao {
    private final DatabaseHelper helper;
    public SQLiteTrainDao(DatabaseHelper helper){this.helper=helper;}
    @Override public List<TrainSearchResult> search(String source,String destination,String date,TravelClass clazz){
        List<TrainSearchResult> results=new ArrayList<>();String sql="SELECT t.train_number,t.name,t.source_station,t.destination_station,t.departure_time,t.arrival_time,t.arrival_day_offset,j.journey_date,s.fare,COUNT(s.seat_id),SUM(CASE WHEN s.status='AVAILABLE' THEN 1 ELSE 0 END) FROM trains t JOIN journeys j ON j.train_id=t.train_id JOIN seats s ON s.journey_id=j.journey_id WHERE t.source_station=? COLLATE NOCASE AND t.destination_station=? COLLATE NOCASE AND j.journey_date=? AND s.travel_class=? GROUP BY j.journey_id,s.travel_class,s.fare ORDER BY t.departure_time";
        try(Cursor c=helper.getReadableDatabase().rawQuery(sql,new String[]{source,destination,date,clazz.getCode()})){while(c.moveToNext())results.add(new TrainSearchResult(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getInt(6),c.getString(7),clazz,c.getInt(8),c.getInt(9),c.getInt(10)));}return results;
    }
    @Override public List<String> getStations(){List<String> stations=new ArrayList<>();String sql="SELECT station FROM (SELECT source_station AS station FROM trains UNION SELECT destination_station AS station FROM trains) ORDER BY station COLLATE NOCASE";try(Cursor c=helper.getReadableDatabase().rawQuery(sql,null)){while(c.moveToNext())stations.add(c.getString(0));}return stations;}
}
