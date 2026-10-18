package com.f1strategy.client;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Map;

/**
 * Tab 5 — strategy decisions:
 * 1) Strategy Recommendation Engine — "when should we pit?"
 * 2) Pit duel vs a rival — "undercut or overcut the car ahead?"
 */
class StrategyView extends VBox {

    private static final String[] COMPOUNDS = {"SOFT", "MEDIUM", "HARD"};

    private final ObjectProperty<String> race = new SimpleObjectProperty<>();
    private final ObjectProperty<String> driver = new SimpleObjectProperty<>();
    private final ApiClient api;

    private final TextField currentLap = new TextField("18");
    private final ChoiceBox<String> compound = new ChoiceBox<>();
    private final ChoiceBox<String> rival = new ChoiceBox<>();
    private final Button recommendButton = Ui.button("Recommend strategy", true);
    private final Button duelButton = Ui.button("Undercut vs Overcut");
    private final Label status = Ui.label("", "banner-info");
    private final Label verdict = Ui.label("Pick a race and a driver, then ask for a recommendation.", "dim");
    private final ProgressBar confidence = new ProgressBar(0);
    private final Label confidencePct = Ui.label("—", "dim");
    private final ListView<String> scenarios = new ListView<>();
    private final java.util.concurrent.atomic.AtomicReference<JsonNode> rivalHolder =
            new java.util.concurrent.atomic.AtomicReference<>();

    StrategyView(ApiClient api) {
        this.api = api;
        setSpacing(12);
        Ui.padded(this, 16);

        compound.getItems().addAll(COMPOUNDS);
        compound.getSelectionModel().select("MEDIUM");
        currentLap.setPrefWidth(70);
        rival.setPrefWidth(110);
        rival.setDisable(true);

        HBox inputs = Ui.row(
                Ui.label("Current lap:"), currentLap,
                Ui.label("Tyre now:"), compound, recommendButton);
        HBox duelInputs = Ui.row(
                Ui.label("Rival:"), rival, duelButton,
                Ui.label("— who wins the pit cycle?", "hint"));
        recommendButton.setOnAction(e -> recommend());
        duelButton.setOnAction(e -> duel());

        confidence.setPrefWidth(280);
        confidence.setPrefHeight(12);

        VBox.setVgrow(scenarios, Priority.ALWAYS);
        scenarios.setPlaceholder(Ui.label("Scenarios appear here — ranked best first.", "dim"));

        getChildren().addAll(
                Ui.card("Current race state", inputs),
                Ui.card("Recommended strategy",
                        verdict,
                        Ui.row(Ui.label("Confidence:"), confidence, confidencePct),
                        status),
                Ui.card("Pit duel vs rival", duelInputs, scenarios));
    }

    private void recommend() {
        String raceId = race.get(), driverId = driver.get();
        if (raceId == null || driverId == null) {
            status.setText("Pick a race (tab 1) and a driver (tab 2) first.");
            return;
        }
        Integer lap = parseLap();
        if (lap == null) {
            return;
        }
        recommendButton.setDisable(true);
        status.setText("Simulating every pit window × compound…");
        var holder = new java.util.concurrent.atomic.AtomicReference<JsonNode>();
        Async.run(
                () -> {
                    holder.set(api.post("/strategy/recommend", Map.of(
                            "race_id", raceId, "driver_id", driverId,
                            "current_lap", lap,
                            "current_compound", compound.getValue())));
                },
                () -> {
                    recommendButton.setDisable(false);
                    status.setText("");
                    renderRecommendation(holder.get());
                },
                msg -> {
                    recommendButton.setDisable(false);
                    status.setText("Error: " + msg);
                    status.getStyleClass().add("banner-error");
                });
    }

    private void duel() {
        String raceId = race.get(), driverId = driver.get(), rivalId = rival.getValue();
        if (raceId == null || driverId == null || rivalId == null) {
            status.setText("Pick a race, a driver and a rival first.");
            return;
        }
        Integer lap = parseLap();
        if (lap == null) {
            return;
        }
        if (rivalId.equals(driverId)) {
            status.setText("Rival must be a different driver.");
            return;
        }
        duelButton.setDisable(true);
        status.setText("Running the pit-cycle duel…");
        var holder = new java.util.concurrent.atomic.AtomicReference<JsonNode>();
        Async.run(
                () -> {
                    holder.set(api.post("/strategy/duel", Map.of(
                            "race_id", raceId, "driver_id", driverId, "rival_id", rivalId,
                            "current_lap", lap,
                            "current_compound", compound.getValue())));
                },
                () -> {
                    duelButton.setDisable(false);
                    status.setText("");
                    renderDuel(holder.get());
                },
                msg -> {
                    duelButton.setDisable(false);
                    status.setText("Error: " + msg);
                    status.getStyleClass().add("banner-error");
                });
    }

    private Integer parseLap() {
        try {
            return Integer.parseInt(currentLap.getText().trim());
        } catch (NumberFormatException e) {
            status.setText("Current lap must be a number.");
            return null;
        }
    }

    private void renderRecommendation(JsonNode res) {
        verdict.setText(String.format(
                "PIT lap %d  →  %s   ·   expected P%d",
                res.path("recommended_pit_lap").asInt(),
                res.path("recommended_compound").asText(),
                res.path("expected_finish_position").asInt()));
        confidence.setProgress(res.path("confidence").asDouble(0));
        confidencePct.setText(String.format("%.0f%%", res.path("confidence").asDouble(0) * 100));

        var items = FXCollections.<String>observableArrayList();
        for (JsonNode s : res.path("scenarios")) {
            items.add(String.format("Pit lap %2d on %-6s →  P%d   (%s)",
                    s.path("pit_lap").asInt(), s.path("compound").asText(),
                    s.path("finish_position").asInt(),
                    Fmt.totalTime(s.path("total_time_ms").asLong())));
        }
        scenarios.setItems(items);
    }

    private void renderDuel(JsonNode res) {
        boolean beats = res.path("beats_rival").asBoolean();
        verdict.setText(String.format("%s%n%s %s rival (we P%s · rival P%s)",
                res.path("best_option").asText(),
                beats ? "✓ beats" : "✗ cannot beat",
                res.path("rival_id").asText(),
                res.path("scenarios").path(0).path("my_finish_position").asText("?"),
                res.path("scenarios").path(0).path("rival_finish_position").asText("?")));

        var items = FXCollections.<String>observableArrayList();
        for (JsonNode s : res.path("scenarios")) {
            items.add(String.format("%s — pit L%d on %s → we P%s, rival P%s",
                    s.path("option").asText(),
                    s.path("pit_lap").asInt(),
                    s.path("compound").asText(),
                    s.path("my_finish_position").asText(),
                    s.path("rival_finish_position").asText()));
        }
        scenarios.setItems(items);
    }

    /** Rival choices = the drivers of the current race (fetched once per race). */
    void setRace(String raceId) {
        race.set(raceId);
        rival.getItems().clear();
        rival.setDisable(true);
        scenarios.getItems().clear();
        verdict.setText("");
        confidence.setProgress(0);
        confidencePct.setText("—");
        Async.run(
                () -> {
                    rivalHolder.set(api.get("/races/" + raceId + "/drivers"));
                },
                () -> {
                    var codes = FXCollections.<String>observableArrayList();
                    for (JsonNode d : rivalHolder.get()) {
                        codes.add(d.path("code").asText().toLowerCase());
                    }
                    rival.getItems().setAll(codes);
                    rival.setDisable(false);
                },
                msg -> {
                    status.setText("Could not load rivals: " + msg);
                    status.getStyleClass().add("banner-error");
                });
    }

    void setDriver(String driverId) {
        driver.set(driverId);
    }
}
