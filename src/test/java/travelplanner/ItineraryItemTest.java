package travelplanner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests time formatting and the ActivityItem / MealItem subclasses (inheritance + polymorphism). */
class ItineraryItemTest {

    @Test
    @DisplayName("Minutes after midnight are shown as 12-hour clock times")
    void formatTime() {
        assertEquals("9:30 AM", ItineraryItem.formatTime(9 * 60 + 30));
        assertEquals("12:00 AM", ItineraryItem.formatTime(0));
        assertEquals("12:00 PM", ItineraryItem.formatTime(12 * 60));
        assertEquals("7:05 PM", ItineraryItem.formatTime(19 * 60 + 5));
    }

    @Test
    @DisplayName("Durations are shown in hours and minutes")
    void formatDuration() {
        assertEquals("45 min", ItineraryItem.formatDuration(45));
        assertEquals("1 hr", ItineraryItem.formatDuration(60));
        assertEquals("2 hr 15 min", ItineraryItem.formatDuration(135));
    }

    @Test
    @DisplayName("Polymorphism: the same method call behaves differently for each subclass")
    void polymorphism() {
        Place fort = new Place("Test Fort", 0, 0, "History");
        Place cafe = new Place("Test Cafe", 0, 0, "Food");

        // both objects are stored as the PARENT type...
        List<ItineraryItem> day = List.of(
                new ActivityItem(fort, 600, 690, 200, 0, 0, true),
                new MealItem(cafe, 750, 810, 500, 1.2, 5, true, "Lunch"));

        // ...but each one answers with its own overridden version
        assertFalse(day.get(0).isMeal());
        assertEquals("History", day.get(0).getLabel());
        assertEquals("", day.get(0).getMealLabel());

        assertTrue(day.get(1).isMeal());
        assertEquals("Lunch", day.get(1).getLabel());
        assertEquals("Lunch", day.get(1).getMealLabel());
    }
}
