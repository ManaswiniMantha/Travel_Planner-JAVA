package travelplanner;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;

import java.util.ArrayList;
import java.util.List;

/**
 * The itinerary screen: a timeline of the days on the left, an interactive map on the right.
 * Clicking a day (in the timeline or above the map) shows that day's route on the map.
 */
public class ResultView {

    private final BorderPane view = new BorderPane();
    private final Itinerary itinerary;
    private final MapView mapView = new MapView();
    private final ToggleGroup dayChips = new ToggleGroup();
    private final List<ToggleButton> chipList = new ArrayList<>();

    public ResultView(Itinerary itinerary, Runnable onModify, Runnable onSave, Runnable onNewTrip) {
        this.itinerary = itinerary;
        UserPreferences p = itinerary.getPreferences();

        // ----- top bar: back, route headline, actions -----
        Button modify = new Button("← Edit trip");
        modify.getStyleClass().add("bar-button");
        modify.setOnAction(e -> onModify.run());

        Label route = new Label(shorten(firstPart(itinerary.getStartLabel(), p.getStartLocation()), 28)
                + "  →  " + titleCase(p.getDestination()));
        route.getStyleClass().add("trip-route");
        Label facts = new Label(tripFacts(p));
        facts.getStyleClass().add("trip-facts");
        VBox titleBox = new VBox(2, route, facts);
        titleBox.setPadding(new Insets(0, 0, 0, 14));

        Button newTrip = new Button("New trip");
        newTrip.getStyleClass().add("bar-button");
        newTrip.setOnAction(e -> onNewTrip.run());
        Button save = new Button("Save as text file");
        save.getStyleClass().add("save-button");
        save.setOnAction(e -> onSave.run());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(10, modify, titleBox, spacer, PlanningView.themeButton(), newTrip, save);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("top-bar");
        view.setTop(bar);

        // ----- left: status, days, costs -----
        VBox column = new VBox(16);
        column.getStyleClass().add("timeline-column");
        column.getChildren().add(statusLine());
        if (itinerary.isUsingOfflineData()) {
            column.getChildren().add(banner("The map service couldn't be reached, so this plan uses the "
                    + "built-in demo places.", "warning-banner"));
        }
        for (String note : itinerary.getNotes()) column.getChildren().add(banner(note, "info-banner"));

        int index = 0;
        for (ArrayList<ItineraryItem> day : itinerary.getDays()) {
            column.getChildren().add(daySection(day, index++));
        }
        column.getChildren().add(costsPanel());

        ScrollPane scroll = new ScrollPane(column);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("scroll-area");

        // ----- right: day chips + map -----
        FlowPane toolbar = new FlowPane(6, 6);
        toolbar.getStyleClass().add("map-toolbar");
        toolbar.getChildren().add(dayChip("All days", -1, null));
        for (int d = 0; d < itinerary.getDays().size(); d++) {
            toolbar.getChildren().add(dayChip("Day " + (d + 1), d, Theme.dayColor(d)));
        }
        chipList.get(0).setSelected(true);
        dayChips.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null && old != null) old.setSelected(true);   // one chip is always selected
            else if (now != null) mapView.showDay((Integer) now.getUserData());
        });
        VBox.setVgrow(mapView, Priority.ALWAYS);
        VBox mapColumn = new VBox(toolbar, mapView);
        mapColumn.getStyleClass().add("map-column");
        mapView.showItinerary(itinerary);

        SplitPane split = new SplitPane(scroll, mapColumn);
        split.setDividerPositions(0.56);
        SplitPane.setResizableWithParent(mapColumn, true);
        view.setCenter(split);
    }

    public Node getView() {
        return view;
    }

    // ------------------------------------------------------------------ top + status

    private String tripFacts(UserPreferences p) {
        StringBuilder sb = new StringBuilder();
        sb.append(p.getDays()).append(p.getDays() == 1 ? " day, " : " days, ");
        sb.append(p.getTravelers()).append(p.getTravelers() == 1 ? " traveller, " : " travellers, ");
        sb.append(Itinerary.formatInr(p.getBudget())).append(" budget, ").append(p.getPace().toLowerCase()).append(" pace");
        if (itinerary.getStartToDestinationKm() > 0) {
            sb.append("     ").append(FileManager.startDistanceText(itinerary));
            String hint = itinerary.getTravelModeHint().replaceAll("^[^A-Za-z]+", "");   // drop the emoji
            if (!hint.isEmpty()) sb.append(", ").append(hint.substring(0, 1).toLowerCase()).append(hint.substring(1));
        }
        return sb.toString();
    }

    private Node statusLine() {
        Label status;
        if (itinerary.isUsedLiveRouting()) {
            status = new Label("●  Travel times are live road estimates from OpenRouteService");
            status.getStyleClass().add("status-live");
        } else {
            status = new Label(itinerary.isApiKeyMissing()
                    ? "●  No API key in config.properties, so travel times are straight-line estimates"
                    : "●  Live routing is unavailable right now, so travel times are straight-line estimates");
            status.getStyleClass().add("status-estimate");
        }
        status.setWrapText(true);
        return status;
    }

    // ------------------------------------------------------------------ one day

    private VBox daySection(ArrayList<ItineraryItem> day, int index) {
        String color = Theme.dayColor(index);
        double dayCost = 0;
        int activities = 0;
        for (ItineraryItem item : day) {
            dayCost += item.getCost();
            if (!item.isMeal()) activities++;
        }

        Region swatch = new Region();
        swatch.setMinSize(6, 22);
        swatch.setMaxSize(6, 22);
        swatch.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 3;");
        Label name = new Label("Day " + (index + 1));
        name.getStyleClass().add("day-name");
        Label meta = new Label(activities + (activities == 1 ? " place" : " places") + ", about "
                + Itinerary.formatInr(dayCost));
        meta.getStyleClass().add("day-meta");
        Label chevron = new Label("▾");
        chevron.getStyleClass().add("chevron");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox head = new HBox(swatch, name, meta, spacer, chevron);
        head.setAlignment(Pos.CENTER_LEFT);
        head.getStyleClass().add("day-head");

        VBox body = new VBox();
        body.getStyleClass().add("day-body");
        if (day.isEmpty()) {
            Label free = new Label("Free day: nothing else fits your budget and interests, so explore at your own pace.");
            free.getStyleClass().add("empty-day");
            free.setWrapText(true);
            body.getChildren().add(free);
        }
        int number = 1;
        for (int i = 0; i < day.size(); i++) {
            ItineraryItem item = day.get(i);
            if (i > 0) body.getChildren().add(legRow(item, color));
            body.getChildren().add(stopRow(item, color, item.isMeal() ? 0 : number, i == 0, i == day.size() - 1));
            if (!item.isMeal()) number++;
        }

        // click the header: show this day on the map and fold/unfold its stops
        head.setOnMouseClicked(e -> {
            boolean open = !body.isVisible();
            body.setVisible(open);
            body.setManaged(open);
            chevron.setText(open ? "▾" : "▸");
            chipList.get(index + 1).setSelected(true);
        });

        VBox section = new VBox(head, body);
        section.getStyleClass().add("day-section");
        return section;
    }

    /** time | rail with numbered dot | name, tags, description | cost */
    private HBox stopRow(ItineraryItem item, String color, int number, boolean first, boolean last) {
        Place place = item.getPlace();

        Label start = new Label(item.getStartTime());
        start.getStyleClass().add("stop-time");
        Label end = new Label(item.getEndTime());
        end.getStyleClass().add("stop-time-end");
        VBox timeBox = new VBox(1, start, end);
        timeBox.setMinWidth(72);
        timeBox.setPrefWidth(72);

        String icon = item.isMeal() ? "🍴" : PlanningView.emojiFor(place.getCategory());
        Label name = new Label(icon + "  " + place.getName());
        name.getStyleClass().add("stop-name");
        name.setWrapText(true);

        Label tag = new Label(item.getLabel());
        tag.getStyleClass().add("stop-tag");
        Label duration = new Label(ItineraryItem.formatDuration(item.getEndMinutes() - item.getStartMinutes()));
        duration.getStyleClass().add("stop-tag");
        HBox tags = new HBox(6, tag, duration);
        if (place.isMustSee()) {
            Label mustSee = new Label("★ Must-see");
            mustSee.getStyleClass().add("must-see");
            tags.getChildren().add(mustSee);
        }
        tags.setAlignment(Pos.CENTER_LEFT);

        Label desc = new Label(place.getDescription());
        desc.getStyleClass().add("stop-desc");
        desc.setWrapText(true);
        VBox info = new VBox(5, name, tags, desc);
        info.setPadding(new Insets(0, 0, 4, 0));
        HBox.setHgrow(info, Priority.ALWAYS);

        Label cost = new Label(item.getCost() == 0 ? "Free" : Itinerary.formatInr(item.getCost()));
        cost.getStyleClass().add("stop-cost");
        VBox costBox = new VBox(1, cost);
        String local = itinerary.toLocalCurrency(item.getCost());
        if (!local.isEmpty() && item.getCost() > 0) {
            Label localLabel = new Label(local);
            localLabel.getStyleClass().add("stop-cost-local");
            costBox.getChildren().add(localLabel);
        }
        costBox.setAlignment(Pos.TOP_RIGHT);
        costBox.setMinWidth(76);

        HBox row = new HBox(12, timeBox, rail(color, number, item.isMeal(), first, last), info, costBox);
        row.setPadding(new Insets(4, 0, 0, 0));
        return row;
    }

    /** Travel between two stops: the rail continues, with the distance and time next to it. */
    private HBox legRow(ItineraryItem item, String color) {
        Region timeGap = new Region();
        timeGap.setMinWidth(72);
        timeGap.setPrefWidth(72);
        Region line = railLine(color, 1.0);
        StackPane railPane = new StackPane(line);
        railPane.setMinWidth(26);
        railPane.setPrefWidth(26);
        Label text = new Label(FileManager.travelText(item));
        text.getStyleClass().add("leg-text");
        HBox row = new HBox(12, timeGap, railPane, text);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** The vertical line + dot. Activities get a filled numbered dot (same number as on the map), meals a ring. */
    private StackPane rail(String color, int number, boolean meal, boolean first, boolean last) {
        Region top = railLine(color, first ? 0 : 1.0);
        top.setMinHeight(6);
        top.setPrefHeight(6);
        top.setMaxHeight(6);
        Region bottom = railLine(color, last ? 0 : 1.0);
        VBox.setVgrow(bottom, Priority.ALWAYS);

        StackPane dot;
        if (meal) {
            Circle ring = new Circle(7);
            ring.setStyle("-fx-fill: -c-surface; -fx-stroke: " + color + "; -fx-stroke-width: 3;");
            dot = new StackPane(ring);
        } else {
            Circle circle = new Circle(11);
            circle.setStyle("-fx-fill: " + color + ";");
            Label n = new Label(String.valueOf(number));
            n.getStyleClass().add("rail-number");
            dot = new StackPane(circle, n);
        }
        dot.setMinSize(24, 24);
        dot.setMaxSize(24, 24);

        VBox column = new VBox(top, dot, bottom);
        column.setAlignment(Pos.TOP_CENTER);
        StackPane pane = new StackPane(column);
        pane.setMinWidth(26);
        pane.setPrefWidth(26);
        return pane;
    }

    private Region railLine(String color, double opacity) {
        Region line = new Region();
        line.setMinWidth(3);
        line.setMaxWidth(3);
        line.setStyle("-fx-background-color: " + color + "; -fx-opacity: " + (opacity * 0.45) + ";");
        return line;
    }

    // ------------------------------------------------------------------ costs

    private VBox costsPanel() {
        Label title = new Label("Costs");
        title.getStyleClass().add("panel-title");

        double budget = itinerary.getBudget();
        double cost = itinerary.getTotalCost();
        double remaining = itinerary.getRemainingBudget();
        boolean low = remaining < budget * 0.10;   // highlighted when negative or under 10% left

        // budget bar: how much of the budget the plan uses
        Region fill = new Region();
        fill.getStyleClass().add("budget-fill");
        if (cost > budget) fill.getStyleClass().add("over");
        StackPane track = new StackPane(fill);
        track.getStyleClass().add("budget-track");
        track.setMinHeight(12);
        track.setMaxHeight(12);
        StackPane.setAlignment(fill, Pos.CENTER_LEFT);
        double share = budget <= 0 ? 0 : Math.min(1.0, cost / budget);
        fill.maxWidthProperty().bind(track.widthProperty().multiply(share));
        fill.setMinWidth(0);
        Label barText = new Label("The plan uses " + Math.round(share * 100) + "% of your "
                + Itinerary.formatInr(budget) + " budget");
        barText.getStyleClass().add("fact-label");

        GridPane facts = new GridPane();
        facts.setHgap(28);
        facts.setVgap(8);
        String distancePrefix = itinerary.isUsedLiveRouting() ? "" : "≈ ";
        int row = 0;
        addFact(facts, row, 0, "Estimated cost", Itinerary.formatInr(cost)
                + suffix(itinerary.toLocalCurrency(cost)), null);
        addFact(facts, row++, 2, "Left over", Itinerary.formatInr(remaining), low ? "bad" : "good");
        addFact(facts, row, 0, "Local transport", Itinerary.formatInr(itinerary.getTotalTransportCost()), null);
        addFact(facts, row++, 2, "Activities", String.valueOf(itinerary.getActivityCount()), null);
        addFact(facts, row, 0, "Travel distance", distancePrefix + String.format("%.1f km", itinerary.getTotalDistanceKm()), null);
        addFact(facts, row, 2, "Travel time", ItineraryItem.formatDuration(itinerary.getTotalTravelMinutes()), null);

        Label foot = new Label("Entry fees, meals and local taxis are estimates. Hotels and travel between "
                + "cities are not included.");
        foot.getStyleClass().add("footnote");
        foot.setWrapText(true);

        VBox panel = new VBox(12, title, track, barText, facts, foot);
        panel.getStyleClass().add("costs-panel");
        return panel;
    }

    private void addFact(GridPane grid, int row, int col, String label, String value, String style) {
        Label l = new Label(label);
        l.getStyleClass().add("fact-label");
        Label v = new Label(value);
        v.getStyleClass().add("fact-value");
        if (style != null) v.getStyleClass().add(style);
        grid.add(l, col, row);
        grid.add(v, col + 1, row);
    }

    private String suffix(String local) {
        return local == null || local.isEmpty() ? "" : "  (" + local + ")";
    }

    // ------------------------------------------------------------------ helpers

    private ToggleButton dayChip(String text, int dayIndex, String color) {
        ToggleButton chip = new ToggleButton(text);
        chip.setUserData(dayIndex);
        chip.setToggleGroup(dayChips);
        chip.getStyleClass().add("day-chip");
        if (color != null) {
            Circle dot = new Circle(5);
            dot.setStyle("-fx-fill: " + color + ";");
            chip.setGraphic(dot);
            chip.setGraphicTextGap(7);
        }
        chipList.add(chip);
        return chip;
    }

    private Label banner(String text, String style) {
        Label l = new Label(text);
        l.getStyleClass().addAll("banner", style);
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }

    /** "Visakhapatnam, Visakhapatnam Urban, ..." -> "Visakhapatnam" */
    private static String firstPart(String label, String fallback) {
        String text = label == null || label.isBlank() ? fallback : label;
        text = text.replace(" (offline)", "");
        int comma = text.indexOf(',');
        return titleCase(comma > 0 ? text.substring(0, comma) : text);
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

    private static String shorten(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
