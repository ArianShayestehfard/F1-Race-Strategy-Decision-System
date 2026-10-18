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

    // backend default is 8088: 8080 is occupied by a local service on the dev machine
    private static final String BASE = System.getProperty("api.base", "http://localhost:8088/api");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private static final ObjectMapper JSON = new ObjectMapper();

    public JsonNode get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + path)).GET().build();
        HttpResponse<String> res = sendWithRetry(req);
        return JSON.readTree(res.body());
    }

    public JsonNode post(String path, Map<String, Object> body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();
        HttpResponse<String> res = sendWithRetry(req);
        return JSON.readTree(res.body());
    }

    /**
     * One retry on transient IO errors (e.g. a pooled connection closed while
     * the backend restarts) so a race at startup does not leave empty screens.
     */
    private HttpResponse<String> sendWithRetry(HttpRequest req) throws Exception {
        try {
            return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (java.io.IOException transientError) {
            Thread.sleep(800);
            return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        }
    }
}
