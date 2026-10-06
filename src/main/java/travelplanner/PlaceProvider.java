package travelplanner;

import java.util.List;

/**
 * INTERFACE: anything that can supply candidate places for a destination.
 *
 * Implemented by:
 *   - OverpassClient  (live OpenStreetMap data)
 *   - FallbackData    (small offline demo file)
 *
 * TravelPlanner keeps a List<PlaceProvider> and tries each one in turn. It never needs to know
 * WHICH class it is talking to - that is runtime polymorphism (dynamic method dispatch).
 * Adding a new source later (e.g. another places API) only means writing one new class.
 */
public interface PlaceProvider {

    /** Places around the centre. Throws PlaceSearchException if this source cannot answer. */
    List<Place> findPlaces(GeoLocation centre, double radiusKm, List<String> interests) throws PlaceSearchException;

    /** Readable name for log messages, e.g. "Overpass (OpenStreetMap)". */
    String getServiceName();

    /** True for demo/offline data, so the UI can show the "offline data" banner. */
    default boolean isOfflineData() {
        return false;
    }
}
