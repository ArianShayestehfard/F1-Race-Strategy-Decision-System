package com.f1strategy.client;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
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
 * Tab 4 — run the lap-by-lap race simulator and inspect the timeline.
 */
class SimulateView extends VBox {

    private static final String[] COMPOUNDS = {"SOFT", "MEDIUM", "HARD"};

    private final ObjectProperty<String> race = new SimpleObjectProperty<>();
    private final ObjectProperty<String> driver = new SimpleObjectProperty<>();
    private final ApiClient api;

    private final ChoiceBox<String> compound = new ChoiceBox<>();
    private final TextField pitLaps = new TextField("20, 40");
    private final Button simulateButton = Ui.button("Simulate race", true);
    private final Label status = Ui.label("", "banner-info");
    private final Label resultLine = Ui.label("", "verdict-title");
    private final TableView<Row> table = new TableView<>();
    private final java.util.concurrent.atomic.AtomicReference<JsonNode> lastResult =
            new java.util.concurrent.atomic.AtomicReference<>();

    /** One timeline row rendered in the table. */
    static final class Row {
        private final int lap;
        private final String compound;
        private final int tyreAge;
        private final int position;
        private final String pit;

        Row(JsonNode t) {
            this.lap = t.path("lap").asInt();
            this.compound = t.path("compound").asText();
            this.tyreAge = t.path("tyre_age").asInt();
            this.position = t.path("position").asInt();
            this.pit = t.path("pit").asBoolean() ? "PIT" : "";
        }

        public int getLap() { return lap; }
        public String getCompound() { return compound; }
        public int getTyreAge() { return tyreAge; }
        public int getPosition() { return position; }
        public String getPit() { return pit; }
    }

    SimulateView(ApiClient api) {
        this.api = api;
        setSpacing(12);
        Ui.padded(this, 16);

        compound.getItems().addAll(COMPOUNDS);
        compound.getSelectionModel().select("MEDIUM");
        pitLaps.setPromptText("e.g. 20, 40");

        HBox inputs = Ui.row(
                Ui.label("Start compound:"), compound,
                Ui.label("Pit laps:"), pitLaps, simulateButton);
        pitLaps.setPrefWidth(140);

        simulateButton.setOnAction(e -> simulate());

        TableColumn<Row, Integer> cLap = new TableColumn<>("Lap");
        cLap.setCellValueFactory(new PropertyValueFactory<>("lap"));
        cLap.setPrefWidth(60);
        TableColumn<Row, String> cComp = new TableColumn<>("Compound");
        cComp.setCellValueFactory(new PropertyValueFactory<>("compound"));
        cComp.setPrefWidth(120);
        TableColumn<Row, Integer> cAge = new TableColumn<>("Tyre age");
        cAge.setCellValueFactory(new PropertyValueFactory<>("tyreAge"));
        cAge.setPrefWidth(90);
        TableColumn<Row, Integer> cPos = new TableColumn<>("Position");
        cPos.setCellValueFactory(new PropertyValueFactory<>("position"));
        cPos.setPrefWidth(90);
        TableColumn<Row, String> cPit = new TableColumn<>("Pit");
        cPit.setCellValueFactory(new PropertyValueFactory<>("pit"));
        cPit.setPrefWidth(60);
        table.getColumns().setAll(cLap, cComp, cAge, cPos, cPit);
        table.setPlaceholder(Ui.label("Run a simulation to see the lap-by-lap timeline.", "dim"));
        VBox.setVgrow(table, Priority.ALWAYS);

        getChildren().addAll(
                Ui.card("Race simulation", inputs, status),
                Ui.card("Result", resultLine),
                table);
    }

    private void simulate() {
        String raceId = race.get(), driverId = driver.get();
        if (raceId == null || driverId == null) {
            status.setText("Pick a race (tab 1) and a driver (tab 2) first.");
            return;
        }
        var pitLapList = FXCollections.<Integer>observableArrayList();
        for (String part : pitLaps.getText().split("[,\\s]+")) {
            if (!part.isBlank()) {
                try {
                    pitLapList.add(Integer.parseInt(part.trim()));
                } catch (NumberFormatException ignored) {
                    status.setText("Pit laps must be numbers separated by commas.");
                    return;
                }
            }
        }
        simulateButton.setDisable(true);
        status.setText("Simulating 20 cars lap by lap…");
        Async.run(
                () -> lastResult.set(api.post("/simulate", java.util.Map.of(
                        "race_id", raceId, "driver_id", driverId,
                        "start_compound", compound.getValue(),
                        "pit_laps", pitLapList))),
                () -> {
                    simulateButton.setDisable(false);
                    status.setText("");
                    render();
                },
                msg -> {
                    simulateButton.setDisable(false);
                    status.setText("Error: " + msg);
                    status.getStyleClass().add("banner-error");
                });
    }

    private void render() {
        JsonNode res = lastResult.get();
        long totalMs = res.path("total_time_ms").asLong();
        resultLine.setText(String.format("Finish P%d   ·   race time %s",
                res.path("finish_position").asInt(), Fmt.totalTime(totalMs)));
        var rows = FXCollections.<Row>observableArrayList();
        for (JsonNode t : res.path("timeline")) {
            rows.add(new Row(t));
        }
        table.setItems(rows);
    }

    void setRace(String raceId) {
        race.set(raceId);
        table.getItems().clear();
        resultLine.setText("");
    }

    void setDriver(String driverId) {
        driver.set(driverId);
    }
}
