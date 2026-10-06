package travelplanner;

/**
 * SUBCLASS of ItineraryItem: a sight or activity (fort, beach, museum...).
 * Inherits all fields and time formatting; overrides only what is different.
 */
public class ActivityItem extends ItineraryItem {

    public ActivityItem(Place place, int startMinutes, int endMinutes, double cost,
                        double travelKm, int travelMinutes, boolean distanceLive) {
        super(place, startMinutes, endMinutes, cost, travelKm, travelMinutes, distanceLive);
    }

    @Override
    public boolean isMeal() {
        return false;
    }

    @Override
    public String getLabel() {
        return getPlace().getCategory();   // e.g. "History"
    }
}
