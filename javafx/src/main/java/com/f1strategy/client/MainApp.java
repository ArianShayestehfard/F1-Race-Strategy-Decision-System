package com.f1strategy.client;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * F1 Race Strategy — Decision Support System, desktop client.
 * Tabs: Races (import + select) → Drivers → Analyze → Simulate → Strategy.
 * Requires the Spring Boot backend (:8088) and the Python engine (:8000).
 */
public class MainApp extends Application {

    private static final String STYLESHEET = "/theme.css";
    private static final String BACKEND_HOST = System.getProperty("api.base", "http://localhost:8088/api");
    private static final String PYTHON_HOST = "http://localhost:8000";

    private final ApiClient api = new ApiClient();

    private final Region backendDot = new Region();
    private final Region pythonDot = new Region();

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

        Scene scene = new Scene(root, 1200, 760);
        scene.getStylesheets().add(getClass().getResource(STYLESHEET).toExternalForm());
        stage.setScene(scene);
        stage.setTitle("F1 Race Strategy — Decision Support System");
        stage.show();

        pingServices();
    }

    private HBox header() {
        backendDot.getStyleClass().add("status-dot");
        pythonDot.getStyleClass().add("status-dot");

        HBox title = new HBox(10,
                Ui.label("🏎 F1 Strategy Decision Support", "app-title"),
                Ui.label("pit now or not?", "app-sub"));
        title.setAlignment(Pos.CENTER_LEFT);

        HBox services = new HBox(8,
                backendDot, Ui.label("backend :8088", "app-sub"),
                pythonDot, Ui.label("python :8000", "app-sub"));
        services.setAlignment(Pos.CENTER_RIGHT);

        HBox bar = new HBox(20, title, Ui.spacer(), services);
        bar.getStyleClass().add("header");
        bar.setPadding(new Insets(12, 20, 12, 20));
        return bar;
    }

    /** Lights the header dots green/red so the user sees what is reachable. */
    private void pingServices() {
        Async.run(
                () -> api.get("/engine/health"),
                () -> {
                    backendDot.getStyleClass().add("ok");
                },
                msg -> {
                    backendDot.getStyleClass().add("down");
                });
        Async.run(
                () -> {
                    java.net.http.HttpClient.newHttpClient().send(
                            java.net.http.HttpRequest.newBuilder(
                                    java.net.URI.create(PYTHON_HOST + "/health")).GET().build(),
                            java.net.http.HttpResponse.BodyHandlers.discarding());
                },
                () -> {
                    pythonDot.getStyleClass().add("ok");
                },
                msg -> {
                    pythonDot.getStyleClass().add("down");
                });
    }

    private Tab tab(String title, VBox content) {
        // scrollable so nothing is clipped on smaller screens
        javafx.scene.control.ScrollPane scroller = new javafx.scene.control.ScrollPane(content);
        scroller.setFitToWidth(true);
        scroller.setFitToHeight(false);
        scroller.getStyleClass().add("edge-to-edge");
        scroller.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
        return new Tab(title, scroller);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
