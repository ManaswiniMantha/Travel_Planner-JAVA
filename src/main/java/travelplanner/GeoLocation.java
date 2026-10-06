package travelplanner;

/**
 * Result of geocoding a text like "Goa" into coordinates.
 */
public class GeoLocation {

    private String label;        // full readable name, e.g. "Goa, India"
    private double lat;
    private double lon;
    private String countryCode;  // ISO-2 code, e.g. "IN", "FR" (may be empty)
    private String layer;        // "locality", "region", "country" ... (tells us how big the area is)

    public GeoLocation(String label, double lat, double lon, String countryCode, String layer) {
        this.label = label;
        this.lat = lat;
        this.lon = lon;
        this.countryCode = countryCode == null ? "" : countryCode.toUpperCase();
        this.layer = layer == null ? "" : layer;
    }

    /** States/regions are big, so we search a wider radius around their centre. */
    public boolean isLargeArea() {
        return layer.equals("region") || layer.equals("macroregion") || layer.equals("state")
                || layer.equals("country") || layer.equals("county") || layer.equals("macrocounty");
    }

    public String getLabel() { return label; }
    public double getLat() { return lat; }
    public double getLon() { return lon; }
    public String getCountryCode() { return countryCode; }
    public String getLayer() { return layer; }
}
