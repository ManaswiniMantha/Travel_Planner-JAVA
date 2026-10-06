package travelplanner;

import java.util.ArrayList;

/**
 * Everything the user typed on the planning form.
 * validate() throws IllegalArgumentException with a friendly message the UI can show directly.
 */
public class UserPreferences {

    public static final int MAX_DAYS = 14;

    private String destination;
    private String startLocation;
    private int days;
    private double budget;      // total trip budget in INR
    private int travelers;
    private ArrayList<String> interests;
    private String pace;        // "Relaxed", "Moderate" or "Packed"

    public UserPreferences(String destination, String startLocation, int days, double budget,
                           int travelers, ArrayList<String> interests, String pace) {
        this.destination = destination == null ? "" : destination.trim();
        this.startLocation = startLocation == null ? "" : startLocation.trim();
        this.days = days;
        this.budget = budget;
        this.travelers = travelers;
        this.interests = interests == null ? new ArrayList<>() : interests;
        this.pace = pace == null ? "Moderate" : pace;
    }

    /** Checks every rule; the first broken rule becomes the error message. */
    public void validate() {
        if (destination.isEmpty()) {
            throw new IllegalArgumentException("Please enter a destination (e.g. Goa, Paris).");
        }
        if (startLocation.isEmpty()) {
            throw new IllegalArgumentException("Please enter where you are starting from (e.g. Chennai).");
        }
        if (days <= 0) {
            throw new IllegalArgumentException("Number of days must be at least 1.");
        }
        if (days > MAX_DAYS) {
            throw new IllegalArgumentException("Please plan at most " + MAX_DAYS + " days at a time.");
        }
        if (budget <= 0) {
            throw new IllegalArgumentException("Budget must be more than ₹0.");
        }
        if (travelers <= 0) {
            throw new IllegalArgumentException("Number of travelers must be at least 1.");
        }
        if (travelers > 50) {
            throw new IllegalArgumentException("Please enter 50 travelers or fewer.");
        }
        if (interests.isEmpty()) {
            throw new IllegalArgumentException("Pick at least one interest so we know what you like.");
        }
    }

    public boolean hasInterest(String interest) {
        return interests.contains(interest);
    }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public String getStartLocation() { return startLocation; }
    public void setStartLocation(String startLocation) { this.startLocation = startLocation; }
    public int getDays() { return days; }
    public void setDays(int days) { this.days = days; }
    public double getBudget() { return budget; }
    public void setBudget(double budget) { this.budget = budget; }
    public int getTravelers() { return travelers; }
    public void setTravelers(int travelers) { this.travelers = travelers; }
    public ArrayList<String> getInterests() { return interests; }
    public void setInterests(ArrayList<String> interests) { this.interests = interests; }
    public String getPace() { return pace; }
    /** The pace as an enum (used by the planner); getPace() keeps the text for the UI. */
    public Pace getPaceType() { return Pace.fromLabel(pace); }
    public void setPace(String pace) { this.pace = pace; }
}
