package com.railreserve.model;

/** Versioned fictional training policy, independent of distance, providers and railway fares. */
public final class SimulationScenarioPolicy {
    public static final String NAMESPACE = "SELECTED_LIVE_V1";
    public static final int SEAT_COUNT = 6;
    private SimulationScenarioPolicy() { }

    public static boolean supportsCode(String code) {
        try {
            TravelClass.fromCode(code);
            return true;
        } catch (IllegalArgumentException unsupported) {
            return false;
        }
    }

    public static int fareRupees(TravelClass travelClass) {
        if (travelClass == null) throw new IllegalArgumentException("Choose a supported travel class.");
        switch (travelClass) {
            case SLEEPER: return 100;
            case AC_THREE_TIER: return 250;
            case AC_TWO_TIER: return 350;
            case FIRST_AC: return 500;
            case AC_THREE_ECONOMY: return 225;
            case CHAIR_CAR: return 200;
            case EXECUTIVE_CHAIR: return 350;
            case SECOND_SITTING: return 75;
            default: throw new IllegalArgumentException("Choose a supported travel class.");
        }
    }
}
