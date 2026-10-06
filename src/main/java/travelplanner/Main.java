package travelplanner;

import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;

/**
 * Entry point. Builds the window and switches between the two screens:
 *   PlanningView (form)  ->  ResultView (itinerary)
 * Run with:  mvn javafx:run
 */
public class Main extends Application {

    private Stage stage;
    private final StackPane root = new StackPane();
    private PlanningView planningView;
    private Itinerary currentItinerary;
    private TravelPlanner planner;

    // loading overlay shown while the itinerary is being generated
    private final VBox loadingOverlay = new VBox();
    private final Label loadingLabel = new Label();

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        Theme.loadFonts();
        ConfigLoader config = new ConfigLoader();
        OpenRouteServiceClient orsClient = new OpenRouteServiceClient(config);
        planner = new TravelPlanner(config, orsClient);

        planningView = new PlanningView(orsClient, config, this::generateItinerary, this::clearCache);
        buildLoadingOverlay();
        showPlanning();

        // size the window to fit the screen
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        double width = Math.min(1320, screen.getWidth() - 40);
        double height = Math.min(860, screen.getHeight() - 40);
        Scene scene = new Scene(root, width, height);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        Theme.attach(scene);   // light or dark mode (remembered from last time)

        stage.setTitle("Trip Planner");
        stage.setMinWidth(900);
        stage.setMinHeight(560);
        stage.setScene(scene);
        stage.show();
    }

    // ------------------------------------------------------------------ screen switching

    private void showPlanning() {
        root.getChildren().setAll(planningView.getView(), loadingOverlay);
    }

    private void showResult(Itinerary itinerary) {
        currentItinerary = itinerary;
        ResultView resultView = new ResultView(itinerary,
                this::showPlanning,                // ← MODIFY TRIP: form keeps all values
                this::saveItinerary,
                () -> {                            // NEW TRIP: empty form
                    planningView.clearForm();
                    showPlanning();
                });
        root.getChildren().setAll(resultView.getView(), loadingOverlay);
    }

    // ------------------------------------------------------------------ generation (background)

    /**
     * Network calls take a few seconds, so they run in a JavaFX Task on a background thread.
     * The whole planning run happens on this background thread: it keeps the window responsive
     * and lets us show progress. (Inside the planner, a thread pool runs some network calls in
     * parallel - see TravelPlanner and OverpassClient.) Task's updateMessage() and the onSucceeded/onFailed handlers safely run on the
     * JavaFX UI thread for us (internally via Platform.runLater).
     */
    private void generateItinerary(UserPreferences prefs) {
        Task<Itinerary> task = new Task<>() {
            @Override
            protected Itinerary call() {
                return planner.generate(prefs, message -> updateMessage(message));
            }
        };

        loadingLabel.textProperty().bind(task.messageProperty());
        setLoading(true);

        task.setOnSucceeded(e -> {
            setLoading(false);
            showResult(task.getValue());
        });
        task.setOnFailed(e -> {
            setLoading(false);
            Throwable error = task.getException();
            if (error instanceof IllegalArgumentException) {
                planningView.showError(error.getMessage());   // friendly message from the planner
            } else {
                System.out.println("Unexpected error: " + error);
                planningView.showError("Something went wrong while planning. Please check your internet "
                        + "connection and try again.");
            }
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);   // don't keep the app alive after the window closes
        thread.start();
    }

    private void buildLoadingOverlay() {
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(64, 64);
        loadingLabel.getStyleClass().add("loading-text");
        Label hint = new Label("Looking up real places and routes. The first search for a city can take "
                + "up to half a minute; repeat searches are instant.");
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);
        hint.setMaxWidth(380);
        VBox card = new VBox(16, spinner, loadingLabel, hint);
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().add("loading-card");
        card.setMaxSize(460, 260);

        loadingOverlay.getChildren().add(card);
        loadingOverlay.setAlignment(Pos.CENTER);
        loadingOverlay.getStyleClass().add("loading-overlay");
        loadingOverlay.setVisible(false);
    }

    private void setLoading(boolean loading) {
        loadingOverlay.setVisible(loading);
        if (!loading) loadingLabel.textProperty().unbind();
    }

    // ------------------------------------------------------------------ save + cache

    private void saveItinerary() {
        if (currentItinerary == null) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save itinerary");
        String safeName = currentItinerary.getPreferences().getDestination().replaceAll("[^A-Za-z0-9]+", "_");
        chooser.setInitialFileName("itinerary_" + safeName + ".txt");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text files", "*.txt"));
        File file = chooser.showSaveDialog(stage);
        if (file == null) return;   // user cancelled

        try {
            FileManager.saveItinerary(currentItinerary, file);
            showAlert(Alert.AlertType.INFORMATION, "Saved",
                    "Itinerary saved successfully!\n\nSaved to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Could not save",
                    "Sorry, the itinerary could not be saved there. Try another folder.\n(" + e.getMessage() + ")");
        }
    }

    private void clearCache() {
        int removed = CacheManager.clear();
        showAlert(Alert.AlertType.INFORMATION, "Cache cleared",
                removed + " cached file(s) removed. The next search will fetch fresh data.");
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.initOwner(stage);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.getDialogPane().getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        if (Theme.isDark()) alert.getDialogPane().getStyleClass().add("dark");
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
