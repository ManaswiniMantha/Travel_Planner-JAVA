package travelplanner;

/**
 * ENUM: the three travel paces. Each constant carries its own settings through the enum's
 * constructor, so all pace rules live in ONE place instead of being spread over switch statements.
 *
 * Viva note: an enum is a special class with a fixed set of objects. Enums can have fields,
 * constructors (always private) and methods - suits(Place) below is an example.
 */
public enum Pace {

    //          label       min max  day start   day end     buffer  durationFactor lunch
    RELAXED ("Relaxed",   2,  3,  10 * 60,      19 * 60,      20,     1.25,          75),
    MODERATE("Moderate",  3,  4,  9 * 60 + 30,  19 * 60 + 30, 15,     1.0,           60),
    PACKED  ("Packed",    5,  6,  9 * 60,       20 * 60,      10,     0.75,          45);

    private final String label;
    private final int minActivities;
    private final int maxActivities;
    private final int dayStart;          // minutes after midnight
    private final int dayEnd;
    private final int bufferMinutes;     // extra time between stops (parking, tickets, photos...)
    private final double durationFactor; // relaxed travellers stay longer at each place
    private final int lunchMinutes;

    Pace(String label, int minActivities, int maxActivities, int dayStart, int dayEnd,
         int bufferMinutes, double durationFactor, int lunchMinutes) {
        this.label = label;
        this.minActivities = minActivities;
        this.maxActivities = maxActivities;
        this.dayStart = dayStart;
        this.dayEnd = dayEnd;
        this.bufferMinutes = bufferMinutes;
        this.durationFactor = durationFactor;
        this.lunchMinutes = lunchMinutes;
    }

    /** Converts the text from the form ("Relaxed", "Moderate", "Packed") into the enum. */
    public static Pace fromLabel(String text) {
        for (Pace p : values()) {
            if (p.label.equalsIgnoreCase(text)) return p;
        }
        return MODERATE;   // safe default
    }

    /** Does this kind of place suit the pace? (used for a small score bonus) */
    public boolean suits(Place p) {
        String c = p.getCategory();
        switch (this) {
            case RELAXED:
                return c.equals("Beaches") || c.equals("Nature") || c.equals("Relaxation") || p.hasTag("amenity", "cafe");
            case PACKED:
                return p.getEstimatedDurationMinutes() <= 75;
            default: // MODERATE
                return c.equals("History") || c.equals("Culture") || c.equals("Sightseeing");
        }
    }

    public String getLabel() { return label; }
    public int getMinActivities() { return minActivities; }
    public int getMaxActivities() { return maxActivities; }
    public int getDayStart() { return dayStart; }
    public int getDayEnd() { return dayEnd; }
    public int getBufferMinutes() { return bufferMinutes; }
    public double getDurationFactor() { return durationFactor; }
    public int getLunchMinutes() { return lunchMinutes; }
}
