package com.railreserve.model;

public enum TravelClass {
    SLEEPER("SL", "Sleeper"), AC_THREE_TIER("3A", "AC 3 Tier");
    private final String code, label;
    TravelClass(String code, String label) { this.code = code; this.label = label; }
    public String getCode() { return code; }
    public String getLabel() { return label; }
    public static TravelClass fromCode(String code) {
        for (TravelClass value : values()) if (value.code.equalsIgnoreCase(code)) return value;
        throw new IllegalArgumentException("Choose Sleeper or AC 3 Tier.");
    }
    @Override public String toString() { return label; }
}
