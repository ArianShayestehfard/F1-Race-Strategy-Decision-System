package com.f1strategy.client;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Tab 3 — statistical analysis (Phase 6): pace trend chart + raw report.
 */
public class AnalyzeView extends VBox {

    private final ObjectProperty<String> race = new SimpleObjectProperty<>();
    private final ObjectProperty<String> driver = new SimpleObjectProperty<>();
    private final TextArea report = new TextArea();
    private final LineChart<Number, Number> chart;
    private final Label status = new Label();

    public AnalyzeView(ApiClient api) {
        setPadding(new Insets(16));
        setSpacing(10);

        NumberAxis x = new NumberAxis("Lap", 0, 60, 5);
        NumberAxis y = new NumberAxis("Lap time (s)", 60, 110, 5);
        chart = new LineChart<>(x, y);
        chart.setTitle("Pace trend");
        chart.setPrefHeight(320);
        chart.setCreateSymbols(false);

        Button analyze = new Button("Analyze driver");
        analyze.setOnAction(e -> run(api));
        report.setPrefRowCount(12);
        VBox.setVgrow(chart, Priority.ALWAYS);

        getChildren().addAll(analyze, status, chart, report);
    }

    private void run(ApiClient api) {
        String r = race.get(), d = driver.get();
        if (r == null || d == null) {
            status.setText("Select a race and a driver first.");
            return;
        }
        new Thread(() -> {
            try {
                JsonNode res = api.get("/analyze/" + r + "/" + d);
                Platform.runLater(() -> {
                    var series = new XYChart.Series<Number, Number>();
                    series.setName(d);
                    for (JsonNode p : res.path("pace_trend")) {
                        series.getData().add(new XYChart.Data<>(
                                p.get("lap").asDouble(), p.get("lap_time_ms").asDouble() / 1000.0));
                    }
                    chart.getData().setAll(series);
                    report.setText(res.toPrettyString());
                    status.setText("Best lap: " + res.path("best_lap_ms").asLong() + " ms · avg: "
                            + res.path("avg_lap_ms").asLong() + " ms");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> status.setText("Error: " + ex.getMessage()));
            }
        }).start();
    }

    public void setRace(String raceId) {
        race.set(raceId);
        chart.getData().clear();
        report.clear();
    }

    public void setDriver(String driverId) {
        driver.set(driverId);
    }
}
