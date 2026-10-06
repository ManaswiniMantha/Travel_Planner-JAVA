package travelplanner;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;

/**
 * Backup geocoder (OpenStreetMap Nominatim). Used ONLY when the HeiGIT geocoder fails.
 * Usage policy: max 1 request per second + a descriptive User-Agent (set in HttpUtil).
 */
public class NominatimClient extends ApiClient implements Geocoder {

    private static final String SEARCH_URL = "https://nominatim.openstreetmap.org/search";
    private static long lastRequestTime = 0;

    public NominatimClient() {
        super("Nominatim");
    }

    @Override
    public String getPurpose() {
        return "backup geocoder (place name -> coordinates)";
    }

    @Override
    public GeoLocation geocode(String text) throws IOException {
        String cacheKey = "nominatim_" + text.trim().toLowerCase();
        String json = CacheManager.get(cacheKey);
        if (json == null) {
            waitForRateLimit();
            String url = SEARCH_URL + "?q=" + HttpUtil.encode(text) + "&format=json&limit=1&addressdetails=1";
            json = HttpUtil.get(url, null, 12);
            CacheManager.put(cacheKey, json);
        }

        JsonArray results = JsonParser.parseString(json).getAsJsonArray();
        if (results.size() == 0) return null;
        JsonObject r = results.get(0).getAsJsonObject();
        double lat = r.get("lat").getAsDouble();
        double lon = r.get("lon").getAsDouble();
        String label = r.has("display_name") ? r.get("display_name").getAsString() : text;
        String country = "";
        if (r.has("address") && r.getAsJsonObject("address").has("country_code")) {
            country = r.getAsJsonObject("address").get("country_code").getAsString();
        }
        String type = r.has("addresstype") ? r.get("addresstype").getAsString() : "";
        return new GeoLocation(label, lat, lon, country, type);
    }

    /** Sleeps if needed so we never send more than 1 request per second. */
    // synchronized: two threads (destination + start location) must not break the 1 request/second rule
    private static synchronized void waitForRateLimit() {
        long wait = 1100 - (System.currentTimeMillis() - lastRequestTime);
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestTime = System.currentTimeMillis();
    }
}
