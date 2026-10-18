package com.f1strategy.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.f1strategy.backend.client.PythonEngineClient;
import com.f1strategy.backend.repository.StrategyRepository;
import com.f1strategy.backend.service.SimulationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(com.f1strategy.backend.web.EngineController.class)
class EngineControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PythonEngineClient engine;

    @TestConfiguration
    static class Config {
        @Bean
        PythonEngineClient engine() {
            return mock(PythonEngineClient.class);
        }

        @Bean
        SimulationService simulations(PythonEngineClient engine) {
            return new SimulationService(engine, mock(StrategyRepository.class));
        }
    }

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void analyzeProxiesToPython() throws Exception {
        when(engine.get("/analyze/2024-8/nor"))
                .thenReturn(json.createObjectNode().put("driver_id", "nor").put("avg_lap_ms", 76110));

        mvc.perform(get("/api/analyze/2024-8/nor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driver_id").value("nor"));
    }

    @Test
    void unreachablePythonReturns502() throws Exception {
        when(engine.get(any(String.class))).thenThrow(new ResourceAccessException("connection refused"));

        mvc.perform(get("/api/analyze/2024-8/nor"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void recommendEndpointProxies() throws Exception {
        when(engine.post(eq("/strategy/recommend"), any()))
                .thenReturn(json.createObjectNode().put("recommended_pit_lap", 22).put("confidence", 0.84));

        mvc.perform(post("/api/strategy/recommend")
                        .contentType("application/json")
                        .content("{\"race_id\":\"2024-8\",\"driver_id\":\"nor\",\"current_lap\":18}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommended_pit_lap").value(22))
                .andExpect(jsonPath("$.confidence").value(0.84));
    }
}
