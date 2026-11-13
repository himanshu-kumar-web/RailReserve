package com.railreserve.live;

/** Labels do not establish that a train offers a class; only provider lists do. */
public final class LiveClassCatalog {
    private LiveClassCatalog() { }
    public static String label(String code) {
        switch (code) {
            case "1A": return "First AC";
            case "2A": return "AC 2 Tier";
            case "3A": return "AC 3 Tier";
            case "3E": return "AC 3 Tier Economy";
            case "SL": return "Sleeper";
            case "CC": return "AC Chair Car";
            case "EC": return "Executive Chair Car";
            case "2S": return "Second Sitting";
            default: return "Provider class";
        }
    }
    public static boolean supportsDemo(String code) {
        try { com.railreserve.model.TravelClass.fromCode(code); return true; }
        catch(IllegalArgumentException unsupported) { return false; }
    }
}
