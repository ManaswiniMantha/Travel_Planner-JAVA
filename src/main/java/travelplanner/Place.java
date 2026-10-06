package travelplanner;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * One real place found on OpenStreetMap (or in the offline demo file).
 * The planner fills in cost, duration and score; the UI shows the rest.
 */
public class Place {

    private String name;
    private double lat;
    private double lon;
    private String category;                       // main category, e.g. "Beaches", "History"
    private ArrayList<String> matchingInterests = new ArrayList<>();
    private boolean popular;                       // popularity proxy (OSM has no ratings)
    private double estimatedCostPerPerson;         // INR, estimated
    private int estimatedDurationMinutes;
    private String description = "";
    private double score;
    private HashMap<String, String> tags = new HashMap<>(); // raw OSM tags (wikipedia, cuisine, ...)

    private int matrixIndex = -1;  // row/column in the distance matrix, -1 = not in matrix
    private boolean used = false;  // already placed in the itinerary?

    // Fame = number of Wikipedia language editions with an article about this place (from Wikidata).
    // Bangalore Palace has dozens, a small junction has a handful. OSM itself has no ratings.
    private int fame = 0;
    private boolean mustSee = false;   // one of the destination's most famous places

    public Place(String name, double lat, double lon, String category) {
        this.name = name;
        this.lat = lat;
        this.lon = lon;
        this.category = category;
    }

    public String getTag(String key) {
        return tags.get(key);
    }

    public boolean hasTag(String key, String value) {
        return value.equals(tags.get(key));
    }

    public String getName() { return name; }
    public double getLat() { return lat; }
    public double getLon() { return lon; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public ArrayList<String> getMatchingInterests() { return matchingInterests; }
    public void setMatchingInterests(ArrayList<String> matchingInterests) { this.matchingInterests = matchingInterests; }
    public boolean isPopular() { return popular; }
    public void setPopular(boolean popular) { this.popular = popular; }
    public double getEstimatedCostPerPerson() { return estimatedCostPerPerson; }
    public void setEstimatedCostPerPerson(double cost) { this.estimatedCostPerPerson = cost; }
    public int getEstimatedDurationMinutes() { return estimatedDurationMinutes; }
    public void setEstimatedDurationMinutes(int minutes) { this.estimatedDurationMinutes = minutes; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
    public HashMap<String, String> getTags() { return tags; }
    public void setTags(HashMap<String, String> tags) { this.tags = tags; }
    public int getMatrixIndex() { return matrixIndex; }
    public void setMatrixIndex(int matrixIndex) { this.matrixIndex = matrixIndex; }
    public boolean isUsed() { return used; }
    public int getFame() { return fame; }
    public void setFame(int fame) { this.fame = fame; }
    public boolean isMustSee() { return mustSee; }
    public void setMustSee(boolean mustSee) { this.mustSee = mustSee; }
    public void setUsed(boolean used) { this.used = used; }
}
