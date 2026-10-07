package travelplanner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the "getting there and back" transport options and prices. */
class TransportEstimatorTest {

    private boolean has(List<TransportOption> options, TransportOption.Mode mode) {
        for (TransportOption o : options) {
            if (o.getMode() == mode) return true;
        }
        return false;
    }

    private TransportOption get(List<TransportOption> options, TransportOption.Mode mode) {
        for (TransportOption o : options) {
            if (o.getMode() == mode) return o;
        }
        throw new AssertionError(mode + " missing");
    }

    @Test
    @DisplayName("Short trips offer road and rail, but no flight")
    void shortTripHasNoFlight() {
        List<TransportOption> options = TransportEstimator.estimate(120, 150, true, 2);
        assertFalse(has(options, TransportOption.Mode.FLIGHT));
        assertTrue(has(options, TransportOption.Mode.CAR));
        assertTrue(has(options, TransportOption.Mode.BUS));
        assertTrue(has(options, TransportOption.Mode.TRAIN));
    }

    @Test
    @DisplayName("Very long trips recommend flying")
    void longTripRecommendsFlight() {
        List<TransportOption> options = TransportEstimator.estimate(1750, 2200, true, 2);
        assertTrue(get(options, TransportOption.Mode.FLIGHT).isRecommended());
        assertFalse(has(options, TransportOption.Mode.BUS), "no 2,000 km bus rides");
    }

    @Test
    @DisplayName("International trips only offer flights")
    void internationalOnlyFlights() {
        List<TransportOption> options = TransportEstimator.estimate(7000, -1, false, 2);
        assertEquals(1, options.size());
        assertEquals(TransportOption.Mode.FLIGHT, options.get(0).getMode());
    }

    @Test
    @DisplayName("Prices are for a return journey for the whole group")
    void returnPriceForGroup() {
        // train: 2.0 per km per person, 500 km, 3 people, there and back
        TransportOption train = get(TransportEstimator.estimate(400, 500, true, 3), TransportOption.Mode.TRAIN);
        assertEquals(2.0 * 500 * 3 * 2, train.getReturnCostForGroup(), 0.01);
    }

    @Test
    @DisplayName("Car cost is per car: 5 travellers need 2 cars")
    void carCostPerVehicle() {
        double fourPeople = get(TransportEstimator.estimate(200, 250, true, 4), TransportOption.Mode.CAR).getReturnCostForGroup();
        double fivePeople = get(TransportEstimator.estimate(200, 250, true, 5), TransportOption.Mode.CAR).getReturnCostForGroup();
        assertEquals(2 * fourPeople, fivePeople, 0.01);
    }

    @Test
    @DisplayName("Exactly one option is recommended, and options are sorted cheapest first")
    void oneRecommendedAndSorted() {
        List<TransportOption> options = TransportEstimator.estimate(600, 750, true, 2);
        int recommended = 0;
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).isRecommended()) recommended++;
            if (i > 0) assertTrue(options.get(i).getReturnCostForGroup() >= options.get(i - 1).getReturnCostForGroup());
        }
        assertEquals(1, recommended);
    }

    @Test
    @DisplayName("No options when start and destination are the same place")
    void alreadyThere() {
        assertTrue(TransportEstimator.estimate(5, 6, true, 2).isEmpty());
    }

    @Test
    @DisplayName("A chosen journey is added on top of the plan's cost; none chosen adds nothing")
    void chosenJourneyAddsToTotal() {
        TravelPlanner planner = TestData.planner(new TestData.FakePlaceProvider(false));
        Itinerary it = planner.generate(TestData.prefs(2, 50000, 2, "Moderate", "History"), message -> { });
        double planOnly = it.getTotalCost();
        assertEquals(planOnly, it.getGrandTotal(), 0.01);

        TransportOption train = get(TransportEstimator.estimate(400, 500, true, 2), TransportOption.Mode.TRAIN);
        it.setChosenTransport(train);
        assertEquals(planOnly + train.getReturnCostForGroup(), it.getGrandTotal(), 0.01);
        assertEquals(50000 - it.getGrandTotal(), it.getRemainingAfterJourney(), 0.01);
        assertEquals(planOnly, it.getTotalCost(), 0.01, "the activity plan itself is unchanged");
    }
}
