package travelplanner;

/**
 * CUSTOM UNCHECKED EXCEPTION: the destination could not be found, or has no tourist places.
 * It extends IllegalArgumentException, so Main already shows its message to the user in the
 * red error banner (Main checks "instanceof IllegalArgumentException").
 */
public class DestinationNotFoundException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public DestinationNotFoundException(String message) {
        super(message);
    }
}
