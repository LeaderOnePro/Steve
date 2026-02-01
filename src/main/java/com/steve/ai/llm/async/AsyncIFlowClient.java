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
 * Asynchronous Client for iFlow API.
 * OpenAI-compatible format.
 * API Keys: https://platform.iflow.cn/profile?tab=apiKey
 */
public class AsyncIFlowClient implements AsyncLLMClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncIFlowClient.class);
    private static final String IFLOW_API_URL = "https://apis.iflow.cn/v1/chat/completions";
    private static final String PROVIDER_ID = "iflow";

    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final double temperature;

    public AsyncIFlowClient(String apiKey, String model, int maxTokens, double temperature) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("iFlow API key cannot be null or empty");
        }

        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;

        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

        LOGGER.info("AsyncIFlowClient initialized (model: {}, maxTokens: {}, temperature: {})",
            model, maxTokens, temperature);
    }

    @Override
    public CompletableFuture<LLMResponse> sendAsync(String prompt, Map<String, Object> params) {
        long startTime = System.currentTimeMillis();
        String requestBody = buildRequestBody(prompt, params);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(IFLOW_API_URL))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .timeout(Duration.ofSeconds(60))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        LOGGER.debug("[iflow] Sending async request");
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                long latency = System.currentTimeMillis() - startTime;
                if (response.statusCode() != 200) {
                    LOGGER.error("iFlow API error: {} - {}", response.statusCode(), truncate(response.body(), 200));
                    throw new LLMException("iFlow API returned status " + response.statusCode(), 
                        mapStatusCodeToErrorType(response.statusCode()), PROVIDER_ID, isRetryable(response.statusCode()));
                }
                return parseResponse(response.body(), latency, params);
            });
    }

    private String buildRequestBody(String prompt, Map<String, Object> params) {
        JsonObject body = new JsonObject();
        String activeModel = (String) params.getOrDefault("model", this.model);
        double activeTemp = (Double) params.getOrDefault("temperature", this.temperature);
        int activeMaxTokens = (Integer) params.getOrDefault("maxTokens", this.maxTokens);

        body.addProperty("model", activeModel);
        body.addProperty("temperature", activeTemp);
        body.addProperty("max_tokens", activeMaxTokens);

        JsonArray messages = new JsonArray();
        
        String systemPrompt = (String) params.get("systemPrompt");
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            JsonObject systemMsg = new JsonObject();
            systemMsg.addProperty("role", "system");
            systemMsg.addProperty("content", systemPrompt);
            messages.add(systemMsg);
        }

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", prompt);
        messages.add(userMsg);

        body.add("messages", messages);
        return body.toString();
    }

    private LLMResponse parseResponse(String responseBody, long latencyMs, Map<String, Object> params) {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            String content = json.getAsJsonArray("choices").get(0).getAsJsonObject()
                .getAsJsonObject("message").get("content").getAsString();
            
            JsonObject usage = json.has("usage") ? json.getAsJsonObject("usage") : null;
            int tokens = usage != null ? usage.get("total_tokens").getAsInt() : 0;
            String activeModel = (String) params.getOrDefault("model", this.model);

            return LLMResponse.builder()
                .content(content)
                .model(activeModel)
                .providerId(PROVIDER_ID)
                .tokensUsed(tokens)
                .latencyMs(latencyMs)
                .fromCache(false)
                .build();
        } catch (com.google.gson.JsonParseException | IllegalStateException | IndexOutOfBoundsException e) {
            LOGGER.error("Error parsing iFlow response", e);
            throw new LLMException("Failed to parse iFlow API response", LLMException.ErrorType.INVALID_RESPONSE, PROVIDER_ID, false, e);
        }
    }

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public boolean isHealthy() {
        return true; 
    }

    private LLMException.ErrorType mapStatusCodeToErrorType(int statusCode) {
        return switch (statusCode) {
            case 401, 403 -> LLMException.ErrorType.AUTH_ERROR;
            case 429 -> LLMException.ErrorType.RATE_LIMIT;
            case 500, 502, 503, 504 -> LLMException.ErrorType.SERVER_ERROR;
            default -> LLMException.ErrorType.CLIENT_ERROR;
        };
    }

    private boolean isRetryable(int statusCode) {
        return statusCode == 429 || (statusCode >= 500 && statusCode <= 599);
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
