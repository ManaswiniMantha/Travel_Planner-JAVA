package travelplanner;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * Saves an itinerary as a plain .txt file using standard Java File I/O
 * (BufferedWriter + FileWriter inside try-with-resources, so the file is always closed).
 */
public class FileManager {

    public static void saveItinerary(Itinerary it, File file) throws IOException {
        UserPreferences p = it.getPreferences();
        String line = "=".repeat(64);

        try (BufferedWriter w = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
            w.write(line); w.newLine();
            w.write("   YOUR PERSONALIZED TRIP - " + p.getDestination().toUpperCase()); w.newLine();
            w.write(line); w.newLine();
            w.write("Destination     : " + it.getDestinationLabel()); w.newLine();
            w.write("Starting from   : " + it.getStartLabel()); w.newLine();
            if (it.getStartToDestinationKm() > 0) {
                w.write("Journey         : " + startDistanceText(it) + "  " + it.getTravelModeHint()); w.newLine();
            }
            w.write("Days            : " + p.getDays()); w.newLine();
            w.write("Travelers       : " + p.getTravelers()); w.newLine();
            w.write("Budget          : " + Itinerary.formatInr(p.getBudget())); w.newLine();
            w.write("Interests       : " + String.join(", ", p.getInterests())); w.newLine();
            w.write("Pace            : " + p.getPace()); w.newLine();
            w.newLine();

            int dayNumber = 1;
            for (ArrayList<ItineraryItem> day : it.getDays()) {
                w.write("---------------------------- DAY " + dayNumber++ + " ----------------------------");
                w.newLine();
                if (day.isEmpty()) {
                    w.write("  Free day - explore the area at your own pace."); w.newLine();
                }
                for (int i = 0; i < day.size(); i++) {
                    ItineraryItem item = day.get(i);
                    if (i > 0) {
                        w.write("      |  " + travelText(item)); w.newLine();
                    }
                    String label = item.getLabel();   // polymorphism: ActivityItem / MealItem decide
                    w.write(String.format("  %8s - %-8s  [%s] %s", item.getStartTime(), item.getEndTime(),
                            label, item.getPlace().getName()));
                    w.newLine();
                    String local = it.toLocalCurrency(item.getCost());
                    w.write("                       Est. cost: " + Itinerary.formatInr(item.getCost())
                            + (local.isEmpty() ? "" : "  (" + local + ")"));
                    w.newLine();
                    if (!item.getPlace().getDescription().isEmpty()) {
                        w.write("                       " + item.getPlace().getDescription()); w.newLine();
                    }
                }
                w.newLine();
            }

            w.write(line); w.newLine();
            w.write("TRIP SUMMARY"); w.newLine();
            w.write(line); w.newLine();
            w.write("Activities             : " + it.getActivityCount()); w.newLine();
            w.write("Places & meals (est.)  : " + Itinerary.formatInr(it.getTotalPlaceCost())); w.newLine();
            w.write("Local transport (est.) : " + Itinerary.formatInr(it.getTotalTransportCost())); w.newLine();
            w.write("Estimated total cost   : " + Itinerary.formatInr(it.getTotalCost())); w.newLine();
            w.write("Budget                 : " + Itinerary.formatInr(it.getBudget())); w.newLine();
            w.write("Remaining budget       : " + Itinerary.formatInr(it.getRemainingBudget())); w.newLine();
            w.write(String.format("Total travel distance  : %.1f km", it.getTotalDistanceKm())); w.newLine();
            w.write("Total travel time      : " + ItineraryItem.formatDuration(it.getTotalTravelMinutes())); w.newLine();
            w.newLine();
            for (String note : it.getNotes()) {
                w.write("Note: " + note); w.newLine();
            }
            w.write("Note: All costs are estimates (entry fees, meals, local taxi). Stay and intercity travel not included.");
            w.newLine();
            w.write(it.isUsedLiveRouting()
                    ? "Routing: live road distances from OpenRouteService."
                    : "Routing: estimated distances (straight line x 1.3) - live routing was unavailable.");
            w.newLine();
            if (it.isUsingOfflineData()) {
                w.write("Places: offline demo data was used (map service unreachable)."); w.newLine();
            }
        }
    }

    public static String travelText(ItineraryItem item) {
        String km = String.format("%.1f km", item.getTravelDistanceKmFromPrevious());
        if (item.getTravelDistanceKmFromPrevious() < 0.05) return "a short walk";
        if (item.isDistanceLive()) return item.getTravelMinutesFromPrevious() + " min · " + km;
        return item.getTravelMinutesFromPrevious() + " min · ≈ " + km + " (est.)";
    }

    public static String startDistanceText(Itinerary it) {
        String km = String.format("%,.0f km", it.getStartToDestinationKm());
        return it.isStartDistanceIsRoad() ? km + " by road" : "≈ " + km + " straight-line";
    }
}
