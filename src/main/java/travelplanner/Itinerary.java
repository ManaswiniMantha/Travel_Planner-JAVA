package travelplanner;

import java.util.ArrayList;

/**
 * The finished plan: a list of days (each day = list of ItineraryItem) plus totals and status flags.
 */
public class Itinerary {

    private UserPreferences preferences;
    private ArrayList<ArrayList<ItineraryItem>> days = new ArrayList<>();

    private String destinationLabel = "";
    private String countryCode = "";
    private String startLabel = "";
    private double startToDestinationKm = -1;      // -1 = unknown
    private boolean startDistanceIsRoad = false;   // true = live road distance, false = straight line
    private String travelModeHint = "";

    // totals (all estimates)
    private double totalPlaceCost;
    private double totalTransportCost;
    private double totalDistanceKm;
    private int totalTravelMinutes;
    private int activityCount;

    // status flags shown as banners in the UI
    private boolean usedLiveRouting;
    private boolean apiKeyMissing;
    private boolean usingOfflineData;
    private ArrayList<String> notes = new ArrayList<>();

    // local currency display (only for international trips)
    private String currencyCode = "INR";
    private String currencySymbol = "₹";
    private double inrToLocalRate = 0;   // 0 = not available -> hide equivalents

    public Itinerary(UserPreferences preferences) {
        this.preferences = preferences;
    }

    public void addDay(ArrayList<ItineraryItem> day) {
        days.add(day);
    }

    /** Recomputes the totals by looping over every item. */
    public void calculateTotals(double transportCost) {
        totalPlaceCost = 0;
        totalDistanceKm = 0;
        totalTravelMinutes = 0;
        activityCount = 0;
        for (ArrayList<ItineraryItem> day : days) {
            for (ItineraryItem item : day) {
                totalPlaceCost += item.getCost();
                totalDistanceKm += item.getTravelDistanceKmFromPrevious();
                totalTravelMinutes += item.getTravelMinutesFromPrevious();
                if (!item.isMeal()) activityCount++;
            }
        }
        totalTransportCost = transportCost;
    }

    public double getTotalCost() { return totalPlaceCost + totalTransportCost; }
    public double getBudget() { return preferences.getBudget(); }
    public double getRemainingBudget() { return getBudget() - getTotalCost(); }

    /** "≈ €12" style text, or "" when the exchange rate is unavailable / trip is in India. */
    public String toLocalCurrency(double inr) {
        if (inrToLocalRate <= 0 || "INR".equals(currencyCode)) return "";
        double local = inr * inrToLocalRate;
        return "≈ " + currencySymbol + String.format("%,.0f", local);
    }

    public static String formatInr(double amount) {
        return (amount < 0 ? "-₹" : "₹") + String.format("%,.0f", Math.abs(amount));
    }

    public UserPreferences getPreferences() { return preferences; }
    public ArrayList<ArrayList<ItineraryItem>> getDays() { return days; }
    public String getDestinationLabel() { return destinationLabel; }
    public void setDestinationLabel(String destinationLabel) { this.destinationLabel = destinationLabel; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public String getStartLabel() { return startLabel; }
    public void setStartLabel(String startLabel) { this.startLabel = startLabel; }
    public double getStartToDestinationKm() { return startToDestinationKm; }
    public void setStartToDestinationKm(double km) { this.startToDestinationKm = km; }
    public boolean isStartDistanceIsRoad() { return startDistanceIsRoad; }
    public void setStartDistanceIsRoad(boolean road) { this.startDistanceIsRoad = road; }
    public String getTravelModeHint() { return travelModeHint; }
    public void setTravelModeHint(String travelModeHint) { this.travelModeHint = travelModeHint; }
    public double getTotalPlaceCost() { return totalPlaceCost; }
    public double getTotalTransportCost() { return totalTransportCost; }
    public double getTotalDistanceKm() { return totalDistanceKm; }
    public int getTotalTravelMinutes() { return totalTravelMinutes; }
    public int getActivityCount() { return activityCount; }
    public boolean isUsedLiveRouting() { return usedLiveRouting; }
    public void setUsedLiveRouting(boolean usedLiveRouting) { this.usedLiveRouting = usedLiveRouting; }
    public boolean isApiKeyMissing() { return apiKeyMissing; }
    public void setApiKeyMissing(boolean apiKeyMissing) { this.apiKeyMissing = apiKeyMissing; }
    public boolean isUsingOfflineData() { return usingOfflineData; }
    public void setUsingOfflineData(boolean usingOfflineData) { this.usingOfflineData = usingOfflineData; }
    public ArrayList<String> getNotes() { return notes; }
    public String getCurrencyCode() { return currencyCode; }
    public String getCurrencySymbol() { return currencySymbol; }
    public double getInrToLocalRate() { return inrToLocalRate; }

    public void setCurrency(String code, String symbol, double rate) {
        this.currencyCode = code;
        this.currencySymbol = symbol;
        this.inrToLocalRate = rate;
    }
}
