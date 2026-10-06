package travelplanner;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;

import java.util.ArrayList;

/**
 * The itinerary result screen: header, status banners, one card per day and a trip summary.
 */
public class ResultView {

    private final BorderPane view = new BorderPane();
    private final Itinerary itinerary;

    public ResultView(Itinerary itinerary, Runnable onModify, Runnable onSave, Runnable onNewTrip) {
        this.itinerary = itinerary;
        UserPreferences p = itinerary.getPreferences();

        // ----- header -----
        Label title = new Label("🗺  YOUR PERSONALIZED TRIP");
        title.getStyleClass().add("header-title");
        Label details = new Label(titleCase(p.getDestination()) + "  ·  " + p.getDays() + (p.getDays() == 1 ? " Day" : " Days")
                + "  ·  " + p.getTravelers() + (p.getTravelers() == 1 ? " Traveler" : " Travelers")
                + "  ·  Budget " + Itinerary.formatInr(p.getBudget()));
        details.getStyleClass().add("header-subtitle");
        details.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        FlowPane chips = new FlowPane(8, 8);
        chips.getChildren().add(chip("🏠 Starting from: " + shorten(itinerary.getStartLabel(), 45)));
        if (itinerary.getStartToDestinationKm() > 0) {
            chips.getChildren().add(chip("📏 " + FileManager.startDistanceText(itinerary)));
            chips.getChildren().add(chip(itinerary.getTravelModeHint()));
        }
        chips.getChildren().add(chip("⏱ " + p.getPace() + " pace"));
        VBox header = new VBox(6, title, details, chips);
        header.getStyleClass().add("header");
        view.setTop(header);

        // ----- body: banners + day cards + summary -----
        VBox body = new VBox(18);
        body.setPadding(new Insets(22, 26, 30, 26));
        body.setMaxWidth(900);

        if (itinerary.isUsedLiveRouting()) {
            Label live = new Label("● Live routing by OpenRouteService");
            live.getStyleClass().add("live-badge");
            body.getChildren().add(live);
        } else {
            String msg = itinerary.isApiKeyMissing()
                    ? "Live routing unavailable (no API key in config.properties). Showing estimated distances."
                    : "Unable to retrieve live travel distance at the moment. Please check your internet "
                    + "connection or API key. Showing estimated distances.";
            body.getChildren().add(banner("⚠  " + msg, "warning-banner"));
        }
        if (itinerary.isUsingOfflineData()) {
            body.getChildren().add(banner("📦  Using offline demo data. The map service could not be reached.",
                    "warning-banner"));
        }
        for (String note : itinerary.getNotes()) {
            body.getChildren().add(banner("ℹ  " + note, "info-banner"));
        }

        int dayNumber = 0;
        for (ArrayList<ItineraryItem> day : itinerary.getDays()) {
            body.getChildren().add(dayCard(day, dayNumber));
            dayNumber++;
        }
        body.getChildren().add(summaryCard());

        StackPane centerBox = new StackPane(body);
        StackPane.setAlignment(body, Pos.TOP_CENTER);
        ScrollPane scroll = new ScrollPane(centerBox);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("scroll-area");
        view.setCenter(scroll);

        // ----- bottom buttons -----
        Button modify = new Button("← MODIFY TRIP");
        modify.getStyleClass().add("secondary-button");
        modify.setOnAction(e -> onModify.run());
        Button newTrip = new Button("✚ NEW TRIP");
        newTrip.getStyleClass().add("secondary-button");
        newTrip.setOnAction(e -> onNewTrip.run());
        Button save = new Button("💾 SAVE ITINERARY");
        save.getStyleClass().add("save-button");
        save.setOnAction(e -> onSave.run());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(12, modify, newTrip, spacer, save);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("bottom-bar");
        view.setBottom(bar);
    }

    public Node getView() {
        return view;
    }

    // ------------------------------------------------------------------ day card

    private VBox dayCard(ArrayList<ItineraryItem> day, int index) {
        double dayCost = 0;
        int activities = 0;
        for (ItineraryItem item : day) {
            dayCost += item.getCost();
            if (!item.isMeal()) activities++;
        }

        Label dayTitle = new Label("DAY " + (index + 1));
        dayTitle.getStyleClass().add("day-title");
        Label meta = new Label(activities + (activities == 1 ? " activity" : " activities")
                + "  ·  " + Itinerary.formatInr(dayCost) + " est.");
        meta.getStyleClass().add("day-meta");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox head = new HBox(dayTitle, spacer, meta);
        head.setAlignment(Pos.CENTER_LEFT);
        head.getStyleClass().addAll("day-header", "day-header-" + (index % 5));

        VBox content = new VBox(4);
        content.getStyleClass().add("day-body");
        if (day.isEmpty()) {
            Label free = new Label("🌅 Free day: no more places fit your budget or interests. Explore the area at your own pace!");
            free.getStyleClass().add("empty-day");
            free.setWrapText(true);
            content.getChildren().add(free);
        }
        for (int i = 0; i < day.size(); i++) {
            ItineraryItem item = day.get(i);
            if (i > 0) {
                Label connector = new Label("↓  " + FileManager.travelText(item));
                connector.getStyleClass().add("connector");
                content.getChildren().add(connector);
            }
            content.getChildren().add(activityRow(item));
        }

        VBox card = new VBox(head, content);
        card.getStyleClass().add("day-card");
        return card;
    }

    private HBox activityRow(ItineraryItem item) {
        Place place = item.getPlace();

        Label start = new Label(item.getStartTime());
        start.getStyleClass().add("time-label");
        Label end = new Label("to " + item.getEndTime());
        end.getStyleClass().add("time-end");
        VBox timeBox = new VBox(2, start, end);
        timeBox.setMinWidth(82);
        timeBox.setPrefWidth(82);

        String emoji = item.isMeal() ? "🍴" : PlanningView.emojiFor(place.getCategory());
        Label name = new Label(emoji + "  " + place.getName());
        name.getStyleClass().add("place-name");
        name.setWrapText(true);
        Label tag = new Label(item.isMeal() ? item.getMealLabel() : place.getCategory());
        tag.getStyleClass().add("category-tag");
        Label duration = new Label(ItineraryItem.formatDuration(item.getEndMinutes() - item.getStartMinutes()));
        duration.getStyleClass().add("time-end");
        HBox tagRow = new HBox(8, tag, duration);
        tagRow.setAlignment(Pos.CENTER_LEFT);
        Label desc = new Label(place.getDescription());
        desc.getStyleClass().add("place-desc");
        desc.setWrapText(true);
        VBox info = new VBox(4, name, tagRow, desc);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label cost = new Label(item.getCost() == 0 ? "Free" : Itinerary.formatInr(item.getCost()) + " est.");
        cost.getStyleClass().add("cost-label");
        VBox costBox = new VBox(2, cost);
        String local = itinerary.toLocalCurrency(item.getCost());
        if (!local.isEmpty() && item.getCost() > 0) {
            Label localLabel = new Label(local);
            localLabel.getStyleClass().add("cost-local");
            costBox.getChildren().add(localLabel);
        }
        costBox.setAlignment(Pos.TOP_RIGHT);
        costBox.setMinWidth(100);

        HBox row = new HBox(14, timeBox, info, costBox);
        row.getStyleClass().add("activity-row");
        if (item.isMeal()) row.getStyleClass().add("meal-row");
        return row;
    }

    // ------------------------------------------------------------------ summary

    private VBox summaryCard() {
        Label title = new Label("📊  TRIP SUMMARY");
        title.getStyleClass().add("section-title");
        title.setStyle("-fx-font-size: 18px;");

        double remaining = itinerary.getRemainingBudget();
        boolean low = remaining < itinerary.getBudget() * 0.10;   // red when negative or under 10% left

        String distancePrefix = itinerary.isUsedLiveRouting() ? "" : "≈ ";
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        for (int c = 0; c < 3; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(33.3);
            grid.getColumnConstraints().add(cc);
        }
        grid.add(stat("🗓 Total Days", String.valueOf(itinerary.getPreferences().getDays()), null), 0, 0);
        grid.add(stat("🎯 Activities", String.valueOf(itinerary.getActivityCount()), null), 1, 0);
        grid.add(stat("💰 Budget", Itinerary.formatInr(itinerary.getBudget()), null), 2, 0);
        grid.add(stat("💳 Estimated Cost", Itinerary.formatInr(itinerary.getTotalCost()),
                itinerary.toLocalCurrency(itinerary.getTotalCost())), 0, 1);
        VBox remainingBox = stat("💵 Remaining Budget", Itinerary.formatInr(remaining), null);
        remainingBox.getChildren().get(1).getStyleClass().add(low ? "stat-bad" : "stat-good");
        grid.add(remainingBox, 1, 1);
        grid.add(stat("🚕 Local Transport (est.)", Itinerary.formatInr(itinerary.getTotalTransportCost()), null), 2, 1);
        grid.add(stat("🛣 Est. Travel Distance",
                distancePrefix + String.format("%.1f km", itinerary.getTotalDistanceKm()), null), 0, 2);
        grid.add(stat("⏳ Est. Travel Time", ItineraryItem.formatDuration(itinerary.getTotalTravelMinutes()), null), 1, 2);
        grid.add(stat("📍 Routing", itinerary.isUsedLiveRouting() ? "Live" : "Estimated", null), 2, 2);

        Label foot = new Label("Budget " + Itinerary.formatInr(itinerary.getBudget())
                + "  −  Estimated cost " + Itinerary.formatInr(itinerary.getTotalCost())
                + "  =  Remaining " + Itinerary.formatInr(remaining)
                + "\nAll costs are estimates (entry fees, meals, local taxi). Stay and intercity travel are not included.");
        foot.getStyleClass().add("footnote");
        foot.setWrapText(true);

        VBox card = new VBox(14, title, grid, foot);
        card.getStyleClass().add("summary-card");
        return card;
    }

    private VBox stat(String label, String value, String extra) {
        Label l = new Label(label);
        l.getStyleClass().add("stat-label");
        Label v = new Label(value);
        v.getStyleClass().add("stat-value");
        VBox box = new VBox(4, l, v);
        if (extra != null && !extra.isEmpty()) {
            Label e = new Label(extra);
            e.getStyleClass().add("cost-local");
            box.getChildren().add(e);
        }
        box.getStyleClass().add("stat-box");
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    // ------------------------------------------------------------------ helpers

    private Label chip(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("header-chip");
        return l;
    }

    private Label banner(String text, String style) {
        Label l = new Label(text);
        l.getStyleClass().addAll("banner", style);
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }

    /** "new delhi" -> "New Delhi" */
    static String titleCase(String text) {
        StringBuilder sb = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (word.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    private String shorten(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
