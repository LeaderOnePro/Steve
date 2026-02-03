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
 * Client for Anthropic Claude API
 * Endpoint: https://api.anthropic.com/v1/messages
 * Authentication: x-api-key header
 */
public class ClaudeClient {
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int MAX_RETRIES = 3;
    private static final int INITIAL_RETRY_DELAY_MS = 1000;

    private final HttpClient client;
    private final String apiKey;

    public ClaudeClient() {
        this.apiKey = SteveConfig.CLAUDE_API_KEY.get();
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }

    public String sendRequest(String systemPrompt, String userPrompt) {
        if (apiKey == null || apiKey.isEmpty()) {
            SteveMod.LOGGER.error("Claude API key is not configured");
            return null;
        }

        String model = SteveConfig.CLAUDE_MODEL.get();
        
        SteveMod.LOGGER.info("[Claude] Sending request with model: {}", model);
        
        long startTime = System.currentTimeMillis();
        JsonObject requestBody = buildRequestBody(systemPrompt, userPrompt, model);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .header("Content-Type", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .timeout(Duration.ofSeconds(60))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
            .build();

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    long latency = System.currentTimeMillis() - startTime;
                    String result = parseResponse(response.body());
                    SteveMod.LOGGER.info("[Claude] Response received ({}ms, {} chars)", 
                        latency, 
                        result != null ? result.length() : 0);
                    return result;
                }

                if (response.statusCode() >= 500 || response.statusCode() == 529) {
                    if (attempt < MAX_RETRIES - 1) {
                        long delayMs = (long) (INITIAL_RETRY_DELAY_MS * Math.pow(2, attempt));
                        SteveMod.LOGGER.warn("Claude API failed ({}), retrying in {}ms", response.statusCode(), delayMs);
                        try {
                            Thread.sleep(delayMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return null;
                        }
                        continue;
                    }
                }

                SteveMod.LOGGER.error("Claude API request failed: {} - {}", response.statusCode(), 
                    truncate(response.body(), 200));
                return null;
            } catch (Exception e) {
                if (attempt < MAX_RETRIES - 1) {
                    long delayMs = (long) (INITIAL_RETRY_DELAY_MS * Math.pow(2, attempt));
                    SteveMod.LOGGER.warn("Error communicating with Claude API, retrying in {}ms", delayMs, e);
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                } else {
                    SteveMod.LOGGER.error("Error communicating with Claude API after {} attempts", MAX_RETRIES, e);
                    return null;
                }
            }
        }
        return null;
    }

    private JsonObject buildRequestBody(String systemPrompt, String userPrompt, String model) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", SteveConfig.MAX_TOKENS.get());
        
        // Claude uses a top-level "system" field instead of a system message in the array
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            body.addProperty("system", systemPrompt);
        }

        JsonArray messages = new JsonArray();
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
            // Claude response format: { "content": [{ "type": "text", "text": "..." }] }
            JsonArray content = json.getAsJsonArray("content");
            if (content != null && !content.isEmpty()) {
                JsonObject firstContent = content.get(0).getAsJsonObject();
                if ("text".equals(firstContent.get("type").getAsString())) {
                    return firstContent.get("text").getAsString();
                }
            }
            SteveMod.LOGGER.error("Claude response missing text content");
            return null;
        } catch (com.google.gson.JsonParseException | IllegalStateException | NullPointerException e) {
            SteveMod.LOGGER.error("Error parsing Claude response", e);
            return null;
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
