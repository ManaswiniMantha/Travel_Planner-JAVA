package travelplanner;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * Talks to OpenRouteService through the HeiGIT host (api.heigit.org):
 *  - geocode()      : place name -> coordinates   (Pelias search)
 *  - autocomplete() : live suggestions while typing (Pelias autocomplete)
 *  - getMatrix()    : distances/durations between MANY points in ONE request
 *
 * The API key comes from config.properties and is sent in the Authorization header.
 */
public class OpenRouteServiceClient extends ApiClient implements Geocoder {

    private static final String BASE_URL = "https://api.heigit.org";
    private static final String SEARCH_URL = BASE_URL + "/pelias/v1/search";
    private static final String AUTOCOMPLETE_URL = BASE_URL + "/pelias/v1/autocomplete";
    private static final String MATRIX_URL = BASE_URL + "/openrouteservice/v2/matrix/driving-car";
    // Directions endpoint - NOT used yet (see stub at the bottom of this class)
    // private static final String DIRECTIONS_URL = BASE_URL + "/openrouteservice/v2/directions/driving-car";

    private final ConfigLoader config;
    private boolean autocompleteWorking = true;   // switched off silently after an error

    // remembers suggestions already fetched, so going back a letter or retyping is instant.
    // Collections.synchronizedMap: autocomplete runs on background threads.
    private final Map<String, List<String>> suggestionMemory = Collections.synchronizedMap(new HashMap<>());

    public OpenRouteServiceClient(ConfigLoader config) {
        super("OpenRouteService (HeiGIT)");
        this.config = config;
    }

    @Override
    public String getPurpose() {
        return "geocoding, live suggestions and road distances (matrix)";
    }

    public boolean isAutocompleteAvailable() {
        return config.isApiKeyAvailable() && autocompleteWorking;
    }

    // ------------------------------------------------------------------ geocoding

    /**
     * Converts text into coordinates. Returns null if nothing was found.
     * Throws IOException if the service could not be reached (caller then tries Nominatim).
     */
    @Override
    public GeoLocation geocode(String text) throws IOException {
        if (!config.isApiKeyAvailable()) throw new IOException("No API key");

        String cacheKey = "ors_geocode_" + text.trim().toLowerCase();
        String json = CacheManager.get(cacheKey);
        if (json == null) {
            String url = SEARCH_URL + "?text=" + HttpUtil.encode(text) + "&size=1";
            json = HttpUtil.get(url, config.getApiKey(), 15);
            CacheManager.put(cacheKey, json);
        }

        JsonArray features = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("features");
        if (features == null || features.size() == 0) return null;

        JsonObject feature = features.get(0).getAsJsonObject();
        // GeoJSON stores coordinates as [longitude, latitude] - careful with the order!
        JsonArray coords = feature.getAsJsonObject("geometry").getAsJsonArray("coordinates");
        double lon = coords.get(0).getAsDouble();
        double lat = coords.get(1).getAsDouble();

        JsonObject props = feature.getAsJsonObject("properties");
        String label = str(props, "label");
        if (label == null) label = str(props, "name");
        String country = str(props, "country_code");
        if (country == null || country.length() != 2) country = iso3ToIso2(str(props, "country_a"));
        return new GeoLocation(label == null ? text : label, lat, lon, country, str(props, "layer"));
    }

    /** Live suggestions for the text fields. Any failure simply returns an empty list. */
    public List<String> autocomplete(String text) {
        List<String> results = new ArrayList<>();
        if (!isAutocompleteAvailable()) return results;
        String memoryKey = text.trim().toLowerCase();
        List<String> remembered = suggestionMemory.get(memoryKey);
        if (remembered != null) return new ArrayList<>(remembered);
        try {
            String url = AUTOCOMPLETE_URL + "?text=" + HttpUtil.encode(text);
            String json = HttpUtil.get(url, config.getApiKey(), 6);
            JsonArray features = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("features");
            if (features == null) return results;
            for (JsonElement f : features) {
                String label = str(f.getAsJsonObject().getAsJsonObject("properties"), "label");
                if (label != null && !results.contains(label)) results.add(label);
                if (results.size() >= 6) break;
            }
            if (!results.isEmpty()) suggestionMemory.put(memoryKey, new ArrayList<>(results));
        } catch (Exception e) {
            // endpoint unavailable / bad key -> quietly disable suggestions, never block the form
            autocompleteWorking = false;
            System.out.println("Autocomplete disabled: " + e.getMessage());
        }
        return results;
    }

    // ------------------------------------------------------------------ matrix

    /**
     * ONE request for ALL locations. points.get(i) = {lat, lon}.
     * Returns km and minutes between every pair. Throws IOException on any failure
     * so the planner can switch to the Haversine fallback.
     */
    public MatrixResult getMatrix(List<double[]> points) throws IOException {
        if (!config.isApiKeyAvailable()) throw new IOException("No API key");
        if (points.size() < 2) throw new IOException("Need at least 2 points");

        // Build {"locations": [[lon,lat],...], "metrics": ["distance","duration"]}
        JsonArray locations = new JsonArray();
        StringBuilder cacheKey = new StringBuilder("ors_matrix");
        for (double[] p : points) {
            JsonArray pair = new JsonArray();
            pair.add(round5(p[1]));  // lon first!
            pair.add(round5(p[0]));
            locations.add(pair);
            cacheKey.append('_').append(round5(p[0])).append(',').append(round5(p[1]));
        }
        JsonObject body = new JsonObject();
        body.add("locations", locations);
        JsonArray metrics = new JsonArray();
        metrics.add("distance");
        metrics.add("duration");
        body.add("metrics", metrics);
        body.addProperty("units", "m");

        String json = CacheManager.get(cacheKey.toString());
        if (json == null) {
            json = HttpUtil.post(MATRIX_URL, body.toString(), "application/json", config.getApiKey(), 25);
            CacheManager.put(cacheKey.toString(), json);
        }

        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray distances = root.getAsJsonArray("distances");
            JsonArray durations = root.getAsJsonArray("durations");
            int n = points.size();
            double[][] km = new double[n][n];
            double[][] minutes = new double[n][n];
            for (int i = 0; i < n; i++) {
                JsonArray dRow = distances.get(i).getAsJsonArray();
                JsonArray tRow = durations.get(i).getAsJsonArray();
                for (int j = 0; j < n; j++) {
                    // null = no road route between these two points
                    km[i][j] = dRow.get(j).isJsonNull() ? -1 : dRow.get(j).getAsDouble() / 1000.0;
                    minutes[i][j] = tRow.get(j).isJsonNull() ? -1 : tRow.get(j).getAsDouble() / 60.0;
                }
            }
            return new MatrixResult(km, minutes);
        } catch (RuntimeException e) {
            throw new IOException("Unexpected matrix response");
        }
    }

    // ------------------------------------------------------------------ directions (stub)

    /*
     * DIRECTIONS STUB - intentionally not implemented.
     * POST https://api.heigit.org/openrouteservice/v2/directions/driving-car
     * Body: {"coordinates": [[lon,lat],[lon,lat]]}
     * Would return the full turn-by-turn route geometry. The Matrix API already gives us
     * every distance/time we need, so this is left as a future improvement.
     */

    // ------------------------------------------------------------------ helpers

    private static double round5(double v) {
        return Math.round(v * 100000.0) / 100000.0;
    }

    private static String str(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return null;
        return obj.get(key).getAsString();
    }

    /** "IND" -> "IN", "FRA" -> "FR" using Java's built-in country list. */
    static String iso3ToIso2(String iso3) {
        if (iso3 == null) return "";
        for (String iso2 : Locale.getISOCountries()) {
            try {
                if (new Locale("", iso2).getISO3Country().equalsIgnoreCase(iso3)) return iso2;
            } catch (Exception ignored) {
                // some codes have no ISO3 equivalent
            }
        }
        return "";
    }
}
