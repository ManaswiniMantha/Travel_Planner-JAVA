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
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The planning (home) screen: a card-style form with validation.
 * When the form is valid it hands a UserPreferences object to the onGenerate callback.
 */
public class PlanningView {

    public static final String[] INTERESTS = {
            "Adventure", "Beaches", "Nature", "History", "Food", "Shopping", "Culture", "Relaxation"
    };

    private final OpenRouteServiceClient orsClient;
    private final BorderPane view = new BorderPane();

    private final TextField destinationField = new TextField();
    private final TextField startField = new TextField();
    private final TextField daysField = new TextField("3");
    private final TextField budgetField = new TextField("10000");
    private final TextField travelersField = new TextField("2");
    private final ArrayList<CheckBox> interestBoxes = new ArrayList<>();
    private final ToggleGroup paceGroup = new ToggleGroup();
    private final Label errorBanner = new Label();

    public PlanningView(OpenRouteServiceClient orsClient, ConfigLoader config,
                        Consumer<UserPreferences> onGenerate, Runnable onClearCache) {
        this.orsClient = orsClient;

        // ----- gradient header -----
        Label title = new Label("✈  Personalized Travel Planner");
        title.getStyleClass().add("header-title");
        Label subtitle = new Label("Tell us about your trip — we build a day-by-day plan from real places on the map.");
        subtitle.getStyleClass().add("header-subtitle");
        VBox titles = new VBox(4, title, subtitle);
        Button clearCacheButton = new Button("🗑 Clear cache");
        clearCacheButton.getStyleClass().add("ghost-button");
        clearCacheButton.setOnAction(e -> onClearCache.run());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(titles, spacer, clearCacheButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("header");
        view.setTop(header);

        // ----- form card -----
        VBox card = new VBox(18);
        card.getStyleClass().add("card");
        card.setMaxWidth(780);

        if (!config.isApiKeyAvailable()) {
            Label keyNotice = new Label("ℹ  " + config.getStatusMessage());
            keyNotice.getStyleClass().addAll("banner", "info-banner");
            keyNotice.setWrapText(true);
            keyNotice.setMaxWidth(Double.MAX_VALUE);
            card.getChildren().add(keyNotice);
        }

        // where
        destinationField.setPromptText("e.g. Goa, Paris, Jaipur");
        startField.setPromptText("e.g. Chennai");
        HBox whereRow = new HBox(16,
                fieldBox("🎯 Destination", destinationField, true),
                fieldBox("🏠 Starting location", startField, true));
        card.getChildren().addAll(sectionTitle("📍 Where are you going?"), whereRow);

        // trip details
        daysField.setPromptText("1 - 14");
        budgetField.setPromptText("e.g. 10000");
        travelersField.setPromptText("e.g. 2");
        HBox detailsRow = new HBox(16,
                fieldBox("🗓 Number of days", daysField, false),
                fieldBox("💰 Budget (₹ total)", budgetField, false),
                fieldBox("👥 Travelers", travelersField, false));
        card.getChildren().addAll(sectionTitle("📋 Trip details"), detailsRow);

        // interests as checkbox "chips"
        FlowPane interestPane = new FlowPane(10, 10);
        for (String interest : INTERESTS) {
            CheckBox box = new CheckBox(emojiFor(interest) + "  " + interest);
            box.setUserData(interest);
            box.getStyleClass().add("chip");
            interestBoxes.add(box);
            interestPane.getChildren().add(box);
        }
        card.getChildren().addAll(sectionTitle("❤ What do you enjoy?"), interestPane);

        // pace radio buttons
        HBox paceRow = new HBox(12);
        String[][] paces = {
                {"Relaxed", "🐢 Relaxed  (2-3 / day)"},
                {"Moderate", "🚶 Moderate  (3-4 / day)"},
                {"Packed", "🏃 Packed  (5-6 / day)"}};
        for (String[] p : paces) {
            RadioButton rb = new RadioButton(p[1]);
            rb.setUserData(p[0]);
            rb.setToggleGroup(paceGroup);
            rb.getStyleClass().add("pace-radio");
            if (p[0].equals("Moderate")) rb.setSelected(true);
            paceRow.getChildren().add(rb);
        }
        card.getChildren().addAll(sectionTitle("⏱ Travel pace"), paceRow);

        // error banner (hidden until a validation error happens)
        errorBanner.getStyleClass().addAll("banner", "error-banner");
        errorBanner.setWrapText(true);
        errorBanner.setMaxWidth(Double.MAX_VALUE);
        hideError();

        Button generateButton = new Button("✨  GENERATE MY ITINERARY");
        generateButton.getStyleClass().add("primary-button");
        generateButton.setMaxWidth(Double.MAX_VALUE);
        generateButton.setDefaultButton(true);
        generateButton.setOnAction(e -> {
            UserPreferences prefs = readForm();
            if (prefs != null) onGenerate.accept(prefs);
        });
        card.getChildren().addAll(errorBanner, generateButton);

        StackPane centerBox = new StackPane(card);
        centerBox.setPadding(new Insets(28, 24, 36, 24));
        StackPane.setAlignment(card, Pos.TOP_CENTER);
        ScrollPane scroll = new ScrollPane(centerBox);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("scroll-area");
        view.setCenter(scroll);

        // put the cursor in the first field once the window is shown
        Platform.runLater(destinationField::requestFocus);
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
            for (CheckBox box : interestBoxes) {
                if (box.isSelected()) interests.add((String) box.getUserData());
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
        errorBanner.setText("⚠  " + message);
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
        for (CheckBox box : interestBoxes) box.setSelected(false);
        for (Toggle t : paceGroup.getToggles()) {
            if ("Moderate".equals(t.getUserData())) t.setSelected(true);
        }
        hideError();
    }

    // ------------------------------------------------------------------ small UI helpers

    private Label sectionTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    private VBox fieldBox(String labelText, TextField field, boolean withAutocomplete) {
        Label label = new Label(labelText);
        label.getStyleClass().add("field-label");
        VBox box = new VBox(6, label, field);
        if (withAutocomplete) box.getChildren().add(createAutocomplete(field));
        HBox.setHgrow(box, Priority.ALWAYS);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
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
            if (!orsClient.isAutocompleteAvailable() || text.length() < 3 || !field.isFocused()) {
                hideList(list);
                return;
            }
            // background network call (allowed exception to "no threads": keeps the UI responsive)
            Task<List<String>> task = new Task<>() {
                @Override
                protected List<String> call() {
                    return orsClient.autocomplete(text);
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
