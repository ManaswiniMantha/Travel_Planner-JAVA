package travelplanner;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Finds REAL places around the destination using the Overpass API (OpenStreetMap data).
 * OpenRouteService is not a database of tourist places, so this is where candidates come from.
 *
 * Each selected interest is mapped to OSM tags (e.g. Beaches -> natural=beach) and we ask
 * Overpass for named places with those tags within a radius around the destination centre.
 */
public class OverpassClient extends ApiClient implements PlaceProvider {

    private static final String PRIMARY_URL = "https://overpass-api.de/api/interpreter";
    private static final String MIRROR_URL = "https://overpass.kumi.systems/api/interpreter";
    private static final int MIRROR_HEAD_START_SECONDS = 6;   // main server gets a head start
    private static final int REQUEST_TIMEOUT_SECONDS = 30;
    private static final String CACHE_VERSION = "v2";          // bump when the query changes

    public OverpassClient() {
        super("Overpass (OpenStreetMap)");
    }

    @Override
    public String getPurpose() {
        return "finding real places (sights, parks, restaurants) around the destination";
    }

    /**
     * PlaceProvider version (used by TravelPlanner through the interface).
     * This is METHOD OVERLOADING: same name as the method below, different parameters.
     */
    @Override
    public List<Place> findPlaces(GeoLocation centre, double radiusKm, List<String> interests)
            throws PlaceSearchException {
        try {
            return findPlaces(centre.getLat(), centre.getLon(), radiusKm, interests, centre.isLargeArea());
        } catch (IOException | RuntimeException e) {
            throw new PlaceSearchException("Overpass could not deliver places: " + e.getMessage(), e);
        }
    }

    /**
     * Returns places near (lat, lon). Order of attempts:
     * 1) on-disk cache  2) main Overpass server  3) mirror server.
     * Throws IOException if everything fails (planner then uses the offline demo file).
     */
    public List<Place> findPlaces(double lat, double lon, double radiusKm, List<String> interests,
                                  boolean largeArea)
            throws IOException {
        String query = buildQuery(lat, lon, radiusKm, interests, largeArea);
        ArrayList<String> sorted = new ArrayList<>(interests);
        Collections.sort(sorted);
        String cacheKey = String.format(Locale.US, "overpass%s_%.3f_%.3f_%.0f_%s", CACHE_VERSION,
                lat, lon, radiusKm, String.join("-", sorted));

        String json = CacheManager.get(cacheKey);
        if (json != null) return parseElements(readElements(json));

        String body = "data=" + HttpUtil.encode(query);
        JsonArray elements = raceServers(body);
        // only cache GOOD answers - an empty/error answer must not be remembered
        if (elements.size() > 0) {
            JsonObject wrapper = new JsonObject();
            wrapper.add("elements", elements);
            CacheManager.put(cacheKey, wrapper.toString());
        }
        return parseElements(elements);
    }

    /**
     * MULTITHREADING: asks the main server first; if it hasn't answered after a few seconds (busy),
     * the mirror is asked TOO, in parallel, and whichever answers first wins. If one server fails,
     * the other one is still tried. Uses a 2-thread pool and an ExecutorCompletionService, which
     * hands back finished tasks in the order they complete.
     */
    private JsonArray raceServers(String body) throws IOException {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CompletionService<JsonArray> race = new ExecutorCompletionService<>(pool);
        try {
            race.submit(() -> query(PRIMARY_URL, body));
            int running = 1;
            boolean mirrorStarted = false;
            IOException lastError = new IOException("Overpass did not answer");
            while (true) {
                Future<JsonArray> finished = mirrorStarted
                        ? race.take()                                                   // wait for whichever is next
                        : race.poll(MIRROR_HEAD_START_SECONDS, TimeUnit.SECONDS);       // wait a little for the main server
                if (finished == null) {
                    log("main server is slow, asking the mirror in parallel...");
                    race.submit(() -> query(MIRROR_URL, body));
                    running++;
                    mirrorStarted = true;
                    continue;
                }
                running--;
                try {
                    return finished.get();      // first good answer wins
                } catch (ExecutionException e) {
                    lastError = e.getCause() instanceof IOException
                            ? (IOException) e.getCause() : new IOException(e.getCause());
                    log("one server failed: " + lastError.getMessage());
                    if (!mirrorStarted) {
                        race.submit(() -> query(MIRROR_URL, body));
                        running++;
                        mirrorStarted = true;
                    } else if (running == 0) {
                        throw lastError;        // both servers failed
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Place search was interrupted");
        } finally {
            pool.shutdownNow();                 // cancels the slower request
        }
    }

    private JsonArray query(String url, String body) throws IOException {
        return readElements(HttpUtil.post(url, body, "application/x-www-form-urlencoded", null, REQUEST_TIMEOUT_SECONDS));
    }

    /**
     * Checks an Overpass answer. A busy server may reply with an HTML page, or with JSON that
     * contains a "remark" like "Query timed out" and no elements - both count as failures.
     */
    private JsonArray readElements(String json) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray elements = root.getAsJsonArray("elements");
            if (elements == null) throw new IOException("No elements in Overpass answer");
            if (elements.size() == 0 && root.has("remark")) {
                throw new IOException("Overpass: " + root.get("remark").getAsString());
            }
            return elements;
        } catch (RuntimeException e) {
            throw new IOException("Overpass returned unexpected data (server busy?)");
        }
    }

    /**
     * Maps one interest to OSM tag filters. Value = max results for that filter
     * (keeps the query small and stops e.g. 500 restaurants flooding the list).
     */
    private Map<String, Integer> filtersFor(String interest) {
        Map<String, Integer> f = new LinkedHashMap<>();
        switch (interest) {
            case "Beaches":
                f.put("[\"natural\"=\"beach\"]", 25);
                break;
            case "Nature":
                f.put("[\"leisure\"=\"park\"]", 15);
                f.put("[\"leisure\"=\"nature_reserve\"]", 10);
                f.put("[\"natural\"=\"waterfall\"]", 10);
                f.put("[\"natural\"=\"peak\"]", 8);
                f.put("[\"tourism\"=\"viewpoint\"]", 12);
                break;
            case "History":
                // plaques are excluded: big cities have thousands of tiny memorial plaques
                f.put("[\"historic\"~\"^(fort|monument|castle|memorial|ruins)$\"][\"memorial\"!=\"plaque\"]", 30);
                break;
            case "Culture":
                f.put("[\"tourism\"~\"^(museum|gallery)$\"]", 20);
                f.put("[\"amenity\"=\"place_of_worship\"]", 15);
                f.put("[\"amenity\"=\"theatre\"]", 8);
                f.put("[\"historic\"=\"archaeological_site\"]", 8);
                break;
            case "Food":
                f.put("[\"amenity\"=\"restaurant\"]", 30);
                f.put("[\"amenity\"~\"^(cafe|food_court)$\"]", 15);
                break;
            case "Shopping":
                f.put("[\"shop\"~\"^(mall|department_store)$\"]", 15);
                f.put("[\"amenity\"=\"marketplace\"]", 10);
                break;
            case "Adventure":
                f.put("[\"leisure\"=\"water_park\"]", 8);
                f.put("[\"tourism\"=\"theme_park\"]", 8);
                f.put("[\"leisure\"=\"sports_centre\"]", 8);
                f.put("[\"sport\"~\"^(surfing|scuba_diving|climbing|kitesurfing|paragliding|water_ski)$\"]", 8);
                break;
            case "Relaxation":
                f.put("[\"leisure\"=\"spa\"]", 8);
                f.put("[\"leisure\"=\"garden\"]", 10);
                f.put("[\"leisure\"=\"park\"]", 10);
                f.put("[\"natural\"=\"beach\"]", 10);
                break;
            default:
                break;
        }
        return f;
    }

    /** Always-searched landmark types (filter -> max results). */
    private static final Map<String, Integer> LANDMARK_FILTERS = new LinkedHashMap<>();
    static {   // static initializer block: runs once when the class is loaded
        LANDMARK_FILTERS.put("[\"historic\"][\"memorial\"!=\"plaque\"]", 30);
        LANDMARK_FILTERS.put("[\"leisure\"~\"^(park|garden|nature_reserve)$\"]", 15);
        LANDMARK_FILTERS.put("[\"tourism\"~\"^(museum|zoo|gallery|viewpoint|theme_park)$\"]", 20);
        LANDMARK_FILTERS.put("[\"amenity\"=\"place_of_worship\"]", 15);
        LANDMARK_FILTERS.put("[\"building\"~\"^(palace|government|cathedral|temple)$\"]", 10);
    }

    /** Builds the Overpass QL text. Each filter gets its own small "out" statement. */
    private String buildQuery(double lat, double lon, double radiusKm, List<String> interests, boolean largeArea) {
        Map<String, Integer> all = new LinkedHashMap<>();
        // always add general tourist attractions as extra candidates (first = most important)
        all.put("[\"tourism\"=\"attraction\"]", 25);
        for (String interest : interests) all.putAll(filtersFor(interest));
        // we always need restaurants for the lunch slot, even if Food was not selected
        if (!all.containsKey("[\"amenity\"=\"restaurant\"]")) all.put("[\"amenity\"=\"restaurant\"]", 15);

        // Two bounding boxes: the full search area, and an inner "city centre" box (half the radius)
        // for famous places + restaurants. For a big region (a state) both are the full area.
        String outerBox = bbox(lat, lon, radiusKm);
        String innerBox = largeArea ? outerBox : bbox(lat, lon, radiusKm / 2);

        // maxsize = 64 MB: small queries get a server slot quickly even when Overpass is busy.
        // The query is ordered by importance: if a busy server stops early ("timed out"), it still
        // returns what it found so far, so the most useful places must come first.
        StringBuilder q = new StringBuilder("[out:json][timeout:25][maxsize:67108864];\n");

        // pass 0: the destination's LANDMARKS, whatever interests were ticked. Only places with a
        // Wikidata entry, so the list stays small; TravelPlanner then ranks them by fame, which
        // is how famous parks/palaces (Lalbagh, Cubbon Park, Bangalore Palace...) get in.
        for (Map.Entry<String, Integer> e : LANDMARK_FILTERS.entrySet()) {
            appendStatement(q, "nwr", e.getKey() + "[\"wikidata\"]", outerBox, e.getValue(), false);
        }

        // pass 1: well-known places (with a Wikidata entry), printed in OSM-ID order: low IDs were
        // mapped first, which are usually the famous landmarks (the Eiffel Tower is way 5013364).
        for (Map.Entry<String, Integer> e : all.entrySet()) {
            if (!isFoodFilter(e.getKey())) {
                appendStatement(q, "nwr", e.getKey() + "[\"wikidata\"]", innerBox, e.getValue(), false);
            }
        }
        // pass 2: restaurants/cafes (needed for lunch). Restaurants are almost always single
        // map points, so "node" is much faster than "nwr" in dense cities like Paris.
        for (Map.Entry<String, Integer> e : all.entrySet()) {
            if (isFoodFilter(e.getKey())) appendStatement(q, "node", e.getKey(), innerBox, e.getValue(), false);
        }
        // pass 3: everything else matching the interests, in the full area
        for (Map.Entry<String, Integer> e : all.entrySet()) {
            if (!isFoodFilter(e.getKey())) appendStatement(q, "nwr", e.getKey(), outerBox, e.getValue(), true);
        }
        return q.toString();
    }

    /** Bounding box (south,west,north,east). 1 degree of latitude is about 111 km.
     *  Much faster for Overpass than "around:" on big areas. */
    private String bbox(double lat, double lon, double radiusKm) {
        double dLat = radiusKm / 111.0;
        double dLon = radiusKm / (111.0 * Math.cos(Math.toRadians(lat)));
        return String.format(Locale.US, "(%.4f,%.4f,%.4f,%.4f)", lat - dLat, lon - dLon, lat + dLat, lon + dLon);
    }

    private boolean isFoodFilter(String filter) {
        return filter.contains("restaurant") || filter.contains("cafe");
    }

    /** One statement + its output line. "center" gives ways/areas a lat/lon, "qt" skips sorting. */
    private void appendStatement(StringBuilder q, String type, String filter, String bbox, int limit, boolean fast) {
        q.append(type).append(filter).append("[\"name\"]").append(bbox).append(";\n");
        q.append(fast ? "out center qt " : "out center ").append(limit).append(";\n");
    }

    /** Converts Overpass JSON elements into Place objects (only named places with coordinates). */
    public static List<Place> parseElements(JsonArray elements) {
        List<Place> places = new ArrayList<>();
        if (elements == null) return places;
        for (JsonElement el : elements) {
            JsonObject obj = el.getAsJsonObject();
            if (!obj.has("tags")) continue;

            HashMap<String, String> tags = new HashMap<>();
            for (Map.Entry<String, JsonElement> t : obj.getAsJsonObject("tags").entrySet()) {
                tags.put(t.getKey(), t.getValue().getAsString());
            }
            String name = tags.get("name:en") != null ? tags.get("name:en") : tags.get("name");
            if (name == null || name.isBlank()) continue;

            double lat, lon;
            if (obj.has("lat")) {                // node
                lat = obj.get("lat").getAsDouble();
                lon = obj.get("lon").getAsDouble();
            } else if (obj.has("center")) {      // way / relation
                lat = obj.getAsJsonObject("center").get("lat").getAsDouble();
                lon = obj.getAsJsonObject("center").get("lon").getAsDouble();
            } else {
                continue;
            }
            places.add(buildPlace(name, lat, lon, tags));
        }
        return places;
    }

    /** Creates a Place and works out its category + matching interests from its OSM tags. */
    public static Place buildPlace(String name, double lat, double lon, HashMap<String, String> tags) {
        Place place = new Place(name, lat, lon, categoryFromTags(tags));
        place.setTags(tags);
        place.setMatchingInterests(interestsFromTags(tags));
        // Popularity proxy: OSM has no star ratings, so a Wikipedia/Wikidata entry,
        // a website, or an explicit "tourist attraction" tag suggests a well-known place.
        boolean popular = tags.containsKey("wikipedia") || tags.containsKey("wikidata")
                || tags.containsKey("website") || "attraction".equals(tags.get("tourism"));
        place.setPopular(popular);
        return place;
    }

    /** Main category (decides emoji, cost and duration). Checked in priority order. */
    static String categoryFromTags(Map<String, String> t) {
        String amenity = t.getOrDefault("amenity", "");
        String tourism = t.getOrDefault("tourism", "");
        String leisure = t.getOrDefault("leisure", "");
        String natural = t.getOrDefault("natural", "");
        String historic = t.getOrDefault("historic", "");
        String shop = t.getOrDefault("shop", "");

        if (natural.equals("beach")) return "Beaches";
        if (amenity.equals("restaurant") || amenity.equals("cafe") || amenity.equals("food_court")) return "Food";
        if (!historic.isEmpty() && !historic.equals("archaeological_site")) return "History";
        if (tourism.equals("museum") || tourism.equals("gallery") || amenity.equals("place_of_worship")
                || amenity.equals("theatre") || historic.equals("archaeological_site")) return "Culture";
        if (shop.equals("mall") || shop.equals("department_store") || amenity.equals("marketplace")) return "Shopping";
        if (leisure.equals("spa") || leisure.equals("garden")) return "Relaxation";
        if (leisure.equals("park") || leisure.equals("nature_reserve") || natural.equals("waterfall")
                || natural.equals("peak") || tourism.equals("viewpoint")) return "Nature";
        if (leisure.equals("water_park") || tourism.equals("theme_park") || leisure.equals("sports_centre")
                || t.containsKey("sport")) return "Adventure";
        return "Sightseeing";
    }

    /** All interests a place matches (a park matches both Nature and Relaxation). */
    static ArrayList<String> interestsFromTags(Map<String, String> t) {
        ArrayList<String> list = new ArrayList<>();
        String amenity = t.getOrDefault("amenity", "");
        String tourism = t.getOrDefault("tourism", "");
        String leisure = t.getOrDefault("leisure", "");
        String natural = t.getOrDefault("natural", "");
        String historic = t.getOrDefault("historic", "");
        String shop = t.getOrDefault("shop", "");

        if (natural.equals("beach")) addOnce(list, "Beaches", "Relaxation");
        if (leisure.equals("park")) addOnce(list, "Nature", "Relaxation");
        if (leisure.equals("nature_reserve") || natural.equals("waterfall") || natural.equals("peak")
                || tourism.equals("viewpoint")) addOnce(list, "Nature");
        if (!historic.isEmpty() && !historic.equals("archaeological_site")) addOnce(list, "History");
        if (historic.equals("archaeological_site")) addOnce(list, "Culture", "History");
        if (tourism.equals("museum") || tourism.equals("gallery") || amenity.equals("place_of_worship")
                || amenity.equals("theatre")) addOnce(list, "Culture");
        if (amenity.equals("restaurant") || amenity.equals("cafe") || amenity.equals("food_court")) addOnce(list, "Food");
        if (shop.equals("mall") || shop.equals("department_store") || amenity.equals("marketplace")) addOnce(list, "Shopping");
        if (leisure.equals("water_park") || tourism.equals("theme_park") || leisure.equals("sports_centre")
                || t.containsKey("sport") || tourism.equals("attraction")) addOnce(list, "Adventure");
        if (leisure.equals("spa") || leisure.equals("garden")) addOnce(list, "Relaxation");
        if (leisure.equals("garden")) addOnce(list, "Nature");
        return list;
    }

    private static void addOnce(ArrayList<String> list, String... values) {
        for (String v : values) {
            if (!list.contains(v)) list.add(v);
        }
    }
}
