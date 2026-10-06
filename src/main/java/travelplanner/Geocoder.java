package travelplanner;

import java.io.IOException;

/**
 * INTERFACE: turns a typed place name ("Bangalore") into coordinates.
 *
 * Implemented by OpenRouteServiceClient (HeiGIT), NominatimClient and FallbackData.
 * TravelPlanner tries them in order through this one interface (polymorphism):
 * HeiGIT -> Nominatim -> offline demo cities.
 */
public interface Geocoder {

    /** Returns the location, or null if the name was not found. Throws IOException if unreachable. */
    GeoLocation geocode(String text) throws IOException;

    String getServiceName();
}
