package com.f1strategy.client;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Tab 2 — drivers of the selected race; the driver feeds every other tab.
 */
class DriverSelectionView extends VBox {

    private final ObjectProperty<String> race = new SimpleObjectProperty<>();
    private final ObjectProperty<String> driver = new SimpleObjectProperty<>();
    private final ListView<String> list = new ListView<>();
    private final Label status = Ui.label("Select a race first.", "banner-info");
    private final Button loadButton = Ui.button("Load drivers", true);

    DriverSelectionView(ApiClient api) {
        setSpacing(12);
        Ui.padded(this, 16);

        list.setCellFactory(v -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
            }
        });
        VBox.setVgrow(list, Priority.ALWAYS);

        loadButton.setOnAction(e -> {
            String raceId = race.get();
            if (raceId == null) {
                status.setText("Select a race in the Races tab first.");
                return;
            }
            loadButton.setDisable(true);
            status.setText("Loading drivers…");
            var holder = new java.util.concurrent.atomic.AtomicReference<JsonNode>();
            Async.run(
                    () -> {
                        holder.set(api.get("/races/" + raceId + "/drivers"));
                    },
                    () -> {
                        loadButton.setDisable(false);
                        var items = FXCollections.<String>observableArrayList();
                        for (JsonNode d : holder.get()) {
                            items.add(String.format("%-4s  %s  ·  %s",
                                    d.path("code").asText(), d.path("fullName").asText(),
                                    d.path("team").asText()));
                        }
                        list.setItems(items);
                        status.setText(items.size() + " drivers — pick one, then use Analyze / Simulate / Strategy.");
                    },
                    msg -> {
                        loadButton.setDisable(false);
                        status.setText("Error: " + msg);
                        status.getStyleClass().add("banner-error");
                    });
        });
        list.getSelectionModel().selectedItemProperty().addListener((obs, o, sel) -> {
            if (sel != null) {
                driver.set(sel.substring(0, 3).toLowerCase());
            }
        });

        getChildren().addAll(Ui.card("Drivers of the selected race", loadButton, status), list);
    }

    void setRace(String raceId) {
        race.set(raceId);
        list.getItems().clear();
    }

    ObjectProperty<String> driverProperty() {
        return driver;
    }
}
