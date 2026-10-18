package com.f1strategy.client;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Tab 5 — the Strategy Recommendation Engine: "should we pit now?"
 * Shows the recommended plan with a confidence bar plus every tested scenario.
 */
class StrategyView extends VBox {

    private static final String[] COMPOUNDS = {"SOFT", "MEDIUM", "HARD"};

    private final ObjectProperty<String> race = new SimpleObjectProperty<>();
    private final ObjectProperty<String> driver = new SimpleObjectProperty<>();
    private final ApiClient api;

    private final TextField currentLap = new TextField("18");
    private final ChoiceBox<String> compound = new ChoiceBox<>();
    private final Button recommendButton = Ui.button("Recommend strategy", true);
    private final Label status = Ui.label("", "banner-info");
    private final Label verdict = Ui.label("Pick a race and a driver, then ask for a recommendation.", "dim");
    private final ProgressBar confidence = new ProgressBar(0);
    private final ListView<String> scenarios = new ListView<>();

    StrategyView(ApiClient api) {
        this.api = api;
        setSpacing(12);
        Ui.padded(this, 16);

        compound.getItems().addAll(COMPOUNDS);
        compound.getSelectionModel().select("MEDIUM");
        currentLap.setPrefWidth(70);

        HBox inputs = Ui.row(
                Ui.label("Current lap:"), currentLap,
                Ui.label("Tyre now:"), compound, recommendButton);
        recommendButton.setOnAction(e -> recommend());

        confidence.setPrefWidth(320);
        confidence.setPrefHeight(14);

        VBox.setVgrow(scenarios, Priority.ALWAYS);
        scenarios.setPlaceholder(Ui.label("Scenarios appear here — ranked best first.", "dim"));

        VBox verdictCard = Ui.card("Recommended strategy",
                verdict,
                Ui.row(Ui.label("Confidence:"), confidence),
                status);
        getChildren().addAll(Ui.card("Current race state", inputs), verdictCard, scenarios);
    }

    private void recommend() {
        String raceId = race.get(), driverId = driver.get();
        if (raceId == null || driverId == null) {
            status.setText("Pick a race (tab 1) and a driver (tab 2) first.");
            return;
        }
        int lap;
        try {
            lap = Integer.parseInt(currentLap.getText().trim());
        } catch (NumberFormatException e) {
            status.setText("Current lap must be a number.");
            return;
        }
        recommendButton.setDisable(true);
        status.setText("Simulating every pit window × compound…");
        var holder = new java.util.concurrent.atomic.AtomicReference<JsonNode>();
        Async.run(
                () -> {
                    holder.set(api.post("/strategy/recommend", java.util.Map.of(
                            "race_id", raceId, "driver_id", driverId,
                            "current_lap", lap,
                            "current_compound", compound.getValue())));
                },
                () -> {
                    recommendButton.setDisable(false);
                    status.setText("");
                    render(holder.get());
                },
                msg -> {
                    recommendButton.setDisable(false);
                    status.setText("Error: " + msg);
                    status.getStyleClass().add("banner-error");
                });
    }

    private void render(JsonNode res) {
        verdict.setText(String.format(
                "PIT lap %d  →  %s   ·   expected P%d",
                res.path("recommended_pit_lap").asInt(),
                res.path("recommended_compound").asText(),
                res.path("expected_finish_position").asInt()));
        confidence.setProgress(res.path("confidence").asDouble(0));

        var items = FXCollections.<String>observableArrayList();
        for (JsonNode s : res.path("scenarios")) {
            items.add(String.format("Pit lap %2d on %-6s →  P%d   (%s)",
                    s.path("pit_lap").asInt(), s.path("compound").asText(),
                    s.path("finish_position").asInt(),
                    Fmt.totalTime(s.path("total_time_ms").asLong())));
        }
        scenarios.setItems(items);
    }

    void setRace(String raceId) {
        race.set(raceId);
        scenarios.getItems().clear();
        verdict.setText("");
        confidence.setProgress(0);
    }

    void setDriver(String driverId) {
        driver.set(driverId);
    }
}
