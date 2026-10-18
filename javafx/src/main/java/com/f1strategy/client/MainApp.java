package com.f1strategy.client;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

/**
 * F1 Race Strategy Decision Support System — desktop client.
 * Tabs: Races, Drivers, Analyze, Simulate, Strategy.
 * Requires the Spring Boot backend (:8080) and the Python engine (:8000).
 */
public class MainApp extends Application {

    private static final javafx.scene.paint.Color BG = javafx.scene.paint.Color.web("#f5f6fa");
    private final ApiClient api = new ApiClient();

    @Override
    public void start(Stage stage) {
        RaceSelectionView raceView = new RaceSelectionView(api);
        DriverSelectionView driverView = new DriverSelectionView(api);
        AnalyzeView analyzeView = new AnalyzeView(api);
        SimulateView simulateView = new SimulateView(api);
        StrategyView strategyView = new StrategyView(api);

        raceView.raceProperty().addListener((obs, old, raceId) -> {
            driverView.setRace(raceId);
            analyzeView.setRace(raceId);
            simulateView.setRace(raceId);
            strategyView.setRace(raceId);
        });
        driverView.driverProperty().addListener((obs, old, driverId) -> {
            analyzeView.setDriver(driverId);
            simulateView.setDriver(driverId);
            strategyView.setDriver(driverId);
        });

        TabPane tabs = new TabPane(
                tab("Races", raceView),
                tab("Drivers", driverView),
                tab("Analyze", analyzeView),
                tab("Simulate", simulateView),
                tab("Compare Strategies", strategyView));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        BorderPane root = new BorderPane();
        root.setTop(header());
        root.setCenter(tabs);
        root.setStyle("-fx-background-color: #f5f6fa;");

        stage.setScene(new Scene(root, 1100, 700));
        stage.setTitle("F1 Race Strategy — Decision Support System");
        stage.show();
    }

    private HBox header() {
        Label title = new Label("🏎 F1 Strategy Decision Support");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #1e272e;");
        Label backend = new Label("backend :8088 · python :8000");
        backend.setStyle("-fx-text-fill: #57606f;");
        HBox h = new HBox(20, title, backend);
        h.setPadding(new Insets(12));
        return h;
    }

    private Tab tab(String title, javafx.scene.Node content) {
        Tab t = new Tab(title, content);
        return t;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
