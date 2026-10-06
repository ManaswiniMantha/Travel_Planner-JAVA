package travelplanner;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads fallback_places.json - a SMALL offline demo file used only when Overpass is unreachable
 * and nothing is cached. It contains places, NOT itineraries: the itinerary is still generated
 * dynamically by TravelPlanner (scoring, budget, routing, scheduling).
 */
public class FallbackData implements PlaceProvider, Geocoder {

    private static final File FILE = new File("fallback_places.json");
    private static final double MAX_MATCH_KM = 150;

    private JsonArray cities;

    public FallbackData() {
        try {
            String json = Files.readString(FILE.toPath(), StandardCharsets.UTF_8);
            cities = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("cities");
        } catch (IOException | RuntimeException e) {
            cities = new JsonArray(); // missing/broken file -> simply no fallback data
            System.out.println("Fallback file not available: " + e.getMessage());
        }
    }

    @Override
    public String getServiceName() {
        return "Offline demo data";
    }

    @Override
    public boolean isOfflineData() {
        return true;   // makes the planner show the "Using offline demo data" banner
    }

    /** PlaceProvider version: demo places near the centre (radius/interests are not needed here). */
    @Override
    public List<Place> findPlaces(GeoLocation centre, double radiusKm, List<String> interests)
            throws PlaceSearchException {
        List<Place> places = placesNear(centre.getLat(), centre.getLon());
        if (places.isEmpty()) throw new PlaceSearchException("No demo data near " + centre.getLabel());
        return places;
    }

    /** Offline geocoding: matches the typed text against the demo city names. */
    @Override
    public GeoLocation geocode(String text) {
        String query = text.trim().toLowerCase();
        for (JsonElement el : cities) {
            JsonObject city = el.getAsJsonObject();
            String name = city.get("city").getAsString();
            if (query.startsWith(name.toLowerCase()) || name.toLowerCase().startsWith(query)) {
                return new GeoLocation(name + " (offline)", city.get("lat").getAsDouble(),
                        city.get("lon").getAsDouble(), city.get("country").getAsString(), "locality");
            }
        }
        return null;
    }

    /** Places of the nearest demo city (within 150 km), or an empty list. */
    public List<Place> placesNear(double lat, double lon) {
        List<Place> result = new ArrayList<>();
        JsonObject best = null;
        double bestKm = MAX_MATCH_KM;
        for (JsonElement el : cities) {
            JsonObject city = el.getAsJsonObject();
            double km = HaversineUtil.distanceKm(lat, lon, city.get("lat").getAsDouble(), city.get("lon").getAsDouble());
            if (km < bestKm) {
                bestKm = km;
                best = city;
            }
        }
        if (best == null) return result;

        for (JsonElement el : best.getAsJsonArray("places")) {
            JsonObject p = el.getAsJsonObject();
            HashMap<String, String> tags = new HashMap<>();
            for (Map.Entry<String, JsonElement> t : p.getAsJsonObject("tags").entrySet()) {
                tags.put(t.getKey(), t.getValue().getAsString());
            }
            result.add(OverpassClient.buildPlace(p.get("name").getAsString(),
                    p.get("lat").getAsDouble(), p.get("lon").getAsDouble(), tags));
        }
        return result;
    }
}
