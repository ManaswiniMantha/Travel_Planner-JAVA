package travelplanner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the Pace enum. */
class PaceTest {

    @Test
    @DisplayName("Form text is converted to the right enum constant (any letter case)")
    void fromLabel() {
        assertEquals(Pace.RELAXED, Pace.fromLabel("Relaxed"));
        assertEquals(Pace.PACKED, Pace.fromLabel("packed"));
        assertEquals(Pace.MODERATE, Pace.fromLabel("MODERATE"));
    }

    @Test
    @DisplayName("Unknown or missing pace falls back to Moderate")
    void unknownDefaultsToModerate() {
        assertEquals(Pace.MODERATE, Pace.fromLabel("Superfast"));
        assertEquals(Pace.MODERATE, Pace.fromLabel(null));
    }

    @Test
    @DisplayName("Every pace has sensible settings")
    void settingsAreSensible() {
        for (Pace p : Pace.values()) {
            assertTrue(p.getMinActivities() <= p.getMaxActivities(), p + ": min <= max");
            assertTrue(p.getDayStart() < p.getDayEnd(), p + ": day starts before it ends");
        }
        assertTrue(Pace.PACKED.getMaxActivities() > Pace.RELAXED.getMaxActivities(), "packed > relaxed");
    }

    @Test
    @DisplayName("Relaxed pace suits beaches, Moderate suits history")
    void suits() {
        HashMap<String, String> beachTags = new HashMap<>();
        beachTags.put("natural", "beach");
        Place beach = OverpassClient.buildPlace("Test Beach", 15.5, 73.8, beachTags);
        HashMap<String, String> fortTags = new HashMap<>();
        fortTags.put("historic", "fort");
        Place fort = OverpassClient.buildPlace("Test Fort", 15.5, 73.8, fortTags);

        assertTrue(Pace.RELAXED.suits(beach));
        assertFalse(Pace.RELAXED.suits(fort));
        assertTrue(Pace.MODERATE.suits(fort));
    }
}
