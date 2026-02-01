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
 * Client for iFlow API - OpenAI-compatible
 * Endpoint: https://apis.iflow.cn/v1/chat/completions
 * API Keys: https://platform.iflow.cn/profile?tab=apiKey
 */
public class IFlowClient {
    private static final String IFLOW_API_URL = "https://apis.iflow.cn/v1/chat/completions";
    private static final int MAX_RETRIES = 3;
    private static final int INITIAL_RETRY_DELAY_MS = 1000;

    private final HttpClient client;
    private final String apiKey;

    public IFlowClient() {
        this.apiKey = SteveConfig.IFLOW_API_KEY.get();
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }

    public String sendRequest(String systemPrompt, String userPrompt) {
        if (apiKey == null || apiKey.isEmpty()) {
            SteveMod.LOGGER.error("iFlow API key not configured!");
            return null;
        }

        JsonObject requestBody = buildRequestBody(systemPrompt, userPrompt);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(IFLOW_API_URL))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(60))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
            .build();

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return parseResponse(response.body());
                }

                if (response.statusCode() == 429 || response.statusCode() >= 500) {
                    if (attempt < MAX_RETRIES - 1) {
                        long delayMs = (long) INITIAL_RETRY_DELAY_MS * (int) Math.pow(2, attempt);
                        SteveMod.LOGGER.warn("iFlow API failed ({}), retrying in {}ms", response.statusCode(), delayMs);
                        try {
                            Thread.sleep(delayMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return null;
                        }
                        continue;
                    }
                }

                SteveMod.LOGGER.error("iFlow API request failed: {}", response.statusCode());
                return null;
            } catch (Exception e) {
                if (attempt < MAX_RETRIES - 1) {
                    long delayMs = (long) INITIAL_RETRY_DELAY_MS * (int) Math.pow(2, attempt);
                    SteveMod.LOGGER.warn("Error communicating with iFlow API, retrying in {}ms", delayMs, e);
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                } else {
                    SteveMod.LOGGER.error("Error communicating with iFlow API after {} attempts", MAX_RETRIES, e);
                    return null;
                }
            }
        }
        return null;
    }

    private JsonObject buildRequestBody(String systemPrompt, String userPrompt) {
        JsonObject body = new JsonObject();
        body.addProperty("model", SteveConfig.IFLOW_MODEL.get());
        body.addProperty("temperature", SteveConfig.TEMPERATURE.get());
        body.addProperty("max_tokens", SteveConfig.MAX_TOKENS.get());

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
        return body;
    }

    private String parseResponse(String responseBody) {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            return json.getAsJsonArray("choices").get(0).getAsJsonObject()
                .getAsJsonObject("message").get("content").getAsString();
        } catch (Exception e) {
            SteveMod.LOGGER.error("Error parsing iFlow response", e);
            return null;
        }
    }
}
