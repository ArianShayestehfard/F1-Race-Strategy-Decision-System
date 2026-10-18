package com.f1strategy.client;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Tab 1 — browse stored races and import new ones (OpenF1 real data,
 * FastF1 or the offline demo race) straight from the UI.
 */
class RaceSelectionView extends VBox {

    private static final String[] SOURCES = {"OpenF1 — real data", "FastF1 — official server", "Demo — offline"};

    private final ObjectProperty<String> race = new SimpleObjectProperty<>();
    private final ApiClient api;

    private final TextField yearField = new TextField("2024");
    private final TextField roundField = new TextField("1");
    private final ChoiceBox<String> source = new ChoiceBox<>();
    private final Label status = Ui.label("", "banner-info");
    private final TableView<RaceRow> table = new TableView<>();
    private final Button importButton = Ui.button("Import race", true);
    private final Button refreshButton = Ui.button("Refresh");

    RaceSelectionView(ApiClient api) {
        this.api = api;
        setSpacing(12);
        Ui.padded(this, 16);

        source.getItems().addAll(SOURCES);
        source.getSelectionModel().selectFirst();

        HBox importRow = Ui.row(
                Ui.label("Year:"), yearField, Ui.label("Round:"), roundField,
                source, importButton, Ui.spacer(), refreshButton);
        yearField.setPrefWidth(70);
        roundField.setPrefWidth(70);

        VBox importCard = Ui.card("Import a race into the database", importRow, status);
        getChildren().addAll(importCard, buildTable());
        VBox.setVgrow(table, Priority.ALWAYS);

        refreshButton.setOnAction(e -> refresh());
        importButton.setOnAction(e -> importRace());
        table.getSelectionModel().selectedItemProperty().addListener((obs, o, sel) -> {
            if (sel != null) {
                race.set(sel.raceId);
                status.setText("Selected " + sel.raceId + " — pick drivers in the next tab.");
            }
        });

        refresh();
    }

    private TableView<RaceRow> buildTable() {
        TableColumn<RaceRow, String> round = new TableColumn<>("Round");
        round.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().round));
        round.setPrefWidth(70);
        TableColumn<RaceRow, String> name = new TableColumn<>("Race");
        name.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name));
        name.setPrefWidth(260);
        TableColumn<RaceRow, String> circuit = new TableColumn<>("Circuit");
        circuit.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().circuit));
        circuit.setPrefWidth(180);
        TableColumn<RaceRow, String> laps = new TableColumn<>("Laps");
        laps.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().laps));
        laps.setPrefWidth(70);
        table.getColumns().setAll(round, name, circuit, laps);
        table.setPlaceholder(Ui.label("No races yet — import one above.", "dim"));
        return table;
    }

    private void refresh() {
        refreshButton.setDisable(true);
        status.setText("Loading races…");
        var holder = new java.util.concurrent.atomic.AtomicReference<JsonNode>();
        Async.run(
                () -> {
                    holder.set(api.get("/races"));
                },
                () -> {
                    refreshButton.setDisable(false);
                    table.setItems(FXCollections.observableArrayList());
                    var rows = FXCollections.<RaceRow>observableArrayList();
                    for (JsonNode r : holder.get()) {
                        rows.add(new RaceRow(r));
                    }
                    table.setItems(rows);
                    status.setText(rows.size() + " races in the database. Select one, then open the Drivers tab.");
                    status.getStyleClass().remove("banner-error");
                },
                msg -> {
                    refreshButton.setDisable(false);
                    error("Backend unreachable — is the Spring Boot tab running? (" + msg + ")");
                });
    }

    private void importRace() {
        String year = yearField.getText().trim();
        String round = roundField.getText().trim();
        if (!year.matches("\\d{4}") || !round.matches("\\d+")) {
            error("Year must be a 4-digit number and round a positive number.");
            return;
        }
        importButton.setDisable(true);
        status.setText("Downloading real race data… this can take a minute.");
        Async.run(
                () -> {
                    String src = source.getSelectionModel().getSelectedItem();
                    if (src.startsWith("Demo")) {
                        api.post("/ingest", java.util.Map.of(
                                "year", Integer.parseInt(year), "round", Integer.parseInt(round),
                                "demo", true));
                        return;
                    }
                    api.post("/ingest", java.util.Map.of(
                            "year", Integer.parseInt(year), "round", Integer.parseInt(round),
                            "source", src.startsWith("OpenF1") ? "openf1" : "fastf1"));
                },
                () -> {
                    importButton.setDisable(false);
                    refresh();
                },
                msg -> {
                    importButton.setDisable(false);
                    error("Import failed: " + msg);
                });
    }

    private void error(String message) {
        status.setText(message);
        if (!status.getStyleClass().contains("banner-error")) {
            status.getStyleClass().add("banner-error");
        }
    }

    ObjectProperty<String> raceProperty() {
        return race;
    }

    /** Row model for the races table. */
    private static final class RaceRow {
        final String raceId;
        final String round;
        final String name;
        final String circuit;
        final String laps;

        RaceRow(JsonNode node) {
            this.raceId = node.path("raceId").asText();
            this.round = node.path("round").asText("-");
            this.name = node.path("name").asText();
            this.circuit = node.path("circuit").asText("-");
            this.laps = node.path("totalLaps").asText("-");
        }
    }
}
