package travelplanner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Tests the straight-line distance formula used when live routing is not available. */
class HaversineUtilTest {

    @Test
    @DisplayName("Distance from a point to itself is zero")
    void samePoint() {
        assertEquals(0.0, HaversineUtil.distanceKm(12.97, 77.59, 12.97, 77.59), 0.001);
    }

    @Test
    @DisplayName("Bangalore to Chennai is about 290 km in a straight line")
    void bangaloreToChennai() {
        double km = HaversineUtil.distanceKm(12.9716, 77.5946, 13.0827, 80.2707);
        assertEquals(290, km, 10);   // allowed error: 10 km
    }

    @Test
    @DisplayName("Distance is the same in both directions")
    void symmetric() {
        double ab = HaversineUtil.distanceKm(15.49, 73.82, 48.85, 2.35);
        double ba = HaversineUtil.distanceKm(48.85, 2.35, 15.49, 73.82);
        assertEquals(ab, ba, 0.0001);
    }
}
