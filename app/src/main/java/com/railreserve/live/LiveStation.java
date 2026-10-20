package com.railreserve.live;

public final class LiveStation {
    private final String code;
    private final String name;
    private final String city;

    public LiveStation(String code, String name, String city) {
        this.code = code;
        this.name = name;
        this.city = city;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getCity() { return city; }

    public String getLabel() {
        if (name == null || name.isEmpty()) return code == null ? "Not provided" : code;
        return code == null || code.isEmpty() ? name : name + " (" + code + ")";
    }

    @Override public String toString() { return getLabel(); }
}
