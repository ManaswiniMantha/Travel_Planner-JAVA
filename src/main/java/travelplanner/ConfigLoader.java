package travelplanner;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Reads settings from config.properties in the project root.
 *
 * The API key is NEVER written in the code. If the file is missing, or the key is
 * empty / still the placeholder, the app keeps working in "offline mode"
 * (straight-line distances, no autocomplete) instead of crashing.
 */
public class ConfigLoader {

    public static final String PLACEHOLDER_KEY = "YOUR_API_KEY_HERE";
    private static final String CONFIG_FILE = "config.properties";

    private String apiKey = "";
    private double searchRadiusKm = 12;
    private boolean apiKeyAvailable = false;
    private String statusMessage = "";

    public ConfigLoader() {
        load();
    }

    /**
     * Second constructor (constructor OVERLOADING), used by the automated tests:
     * settings are given directly instead of being read from config.properties.
     * Pass an empty key for offline mode, so tests never call the internet.
     */
    ConfigLoader(String apiKey, double searchRadiusKm) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.searchRadiusKm = searchRadiusKm;
        this.apiKeyAvailable = !this.apiKey.isEmpty() && !this.apiKey.equals(PLACEHOLDER_KEY);
    }

    private void load() {
        File file = new File(CONFIG_FILE);
        if (!file.exists()) {
            statusMessage = "No config.properties found. Copy config.properties.example to config.properties "
                    + "and paste your free OpenRouteService key to enable live routing. Running in offline mode.";
            return;
        }

        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            statusMessage = "Could not read config.properties. Running in offline mode.";
            return;
        }

        apiKey = props.getProperty("ORS_API_KEY", "").trim();

        try {
            searchRadiusKm = Double.parseDouble(props.getProperty("SEARCH_RADIUS_KM", "12").trim());
        } catch (NumberFormatException e) {
            searchRadiusKm = 12; // bad value in the file -> use the default
        }
        // keep the radius sensible so Overpass queries stay small
        if (searchRadiusKm < 3) searchRadiusKm = 3;
        if (searchRadiusKm > 50) searchRadiusKm = 50;

        apiKeyAvailable = !apiKey.isEmpty() && !apiKey.equals(PLACEHOLDER_KEY);
        if (!apiKeyAvailable) {
            statusMessage = "No OpenRouteService API key set in config.properties (ORS_API_KEY). "
                    + "Running in offline mode: distances are estimated and suggestions are off.";
        }
    }

    public String getApiKey() { return apiKey; }
    public double getSearchRadiusKm() { return searchRadiusKm; }
    public boolean isApiKeyAvailable() { return apiKeyAvailable; }
    public String getStatusMessage() { return statusMessage; }
}
