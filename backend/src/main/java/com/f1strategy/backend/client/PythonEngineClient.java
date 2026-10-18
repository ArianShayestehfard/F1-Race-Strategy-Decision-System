package com.f1strategy.backend.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;

/**
 * HTTP client for the Python engine (FastAPI on :8000).
 * All analysis / simulation / ML lives behind these calls — the Java side
 * never computes results itself (see docs/ARCHITECTURE.md).
 *
 * HTTP/1.1 is forced: the JDK default HTTP/2 (h2c upgrade) drops POST
 * bodies against uvicorn/h11.
 */
@Component
public class PythonEngineClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient rest;

    public PythonEngineClient(@Value("${python.engine.url:http://localhost:8000}") String baseUrl) {
        HttpClient jdk = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.rest = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(jdk))
                .baseUrl(baseUrl)
                .build();
    }

    public JsonNode get(String path) {
        return rest.get().uri(path).retrieve().body(JsonNode.class);
    }

    /** JSON is serialized explicitly: Map bodies were arriving empty at the engine. */
    public JsonNode post(String path, Object body) {
        try {
            String json = MAPPER.writeValueAsString(body);
            return rest.post().uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialize request body", e);
        }
    }

    public boolean isReachable() {
        try {
            return get("/health") != null;
        } catch (RestClientException e) {
            return false;
        }
    }
}
