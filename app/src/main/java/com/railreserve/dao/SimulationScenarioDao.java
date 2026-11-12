package com.railreserve.dao;

import com.railreserve.model.SimulationRequest;
import com.railreserve.model.TrainSearchResult;

public interface SimulationScenarioDao {
    TrainSearchResult getOrCreate(SimulationRequest request);
}
