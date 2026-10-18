package com.railreserve.service;
import com.railreserve.model.TrainSearchResult;
import com.railreserve.model.TravelClass;
import java.util.List;
public interface TrainSearchService { void ensureDemoData(); List<String> getStationSuggestions(); List<TrainSearchResult> search(String source,String destination,String date,TravelClass travelClass); }
