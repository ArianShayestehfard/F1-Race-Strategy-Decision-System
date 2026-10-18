package com.f1strategy.client;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Tab 2 — drivers of the selected race.
 */
public class DriverSelectionView extends VBox {

    private final ObjectProperty<String> race = new SimpleObjectProperty<>();
    private final ObjectProperty<String> driver = new SimpleObjectProperty<>();
    private final ListView<String> list = new ListView<>();

    public DriverSelectionView(ApiClient api) {
        setPadding(new Insets(16));
        setSpacing(10);

        Label hint = new Label("Drivers of the selected race:");
        Label status = new Label();
        Button load = new Button("Load drivers");
        load.setOnAction(e -> {
            String raceId = race.get();
            if (raceId == null) {
                status.setText("Select a race first (Races tab).");
                return;
            }
            new Thread(() -> {
                try {
                    JsonNode drivers = api.get("/races/" + raceId + "/drivers");
                    var items = FXCollections.<String>observableArrayList();
                    for (JsonNode d : drivers) {
                        items.add(d.get("code").asText() + " — " + d.path("fullName").asText()
                                + " (" + d.path("team").asText() + ")");
                    }
                    javafx.application.Platform.runLater(() -> {
                        list.setItems(items);
                        status.setText(items.size() + " drivers");
                    });
                } catch (Exception ex) {
                    javafx.application.Platform.runLater(() ->
                            status.setText("Error: " + ex.getMessage()));
                }
            }).start();
        });

        list.getSelectionModel().selectedItemProperty().addListener((obs, o, sel) -> {
            if (sel != null) {
                driver.set(sel.split(" — ")[0].toLowerCase());
            }
        });

        VBox.setVgrow(list, Priority.ALWAYS);
        getChildren().addAll(hint, load, list, status);
    }

    public void setRace(String raceId) {
        race.set(raceId);
        list.getItems().clear();
    }

    public ObjectProperty<String> driverProperty() {
        return driver;
    }
}
