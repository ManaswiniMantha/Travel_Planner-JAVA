package travelplanner;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;

/**
 * Fetches a one-line description from Wikipedia for places that end up in the itinerary.
 * Completely optional: any failure just returns null and the planner uses a generic description.
 *
 * Extends the abstract ApiClient (inheritance): caching and "stop after a network failure"
 * come from the parent class. getSummary() is called from several threads at once
 * (TravelPlanner's thread pool), so it keeps no shared state of its own.
 */
public class WikipediaClient extends ApiClient {

    private static final String SUMMARY_URL = "https://en.wikipedia.org/api/rest_v1/page/summary/";

    public WikipediaClient() {
        super("Wikipedia");
    }

    @Override
    public String getPurpose() {
        return "one-line descriptions for the places in the itinerary";
    }

    /**
     * @param wikipediaTag OSM "wikipedia" tag like "en:Fort Aguada" (may be null)
     * @param place        the place (used for name search + checking the article is the right one)
     */
    public String getSummary(String wikipediaTag, Place place) {
        if (!isReachable()) return null;

        String title;
        boolean fromTag;
        if (wikipediaTag != null && wikipediaTag.startsWith("en:")) {
            title = wikipediaTag.substring(3);
            fromTag = true;
        } else {
            // name search only for multi-word names (single words like "Cafe" give wrong pages)
            if (!place.getName().contains(" ")) return null;
            title = place.getName();
            fromTag = false;
        }

        String cacheKey = "wiki_" + title.toLowerCase();
        String json;
        try {
            json = fetchCached(cacheKey, SUMMARY_URL + HttpUtil.encode(title.replace(' ', '_')), null, 6);
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("HTTP ")) {
                CacheManager.put(cacheKey, "{}");   // page doesn't exist - remember that
            }
            recordFailure(e);                      // no internet -> stop trying (inherited method)
            return null;
        }

        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (!obj.has("extract")) return null;
            if (obj.has("type") && "disambiguation".equals(obj.get("type").getAsString())) return null;

            // For name searches, make sure the article is about a place near OUR place (same name elsewhere = wrong)
            if (!fromTag) {
                if (!obj.has("coordinates")) return null;
                JsonObject c = obj.getAsJsonObject("coordinates");
                double km = HaversineUtil.distanceKm(place.getLat(), place.getLon(),
                        c.get("lat").getAsDouble(), c.get("lon").getAsDouble());
                if (km > 25) return null;
            }
            String extract = obj.get("extract").getAsString();

            // Wrong-article guard: OSM often links a spot to its TOWN's article
            // (e.g. "Candolim Beach" -> "Candolim is a census town..."). If the article
            // is about a settlement but our place is not that settlement, skip it so the
            // planner falls back to a generic description for the actual spot.
            String articleTitle = obj.has("title") ? obj.get("title").getAsString() : title;
            String shortDesc = obj.has("description") ? obj.get("description").getAsString() : "";
            if (describesSettlement(shortDesc, extract) && place.getTag("place") == null
                    && !sameName(place.getName(), articleTitle)) {
                return null;
            }
            return firstSentence(extract);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // Wikipedia short descriptions for settlements START with the type,
    // e.g. "census town in North Goa", "coastal village in Kerala", "commune in France".
    // Anchored at the start so "Shopping street in the city of X" is NOT matched.
    private static final java.util.regex.Pattern SETTLEMENT = java.util.regex.Pattern.compile(
            "^(\\w+ )?(census town|town|village|city|municipality|suburb|neighbou?rhood|locality|commune|hamlet)\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    /** True if the article is about a town/village rather than a specific spot. */
    private boolean describesSettlement(String shortDesc, String extract) {
        if (SETTLEMENT.matcher(shortDesc.trim()).find()) return true;
        // e.g. "Candolim is a census town in ..." / "X is a village in ..."
        String first = firstSentence(extract).toLowerCase();
        return first.matches(".*\\bis an? (census town|town|village|city|municipality|suburb|neighbou?rhood|locality|hamlet)\\b.*");
    }

    /** Compares a place name with an article title, ignoring case and "(Goa)"-style suffixes. */
    private boolean sameName(String placeName, String articleTitle) {
        String a = placeName.toLowerCase().trim();
        String b = articleTitle.replaceAll("\\s*\\(.*\\)", "").replace('_', ' ').toLowerCase().trim();
        return a.equals(b);
    }

    /** First sentence of the text, without being fooled by abbreviations like "St." or "Mt.". */
    private String firstSentence(String text) {
        int end = text.indexOf(". ");
        while (end > 0) {
            int wordStart = text.lastIndexOf(' ', end - 1) + 1;
            String word = text.substring(wordStart, end);
            boolean abbreviation = word.length() <= 3 && !word.isEmpty() && Character.isUpperCase(word.charAt(0));
            if (!abbreviation) break;
            end = text.indexOf(". ", end + 2);
        }
        String sentence = end > 0 ? text.substring(0, end + 1) : text;
        if (sentence.length() > 170) sentence = sentence.substring(0, 167).trim() + "…";
        return sentence;
    }
}
