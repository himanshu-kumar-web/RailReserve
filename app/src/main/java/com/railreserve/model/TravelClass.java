package com.railreserve.model;

public enum TravelClass {
    SLEEPER("SL", "Sleeper"), AC_THREE_TIER("3A", "AC 3 Tier"),
    AC_TWO_TIER("2A", "AC 2 Tier"), FIRST_AC("1A", "First AC"),
    AC_THREE_ECONOMY("3E", "AC 3 Tier Economy"), CHAIR_CAR("CC", "AC Chair Car"),
    EXECUTIVE_CHAIR("EC", "Executive Chair Car"), SECOND_SITTING("2S", "Second Sitting");
    private final String code, label;
    TravelClass(String code, String label) { this.code = code; this.label = label; }
    public String getCode() { return code; }
    public String getLabel() { return label; }
    public static TravelClass fromCode(String code) {
        for (TravelClass value : values()) if (value.code.equalsIgnoreCase(code)) return value;
        throw new IllegalArgumentException("Choose a supported travel class.");
    }
    @Override public String toString() { return label; }
}
