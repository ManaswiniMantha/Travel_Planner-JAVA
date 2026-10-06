package travelplanner;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Scene;
import javafx.scene.text.Font;

import java.io.InputStream;
import java.util.prefs.Preferences;

/**
 * Light / dark mode and the app's fonts and day colours.
 *
 * Dark mode works by adding the CSS class "dark" to the scene's root node; style.css redefines
 * its colour variables under ".root.dark". The choice is remembered between runs with
 * java.util.prefs (stored by Java in the user's profile, not in the project folder).
 */
public final class Theme {

    /** One colour per day (route line + timeline + map pins). Repeats after 7 days. */
    private static final String[] DAY_COLORS = {
            "#F4A300", "#0F7B74", "#7A4FA0", "#2B7BBF", "#C2185B", "#5F7F2A", "#8A6A3B"
    };

    private static final Preferences PREFS = Preferences.userNodeForPackage(Theme.class);
    private static final String DARK_KEY = "darkMode";
    private static final BooleanProperty DARK = new SimpleBooleanProperty(PREFS.getBoolean(DARK_KEY, false));

    private Theme() { }   // only static members

    public static String dayColor(int dayIndex) {
        return DAY_COLORS[dayIndex % DAY_COLORS.length];
    }

    public static BooleanProperty darkProperty() {
        return DARK;
    }

    public static boolean isDark() {
        return DARK.get();
    }

    public static void toggle() {
        DARK.set(!DARK.get());
        PREFS.putBoolean(DARK_KEY, DARK.get());
    }

    /** Applies the current mode now and every time it changes. */
    public static void attach(Scene scene) {
        apply(scene, DARK.get());
        DARK.addListener((obs, wasDark, isDark) -> apply(scene, isDark));
    }

    private static void apply(Scene scene, boolean dark) {
        scene.getRoot().getStyleClass().remove("dark");
        if (dark) scene.getRoot().getStyleClass().add("dark");
    }

    /** Loads the bundled IBM Plex Sans font files (must happen before the CSS is applied). */
    public static void loadFonts() {
        for (String weight : new String[]{"Regular", "Medium", "SemiBold", "Bold"}) {
            try (InputStream in = Theme.class.getResourceAsStream("/fonts/IBMPlexSans-" + weight + ".ttf")) {
                if (in != null) Font.loadFont(in, 14);
            } catch (Exception e) {
                System.out.println("Font not loaded (" + weight + "): " + e.getMessage());   // system font is used
            }
        }
    }
}
