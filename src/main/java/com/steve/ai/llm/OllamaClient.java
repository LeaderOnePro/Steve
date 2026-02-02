package com.steve.ai.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.steve.ai.SteveMod;
import com.steve.ai.config.SteveConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Client for Ollama API - Local LLM
 * Endpoint: http://localhost:11434/api/chat
 * No API key required (runs locally)
 */
public class OllamaClient {
    private static final int MAX_RETRIES = 3;
    private static final int INITIAL_RETRY_DELAY_MS = 1000;

    private final HttpClient client;
    private final String host;

    public OllamaClient() {
        this.host = SteveConfig.OLLAMA_HOST.get();
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }

    public String sendRequest(String systemPrompt, String userPrompt) {
        String apiUrl = host + "/api/chat";
        String model = SteveConfig.OLLAMA_MODEL.get();
        
        SteveMod.LOGGER.info("[Ollama] Sending request to {} with model: {}", apiUrl, model);
        
        JsonObject requestBody = buildRequestBody(systemPrompt, userPrompt);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiUrl))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(120)) // Local models may be slower
            .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
            .build();

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    String result = parseResponse(response.body());
                    SteveMod.LOGGER.info("[Ollama] Response received ({}ms, {} chars)", 
                        System.currentTimeMillis() - System.currentTimeMillis(), 
                        result != null ? result.length() : 0);
                    return result;
                }

                if (response.statusCode() >= 500) {
                    if (attempt < MAX_RETRIES - 1) {
                        long delayMs = (long) (INITIAL_RETRY_DELAY_MS * Math.pow(2, attempt));
                        SteveMod.LOGGER.warn("Ollama API failed ({}), retrying in {}ms", response.statusCode(), delayMs);
                        try {
                            Thread.sleep(delayMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return null;
                        }
                        continue;
                    }
                }

                SteveMod.LOGGER.error("Ollama API request failed: {}", response.statusCode());
                return null;
            } catch (Exception e) {
                if (attempt < MAX_RETRIES - 1) {
                    long delayMs = (long) (INITIAL_RETRY_DELAY_MS * Math.pow(2, attempt));
                    SteveMod.LOGGER.warn("Error communicating with Ollama API, retrying in {}ms", delayMs, e);
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                } else {
                    SteveMod.LOGGER.error("Error communicating with Ollama API after {} attempts", MAX_RETRIES, e);
                    return null;
                }
            }
        }
        return null;
    }

    private JsonObject buildRequestBody(String systemPrompt, String userPrompt) {
        JsonObject body = new JsonObject();
        String model = SteveConfig.OLLAMA_MODEL.get();
        body.addProperty("model", model);
        body.addProperty("stream", false); // Disable streaming for simpler response handling

        JsonArray messages = new JsonArray();
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", systemPrompt);
        messages.add(systemMessage);

        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", userPrompt);
        messages.add(userMessage);

        body.add("messages", messages);
        
        // Add options for temperature and token limits
        JsonObject options = new JsonObject();
        options.addProperty("temperature", SteveConfig.TEMPERATURE.get());
        options.addProperty("num_predict", SteveConfig.MAX_TOKENS.get());
        body.add("options", options);
        
        return body;
    }

    private String parseResponse(String responseBody) {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            return json.getAsJsonObject("message").get("content").getAsString();
        } catch (com.google.gson.JsonParseException | IllegalStateException | NullPointerException e) {
            SteveMod.LOGGER.error("Error parsing Ollama response", e);
            return null;
        }
    }
}
