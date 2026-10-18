package com.f1strategy.backend.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.f1strategy.backend.client.PythonEngineClient;
import com.f1strategy.backend.service.SimulationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Proxies the Python engine endpoints (ingest / analyze / predict /
 * simulate / recommend) under /api. See docs/API_CONTRACT.md for payload
 * shapes.
 */
@RestController
@RequestMapping("/api")
public class EngineController {

    private final PythonEngineClient engine;
    private final SimulationService simulations;

    public EngineController(PythonEngineClient engine, SimulationService simulations) {
        this.engine = engine;
        this.simulations = simulations;
    }

    @GetMapping("/engine/health")
    public ResponseEntity<JsonNode> engineHealth() {
        return ResponseEntity.ok(engine.get("/health"));
    }

    @PostMapping("/ingest")
    public JsonNode ingest(@RequestBody Map<String, Object> request) {
        return engine.post("/ingest", request);
    }

    @GetMapping("/analyze/{raceId}/{driverId}")
    public JsonNode analyze(@PathVariable String raceId, @PathVariable String driverId) {
        return engine.get("/analyze/" + raceId + "/" + driverId);
    }

    @PostMapping("/predict")
    public JsonNode predict(@RequestBody Map<String, Object> request) {
        return engine.post("/predict", request);
    }

    @PostMapping("/simulate")
    public JsonNode simulate(@RequestBody Map<String, Object> request) {
        return simulations.simulate(request);
    }

    @PostMapping("/strategy/recommend")
    public JsonNode recommend(@RequestBody Map<String, Object> request) {
        return simulations.recommend(request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> onError(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "python engine unreachable: " + e.getMessage()));
    }
}
