package com.steve.ai.llm.async;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Asynchronous LongCat API client.
 * OpenAI-compatible format.
 */
public class AsyncLongCatClient implements AsyncLLMClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncLongCatClient.class);
    private static final String LONGCAT_API_URL = "https://api.longcat.chat/openai/v1/chat/completions";
    private static final String PROVIDER_ID = "longcat";

    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final double temperature;

    public AsyncLongCatClient(String apiKey, String model, int maxTokens, double temperature) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("LongCat API key cannot be null or empty");
        }

        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;

        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

        LOGGER.info("AsyncLongCatClient initialized (model: {}, maxTokens: {}, temperature: {})",
            model, maxTokens, temperature);
    }

    @Override
    public CompletableFuture<LLMResponse> sendAsync(String prompt, Map<String, Object> params) {
        long startTime = System.currentTimeMillis();
        String requestBody = buildRequestBody(prompt, params);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(LONGCAT_API_URL))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .timeout(Duration.ofSeconds(60))
            .build();

        LOGGER.debug("[longcat] Sending async request");

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                long latencyMs = System.currentTimeMillis() - startTime;

                if (response.statusCode() != 200) {
                    LLMException.ErrorType errorType = determineErrorType(response.statusCode());
                    LOGGER.error("[longcat] API error: status={}, body={}", response.statusCode(), truncate(response.body(), 200));
                    throw new LLMException("LongCat API error: HTTP " + response.statusCode(), errorType, PROVIDER_ID, response.statusCode() >= 500);
                }

                return parseResponse(response.body(), latencyMs);
            });
    }

    private String buildRequestBody(String prompt, Map<String, Object> params) {
        JsonObject body = new JsonObject();
        body.addProperty("model", (String) params.getOrDefault("model", this.model));
        body.addProperty("max_tokens", (int) params.getOrDefault("maxTokens", this.maxTokens));
        body.addProperty("temperature", (double) params.getOrDefault("temperature", this.temperature));

        JsonArray messages = new JsonArray();
        String systemPrompt = (String) params.get("systemPrompt");
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            JsonObject systemMessage = new JsonObject();
            systemMessage.addProperty("role", "system");
            systemMessage.addProperty("content", systemPrompt);
            messages.add(systemMessage);
        }

        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", prompt);
        messages.add(userMessage);

        body.add("messages", messages);
        return body.toString();
    }

    private LLMResponse parseResponse(String responseBody, long latencyMs) {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonObject firstChoice = json.getAsJsonArray("choices").get(0).getAsJsonObject();
            String content = firstChoice.getAsJsonObject("message").get("content").getAsString();

            int tokensUsed = json.has("usage") ? json.getAsJsonObject("usage").get("total_tokens").getAsInt() : 0;

            return LLMResponse.builder()
                .content(content)
                .model(model)
                .providerId(PROVIDER_ID)
                .latencyMs(latencyMs)
                .tokensUsed(tokensUsed)
                .fromCache(false)
                .build();
        } catch (Exception e) {
            throw new LLMException("Failed to parse LongCat response", LLMException.ErrorType.INVALID_RESPONSE, PROVIDER_ID, false, e);
        }
    }

    private LLMException.ErrorType determineErrorType(int statusCode) {
        return switch (statusCode) {
            case 429 -> LLMException.ErrorType.RATE_LIMIT;
            case 401, 403 -> LLMException.ErrorType.AUTH_ERROR;
            case 400 -> LLMException.ErrorType.CLIENT_ERROR;
            default -> statusCode >= 500 ? LLMException.ErrorType.SERVER_ERROR : LLMException.ErrorType.CLIENT_ERROR;
        };
    }

    private String truncate(String str, int maxLength) {
        if (str == null) return "[null]";
        return str.length() <= maxLength ? str : str.substring(0, maxLength) + "...";
    }

    @Override public String getProviderId() { return PROVIDER_ID; }
    @Override public boolean isHealthy() { return true; }
}
