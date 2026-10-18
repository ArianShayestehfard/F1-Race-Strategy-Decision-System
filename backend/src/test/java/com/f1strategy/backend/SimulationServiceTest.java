package com.f1strategy.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.f1strategy.backend.client.PythonEngineClient;
import com.f1strategy.backend.model.Strategy;
import com.f1strategy.backend.repository.StrategyRepository;
import com.f1strategy.backend.service.SimulationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SimulationServiceTest {

    private PythonEngineClient engine;
    private StrategyRepository strategies;
    private SimulationService service;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        engine = mock(PythonEngineClient.class);
        strategies = mock(StrategyRepository.class);
        service = new SimulationService(engine, strategies);
    }

    @Test
    void simulateProxiesAndPersists() throws Exception {
        Map<String, Object> request = Map.of(
                "race_id", "2024-8", "driver_id", "nor",
                "start_compound", "MEDIUM", "pit_laps", List.of(20, 41));
        JsonNode result = mapper.readTree(
                "{\"finish_position\": 4, \"total_time_ms\": 5321000, \"timeline\": []}");
        when(engine.post(eq("/simulate"), any())).thenReturn(result);

        JsonNode out = service.simulate(request);

        assertThat(out.get("finish_position").asInt()).isEqualTo(4);
        ArgumentCaptor<Strategy> captor = ArgumentCaptor.forClass(Strategy.class);
        verify(strategies).save(captor.capture());
        Strategy saved = captor.getValue();
        assertThat(saved.getRaceId()).isEqualTo("2024-8");
        assertThat(saved.getDriverId()).isEqualTo("nor");
        assertThat(saved.getParamsJson()).contains("pit_laps");
        assertThat(saved.getResultJson()).contains("5321000");
    }

    @Test
    void recommendProxiesWithoutPersisting() {
        when(engine.post(eq("/strategy/recommend"), any()))
                .thenReturn(mapper.createObjectNode().put("recommended_pit_lap", 22));

        JsonNode out = service.recommend(Map.of("race_id", "2024-8", "current_lap", 18));

        assertThat(out.get("recommended_pit_lap").asInt()).isEqualTo(22);
        verify(strategies, org.mockito.Mockito.never()).save(any());
    }
}
