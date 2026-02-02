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
 * Asynchronous Client for Ollama API.
 * Local LLM - no API key required.
 * Endpoint: http://localhost:11434/api/chat
 */
public class AsyncOllamaClient implements AsyncLLMClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncOllamaClient.class);
    private static final String PROVIDER_ID = "ollama";

    private final HttpClient httpClient;
    private final String host;
    private final String model;
    private final int maxTokens;
    private final double temperature;

    public AsyncOllamaClient(String host, String model, int maxTokens, double temperature) {
        // Normalize host: remove trailing slash to prevent double slashes in URL
        String hostValue = (host != null && !host.isEmpty()) ? host : "http://localhost:11434";
        this.host = hostValue.endsWith("/") ? hostValue.substring(0, hostValue.length() - 1) : hostValue;
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;

        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

        LOGGER.info("AsyncOllamaClient initialized (host: {}, model: {}, maxTokens: {}, temperature: {})",
            this.host, model, maxTokens, temperature);
    }

    @Override
    public CompletableFuture<LLMResponse> sendAsync(String prompt, Map<String, Object> params) {
        long startTime = System.currentTimeMillis();
        String requestBody = buildRequestBody(prompt, params);
        String apiUrl = host + "/api/chat";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiUrl))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(120)) // Local models may be slower
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        LOGGER.debug("[ollama] Sending async request to {}", apiUrl);
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                long latency = System.currentTimeMillis() - startTime;
                if (response.statusCode() != 200) {
                    LOGGER.error("Ollama API error: {} - {}", response.statusCode(), truncate(response.body(), 200));
                    throw new LLMException("Ollama API returned status " + response.statusCode(), 
                        mapStatusCodeToErrorType(response.statusCode()), PROVIDER_ID, isRetryable(response.statusCode()));
                }
                return parseResponse(response.body(), latency, params);
            });
    }

    private String buildRequestBody(String prompt, Map<String, Object> params) {
        JsonObject body = new JsonObject();
        String activeModel = (String) params.getOrDefault("model", this.model);
        double activeTemp = (double) params.getOrDefault("temperature", this.temperature);
        int activeMaxTokens = (int) params.getOrDefault("maxTokens", this.maxTokens);

        body.addProperty("model", activeModel);
        body.addProperty("stream", false); // Disable streaming

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
        
        // Add options for temperature and token limits
        JsonObject options = new JsonObject();
        options.addProperty("temperature", activeTemp);
        options.addProperty("num_predict", activeMaxTokens);
        body.add("options", options);
        
        return body.toString();
    }

    private LLMResponse parseResponse(String responseBody, long latencyMs, Map<String, Object> params) {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            String content = json.getAsJsonObject("message").get("content").getAsString();
            
            // Ollama includes eval_count for tokens used
            int tokens = json.has("eval_count") ? json.get("eval_count").getAsInt() : 0;
            String activeModel = (String) params.getOrDefault("model", this.model);

            return LLMResponse.builder()
                .content(content)
                .model(activeModel)
                .providerId(PROVIDER_ID)
                .tokensUsed(tokens)
                .latencyMs(latencyMs)
                .fromCache(false)
                .build();
        } catch (com.google.gson.JsonParseException | IllegalStateException | NullPointerException e) {
            LOGGER.error("Error parsing Ollama response", e);
            throw new LLMException("Failed to parse Ollama API response", LLMException.ErrorType.INVALID_RESPONSE, PROVIDER_ID, false, e);
        }
    }

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public boolean isHealthy() {
        // Try a quick HEAD request to check if Ollama is running
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(host))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(2))
                .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (Exception e) {
            LOGGER.debug("[ollama] Health check failed: {}", e.getMessage());
            return false;
        }
    }

    private LLMException.ErrorType mapStatusCodeToErrorType(int statusCode) {
        return switch (statusCode) {
            case 404 -> LLMException.ErrorType.CLIENT_ERROR; // Model not found
            case 500, 502, 503, 504 -> LLMException.ErrorType.SERVER_ERROR;
            default -> LLMException.ErrorType.CLIENT_ERROR;
        };
    }

    private boolean isRetryable(int statusCode) {
        return statusCode >= 500 && statusCode <= 599;
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
