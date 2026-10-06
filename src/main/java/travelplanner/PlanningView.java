package travelplanner;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The planning (home) screen, designed like a travel ticket: the route (From -> To) on top,
 * a perforated tear-off line, then the trip details. Validates the form before planning.
 * When the form is valid it hands a UserPreferences object to the onGenerate callback.
 */
public class PlanningView {

    public static final String[] INTERESTS = {
            "History", "Culture", "Nature", "Food", "Beaches", "Shopping", "Adventure", "Relaxation"
    };

    private final OpenRouteServiceClient orsClient;
    private final PhotonClient photonClient = new PhotonClient();
    private final BorderPane view = new BorderPane();

    private final TextField destinationField = new TextField();
    private final TextField startField = new TextField();
    private final TextField daysField = new TextField("3");
    private final TextField budgetField = new TextField("10000");
    private final TextField travelersField = new TextField("2");
    private final ArrayList<ToggleButton> interestToggles = new ArrayList<>();
    private final ToggleGroup paceGroup = new ToggleGroup();
    private final Label paceHint = new Label();
    private final Label errorBanner = new Label();

    public PlanningView(OpenRouteServiceClient orsClient, ConfigLoader config,
                        Consumer<UserPreferences> onGenerate, Runnable onClearCache) {
        this.orsClient = orsClient;

        // ----- top bar: app name + dark mode + clear saved searches -----
        Button clearCacheButton = new Button("Clear saved searches");
        clearCacheButton.getStyleClass().add("bar-button");
        clearCacheButton.setTooltip(new Tooltip("Deletes the cache folder so places are fetched fresh"));
        clearCacheButton.setOnAction(e -> onClearCache.run());
        view.setTop(topBar(themeButton(), clearCacheButton));

        // ----- heading -----
        Label title = new Label("Plan a trip");
        title.getStyleClass().add("page-title");
        Label intro = new Label("Real places from OpenStreetMap, arranged into days that fit your budget and pace.");
        intro.getStyleClass().add("page-intro");
        intro.setWrapText(true);
        VBox heading = new VBox(4, title, intro);

        // ----- ticket, part 1: the route -----
        destinationField.setPromptText("Goa, Paris, Jaipur…");
        startField.setPromptText("Your city");
        destinationField.getStyleClass().add("route-field");
        startField.getStyleClass().add("route-field");
        Label arrow = new Label("→");
        arrow.getStyleClass().add("route-arrow");
        arrow.setMinWidth(Region.USE_PREF_SIZE);
        HBox routeRow = new HBox(18, routeBox("From", startField), arrow, routeBox("To", destinationField));
        routeRow.setAlignment(Pos.TOP_LEFT);
        HBox.setMargin(arrow, new Insets(28, 0, 0, 0));
        VBox routeSection = new VBox(routeRow);
        routeSection.getStyleClass().add("ticket-section");

        // ----- ticket, part 2: the details -----
        daysField.setPromptText("1-14");
        budgetField.setPromptText("10000");
        travelersField.setPromptText("2");
        HBox numbersRow = new HBox(16,
                fieldBox("Days", daysField, 110),
                fieldBox("Travellers", travelersField, 110),
                fieldBox("Budget for the whole trip (₹)", budgetField, 260));

        FlowPane interestPane = new FlowPane(8, 8);
        for (String interest : INTERESTS) {
            ToggleButton toggle = new ToggleButton(emojiFor(interest) + "  " + interest);
            toggle.setUserData(interest);
            toggle.getStyleClass().add("interest-toggle");
            interestToggles.add(toggle);
            interestPane.getChildren().add(toggle);
        }

        HBox paceRow = new HBox();
        String[][] paces = {{"Relaxed", "Relaxed"}, {"Moderate", "Moderate"}, {"Packed", "Packed"}};
        for (int i = 0; i < paces.length; i++) {
            ToggleButton segment = new ToggleButton(paces[i][1]);
            segment.setUserData(paces[i][0]);
            segment.setToggleGroup(paceGroup);
            segment.getStyleClass().add("segment");
            if (i == 0) segment.getStyleClass().add("first");
            if (i == paces.length - 1) segment.getStyleClass().add("last");
            paceRow.getChildren().add(segment);
        }
        // a pace must always be chosen: clicking the selected segment again must not unselect it
        paceGroup.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null && old != null) old.setSelected(true);
            else updatePaceHint();
        });
        selectPace("Moderate");
        paceHint.getStyleClass().add("hint");
        HBox paceLine = new HBox(14, paceRow, paceHint);
        paceLine.setAlignment(Pos.CENTER_LEFT);

        errorBanner.getStyleClass().addAll("banner", "error-banner");
        errorBanner.setWrapText(true);
        errorBanner.setMaxWidth(Double.MAX_VALUE);
        hideError();

        Button generateButton = new Button("Plan my trip");
        generateButton.getStyleClass().add("primary-button");
        generateButton.setDefaultButton(true);
        generateButton.setOnAction(e -> {
            UserPreferences prefs = readForm();
            if (prefs != null) onGenerate.accept(prefs);
        });

        VBox detailsSection = new VBox(20,
                numbersRow,
                new VBox(10, groupTitle("What do you enjoy?"), interestPane),
                new VBox(10, groupTitle("Pace"), paceLine),
                errorBanner,
                generateButton);
        detailsSection.getStyleClass().add("ticket-section");

        VBox ticket = new VBox(routeSection, perforation(), detailsSection);
        ticket.getStyleClass().add("ticket");

        VBox page = new VBox(22, heading);
        if (!config.isApiKeyAvailable() && !config.getStatusMessage().isEmpty()) {
            Label keyNotice = new Label(config.getStatusMessage());
            keyNotice.getStyleClass().addAll("banner", "warning-banner");
            keyNotice.setWrapText(true);
            keyNotice.setMaxWidth(Double.MAX_VALUE);
            page.getChildren().add(keyNotice);
        }
        page.getChildren().add(ticket);
        page.setMaxWidth(760);

        StackPane centerBox = new StackPane(page);
        centerBox.setPadding(new Insets(34, 24, 40, 24));
        StackPane.setAlignment(page, Pos.TOP_CENTER);
        ScrollPane scroll = new ScrollPane(centerBox);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("scroll-area");
        view.setCenter(scroll);

        // put the cursor in the first field once the window is shown
        Platform.runLater(startField::requestFocus);
    }

    /** Navy bar with the app name on the left and the given buttons on the right (also used by ResultView). */
    static HBox topBar(Node... rightSide) {
        Label mark = new Label("◆");
        mark.getStyleClass().add("app-name-mark");
        Label name = new Label("Trip Planner");
        name.getStyleClass().add("app-name");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(8, mark, name, spacer);
        bar.getChildren().addAll(rightSide);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("top-bar");
        return bar;
    }

    /** Button that switches light/dark mode; its text always says what it will switch TO. */
    static Button themeButton() {
        Button b = new Button();
        b.getStyleClass().add("bar-button");
        Runnable refresh = () -> b.setText(Theme.isDark() ? "☀  Light mode" : "☾  Dark mode");
        refresh.run();
        Theme.darkProperty().addListener((obs, was, now) -> refresh.run());
        b.setOnAction(e -> Theme.toggle());
        return b;
    }

    private void selectPace(String pace) {
        for (Toggle t : paceGroup.getToggles()) {
            if (pace.equals(t.getUserData())) t.setSelected(true);
        }
        updatePaceHint();
    }

    private void updatePaceHint() {
        Toggle t = paceGroup.getSelectedToggle();
        Pace pace = Pace.fromLabel(t == null ? "Moderate" : (String) t.getUserData());
        paceHint.setText(pace.getMinActivities() + "-" + pace.getMaxActivities() + " places a day, starting "
                + ItineraryItem.formatTime(pace.getDayStart()));
    }

    public Node getView() {
        return view;
    }

    /**
     * Reads + validates the form. Returns null (and shows the red banner) if anything is wrong.
     * Uses exceptions for validation: NumberFormatException for non-numbers,
     * IllegalArgumentException (thrown by UserPreferences.validate) for rule violations.
     */
    private UserPreferences readForm() {
        hideError();
        try {
            int days = parseWholeNumber(daysField.getText(), "Number of days");
            double budget = parseBudget(budgetField.getText());
            int travelers = parseWholeNumber(travelersField.getText(), "Number of travelers");

            ArrayList<String> interests = new ArrayList<>();
            for (ToggleButton toggle : interestToggles) {
                if (toggle.isSelected()) interests.add((String) toggle.getUserData());
            }
            String pace = paceGroup.getSelectedToggle() == null
                    ? "Moderate" : (String) paceGroup.getSelectedToggle().getUserData();

            UserPreferences prefs = new UserPreferences(destinationField.getText(), startField.getText(),
                    days, budget, travelers, interests, pace);
            prefs.validate();
            return prefs;
        } catch (IllegalArgumentException e) {
            // NumberFormatException is a subclass of IllegalArgumentException, so both land here
            showError(e.getMessage());
            return null;
        }
    }

    private int parseWholeNumber(String text, String fieldName) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new NumberFormatException(fieldName + " must be a whole number (for example 3).");
        }
    }

    private double parseBudget(String text) {
        String cleaned = text.replace(",", "").replace("₹", "").trim();
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Budget must be a number in rupees (for example 10000).");
        }
    }

    public void showError(String message) {
        errorBanner.setText(message);
        errorBanner.setVisible(true);
        errorBanner.setManaged(true);
    }

    public void hideError() {
        errorBanner.setVisible(false);
        errorBanner.setManaged(false);
    }

    /** "NEW TRIP" button: reset everything to defaults. */
    public void clearForm() {
        destinationField.clear();
        startField.clear();
        daysField.setText("3");
        budgetField.setText("10000");
        travelersField.setText("2");
        for (ToggleButton toggle : interestToggles) toggle.setSelected(false);
        selectPace("Moderate");
        hideError();
    }

    // ------------------------------------------------------------------ small UI helpers

    private Label groupTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("group-title");
        return label;
    }

    /** "From" / "To" block: small label, big underlined field, suggestion list below. */
    private VBox routeBox(String labelText, TextField field) {
        Label label = new Label(labelText);
        label.getStyleClass().add("route-label");
        VBox box = new VBox(4, label, field, createAutocomplete(field));
        HBox.setHgrow(box, Priority.ALWAYS);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private VBox fieldBox(String labelText, TextField field, double width) {
        Label label = new Label(labelText);
        label.getStyleClass().add("field-label");
        VBox box = new VBox(6, label, field);
        box.setPrefWidth(width);
        return box;
    }

    /** Dashed tear-off line with a half-circle notch on each side, like a ticket stub. */
    private StackPane perforation() {
        Line line = new Line(0, 0, 100, 0);
        line.getStyleClass().add("perforation");
        Circle left = new Circle(11);
        Circle right = new Circle(11);
        left.getStyleClass().add("notch");
        right.getStyleClass().add("notch");
        StackPane strip = new StackPane(line, left, right);
        strip.setMinHeight(22);
        StackPane.setAlignment(left, Pos.CENTER_LEFT);
        StackPane.setAlignment(right, Pos.CENTER_RIGHT);
        left.setTranslateX(-11);
        right.setTranslateX(11);
        // the dashed line always spans the ticket's width (minus the notches)
        line.endXProperty().bind(strip.widthProperty().subtract(44));
        return strip;
    }

    /**
     * Live suggestions while typing. A PauseTransition waits 250 ms after the last key press
     * (debounce) so we don't call the API on every single letter.
     * The network call runs in a background Task so the UI never freezes.
     */
    private ListView<String> createAutocomplete(TextField field) {
        ListView<String> list = new ListView<>();
        list.getStyleClass().add("suggestions");
        list.setFocusTraversable(false);   // keeps keyboard focus in the text field
        list.setPrefHeight(150);
        list.setFixedCellSize(34);
        list.setVisible(false);
        list.setManaged(false);

        // flag to ignore the text change caused by picking a suggestion
        final boolean[] ignoreNextChange = {false};
        PauseTransition debounce = new PauseTransition(Duration.millis(250));

        field.textProperty().addListener((obs, oldText, newText) -> {
            if (ignoreNextChange[0]) {
                ignoreNextChange[0] = false;
                return;
            }
            debounce.playFromStart();
        });

        debounce.setOnFinished(e -> {
            String text = field.getText().trim();
            if (text.length() < 3 || !field.isFocused()) {
                hideList(list);
                return;
            }
            // background network call (allowed exception to "no threads": keeps the UI responsive)
            Task<List<String>> task = new Task<>() {
                @Override
                protected List<String> call() {
                    // Photon is fast and needs no key; OpenRouteService is the backup
                    List<String> suggestions = photonClient.suggest(text);
                    return suggestions.isEmpty() ? orsClient.autocomplete(text) : suggestions;
                }
            };
            task.setOnSucceeded(ev -> {
                List<String> suggestions = task.getValue();
                // ignore stale results if the user kept typing
                if (suggestions.isEmpty() || !field.getText().trim().equals(text) || !field.isFocused()) {
                    hideList(list);
                    return;
                }
                list.getItems().setAll(suggestions);
                list.setPrefHeight(Math.min(suggestions.size(), 5) * 34 + 6);
                list.setVisible(true);
                list.setManaged(true);
            });
            Thread thread = new Thread(task);
            thread.setDaemon(true);
            thread.start();
        });

        list.setOnMouseClicked(e -> {
            String chosen = list.getSelectionModel().getSelectedItem();
            if (chosen != null) {
                ignoreNextChange[0] = true;
                field.setText(chosen);
                field.positionCaret(chosen.length());
            }
            hideList(list);
        });
        field.focusedProperty().addListener((obs, was, isNow) -> {
            if (!isNow) hideList(list);
        });
        field.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) hideList(list);
        });
        return list;
    }

    private void hideList(ListView<String> list) {
        list.setVisible(false);
        list.setManaged(false);
        list.getSelectionModel().clearSelection();
    }

    /** Emoji for each interest / category (also used by the result screen). */
    public static String emojiFor(String category) {
        switch (category) {
            case "Beaches": return "🏖";
            case "History": return "🏰";
            case "Nature": return "🌿";
            case "Food": return "🍴";
            case "Shopping": return "🛍";
            case "Culture": return "🎭";
            case "Adventure": return "🏄";   // (🧗 is too new for JavaFX fonts)
            case "Relaxation": return "💆";
            default: return "📍";
        }
    }
}
