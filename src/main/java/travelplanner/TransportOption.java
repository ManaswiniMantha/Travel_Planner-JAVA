package travelplanner;

/**
 * One way of getting from the start city to the destination and back, e.g. "Train, about
 * 7 hours, ₹2,800 return for 2". Created by TransportEstimator; immutable (all fields final).
 */
public class TransportOption {

    /** ENUM of the transport types, each with its icon and display name. */
    public enum Mode {
        FLIGHT("✈", "Flight"),
        TRAIN("🚆", "Train (3AC)"),
        BUS("🚌", "Bus (AC)"),
        CAR("🚗", "Car (fuel + tolls)");

        private final String icon;
        private final String displayName;

        Mode(String icon, String displayName) {
            this.icon = icon;
            this.displayName = displayName;
        }

        public String getIcon() { return icon; }
        public String getDisplayName() { return displayName; }
    }

    private final Mode mode;
    private final double hoursOneWay;
    private final double returnCostForGroup;   // INR, there and back, whole group
    private boolean recommended;

    public TransportOption(Mode mode, double hoursOneWay, double returnCostForGroup) {
        this.mode = mode;
        this.hoursOneWay = hoursOneWay;
        this.returnCostForGroup = returnCostForGroup;
    }

    public Mode getMode() { return mode; }
    public double getHoursOneWay() { return hoursOneWay; }
    public double getReturnCostForGroup() { return returnCostForGroup; }
    public boolean isRecommended() { return recommended; }
    void setRecommended(boolean recommended) { this.recommended = recommended; }

    /** "about 6 h" / "about 45 min" */
    public String getDurationText() {
        if (hoursOneWay < 1) return "about " + Math.max(10, Math.round(hoursOneWay * 60 / 5) * 5) + " min";
        return "about " + Math.round(hoursOneWay) + " h";
    }

    @Override
    public String toString() {
        return mode.getDisplayName() + ", " + getDurationText() + ", " + Itinerary.formatInr(returnCostForGroup);
    }
}
