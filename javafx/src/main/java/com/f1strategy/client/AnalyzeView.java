package com.f1strategy.client;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Map;

/**
 * Tab 3 — statistical analysis of one driver:
 * stat cards, pace chart, tyre-stint timeline, actual pit stops and an
 * ML lap-time prediction panel (Phase 8 models, live from the engine).
 */
class AnalyzeView extends VBox {

    private static final Map<String, String> MODEL_LABELS = Map.of(
            "gradient_boosting", "Gradient Boosting (best)",
            "random_forest", "Random Forest",
            "linear_regression", "Linear Regression");

    private final ObjectProperty<String> race = new SimpleObjectProperty<>();
    private final ObjectProperty<String> driver = new SimpleObjectProperty<>();
    private final ApiClient api;

    private final Button analyzeButton = Ui.button("Analyze driver", true);
    private final Label status = Ui.label("", "banner-info");
    private final TextArea report = new TextArea();
    private final LineChart<Number, Number> chart;
    private final VBox statsBox = new VBox(10);
    private final VBox stintsBox = new VBox(8);
    private final VBox predictionBox = new VBox(8);
    private final ChoiceBox<String> model = new ChoiceBox<>();
    private final Button predictButton = Ui.button("Run prediction", false);
    private final Label predictionStatus = Ui.label("", "hint");
    private final Label predictionResult = Ui.label("", "dim");

    private final java.util.concurrent.atomic.AtomicReference<JsonNode> lastReport =
            new java.util.concurrent.atomic.AtomicReference<>();

    AnalyzeView(ApiClient api) {
        this.api = api;
        setSpacing(12);
        Ui.padded(this, 16);

        chart = new LineChart<>(new NumberAxis(), new NumberAxis());
        chart.setTitle("Lap time per lap");
        chart.setCreateSymbols(false);
        chart.setLegendVisible(false);
        chart.setPrefHeight(300);
        VBox.setVgrow(chart, Priority.ALWAYS);

        model.getItems().addAll(MODEL_LABELS.keySet());
        model.getSelectionModel().select("gradient_boosting");
        model.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(String key) { return MODEL_LABELS.get(key); }
            @Override public String fromString(String s) { return s; }
        });

        report.setPrefRowCount(8);
        report.setEditable(false);
        predictButton.setOnAction(e -> predict());
        analyzeButton.setOnAction(e -> analyze());

        getChildren().addAll(
                Ui.card("Statistical analysis — pick a race and a driver first", analyzeButton, status),
                statsBox,
                chart,
                Ui.card("Tyre stints (real data — color = compound)", stintsBox),
                Ui.card("Machine-learning prediction",
                        Ui.row(Ui.label("Model:"), model, predictButton, predictionStatus),
                        predictionResult),
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
        lastReport.set(res);
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

        renderStints(res);
        predictionResult.setText("");
        report.setText(res.toPrettyString());
    }

    /** One colored bar per stint, width proportional to stint length. */
    private void renderStints(JsonNode res) {
        stintsBox.getChildren().clear();
        JsonNode stints = res.path("stints");
        if (!stints.isArray() || stints.isEmpty()) {
            stintsBox.getChildren().add(Ui.label("No stint data.", "dim"));
            return;
        }
        int totalLaps = stints.get(stints.size() - 1).path("end_lap").asInt(1);
        HBox bar = new HBox(2);
        bar.getStyleClass().add("stint-bar");
        for (JsonNode s : stints) {
            int start = s.path("start_lap").asInt();
            int end = s.path("end_lap").asInt();
            Region segment = new Region();
            segment.getStyleClass().add("bar-" + s.path("compound").asText());
            HBox.setHgrow(segment, Priority.ALWAYS);
            segment.prefWidthProperty().bind(bar.widthProperty()
                    .multiply((end - start + 1.0) / totalLaps).subtract(4));
            bar.getChildren().add(segment);
        }
        HBox legend = new HBox(16);
        for (JsonNode s : stints) {
            legend.getChildren().add(Ui.label(String.format("%s  L%d–%d",
                    s.path("compound").asText(),
                    s.path("start_lap").asInt(), s.path("end_lap").asInt()), "hint"));
        }
        JsonNode pits = res.path("pit_stops");
        HBox pitLine = new HBox(12, Ui.label("Actual pit stops:", "hint"));
        if (pits.isArray() && !pits.isEmpty()) {
            for (JsonNode p : pits) {
                pitLine.getChildren().add(Ui.label(
                        String.format("Lap %d (%s)", p.path("lap").asInt(),
                                Fmt.lapTime(p.path("duration_ms").asLong())), "dim"));
            }
        } else {
            pitLine.getChildren().add(Ui.label("none recorded", "dim"));
        }
        stintsBox.getChildren().addAll(bar, legend, pitLine);
    }

    /** Phase 8 models, called live — answers "how many laps left on this tyre?". */
    private void predict() {
        String raceId = race.get(), driverId = driver.get();
        if (raceId == null || driverId == null) {
            predictionStatus.setText("Pick a race and a driver first.");
            return;
        }
        predictButton.setDisable(true);
        predictionStatus.setText("Training model…");
        var holder = new java.util.concurrent.atomic.AtomicReference<JsonNode>();
        Async.run(
                () -> {
                    holder.set(api.post("/predict", Map.of(
                            "race_id", raceId, "driver_id", driverId,
                            "model", model.getValue())));
                },
                () -> {
                    predictButton.setDisable(false);
                    JsonNode res = holder.get();
                    predictionStatus.setText("");
                    StringBuilder sb = new StringBuilder(String.format(
                            "Model MAE: %d ms   ·   tyre degradation: %s ms/lap%nNext laps: ",
                            res.path("mae_ms").asInt(),
                            res.path("tyre_degradation_ms_per_lap").asText("-")));
                    for (JsonNode p : res.path("predictions")) {
                        sb.append(String.format("L%d→%s  ", p.path("lap").asInt(),
                                Fmt.lapTime(p.path("predicted_lap_time_ms").asLong())));
                    }
                    predictionResult.setText(sb.toString());
                    predictionResult.getStyleClass().remove("dim");
                    predictionResult.getStyleClass().add("points");
                },
                msg -> {
                    predictButton.setDisable(false);
                    predictionStatus.setText("Error: " + msg);
                    predictionStatus.getStyleClass().add("banner-error");
                });
    }

    private VBox statCard(String caption, String value) {
        VBox card = Ui.stat(caption, value);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPrefWidth(220);
        return card;
    }

    void setRace(String raceId) {
        race.set(raceId);
        clear();
    }

    void setDriver(String driverId) {
        driver.set(driverId);
        clear();
    }

    private void clear() {
        chart.getData().clear();
        report.clear();
        statsBox.getChildren().clear();
        stintsBox.getChildren().clear();
        predictionResult.setText("");
        lastReport.set(null);
    }
}
