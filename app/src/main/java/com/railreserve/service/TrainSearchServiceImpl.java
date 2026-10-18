package com.railreserve.service;
import com.railreserve.dao.TrainDao;
import com.railreserve.database.DemoDataSeeder;
import com.railreserve.database.DatabaseHelper;
import com.railreserve.model.TrainSearchResult;
import com.railreserve.model.TravelClass;
import java.time.LocalDate;
import java.util.List;
public final class TrainSearchServiceImpl implements TrainSearchService {
    private final TrainDao dao; private final DatabaseHelper database; private final DemoDataSeeder seeder;
    public TrainSearchServiceImpl(TrainDao dao,DatabaseHelper database,DemoDataSeeder seeder){this.dao=dao;this.database=database;this.seeder=seeder;}
    @Override public void ensureDemoData(){seeder.ensureSchedule(database.getWritableDatabase());}
    @Override public List<String> getStationSuggestions(){return dao.getStations();}
    @Override public List<TrainSearchResult> search(String source,String destination,String date,TravelClass clazz){String from=normalize(source),to=normalize(destination);if(from.isEmpty())throw new IllegalArgumentException("Enter a source station.");if(to.isEmpty())throw new IllegalArgumentException("Enter a destination station.");if(from.equalsIgnoreCase(to))throw new IllegalArgumentException("Source and destination must be different.");if(clazz==null)throw new IllegalArgumentException("Choose a travel class.");LocalDate journey;try{journey=LocalDate.parse(date);}catch(Exception ex){throw new IllegalArgumentException("Choose a valid journey date.");}if(journey.isBefore(LocalDate.now()))throw new IllegalArgumentException("Journey date cannot be in the past.");if(journey.isAfter(LocalDate.now().plusDays(14)))throw new IllegalArgumentException("Choose a date within the next 15 days.");return dao.search(from,to,journey.toString(),clazz);}
    private String normalize(String value){return value==null?"":value.trim().replaceAll("\\s+"," ");}
}
