package travelplanner;

/**
 * CUSTOM CHECKED EXCEPTION: a place source (PlaceProvider) could not deliver places.
 * Being "checked" (extends Exception), the compiler forces TravelPlanner to handle it,
 * which is exactly where we switch to the next provider.
 */
public class PlaceSearchException extends Exception {

    private static final long serialVersionUID = 1L;

    public PlaceSearchException(String message) {
        super(message);
    }

    public PlaceSearchException(String message, Throwable cause) {
        super(message, cause);   // keeps the original error for debugging
    }
}
