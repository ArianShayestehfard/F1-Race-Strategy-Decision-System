package com.f1strategy.client;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Tab 3 — statistical analysis (pace trend chart + summary stat cards).
 */
class AnalyzeView extends VBox {

    private final ObjectProperty<String> race = new SimpleObjectProperty<>();
    private final ObjectProperty<String> driver = new SimpleObjectProperty<>();
    private final ApiClient api;

    private final Button analyzeButton = Ui.button("Analyze driver", true);
    private final Label status = Ui.label("", "banner-info");
    private final TextArea report = new TextArea();
    private final LineChart<Number, Number> chart;
    private final VBox statsBox = new VBox(10);

    AnalyzeView(ApiClient api) {
        this.api = api;
        setSpacing(12);
        Ui.padded(this, 16);

        chart = new LineChart<>(new NumberAxis(), new NumberAxis());
        chart.setTitle("Lap time per lap");
        chart.setCreateSymbols(false);
        chart.setLegendVisible(false);
        chart.setPrefHeight(320);
        VBox.setVgrow(chart, Priority.ALWAYS);

        report.setPrefRowCount(10);
        report.setEditable(false);

        analyzeButton.setOnAction(e -> analyze());

        getChildren().addAll(
                Ui.card("Statistical analysis — select a race and a driver first", analyzeButton, status),
                statsBox, chart,
                Ui.card("Raw report", report));
    }

    private void analyze() {
        String raceId = race.get(), driverId = driver.get();
        if (raceId == null || driverId == null) {
            status.setText("Pick a race (tab 1) and a driver (tab 2) first.");
            return;
        }
        analyzeButton.setDisable(true);
        status.setText("Analyzing…");
        var holder = new java.util.concurrent.atomic.AtomicReference<JsonNode>();
        Async.run(
                () -> {
                    holder.set(api.get("/analyze/" + raceId + "/" + driverId));
                },
                () -> {
                    analyzeButton.setDisable(false);
                    status.setText("");
                    render(holder.get());
                },
                msg -> {
                    analyzeButton.setDisable(false);
                    status.setText("Error: " + msg);
                    status.getStyleClass().add("banner-error");
                });
    }

    private void render(JsonNode res) {
        statsBox.getChildren().setAll(new HBox(12,
                statCard("Best lap", Fmt.lapTime(res.path("best_lap_ms").asLong())),
                statCard("Average lap", Fmt.lapTime(res.path("avg_lap_ms").asLong())),
                statCard("Fuel effect",
                        res.path("fuel_effect_s_per_lap").asText("-") + " s/lap/kg"),
                statCard("Laps analyzed", String.valueOf(res.path("pace_trend").size()))));

        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        for (JsonNode p : res.path("pace_trend")) {
            series.getData().add(new XYChart.Data<>(
                    p.get("lap").asDouble(), p.get("lap_time_ms").asDouble() / 1000.0));
        }
        chart.getData().setAll(series);
        chart.getXAxis().setLabel("Lap");
        chart.getYAxis().setLabel("Lap time (s)");
        report.setText(res.toPrettyString());
    }

    private VBox statCard(String caption, String value) {
        VBox card = Ui.stat(caption, value);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPrefWidth(220);
        return card;
    }

    void setRace(String raceId) {
        race.set(raceId);
        chart.getData().clear();
        report.clear();
        statsBox.getChildren().clear();
    }

    void setDriver(String driverId) {
        driver.set(driverId);
    }
}
