package travelplanner;

/**
 * ABSTRACT CLASS: one stop in a day - a place with its time slot, cost and the travel needed
 * to reach it.
 *
 * Two subclasses (inheritance):
 *   - ActivityItem : a sight / activity
 *   - MealItem     : a lunch or dinner stop
 *
 * Each subclass OVERRIDES isMeal() and getLabel(). Code that works with a list of
 * ItineraryItem (ResultView, FileManager, Itinerary) simply calls item.getLabel() and Java
 * runs the right version at runtime - that is polymorphism.
 */
public abstract class ItineraryItem {

    private final Place place;
    private final int startMinutes;               // minutes after midnight, e.g. 570 = 9:30 AM
    private final int endMinutes;
    private final double cost;                    // estimated INR for the whole group
    private final double travelDistanceKmFromPrevious;
    private final int travelMinutesFromPrevious;
    private final boolean distanceLive;           // true = from OpenRouteService, false = Haversine estimate

    protected ItineraryItem(Place place, int startMinutes, int endMinutes, double cost,
                            double travelKm, int travelMinutes, boolean distanceLive) {
        this.place = place;
        this.startMinutes = startMinutes;
        this.endMinutes = endMinutes;
        this.cost = cost;
        this.travelDistanceKmFromPrevious = travelKm;
        this.travelMinutesFromPrevious = travelMinutes;
        this.distanceLive = distanceLive;
    }

    /** true for lunch/dinner stops. Implemented differently by each subclass. */
    public abstract boolean isMeal();

    /** Tag shown on the card: the category ("History") or the meal ("Lunch"). */
    public abstract String getLabel();

    /** "Lunch" / "Dinner" for meals; empty for activities (MealItem overrides this). */
    public String getMealLabel() {
        return "";
    }

    /** Converts minutes-after-midnight into "9:30 AM" style text. */
    public static String formatTime(int minutes) {
        int h = (minutes / 60) % 24;
        int m = minutes % 60;
        String amPm = h < 12 ? "AM" : "PM";
        int h12 = h % 12;
        if (h12 == 0) h12 = 12;
        return String.format("%d:%02d %s", h12, m, amPm);
    }

    /** Formats a duration like "2 hr 15 min" or "25 min". */
    public static String formatDuration(int minutes) {
        if (minutes < 60) return minutes + " min";
        int h = minutes / 60;
        int m = minutes % 60;
        return m == 0 ? h + " hr" : h + " hr " + m + " min";
    }

    public String getStartTime() { return formatTime(startMinutes); }
    public String getEndTime() { return formatTime(endMinutes); }

    public Place getPlace() { return place; }
    public int getStartMinutes() { return startMinutes; }
    public int getEndMinutes() { return endMinutes; }
    public double getCost() { return cost; }
    public double getTravelDistanceKmFromPrevious() { return travelDistanceKmFromPrevious; }
    public int getTravelMinutesFromPrevious() { return travelMinutesFromPrevious; }
    public boolean isDistanceLive() { return distanceLive; }

    @Override
    public String toString() {
        return getStartTime() + " - " + getEndTime() + "  " + place.getName() + " [" + getLabel() + "]";
    }
}
