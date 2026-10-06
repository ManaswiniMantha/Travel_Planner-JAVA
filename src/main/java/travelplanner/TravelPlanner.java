package travelplanner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * THE BRAIN OF THE APP. Java makes every decision here:
 *
 *   1. Candidate collection  - geocode destination, fetch real places from OpenStreetMap
 *   2. Cost + duration       - estimated per category, adjusted for the destination country
 *   3. Scoring               - interest match + fame (Wikidata) + affordability + pace - distance
 *   4. Routing               - ONE matrix request for all candidates (Haversine fallback)
 *   5. Greedy day building   - best place first, then "nearest good place" repeatedly,
 *                              with a BUDGET GUARD before every addition
 *   6. Scheduling            - start time + travel + visit duration + buffer, lunch slot
 *
 * Nothing about any city is hard-coded: the same steps run for any destination.
 */
public class TravelPlanner {

    // ===================================================================================
    //  ESTIMATION CONSTANTS - all in ONE place. Every cost is shown as "est." in the UI.
    // ===================================================================================
    private static final double ROAD_FACTOR = 1.3;            // roads are ~30% longer than a straight line
    private static final double AVG_SPEED_KMPH = 35.0;        // average city driving speed (fallback)
    private static final double TRANSPORT_INR_PER_KM = 15.0;  // local taxi/auto fare per vehicle-km (India)
    private static final int TRAVELERS_PER_VEHICLE = 4;       // one taxi carries 4 people
    private static final int LUNCH_TRIGGER = 12 * 60;         // after 12:00 PM we look for lunch
    private static final int LUNCH_EARLIEST = 12 * 60 + 30;   // lunch never starts before 12:30 PM
    private static final int DINNER_EARLIEST = 19 * 60;       // dinner at 7:00 PM (only if Food selected)
    private static final int MAX_CANDIDATES = 40;             // sights we keep after scoring
    private static final int MAX_FOOD_CANDIDATES = 25;        // restaurants we keep for meal slots
    private static final double VARIETY_PENALTY = 15;         // per same-category stop already today
    private static final int MAX_MATRIX_LOCATIONS = 20;       // ONE matrix call with at most 20 points
    private static final double DISTANCE_PENALTY_PER_KM = 4;  // score points lost per km of travel
    private static final int THREAD_POOL_SIZE = 6;            // parallel network calls per trip
    private static final int MUST_SEE_COUNT = 5;              // the city's N most famous places get a bonus
    private static final int MUST_SEE_MIN_FAME = 8;           // ...but only if really known (8+ Wikipedia languages)
    private static final double MAX_FAME_POINTS = 45;
    private static final double MUST_SEE_BONUS = 30;

    /** Base cost per person in INR (India prices). Multiplied by the country factor. */
    private static double baseCostInr(Place p) {
        if (p.hasTag("amenity", "place_of_worship")) return 0;
        if (p.hasTag("leisure", "spa")) return 1500;
        if (p.hasTag("leisure", "garden") || p.hasTag("leisure", "park")) return 50;
        if (p.hasTag("tourism", "theme_park") || p.hasTag("leisure", "water_park")) return 1200;
        if (p.hasTag("historic", "memorial")) return 0;
        if (p.hasTag("amenity", "cafe")) return 250;
        if (p.hasTag("amenity", "food_court")) return 200;
        switch (p.getCategory()) {
            case "Beaches": return 0;
            case "Nature": return 50;
            case "History": return 100;
            case "Culture": return 150;
            case "Food": return 400;
            case "Shopping": return 500;
            case "Adventure": return 800;
            case "Relaxation": return 300;
            default: return 100;       // Sightseeing / general attraction
        }
    }

    /** Typical visit length in minutes (before the pace adjustment). */
    private static int baseDurationMinutes(Place p) {
        if (p.hasTag("amenity", "place_of_worship")) return 45;
        switch (p.getCategory()) {
            case "Beaches": return 120;
            case "Nature": return 90;
            case "History": return 75;
            case "Culture": return 75;
            case "Shopping": return 90;
            case "Adventure": return 120;
            case "Relaxation": return 90;
            case "Food": return 60;
            default: return 60;
        }
    }

    /** Destination cost level compared to India (simple switch on ISO country code). */
    private static double countryCostFactor(String countryCode) {
        switch (countryCode) {
            case "IN": case "": return 1.0;
            case "NP": case "LK": case "BD": case "PK": return 0.9;
            case "TH": case "VN": case "ID": case "MY": return 1.5;
            case "AE": case "SG": case "QA": return 3.0;
            case "FR": case "DE": case "IT": case "ES": case "NL": case "BE": case "AT": case "PT": case "GR":
                return 3.5;
            case "GB": case "US": case "CA": case "AU": case "CH": case "JP": return 4.0;
            default: return 2.0;
        }
    }

    // ===================================================================================

    /** Travel between two places: distance, time and whether it came from live routing. */
    private static class Leg {
        double km;
        int minutes;
        boolean live;

        Leg(double km, int minutes, boolean live) {
            this.km = km;
            this.minutes = minutes;
            this.live = live;
        }
    }

    private final ConfigLoader config;
    private final OpenRouteServiceClient orsClient;
    private final WikipediaClient wikipediaClient = new WikipediaClient();
    private final WikidataClient wikidataClient = new WikidataClient();
    private final CurrencyClient currencyClient = new CurrencyClient();

    // POLYMORPHISM: the planner only sees the interfaces, not the concrete classes.
    // Each list is tried in order until one source answers.
    private final List<Geocoder> geocoders;            // HeiGIT -> Nominatim -> offline demo cities
    private final List<PlaceProvider> placeProviders;  // Overpass -> offline demo places

    // state for one generate() run
    private MatrixResult matrix;
    private double runningTotal;       // money already planned (places + transport)
    private double transportTotal;
    private boolean budgetBlocked;     // did the budget guard ever reject a place?
    private double costFactor;

    public TravelPlanner(ConfigLoader config, OpenRouteServiceClient orsClient) {
        this(config, orsClient, defaultGeocoders(orsClient), defaultProviders());
    }

    /**
     * DEPENDENCY INJECTION: the geocoders and place providers are passed in from outside.
     * The app uses the real ones (constructor above, via this(...)); the automated tests pass
     * small fake ones, so the algorithm can be tested offline with known places.
     */
    TravelPlanner(ConfigLoader config, OpenRouteServiceClient orsClient,
                  List<Geocoder> geocoders, List<PlaceProvider> placeProviders) {
        this.config = config;
        this.orsClient = orsClient;
        this.geocoders = geocoders;
        this.placeProviders = placeProviders;
    }

    private static final FallbackData FALLBACK_DATA = new FallbackData();

    private static List<Geocoder> defaultGeocoders(OpenRouteServiceClient orsClient) {
        return List.of(orsClient, new NominatimClient(), FALLBACK_DATA);
    }

    private static List<PlaceProvider> defaultProviders() {
        return List.of(new OverpassClient(), FALLBACK_DATA);
    }

    /**
     * Builds a complete itinerary. Runs on a background thread (see Main),
     * progress messages are shown on the loading overlay.
     */
    public Itinerary generate(UserPreferences prefs, Consumer<String> progress) {
        // MULTITHREADING: one small thread pool per trip for the network calls that can run
        // at the same time (start-location lookup, fame batches, place descriptions).
        ExecutorService pool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        try {
            return generate(prefs, progress, pool);
        } finally {
            pool.shutdownNow();   // always release the threads, even after an error
        }
    }

    private Itinerary generate(UserPreferences prefs, Consumer<String> progress, ExecutorService pool) {
        Itinerary itinerary = new Itinerary(prefs);
        itinerary.setApiKeyMissing(!config.isApiKeyAvailable());
        matrix = null;
        runningTotal = 0;
        transportTotal = 0;
        budgetBlocked = false;

        // ---------- STEP 1: where is the destination? ----------
        progress.accept("📍 Finding " + prefs.getDestination() + " on the map…");
        GeoLocation dest = locate(prefs.getDestination());
        if (dest == null) {
            throw new DestinationNotFoundException("We couldn't find that destination. Try adding the country or state.");
        }
        itinerary.setDestinationLabel(dest.getLabel());
        itinerary.setCountryCode(dest.getCountryCode());
        costFactor = countryCostFactor(dest.getCountryCode());

        // the start location is only needed for the header, so look it up IN PARALLEL
        Future<?> startJob = pool.submit(() -> calculateStartDistance(prefs, dest, itinerary));

        // ---------- STEP 1b: candidate places ----------
        progress.accept("📍 Finding places you'll love…");
        double radiusKm = config.getSearchRadiusKm();
        if (dest.isLargeArea()) radiusKm = Math.max(radiusKm, 40);  // states like "Goa" are big
        List<Place> places = fetchPlaces(dest, radiusKm, prefs, itinerary);
        if (places.isEmpty()) {
            throw new DestinationNotFoundException("We couldn't find tourist places around " + prefs.getDestination()
                    + ". Try a nearby bigger city, or select more interests.");
        }

        // ---------- STEP 1c: how famous is each place? (Wikidata, parallel batches) ----------
        progress.accept("🌟 Finding the must-see places…");
        applyFame(places, pool);

        // ---------- STEP 2 + 3: estimate costs, score, sort ----------
        progress.accept("⭐ Scoring " + places.size() + " places…");
        Pace pace = prefs.getPaceType();
        for (Place p : places) {
            p.setEstimatedCostPerPerson(Math.round(baseCostInr(p) * costFactor));
            int minutes = (int) (baseDurationMinutes(p) * pace.getDurationFactor());
            p.setEstimatedDurationMinutes(Math.max(30, (minutes / 5) * 5));  // round to 5 minutes
            p.setScore(scorePlace(p, prefs, pace, dest, radiusKm));
        }
        Collections.sort(places, Comparator.comparingDouble(Place::getScore).reversed());
        places = removeDuplicates(places);   // after sorting, so the better copy of a duplicate is kept

        // activities vs. restaurants (restaurants are used for meal slots). Each list keeps its
        // own top-N so restaurants are not pushed out by higher-scoring sights.
        List<Place> activities = new ArrayList<>();
        List<Place> foodPlaces = new ArrayList<>();
        for (Place p : places) {
            if (p.getCategory().equals("Food")) {
                if (foodPlaces.size() < MAX_FOOD_CANDIDATES) foodPlaces.add(p);
            } else if (activities.size() < MAX_CANDIDATES) {
                activities.add(p);
            }
        }

        // ---------- STEP 4: ONE matrix request for the best candidates ----------
        progress.accept("🛣 Calculating routes…");
        List<Place> matrixPool = new ArrayList<>();
        int foodSlots = Math.min(foodPlaces.size(), 6);
        int activitySlots = Math.min(activities.size(), MAX_MATRIX_LOCATIONS - foodSlots);
        matrixPool.addAll(activities.subList(0, activitySlots));
        matrixPool.addAll(foodPlaces.subList(0, foodSlots));
        List<double[]> points = new ArrayList<>();
        for (int i = 0; i < matrixPool.size(); i++) {
            matrixPool.get(i).setMatrixIndex(i);
            points.add(new double[]{matrixPool.get(i).getLat(), matrixPool.get(i).getLon()});
        }
        if (config.isApiKeyAvailable() && points.size() >= 2) {
            try {
                matrix = orsClient.getMatrix(points);
            } catch (IOException e) {
                // FALLBACK 1: no live routing -> Haversine estimates (itinerary still works)
                System.out.println("Matrix API failed: " + e.getMessage());
                matrix = null;
            }
        }
        itinerary.setUsedLiveRouting(matrix != null);

        // ---------- STEP 5 + 6: greedy day building + scheduling ----------
        progress.accept("💡 Building your itinerary…");
        buildDays(prefs, pace, activities, foodPlaces, itinerary);

        // ---------- extras: descriptions (parallel) + local currency ----------
        progress.accept("📖 Adding place descriptions…");
        addDescriptions(itinerary, pool);
        String currency = CurrencyClient.currencyForCountry(dest.getCountryCode());
        if (!currency.equals("INR")) {
            itinerary.setCurrency(currency, CurrencyClient.symbolFor(currency), currencyClient.getRateFromInr(currency));
        }

        waitForStartDistance(startJob, prefs, itinerary);
        itinerary.calculateTotals(transportTotal);
        addFriendlyNotes(itinerary, pace, prefs);
        return itinerary;
    }

    // ------------------------------------------------------------------ STEP 1 helpers

    /**
     * Geocoder chain: HeiGIT -> Nominatim -> offline demo cities. Returns null if not found.
     * g.geocode(...) runs a DIFFERENT method for each object in the list (runtime polymorphism).
     */
    private GeoLocation locate(String text) {
        for (Geocoder g : geocoders) {
            try {
                GeoLocation location = g.geocode(text);
                if (location != null) return location;
            } catch (IOException | RuntimeException e) {
                System.out.println(g.getServiceName() + " geocoder failed: " + e.getMessage());
            }
        }
        return null;
    }

    /** Tries every PlaceProvider in order (Overpass with cache + mirror, then offline demo data). */
    private List<Place> fetchPlaces(GeoLocation dest, double radiusKm, UserPreferences prefs, Itinerary itinerary) {
        for (PlaceProvider provider : placeProviders) {
            try {
                List<Place> places = provider.findPlaces(dest, radiusKm, prefs.getInterests());
                // small town with few results -> try once more with double the radius
                if (places.size() < 8 && radiusKm < 40 && !provider.isOfflineData()) {
                    places = provider.findPlaces(dest, radiusKm * 2, prefs.getInterests());
                }
                if (!places.isEmpty()) {
                    // FALLBACK 2 shows a banner: the itinerary is still built by the algorithm below
                    if (provider.isOfflineData()) itinerary.setUsingOfflineData(true);
                    return places;
                }
            } catch (PlaceSearchException e) {
                System.out.println(provider.getServiceName() + ": " + e.getMessage());
            }
        }
        return new ArrayList<>();
    }

    /**
     * Fame from Wikidata: the number of Wikipedia language editions about each place.
     * Places are sent in batches of 50, and all batches run IN PARALLEL on the thread pool.
     * Then the destination's MUST_SEE_COUNT most famous places are flagged as must-see.
     */
    private void applyFame(List<Place> places, ExecutorService pool) {
        List<String> ids = new ArrayList<>();
        for (Place p : places) {
            String id = p.getTag("wikidata");
            if (!p.getCategory().equals("Food") && WikidataClient.isValidId(id) && !ids.contains(id)) ids.add(id);
        }
        if (ids.isEmpty()) return;

        List<Future<Map<String, Integer>>> jobs = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += WikidataClient.BATCH_SIZE) {
            List<String> batch = new ArrayList<>(ids.subList(i, Math.min(ids.size(), i + WikidataClient.BATCH_SIZE)));
            jobs.add(pool.submit(() -> wikidataClient.getFame(batch)));
        }
        Map<String, Integer> fame = new HashMap<>();
        for (Future<Map<String, Integer>> job : jobs) {
            try {
                fame.putAll(job.get(12, TimeUnit.SECONDS));
            } catch (TimeoutException | ExecutionException e) {
                job.cancel(true);   // fame is only a bonus - carry on without it
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        for (Place p : places) {
            Integer f = fame.get(p.getTag("wikidata"));
            if (f != null) p.setFame(f);
        }

        // must-see: the most famous places of THIS destination (relative, so it works for any city)
        List<Place> byFame = new ArrayList<>(places);
        byFame.sort(Comparator.comparingInt(Place::getFame).reversed());
        Set<String> chosen = new HashSet<>();
        for (Place p : byFame) {
            if (chosen.size() >= MUST_SEE_COUNT || p.getFame() < MUST_SEE_MIN_FAME) break;
            if (chosen.add(p.getTag("wikidata"))) p.setMustSee(true);
        }
    }

    // ------------------------------------------------------------------ STEP 2: scoring

    /**
     * Simple, explainable score:
     *  +50 per matching interest (max 2 counted)  -> places you care about come first
     *  +0..45 fame: grows with the number of Wikipedia languages (log scale, from Wikidata)
     *  +30 if it is one of the destination's 5 most famous places ("must-see")
     *  +10 popularity hint for unknown-fame places (website / tourist-attraction tag)
     *  +20 / +10 if cheap compared to the per-person daily budget
     *  +10 if it suits the chosen pace
     *  - up to 20 points the further it is from the destination centre
     *  -30 if the name has no Latin letters (unreadable in an English UI)
     */
    private double scorePlace(Place p, UserPreferences prefs, Pace pace, GeoLocation dest, double radiusKm) {
        double score = 0;

        int matches = 0;
        for (String interest : p.getMatchingInterests()) {
            if (prefs.hasInterest(interest)) matches++;
        }
        score += 50 * Math.min(matches, 2);

        // log scale: going from 1 to 10 languages matters much more than from 40 to 50
        if (p.getFame() > 0) score += Math.min(MAX_FAME_POINTS, 13 * Math.log(1 + p.getFame()));
        else if (p.isPopular()) score += 10;
        if (p.isMustSee()) score += MUST_SEE_BONUS;

        double perPersonPerDay = prefs.getBudget() / prefs.getTravelers() / prefs.getDays();
        double cost = p.getEstimatedCostPerPerson();
        if (cost <= 0.30 * perPersonPerDay) score += 20;
        else if (cost <= 0.50 * perPersonPerDay) score += 10;

        if (pace.suits(p)) score += 10;

        // Distance from the centre. Skipped for big regions (a state like Goa): their geometric
        // centre can be inland forest, which would wrongly punish the famous coastal places.
        if (!dest.isLargeArea()) {
            double kmFromCentre = HaversineUtil.distanceKm(dest.getLat(), dest.getLon(), p.getLat(), p.getLon());
            score -= Math.min(20, kmFromCentre / radiusKm * 20);
        }

        // the app is in English: names without any Latin letters go to the back of the queue
        if (!p.getName().matches(".*[A-Za-z].*")) score -= 30;
        return score;
    }

    /**
     * Removes duplicates: same name, same Wikidata ID (one landmark mapped twice, e.g. a fort
     * and its walls), or two places of the same category within 150 m.
     */
    private List<Place> removeDuplicates(List<Place> places) {
        List<Place> unique = new ArrayList<>();
        for (Place p : places) {
            boolean duplicate = false;
            for (Place u : unique) {
                boolean sameName = u.getName().equalsIgnoreCase(p.getName());
                boolean sameWikidata = p.getTag("wikidata") != null && p.getTag("wikidata").equals(u.getTag("wikidata"));
                boolean sameCategory = u.getCategory().equals(p.getCategory());
                double km = HaversineUtil.distanceKm(u, p);
                boolean sameSpot = sameCategory && km < 0.15;
                // "Fort Aguada" vs "Aguada Fortress": same category, close by, share a distinctive word
                boolean similarName = sameCategory && km < 2 && shareDistinctiveWord(u.getName(), p.getName());
                if (sameName || sameWikidata || sameSpot || similarName) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) unique.add(p);
        }
        return unique;
    }

    /** Words that appear in many place names, so sharing them proves nothing. */
    private static final String[] GENERIC_WORDS = {"fort", "fortress", "beach", "temple", "church", "park",
            "museum", "garden", "gardens", "market", "lake", "palace", "cathedral", "basilica", "chapel",
            "mosque", "monument", "memorial", "ruins", "statue", "the", "and", "of", "saint", "national"};

    private boolean shareDistinctiveWord(String a, String b) {
        String[] wordsA = a.toLowerCase().split("[^a-z]+");
        String bLower = " " + b.toLowerCase().replaceAll("[^a-z]+", " ") + " ";
        for (String w : wordsA) {
            if (w.length() < 4) continue;
            boolean generic = false;
            for (String g : GENERIC_WORDS) {
                if (g.equals(w)) {
                    generic = true;
                    break;
                }
            }
            if (!generic && bLower.contains(" " + w + " ")) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ STEP 4: routing

    /** Travel from a to b: live matrix value if available, otherwise Haversine estimate. */
    private Leg travel(Place a, Place b) {
        int i = a.getMatrixIndex();
        int j = b.getMatrixIndex();
        if (matrix != null && i >= 0 && j >= 0 && matrix.getKm(i, j) >= 0) {
            return new Leg(matrix.getKm(i, j), (int) Math.round(matrix.getMinutes(i, j)), true);
        }
        // FALLBACK: straight line x road factor, at an average city speed
        double km = HaversineUtil.distanceKm(a, b) * ROAD_FACTOR;
        int minutes = (int) Math.round(km / AVG_SPEED_KMPH * 60);
        return new Leg(km, minutes, false);
    }

    /** Local transport estimate for one leg (per vehicle, not per person). */
    private double transportCost(double km, int travelers) {
        int vehicles = (travelers + TRAVELERS_PER_VEHICLE - 1) / TRAVELERS_PER_VEHICLE;
        return Math.round(km * TRANSPORT_INR_PER_KM * costFactor * vehicles);
    }

    private void calculateStartDistance(UserPreferences prefs, GeoLocation dest, Itinerary itinerary) {
        GeoLocation start = locate(prefs.getStartLocation());
        if (start == null) {
            itinerary.setStartLabel(prefs.getStartLocation());
            return;
        }
        itinerary.setStartLabel(start.getLabel());
        double straightKm = HaversineUtil.distanceKm(start.getLat(), start.getLon(), dest.getLat(), dest.getLon());
        double km = -1;
        if (config.isApiKeyAvailable() && straightKm < 3000) {
            try {
                List<double[]> two = new ArrayList<>();
                two.add(new double[]{start.getLat(), start.getLon()});
                two.add(new double[]{dest.getLat(), dest.getLon()});
                km = orsClient.getMatrix(two).getKm(0, 1);   // -1 if there is no road (e.g. across the sea)
            } catch (IOException | RuntimeException e) {
                km = -1;
            }
        }
        if (km > 0) {
            itinerary.setStartToDestinationKm(km);
            itinerary.setStartDistanceIsRoad(true);
        } else {
            itinerary.setStartToDestinationKm(straightKm);
            itinerary.setStartDistanceIsRoad(false);
        }
        if (straightKm > 800) itinerary.setTravelModeHint("✈ Consider flying");
        else if (straightKm > 300) itinerary.setTravelModeHint("🚆 Train or flight recommended");
        else if (straightKm > 20) itinerary.setTravelModeHint("🚗 Comfortable by road");
        else itinerary.setTravelModeHint("You're already close by!");
    }

    /** Waits for the parallel start-location job (it normally finished long ago). */
    private void waitForStartDistance(Future<?> startJob, UserPreferences prefs, Itinerary itinerary) {
        try {
            startJob.get(20, TimeUnit.SECONDS);
        } catch (TimeoutException | ExecutionException e) {
            startJob.cancel(true);
            itinerary.setStartLabel(prefs.getStartLocation());   // header still shows what was typed
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------ STEP 5 + 6: build the days

    private void buildDays(UserPreferences prefs, Pace pace, List<Place> activities,
                           List<Place> foodPlaces, Itinerary itinerary) {
        int travelers = prefs.getTravelers();
        double lunchReserve = Math.round(250 * costFactor) * travelers;   // cheap lunch money kept aside

        for (int d = 0; d < prefs.getDays(); d++) {
            // DAILY BUDGET: the money still left is split evenly over the remaining days, so day 1
            // can't spend everything. Money not spent today automatically rolls over to tomorrow.
            double dayLimit = runningTotal + (prefs.getBudget() - runningTotal) / (prefs.getDays() - d);

            ArrayList<ItineraryItem> day = new ArrayList<>();
            ArrayList<String> categoriesToday = new ArrayList<>();
            int time = pace.getDayStart();
            Place previous = null;
            int activityCount = 0;
            boolean lunchDone = false;

            while (activityCount < pace.getMaxActivities()) {
                // lunch slot once the clock passes 12:00
                if (!lunchDone && previous != null && time >= LUNCH_TRIGGER) {
                    ItineraryItem lunch = planMeal("Lunch", previous, time, LUNCH_EARLIEST, pace.getLunchMinutes(),
                            foodPlaces, travelers, dayLimit);
                    lunchDone = true;
                    if (lunch != null) {
                        day.add(lunch);
                        time = lunch.getEndMinutes();
                        previous = lunch.getPlace();
                    }
                    continue;
                }

                // until lunch is planned, keep some money aside for it
                double spendable = dayLimit - (lunchDone ? 0 : lunchReserve);
                Place next = pickNextActivity(previous, time, activities, pace, travelers, spendable, categoriesToday);
                if (next == null) break;   // nothing fits in time or budget today

                Leg leg = previous == null ? new Leg(0, 0, true) : travel(previous, next);
                int arrive = previous == null ? time : time + leg.minutes + pace.getBufferMinutes();
                int leave = arrive + next.getEstimatedDurationMinutes();
                double cost = next.getEstimatedCostPerPerson() * travelers;
                double legTransport = transportCost(leg.km, travelers);

                day.add(new ActivityItem(next, arrive, leave, cost, leg.km, leg.minutes, leg.live));
                next.setUsed(true);
                categoriesToday.add(next.getCategory());
                runningTotal += cost + legTransport;
                transportTotal += legTransport;
                time = leave;
                previous = next;
                activityCount++;
            }

            // short day that ended before lunch -> still add lunch if it's not too late
            if (!lunchDone && previous != null && time <= 15 * 60) {
                ItineraryItem lunch = planMeal("Lunch", previous, time, LUNCH_EARLIEST, pace.getLunchMinutes(),
                        foodPlaces, travelers, dayLimit);
                if (lunch != null) {
                    day.add(lunch);
                    previous = lunch.getPlace();
                    time = lunch.getEndMinutes();
                }
            }
            // food lovers get a dinner recommendation too
            if (prefs.hasInterest("Food") && previous != null) {
                ItineraryItem dinner = planMeal("Dinner", previous, time, DINNER_EARLIEST, 75,
                        foodPlaces, travelers, dayLimit);
                if (dinner != null) day.add(dinner);
            }
            itinerary.addDay(day);
        }
    }

    /**
     * GREEDY NEAREST-NEIGHBOUR choice of the next activity.
     *  - first stop of the day: the highest-scoring unused place (days naturally cluster around it)
     *  - later stops: maximise  score - distancePenalty - varietyPenalty  (good AND close AND different)
     * BUDGET GUARD: a place is skipped if runningTotal + its cost would exceed today's budget.
     * TIME GUARD: a place is skipped if the visit would end after the day's end time.
     */
    private Place pickNextActivity(Place previous, int time, List<Place> activities, Pace pace,
                                   int travelers, double spendableBudget, ArrayList<String> categoriesToday) {
        Place best = null;
        double bestValue = Double.NEGATIVE_INFINITY;
        for (Place p : activities) {
            if (p.isUsed()) continue;

            Leg leg = previous == null ? new Leg(0, 0, true) : travel(previous, p);
            int arrive = previous == null ? time : time + leg.minutes + pace.getBufferMinutes();
            if (arrive + p.getEstimatedDurationMinutes() > pace.getDayEnd()) continue;   // TIME GUARD

            double cost = p.getEstimatedCostPerPerson() * travelers + transportCost(leg.km, travelers);
            if (runningTotal + cost > spendableBudget) {                            // BUDGET GUARD
                budgetBlocked = true;
                continue;   // try cheaper alternatives instead
            }

            int sameCategory = 0;
            for (String c : categoriesToday) {
                if (c.equals(p.getCategory())) sameCategory++;
            }
            double value = p.getScore() - leg.km * DISTANCE_PENALTY_PER_KM - sameCategory * VARIETY_PENALTY;
            if (value > bestValue) {
                bestValue = value;
                best = p;
            }
        }
        return best;
    }

    /**
     * Picks a restaurant near the previous stop for lunch/dinner (nearby matters twice as much).
     * If no listed restaurant is affordable/nearby we suggest a "local eatery" at the same spot.
     */
    private ItineraryItem planMeal(String label, Place previous, int time, int earliest, int duration,
                                   List<Place> foodPlaces, int travelers, double dayLimit) {
        Place best = null;
        Leg bestLeg = null;
        double bestValue = Double.NEGATIVE_INFINITY;
        for (Place f : foodPlaces) {
            if (f.isUsed()) continue;
            Leg leg = travel(previous, f);
            if (leg.km > 15) continue;   // too far for a meal break
            double cost = f.getEstimatedCostPerPerson() * travelers + transportCost(leg.km, travelers);
            if (runningTotal + cost > dayLimit) {
                budgetBlocked = true;
                continue;
            }
            double value = f.getScore() - leg.km * DISTANCE_PENALTY_PER_KM * 2;
            if (value > bestValue) {
                bestValue = value;
                best = f;
                bestLeg = leg;
            }
        }

        if (best == null) {
            // generic suggestion right where the traveller already is (cheaper local food)
            String nearName = previous.getName().replace("Local eatery near ", "");
            best = new Place("Local eatery near " + nearName, previous.getLat(), previous.getLon(), "Food");
            best.setEstimatedCostPerPerson(Math.round(250 * costFactor));
            best.setDescription("No listed restaurant close by, so grab a meal at a local eatery here.");
            bestLeg = new Leg(0, 0, true);
            if (runningTotal + best.getEstimatedCostPerPerson() * travelers > dayLimit) {
                return null;   // not even a cheap meal fits the budget
            }
        }

        int arrive = Math.max(time + bestLeg.minutes, earliest);
        double cost = best.getEstimatedCostPerPerson() * travelers;
        double legTransport = transportCost(bestLeg.km, travelers);
        ItineraryItem meal = new MealItem(best, arrive, arrive + duration, cost,
                bestLeg.km, bestLeg.minutes, bestLeg.live, label);   // a MealItem IS-A ItineraryItem
        best.setUsed(true);
        runningTotal += cost + legTransport;
        transportTotal += legTransport;
        return meal;
    }

    // ------------------------------------------------------------------ extras

    /**
     * Wikipedia summary for the chosen places only (never for all candidates), else a generic line.
     * MULTITHREADING: all Wikipedia requests are submitted to the thread pool at once, so 20 stops
     * take about as long as the slowest single request instead of 20 requests one after another.
     */
    private void addDescriptions(Itinerary itinerary, ExecutorService pool) {
        Map<Place, Future<String>> pending = new LinkedHashMap<>();
        for (ArrayList<ItineraryItem> day : itinerary.getDays()) {
            for (ItineraryItem item : day) {
                Place p = item.getPlace();
                if (!p.getDescription().isEmpty() || pending.containsKey(p)) continue;
                String text = p.getTag("description");
                // ignore junk descriptions in OSM (e.g. just "4.4")
                if (text != null && (text.length() < 15 || !text.matches(".*[A-Za-z]{3,}.*"))) text = null;
                if (text != null) {
                    p.setDescription(text);
                } else if (p.getCategory().equals("Food")) {
                    p.setDescription(genericDescription(p));
                } else {
                    pending.put(p, pool.submit(() -> wikipediaClient.getSummary(p.getTag("wikipedia"), p)));
                }
            }
        }
        for (Map.Entry<Place, Future<String>> entry : pending.entrySet()) {
            String text = null;
            try {
                text = entry.getValue().get(10, TimeUnit.SECONDS);
            } catch (TimeoutException | ExecutionException e) {
                entry.getValue().cancel(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Place p = entry.getKey();
            p.setDescription(text == null || text.isBlank() ? genericDescription(p) : text);
        }
    }

    private String genericDescription(Place p) {
        switch (p.getCategory()) {
            case "Beaches": return "A sandy beach - perfect for a stroll, the sunset and some sea breeze.";
            case "History": return "A historic " + tagOr(p, "historic", "landmark").replace('_', ' ')
                    + " worth exploring for its story and architecture.";
            case "Culture":
                if (p.hasTag("amenity", "place_of_worship")) return "A place of worship known for its peaceful atmosphere and architecture.";
                if (p.hasTag("tourism", "museum")) return "A museum worth exploring for its collections and local heritage.";
                if (p.hasTag("tourism", "gallery")) return "An art gallery featuring local and visiting artists.";
                if (p.hasTag("amenity", "theatre")) return "A theatre - check what's on during your visit.";
                if (p.hasTag("historic", "archaeological_site")) return "An archaeological site that reveals the area's ancient past.";
                return "A cultural spot with local art and heritage.";
            case "Nature": return "Green, open space to enjoy nature and fresh air.";
            case "Food":
                String cuisine = p.getTag("cuisine");
                return cuisine != null
                        ? "Popular for " + cuisine.replace(';', ',').replace('_', ' ') + " food."
                        : "A well-rated local spot to refuel.";
            case "Shopping": return "Browse local shops and pick up souvenirs.";
            case "Adventure": return "Get your adrenaline going with fun activities here.";
            case "Relaxation": return "Unwind and recharge at a calm, relaxing spot.";
            default: return "A popular local attraction worth a visit.";
        }
    }

    private String tagOr(Place p, String key, String fallback) {
        String v = p.getTag(key);
        return (v == null || v.equals("yes")) ? fallback : v;
    }

    private void addFriendlyNotes(Itinerary itinerary, Pace pace, UserPreferences prefs) {
        int wanted = pace.getMinActivities() * prefs.getDays();
        int got = itinerary.getActivityCount();
        if (got < wanted && budgetBlocked) {
            itinerary.getNotes().add("Your budget only covered " + got + " activities. Increase budget for more.");
        } else if (got < wanted) {
            itinerary.getNotes().add("We found only " + got + " matching places nearby, so some days are lighter. "
                    + "Try selecting more interests for a fuller plan.");
        }
    }
}
