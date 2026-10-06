package travelplanner;

/**
 * SUBCLASS of ItineraryItem: a lunch or dinner stop.
 * Adds one extra field (the meal type) and overrides isMeal(), getLabel() and getMealLabel().
 */
public class MealItem extends ItineraryItem {

    private final String mealType;   // "Lunch" or "Dinner"

    public MealItem(Place place, int startMinutes, int endMinutes, double cost,
                    double travelKm, int travelMinutes, boolean distanceLive, String mealType) {
        super(place, startMinutes, endMinutes, cost, travelKm, travelMinutes, distanceLive);
        this.mealType = mealType;
    }

    @Override
    public boolean isMeal() {
        return true;
    }

    @Override
    public String getLabel() {
        return mealType;
    }

    @Override
    public String getMealLabel() {
        return mealType;
    }
}
