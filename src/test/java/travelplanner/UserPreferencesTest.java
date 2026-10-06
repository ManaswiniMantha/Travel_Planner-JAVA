package travelplanner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Tests the form validation rules (every red error banner on the planning screen). */
class UserPreferencesTest {

    private UserPreferences prefs(String dest, String start, int days, double budget, int travelers,
                                  List<String> interests) {
        return new UserPreferences(dest, start, days, budget, travelers, new ArrayList<>(interests), "Moderate");
    }

    private final List<String> history = List.of("History");

    @Test
    @DisplayName("A correctly filled form passes validation")
    void validFormPasses() {
        assertDoesNotThrow(() -> prefs("Goa", "Chennai", 3, 10000, 2, history).validate());
    }

    @Test
    @DisplayName("Empty destination or start location is rejected")
    void emptyFieldsRejected() {
        assertThrows(IllegalArgumentException.class, () -> prefs("", "Chennai", 3, 10000, 2, history).validate());
        assertThrows(IllegalArgumentException.class, () -> prefs("Goa", "   ", 3, 10000, 2, history).validate());
    }

    @Test
    @DisplayName("Days must be between 1 and 14")
    void dayLimits() {
        assertThrows(IllegalArgumentException.class, () -> prefs("Goa", "Chennai", 0, 10000, 2, history).validate());
        assertThrows(IllegalArgumentException.class, () -> prefs("Goa", "Chennai", 15, 10000, 2, history).validate());
        assertDoesNotThrow(() -> prefs("Goa", "Chennai", 1, 10000, 2, history).validate());
        assertDoesNotThrow(() -> prefs("Goa", "Chennai", 14, 10000, 2, history).validate());
    }

    @Test
    @DisplayName("Budget and travelers must be positive")
    void budgetAndTravelers() {
        assertThrows(IllegalArgumentException.class, () -> prefs("Goa", "Chennai", 3, 0, 2, history).validate());
        assertThrows(IllegalArgumentException.class, () -> prefs("Goa", "Chennai", 3, 10000, 0, history).validate());
    }

    @Test
    @DisplayName("At least one interest is required")
    void interestRequired() {
        assertThrows(IllegalArgumentException.class, () -> prefs("Goa", "Chennai", 3, 10000, 2, List.of()).validate());
    }

    @Test
    @DisplayName("Extra spaces around names are removed")
    void namesAreTrimmed() {
        UserPreferences p = prefs("  Goa  ", " Chennai ", 3, 10000, 2, history);
        assertEquals("Goa", p.getDestination());
        assertEquals("Chennai", p.getStartLocation());
    }
}
