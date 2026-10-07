package travelplanner;

import java.util.ArrayList;
import java.util.List;

/**
 * Estimates how to get from the start city to the destination: which transport types make
 * sense for the distance, how long each takes, and roughly what a RETURN journey costs for
 * the whole group.
 *
 * The prices are simple per-km rates for India (2026), like the activity prices: good enough
 * to compare options and plan a budget, not real ticket prices.
 *
 *   Flight : ₹2,500 + ₹3.5/km per person (international: ₹5,000 + ₹6/km), 2.5 h airport time + 650 km/h
 *   Train  : ₹2.0/km per person (3AC), 55 km/h, only within one country
 *   Bus    : ₹2.6/km per person (AC sleeper/seater), 45 km/h, up to 1,200 km
 *   Car    : ₹11/km per car (fuel + tolls), one car per 4 travellers, 55 km/h, up to 1,500 km
 */
public final class TransportEstimator {

    private static final double ROAD_FACTOR = 1.25;   // roads are ~25% longer than a straight line

    private TransportEstimator() { }   // utility class: static methods only

    /**
     * @param straightKm   straight-line distance start -> destination
     * @param roadKm       road distance if known (live routing), otherwise a value <= 0
     * @param sameCountry  false for international trips (then only flights make sense)
     * @param travelers    group size
     * @return the sensible options, cheapest first, one marked as recommended; empty if very close
     */
    public static List<TransportOption> estimate(double straightKm, double roadKm, boolean sameCountry, int travelers) {
        List<TransportOption> options = new ArrayList<>();
        if (straightKm < 15 || travelers < 1) return options;   // already there
        double groundKm = roadKm > 0 ? roadKm : straightKm * ROAD_FACTOR;

        if (straightKm >= 250) {
            double perPerson = sameCountry ? 2500 + 3.5 * straightKm : 5000 + 6 * straightKm;
            options.add(new TransportOption(TransportOption.Mode.FLIGHT,
                    2.5 + straightKm / 650, perPerson * travelers * 2));
        }
        if (sameCountry) {
            if (groundKm >= 60 && groundKm <= 3500) {
                options.add(new TransportOption(TransportOption.Mode.TRAIN,
                        groundKm / 55 + 0.5, Math.max(250, 2.0 * groundKm) * travelers * 2));
            }
            if (groundKm <= 1200) {
                options.add(new TransportOption(TransportOption.Mode.BUS,
                        groundKm / 45, Math.max(150, 2.6 * groundKm) * travelers * 2));
            }
            if (groundKm <= 1500) {
                int cars = (int) Math.ceil(travelers / 4.0);
                options.add(new TransportOption(TransportOption.Mode.CAR,
                        groundKm / 55, 11 * groundKm * cars * 2));
            }
        }
        options.sort((a, b) -> Double.compare(a.getReturnCostForGroup(), b.getReturnCostForGroup()));
        markRecommended(options);
        return options;
    }

    /** The cheapest option that takes at most 12 hours; if none does, the fastest one. */
    private static void markRecommended(List<TransportOption> options) {
        if (options.isEmpty()) return;
        TransportOption best = null;
        for (TransportOption o : options) {   // list is sorted cheapest first
            if (o.getHoursOneWay() <= 12) {
                best = o;
                break;
            }
        }
        if (best == null) {
            best = options.get(0);
            for (TransportOption o : options) {
                if (o.getHoursOneWay() < best.getHoursOneWay()) best = o;
            }
        }
        best.setRecommended(true);
    }
}
