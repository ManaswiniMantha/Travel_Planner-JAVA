package travelplanner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fake, OFFLINE data for the tests: a small made-up city with known places.
 * Because the planner receives its geocoders and place providers from outside
 * (dependency injection), the tests can hand it these fakes instead of the real APIs.
 * Nothing here touches the internet, so the tests are fast and always give the same result.
 */
final class TestData {

    static final double CITY_LAT = 12.9716;
    static final double CITY_LON = 77.5946;

    private TestData() { }   // utility class: no objects

    /** Fake geocoder: knows "Testcity" and "Startville", nothing else. */
    static class FakeGeocoder implements Geocoder {
        @Override
        public GeoLocation geocode(String text) {
            if (text.equalsIgnoreCase("Testcity")) {
                return new GeoLocation("Testcity, India", CITY_LAT, CITY_LON, "IN", "locality");
            }
            if (text.equalsIgnoreCase("Startville")) {
                return new GeoLocation("Startville, India", 13.0827, 80.2707, "IN", "locality");
            }
            return null;   // unknown place
        }

        @Override
        public String getServiceName() {
            return "Fake geocoder";
        }
    }

    /** Fake place source that always returns the same list. */
    static class FakePlaceProvider implements PlaceProvider {
        private final boolean offline;

        FakePlaceProvider(boolean offline) {
            this.offline = offline;
        }

        @Override
        public List<Place> findPlaces(GeoLocation centre, double radiusKm, List<String> interests) {
            return places();   // a NEW list every time, so one test can't affect another
        }

        @Override
        public String getServiceName() {
            return "Fake places";
        }

        @Override
        public boolean isOfflineData() {
            return offline;
        }
    }

    /** A place source that is always "down" - used to test the fallback chain. */
    static class BrokenPlaceProvider implements PlaceProvider {
        @Override
        public List<Place> findPlaces(GeoLocation centre, double radiusKm, List<String> interests)
                throws PlaceSearchException {
            throw new PlaceSearchException("Simulated server failure");
        }

        @Override
        public String getServiceName() {
            return "Broken places";
        }
    }

    /** ~20 places around the fake city centre: sights, parks, museums and restaurants. */
    static List<Place> places() {
        List<Place> list = new ArrayList<>();
        add(list, "Old Hill Fort", 0.010, 0.012, "historic", "fort");
        add(list, "Kings Palace", -0.008, 0.004, "historic", "castle");
        add(list, "War Memorial Hall", 0.004, -0.010, "historic", "monument");
        add(list, "Ancient Ruins Park", 0.020, 0.018, "historic", "ruins");
        add(list, "City Clock Tower", -0.015, -0.006, "historic", "monument");
        add(list, "Botanical Garden", -0.012, 0.010, "leisure", "garden");
        add(list, "Central Park", 0.002, 0.003, "leisure", "park");
        add(list, "Lakeside Park", 0.025, -0.020, "leisure", "park");
        add(list, "Hilltop Viewpoint", 0.030, 0.025, "tourism", "viewpoint");
        add(list, "State Museum", 0.006, 0.008, "tourism", "museum");
        add(list, "Modern Art Gallery", -0.004, 0.014, "tourism", "gallery");
        add(list, "Great Temple", -0.018, 0.002, "amenity", "place_of_worship");
        add(list, "Grand Mall", 0.014, -0.014, "shop", "mall");
        add(list, "Flower Market", -0.006, -0.016, "amenity", "marketplace");
        add(list, "Spice Kitchen", 0.003, 0.009, "amenity", "restaurant");
        add(list, "Dosa Corner", -0.010, 0.006, "amenity", "restaurant");
        add(list, "Garden Cafe", -0.011, 0.011, "amenity", "cafe");
        add(list, "Hill View Diner", 0.018, 0.015, "amenity", "restaurant");
        add(list, "Lake Bistro", 0.022, -0.017, "amenity", "restaurant");
        add(list, "Market Eats", -0.005, -0.014, "amenity", "restaurant");
        return list;
    }

    private static void add(List<Place> list, String name, double dLat, double dLon, String key, String value) {
        HashMap<String, String> tags = new HashMap<>();
        tags.put(key, value);
        tags.put("name", name);
        // a description tag means the planner does NOT ask Wikipedia (keeps tests offline)
        tags.put("description", "A test place used by the automated tests.");
        list.add(OverpassClient.buildPlace(name, CITY_LAT + dLat, CITY_LON + dLon, tags));
    }

    /** A planner wired to the fakes, in offline mode (no API key). */
    static TravelPlanner planner(PlaceProvider... providers) {
        ConfigLoader offline = new ConfigLoader("", 12);
        return new TravelPlanner(offline, new OpenRouteServiceClient(offline),
                List.of(new FakeGeocoder()), List.of(providers));
    }

    static UserPreferences prefs(int days, double budget, int travelers, String pace, String... interests) {
        return new UserPreferences("Testcity", "Startville", days, budget, travelers,
                new ArrayList<>(List.of(interests)), pace);
    }

    /** Counts how many items of each type a day has - used by several tests. */
    static Map<String, Integer> countTypes(List<ItineraryItem> day) {
        Map<String, Integer> counts = new HashMap<>();
        for (ItineraryItem item : day) {
            counts.merge(item.isMeal() ? "meal" : "activity", 1, Integer::sum);
        }
        return counts;
    }
}
