package com.railreserve.dao;
import com.railreserve.model.TrainSearchResult;
import com.railreserve.model.TravelClass;
import java.util.List;
public interface TrainDao { List<TrainSearchResult> search(String source,String destination,String date,TravelClass travelClass); List<String> getStations(); }
