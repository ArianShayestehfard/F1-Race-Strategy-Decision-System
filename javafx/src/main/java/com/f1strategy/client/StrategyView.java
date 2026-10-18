package com.f1strategy.client;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Tab 5 — Strategy Recommendation Engine (Phase 9):
 * "should we pit now or not?" — shows the best scenario plus all tested ones.
 */
public class StrategyView extends VBox {

    private final ObjectProperty<String> race = new SimpleObjectProperty<>();
    private final ObjectProperty<String> driver = new SimpleObjectProperty<>();
    private final TextField currentLap = new TextField("18");
    private final ChoiceBox<String> compound = new ChoiceBox<>();
    private final Label verdict = new Label();
    private final ListView<String> scenarios = new ListView<>();
    private final Label status = new Label();

    public StrategyView(ApiClient api) {
        setPadding(new Insets(16));
        setSpacing(10);

        compound.getItems().addAll("SOFT", "MEDIUM", "HARD");
        compound.getSelectionModel().select("MEDIUM");

        Button recommend = new Button("Recommend strategy");
        recommend.setOnAction(e -> run(api));

        HBox inputs = new HBox(10,
                new Label("Current lap:"), currentLap,
                new Label("Current compound:"), compound, recommend);

        verdict.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #10ac84;");
        VBox.setVgrow(scenarios, Priority.ALWAYS);

        getChildren().addAll(inputs, verdict, scenarios, status);
    }

    private void run(ApiClient api) {
        String r = race.get(), d = driver.get();
        if (r == null || d == null) {
            status.setText("Select a race and a driver first.");
            return;
        }
        var body = new java.util.HashMap<String, Object>();
        body.put("race_id", r);
        body.put("driver_id", d);
        body.put("current_lap", Integer.parseInt(currentLap.getText().trim()));
        body.put("current_compound", compound.getValue());

        new Thread(() -> {
            try {
                JsonNode res = api.post("/strategy/recommend", body);
                Platform.runLater(() -> {
                    verdict.setText(String.format(
                            "Recommended: PIT lap %d → %s · expected P%d · confidence %.0f%%",
                            res.path("recommended_pit_lap").asInt(),
                            res.path("recommended_compound").asText(),
                            res.path("expected_finish_position").asInt(),
                            res.path("confidence").asDouble() * 100));
                    var items = FXCollections.<String>observableArrayList();
                    for (JsonNode s : res.path("scenarios")) {
                        items.add(String.format("Pit lap %2d on %-6s → P%d (%.1f s)",
                                s.get("pit_lap").asInt(), s.get("compound").asText(),
                                s.get("finish_position").asInt(),
                                s.get("total_time_ms").asLong() / 1000.0));
                    }
                    scenarios.setItems(items);
                    status.setText("");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> status.setText("Error: " + ex.getMessage()));
            }
        }).start();
    }

    public void setRace(String raceId) { race.set(raceId); scenarios.getItems().clear(); verdict.setText(""); }
    public void setDriver(String driverId) { driver.set(driverId); }
}
