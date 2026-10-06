package travelplanner;

/**
 * Starts the app. It deliberately does NOT extend javafx.application.Application:
 * when the main class is a plain class, Java can load JavaFX from the normal classpath
 * instead of as Java modules, which avoids the "Module jdk.jsobject not found" error that
 * the map's web view causes on new Java versions. All it does is call Main.
 */
public class Launcher {

    public static void main(String[] args) {
        Main.main(args);
    }
}
