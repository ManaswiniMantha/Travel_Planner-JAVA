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

/**
 * Live place suggestions while typing, from Photon (photon.komoot.io).
 * Photon is built for search-as-you-type on OpenStreetMap data and needs no API key,
 * so suggestions work even without an OpenRouteService key. If Photon fails, PlanningView
 * falls back to OpenRouteService's autocomplete.
 */
public class PhotonClient extends ApiClient {

    private static final String URL = "https://photon.komoot.io/api/?limit=8&lang=en&q=";
    // already-fetched suggestions (typing back and forth is instant); used from background threads
    private final Map<String, List<String>> memory = Collections.synchronizedMap(new HashMap<>());

    public PhotonClient() {
        super("Photon");
    }

    @Override
    public String getPurpose() {
        return "fast place-name suggestions while typing";
    }

    /** Up to 6 readable suggestions like "Bengaluru, Karnataka, India". Never throws. */
    public List<String> suggest(String text) {
        String key = text.trim().toLowerCase();
        List<String> remembered = memory.get(key);
        if (remembered != null) return new ArrayList<>(remembered);

        List<String> results = new ArrayList<>();
        if (!isReachable()) return results;
        try {
            String json = HttpUtil.get(URL + HttpUtil.encode(text.trim()), null, 5);
            JsonArray features = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("features");
            if (features == null) return results;
            for (JsonElement f : features) {
                String label = label(f.getAsJsonObject().getAsJsonObject("properties"));
                if (label != null && !results.contains(label)) results.add(label);
                if (results.size() >= 6) break;
            }
            if (!results.isEmpty()) memory.put(key, new ArrayList<>(results));
        } catch (IOException e) {
            recordFailure(e);
        } catch (RuntimeException e) {
            log("unexpected answer: " + e.getMessage());
        }
        return results;
    }

    /** name, then the bigger areas it belongs to, without repeats: "Goa, India". */
    private static String label(JsonObject p) {
        if (p == null || !p.has("name")) return null;
        List<String> parts = new ArrayList<>();
        for (String field : new String[]{"name", "city", "county", "state", "country"}) {
            if (p.has(field)) {
                String value = p.get(field).getAsString();
                if (!value.isBlank() && !parts.contains(value)) parts.add(value);
            }
        }
        if (parts.size() > 3) {   // keep it short: place, state, country
            parts = List.of(parts.get(0), parts.get(parts.size() - 2), parts.get(parts.size() - 1));
        }
        return String.join(", ", parts);
    }
}
