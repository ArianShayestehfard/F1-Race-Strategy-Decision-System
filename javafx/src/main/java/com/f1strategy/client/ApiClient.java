package com.f1strategy.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Thin HTTP client for the Spring Boot backend (http://localhost:8080/api).
 */
public class ApiClient {

    private static final String BASE = System.getProperty("api.base", "http://localhost:8080/api");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private static final ObjectMapper JSON = new ObjectMapper();

    public JsonNode get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + path)).GET().build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        return JSON.readTree(res.body());
    }

    public JsonNode post(String path, Map<String, Object> body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        return JSON.readTree(res.body());
    }
}
