package com.f1strategy.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.f1strategy.backend.client.PythonEngineClient;
import com.f1strategy.backend.model.Strategy;
import com.f1strategy.backend.repository.StrategyRepository;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Runs simulations via the Python engine and persists every scenario
 * into the strategies table so past runs can be compared later.
 */
@Service
public class SimulationService {

    private final PythonEngineClient engine;
    private final StrategyRepository strategies;
    private final ObjectMapper mapper = new ObjectMapper();

    public SimulationService(PythonEngineClient engine, StrategyRepository strategies) {
        this.engine = engine;
        this.strategies = strategies;
    }

    public JsonNode simulate(Map<String, Object> request) {
        JsonNode result = engine.post("/simulate", request);
        persist(request, result);
        return result;
    }

    public JsonNode recommend(Map<String, Object> request) {
        return engine.post("/strategy/recommend", request);
    }

    private void persist(Map<String, Object> request, JsonNode result) {
        try {
            Strategy s = new Strategy();
            s.setRaceId(String.valueOf(request.get("race_id")));
            s.setDriverId(String.valueOf(request.get("driver_id")));
            s.setName("simulate:pits=" + request.get("pit_laps"));
            s.setParamsJson(mapper.writeValueAsString(request));
            s.setResultJson(mapper.writeValueAsString(result));
            strategies.save(s);
        } catch (Exception ignored) {
            // persistence must never break the API response
        }
    }
}
