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
 * Tab 1 — lists races known to the backend; selecting one drives the other tabs.
 */
public class RaceSelectionView extends VBox {

    private final ObjectProperty<String> race = new SimpleObjectProperty<>();
    private final ListView<String> list = new ListView<>();

    public RaceSelectionView(ApiClient api) {
        setPadding(new Insets(16));
        setSpacing(10);

        Label hint = new Label("Select a race (loaded from SQLite via the backend):");
        Label status = new Label();
        Button refresh = new Button("Refresh races");
        refresh.setOnAction(e -> new Thread(() -> {
            try {
                JsonNode races = api.get("/races");
                var items = FXCollections.<String>observableArrayList();
                for (JsonNode r : races) {
                    items.add(r.get("raceId").asText() + " — " + r.path("name").asText());
                }
                javafx.application.Platform.runLater(() -> {
                    list.setItems(items);
                    status.setText(items.size() + " races loaded");
                });
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() ->
                        status.setText("Backend unreachable: " + ex.getMessage()));
            }
        }).start());

        list.getSelectionModel().selectedItemProperty().addListener((obs, o, sel) -> {
            if (sel != null) {
                race.set(sel.split(" — ")[0]);
            }
        });

        VBox.setVgrow(list, Priority.ALWAYS);
        getChildren().addAll(hint, refresh, list, status);
    }

    public ObjectProperty<String> raceProperty() {
        return race;
    }
}
