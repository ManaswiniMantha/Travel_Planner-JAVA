package travelplanner;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Measures how FAMOUS a place is, using Wikidata (no API key needed).
 *
 * Every OpenStreetMap place with a "wikidata" tag (e.g. Q1325480) has a Wikidata entry, and that
 * entry lists every Wikipedia language edition with an article about the place ("sitelinks").
 * Bangalore Palace has articles in dozens of languages, a small street junction in very few.
 * That count is our fame score - OpenStreetMap itself has no ratings or reviews.
 *
 * One request handles up to 50 places; TravelPlanner sends several batches in parallel.
 */
public class WikidataClient extends ApiClient {

    private static final String API_URL =
            "https://www.wikidata.org/w/api.php?action=wbgetentities&props=sitelinks&format=json&ids=";
    public static final int BATCH_SIZE = 50;   // Wikidata's limit per request

    public WikidataClient() {
        super("Wikidata");
    }

    @Override
    public String getPurpose() {
        return "fame score (number of Wikipedia languages) for ranking places";
    }

    /** True for valid Wikidata IDs like "Q42" (OSM sometimes has typos or lists). */
    public static boolean isValidId(String id) {
        return id != null && id.matches("Q[0-9]+");
    }

    /**
     * Fame (sitelink count) for up to 50 IDs. IDs it could not check are simply missing from the
     * map, so the caller treats them as 0. Never throws: fame is a bonus, not a requirement.
     */
    public Map<String, Integer> getFame(List<String> ids) {
        Map<String, Integer> result = new HashMap<>();
        List<String> toFetch = new ArrayList<>();

        // 1) cache first - one small cache file per place
        for (String id : ids) {
            String cached = CacheManager.get(cacheKey(id));
            if (cached != null) {
                try {
                    result.put(id, Integer.parseInt(cached.trim()));
                    continue;
                } catch (NumberFormatException ignored) {
                    // broken cache file -> fetch again
                }
            }
            toFetch.add(id);
        }
        if (toFetch.isEmpty() || !isReachable()) return result;

        // 2) one request for all the remaining IDs
        try {
            String url = API_URL + HttpUtil.encode(String.join("|", toFetch));
            String json = HttpUtil.get(url, null, 10);
            JsonObject entities = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("entities");
            if (entities == null) return result;
            for (String id : toFetch) {
                int count = 0;
                if (entities.has(id)) {
                    JsonObject entity = entities.getAsJsonObject(id);
                    if (entity.has("sitelinks")) count = entity.getAsJsonObject("sitelinks").size();
                }
                result.put(id, count);
                CacheManager.put(cacheKey(id), String.valueOf(count));
            }
        } catch (IOException e) {
            recordFailure(e);
        } catch (RuntimeException e) {
            log("unexpected answer, fame skipped for this batch");
        }
        return result;
    }

    private static String cacheKey(String id) {
        return "wikidata_fame_" + id;
    }
}
