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
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Tab 4 — run the race simulator (Phase 7) and show the outcome.
 */
public class SimulateView extends VBox {

    private final ObjectProperty<String> race = new SimpleObjectProperty<>();
    private final ObjectProperty<String> driver = new SimpleObjectProperty<>();
    private final ChoiceBox<String> compound = new ChoiceBox<>();
    private final TextField pitLaps = new TextField("20, 41");
    private final TableView<Row> table = new TableView<>();
    private final Label status = new Label();

    public static class Row {
        private final int lap; private final String compound; private final int tyreAge;
        private final int position; private final String pit;
        public Row(int lap, String compound, int tyreAge, int position, boolean pit) {
            this.lap = lap; this.compound = compound; this.tyreAge = tyreAge;
            this.position = position; this.pit = pit ? "PIT" : "";
        }
        public int getLap() { return lap; }
        public String getCompound() { return compound; }
        public int getTyreAge() { return tyreAge; }
        public int getPosition() { return position; }
        public String getPit() { return pit; }
    }

    public SimulateView(ApiClient api) {
        setPadding(new Insets(16));
        setSpacing(10);

        compound.getItems().addAll("SOFT", "MEDIUM", "HARD");
        compound.getSelectionModel().select("MEDIUM");

        Button run = new Button("Simulate race");
        run.setOnAction(e -> run(api));

        HBox inputs = new HBox(10,
                new Label("Start compound:"), compound,
                new Label("Pit laps:"), pitLaps, run);

        TableColumn<Row, Integer> cLap = new TableColumn<>("Lap");
        cLap.setCellValueFactory(new PropertyValueFactory<>("lap"));
        TableColumn<Row, String> cComp = new TableColumn<>("Compound");
        cComp.setCellValueFactory(new PropertyValueFactory<>("compound"));
        TableColumn<Row, Integer> cAge = new TableColumn<>("Tyre age");
        cAge.setCellValueFactory(new PropertyValueFactory<>("tyreAge"));
        TableColumn<Row, Integer> cPos = new TableColumn<>("Position");
        cPos.setCellValueFactory(new PropertyValueFactory<>("position"));
        TableColumn<Row, String> cPit = new TableColumn<>("Pit");
        cPit.setCellValueFactory(new PropertyValueFactory<>("pit"));
        table.getColumns().setAll(cLap, cComp, cAge, cPos, cPit);
        VBox.setVgrow(table, Priority.ALWAYS);

        getChildren().addAll(inputs, status, table);
    }

    private void run(ApiClient api) {
        String r = race.get(), d = driver.get();
        if (r == null || d == null) {
            status.setText("Select a race and a driver first.");
            return;
        }
        var laps = FXCollections.<Integer>observableArrayList();
        for (String part : pitLaps.getText().split("[,\\s]+")) {
            if (!part.isBlank()) {
                laps.add(Integer.parseInt(part.trim()));
            }
        }
        var body = new java.util.HashMap<String, Object>();
        body.put("race_id", r);
        body.put("driver_id", d);
        body.put("start_compound", compound.getValue());
        body.put("pit_laps", laps);

        new Thread(() -> {
            try {
                JsonNode res = api.post("/simulate", body);
                Platform.runLater(() -> {
                    status.setText("Finish P" + res.path("finish_position").asInt()
                            + " · race time " + formatTime(res.path("total_time_ms").asLong()));
                    var rows = FXCollections.<Row>observableArrayList();
                    for (JsonNode t : res.path("timeline")) {
                        rows.add(new Row(t.get("lap").asInt(), t.get("compound").asText(),
                                t.get("tyre_age").asInt(), t.get("position").asInt(), t.get("pit").asBoolean()));
                    }
                    table.setItems(rows);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> status.setText("Error: " + ex.getMessage()));
            }
        }).start();
    }

    private static String formatTime(long ms) {
        long h = ms / 3_600_000, m = (ms % 3_600_000) / 60_000, s = (ms % 60_000) / 1000;
        return String.format("%d:%02d:%02d", h, m, s);
    }

    public void setRace(String raceId) { race.set(raceId); table.getItems().clear(); }
    public void setDriver(String driverId) { driver.set(driverId); }
}
