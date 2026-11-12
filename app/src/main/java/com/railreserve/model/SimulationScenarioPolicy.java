package com.railreserve.model;

/** Versioned fictional training policy, independent of distance, providers and railway fares. */
public final class SimulationScenarioPolicy {
    public static final String NAMESPACE = "SELECTED_LIVE_V1";
    public static final int SEAT_COUNT = 6;
    private SimulationScenarioPolicy() { }
    public static int fareRupees(TravelClass travelClass) {
        if (travelClass == TravelClass.SLEEPER) return 100;
        if (travelClass == TravelClass.AC_THREE_TIER) return 250;
        throw new IllegalArgumentException("Choose Sleeper or AC 3 Tier for the simulation.");
    }
}
