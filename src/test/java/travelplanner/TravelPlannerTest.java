package travelplanner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the planning algorithm with fake offline data (see TestData).
 * Each test checks one rule the itinerary must always follow.
 */
class TravelPlannerTest {

    private Itinerary plan(int days, double budget, int travelers, String pace, String... interests) {
        TravelPlanner planner = TestData.planner(new TestData.FakePlaceProvider(false));
        return planner.generate(TestData.prefs(days, budget, travelers, pace, interests), message -> { });
    }

    @Test
    @DisplayName("Creates exactly the number of days the user asked for")
    void createsRequestedNumberOfDays() {
        for (int days = 1; days <= 5; days++) {
            Itinerary it = plan(days, 50000, 2, "Moderate", "History", "Nature");
            assertEquals(days, it.getDays().size(), "days for a " + days + "-day trip");
        }
    }

    @Test
    @DisplayName("Estimated cost never goes over the budget")
    void neverExceedsBudget() {
        double[] budgets = {1500, 4000, 10000, 30000, 100000};
        for (double budget : budgets) {
            Itinerary it = plan(3, budget, 4, "Packed", "History", "Culture", "Food");
            assertTrue(it.getTotalCost() <= budget,
                    "cost " + it.getTotalCost() + " is over the budget " + budget);
        }
    }

    @Test
    @DisplayName("Never plans more activities per day than the pace allows")
    void respectsPaceLimit() {
        for (Pace pace : Pace.values()) {
            Itinerary it = plan(3, 100000, 2, pace.getLabel(), "History", "Nature", "Culture", "Shopping");
            for (ArrayList<ItineraryItem> day : it.getDays()) {
                int activities = TestData.countTypes(day).getOrDefault("activity", 0);
                assertTrue(activities <= pace.getMaxActivities(),
                        pace + " allows " + pace.getMaxActivities() + " but a day has " + activities);
            }
        }
    }

    @Test
    @DisplayName("Activities finish before the end of the day")
    void activitiesEndOnTime() {
        for (Pace pace : Pace.values()) {
            Itinerary it = plan(2, 100000, 2, pace.getLabel(), "History", "Nature", "Culture");
            for (ArrayList<ItineraryItem> day : it.getDays()) {
                for (ItineraryItem item : day) {
                    if (!item.isMeal()) {
                        assertTrue(item.getEndMinutes() <= pace.getDayEnd(),
                                item + " ends after the " + pace + " day end");
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Lunch is never scheduled before 12:30 PM")
    void lunchNotTooEarly() {
        Itinerary it = plan(3, 50000, 2, "Moderate", "History", "Nature");
        boolean sawLunch = false;
        for (ArrayList<ItineraryItem> day : it.getDays()) {
            for (ItineraryItem item : day) {
                if ("Lunch".equals(item.getMealLabel())) {
                    sawLunch = true;
                    assertTrue(item.getStartMinutes() >= 12 * 60 + 30, "lunch at " + item.getStartTime());
                }
            }
        }
        assertTrue(sawLunch, "every normal trip should include at least one lunch");
    }

    @Test
    @DisplayName("Stops in a day never overlap in time")
    void stopsDoNotOverlap() {
        Itinerary it = plan(3, 50000, 2, "Packed", "History", "Nature", "Food");
        for (ArrayList<ItineraryItem> day : it.getDays()) {
            for (int i = 1; i < day.size(); i++) {
                assertTrue(day.get(i).getStartMinutes() >= day.get(i - 1).getEndMinutes(),
                        day.get(i) + " starts before " + day.get(i - 1) + " ends");
            }
        }
    }

    @Test
    @DisplayName("No place is visited twice in the same trip")
    void noPlaceVisitedTwice() {
        Itinerary it = plan(4, 100000, 2, "Packed", "History", "Nature", "Culture", "Food");
        Set<Place> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ArrayList<ItineraryItem> day : it.getDays()) {
            for (ItineraryItem item : day) {
                assertTrue(seen.add(item.getPlace()), item.getPlace().getName() + " appears twice");
            }
        }
    }

    @Test
    @DisplayName("Unknown destination gives a friendly DestinationNotFoundException")
    void unknownDestinationThrows() {
        TravelPlanner planner = TestData.planner(new TestData.FakePlaceProvider(false));
        UserPreferences prefs = new UserPreferences("Nowhereland", "Startville", 2, 10000, 2,
                new ArrayList<>(java.util.List.of("History")), "Moderate");
        DestinationNotFoundException e = assertThrows(DestinationNotFoundException.class,
                () -> planner.generate(prefs, message -> { }));
        assertTrue(e.getMessage().contains("couldn't find"), "message should explain the problem");
    }

    @Test
    @DisplayName("If the first place source fails, the next one is used (polymorphism + fallback)")
    void fallsBackToNextProvider() {
        TravelPlanner planner = TestData.planner(new TestData.BrokenPlaceProvider(),
                new TestData.FakePlaceProvider(true));
        Itinerary it = planner.generate(TestData.prefs(2, 20000, 2, "Moderate", "History"), message -> { });
        assertTrue(it.isUsingOfflineData(), "offline banner should be switched on");
        assertTrue(it.getActivityCount() > 0, "the fallback places should still produce a plan");
    }

    @Test
    @DisplayName("A very small budget produces a shorter plan with an explanation")
    void tinyBudgetExplainsItself() {
        Itinerary it = plan(3, 300, 4, "Packed", "History", "Culture", "Shopping");
        assertTrue(it.getTotalCost() <= 300, "still within budget");
        assertFalse(it.getNotes().isEmpty(), "the user should be told why the plan is short");
    }

    @Test
    @DisplayName("Without an API key, distances are marked as estimates")
    void offlineModeUsesEstimates() {
        Itinerary it = plan(2, 50000, 2, "Moderate", "History");
        assertFalse(it.isUsedLiveRouting(), "no key -> no live routing");
        assertTrue(it.isApiKeyMissing());
    }
}
