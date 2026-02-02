package com.steve.ai.llm;

import com.steve.ai.SteveMod;
import com.steve.ai.action.Task;
import com.steve.ai.config.SteveConfig;
import com.steve.ai.entity.SteveEntity;
import com.steve.ai.llm.async.*;
import com.steve.ai.llm.resilience.LLMFallbackHandler;
import com.steve.ai.llm.resilience.ResilientLLMClient;
import com.steve.ai.memory.WorldKnowledge;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class TaskPlanner {
    // Legacy synchronous clients (for backward compatibility)
    private final OllamaClient ollamaClient;
    private final LongCatClient longCatClient;
    private final IFlowClient iflowClient;
    private final DeepSeekClient deepSeekClient;
    private final OpenAIClient openAIClient;
    private final GeminiClient geminiClient;
    private final GroqClient groqClient;

    // NEW: Async resilient clients
    private final AsyncLLMClient asyncOllamaClient;
    private final AsyncLLMClient asyncLongCatClient;
    private final AsyncLLMClient asyncIFlowClient;
    private final AsyncLLMClient asyncDeepSeekClient;
    private final AsyncLLMClient asyncOpenAIClient;
    private final AsyncLLMClient asyncGeminiClient;
    private final AsyncLLMClient asyncGroqClient;
    private final LLMCache llmCache;
    private final LLMFallbackHandler fallbackHandler;

    public TaskPlanner() {
        // Legacy clients (always initialize - these work without external dependencies)
        this.ollamaClient = new OllamaClient();
        this.longCatClient = new LongCatClient();
        this.iflowClient = new IFlowClient();
        this.deepSeekClient = new DeepSeekClient();
        this.openAIClient = new OpenAIClient();
        this.geminiClient = new GeminiClient();
        this.groqClient = new GroqClient();

        // Initialize async infrastructure (may fail if Caffeine/Resilience4j not available in runtime)
        LLMCache tempCache = null;
        LLMFallbackHandler tempFallback = null;
        AsyncLLMClient tempAsyncOllama = null;
        AsyncLLMClient tempAsyncLongCat = null;
        AsyncLLMClient tempAsyncIFlow = null;
        AsyncLLMClient tempAsyncDeepSeek = null;
        AsyncLLMClient tempAsyncOpenAI = null;
        AsyncLLMClient tempAsyncGemini = null;
        AsyncLLMClient tempAsyncGroq = null;

        try {
            tempCache = new LLMCache();
            tempFallback = new LLMFallbackHandler();

            // Initialize async clients with resilience wrappers
            int maxTokens = SteveConfig.MAX_TOKENS.get();
            double temperature = SteveConfig.TEMPERATURE.get();

            // Create base async clients with their respective configurations
            AsyncLLMClient baseOllama = new AsyncOllamaClient(
                SteveConfig.OLLAMA_HOST.get(),
                SteveConfig.OLLAMA_MODEL.get(),
                maxTokens,
                temperature
            );

            AsyncLLMClient baseLongCat = new AsyncLongCatClient(
                SteveConfig.LONGCAT_API_KEY.get(),
                SteveConfig.LONGCAT_MODEL.get(),
                maxTokens,
                temperature
            );

            AsyncLLMClient baseIFlow = new AsyncIFlowClient(
                SteveConfig.IFLOW_API_KEY.get(),
                SteveConfig.IFLOW_MODEL.get(),
                maxTokens,
                temperature
            );

            AsyncLLMClient baseDeepSeek = new AsyncDeepSeekClient(
                SteveConfig.DEEPSEEK_API_KEY.get(),
                SteveConfig.DEEPSEEK_MODEL.get(),
                maxTokens,
                temperature
            );

            AsyncLLMClient baseOpenAI = new AsyncOpenAIClient(
                SteveConfig.OPENAI_API_KEY.get(), 
                SteveConfig.OPENAI_MODEL.get(), 
                maxTokens, 
                temperature
            );

            AsyncLLMClient baseGemini = new AsyncGeminiClient(
                SteveConfig.GEMINI_API_KEY.get(), 
                SteveConfig.GEMINI_MODEL.get(), 
                maxTokens, 
                temperature
            );

            AsyncLLMClient baseGroq = new AsyncGroqClient(
                SteveConfig.GROQ_API_KEY.get(), 
                SteveConfig.GROQ_MODEL.get(), 
                maxTokens, 
                temperature
            );

            // Wrap with resilience patterns (caching, retries, circuit breaker)
            tempAsyncOllama = new ResilientLLMClient(baseOllama, tempCache, tempFallback);
            tempAsyncLongCat = new ResilientLLMClient(baseLongCat, tempCache, tempFallback);
            tempAsyncIFlow = new ResilientLLMClient(baseIFlow, tempCache, tempFallback);
            tempAsyncDeepSeek = new ResilientLLMClient(baseDeepSeek, tempCache, tempFallback);
            tempAsyncOpenAI = new ResilientLLMClient(baseOpenAI, tempCache, tempFallback);
            tempAsyncGemini = new ResilientLLMClient(baseGemini, tempCache, tempFallback);
            tempAsyncGroq = new ResilientLLMClient(baseGroq, tempCache, tempFallback);

            SteveMod.LOGGER.info("TaskPlanner initialized with async resilient clients");
        } catch (NoClassDefFoundError | Exception e) {
            SteveMod.LOGGER.warn("Failed to initialize async clients (missing dependencies), using sync-only mode: {}", e.getMessage());
        }

        this.llmCache = tempCache;
        this.fallbackHandler = tempFallback;
        this.asyncOllamaClient = tempAsyncOllama;
        this.asyncLongCatClient = tempAsyncLongCat;
        this.asyncIFlowClient = tempAsyncIFlow;
        this.asyncDeepSeekClient = tempAsyncDeepSeek;
        this.asyncOpenAIClient = tempAsyncOpenAI;
        this.asyncGeminiClient = tempAsyncGemini;
        this.asyncGroqClient = tempAsyncGroq;
    }

    public ResponseParser.ParsedResponse planTasks(SteveEntity steve, String command) {
        try {
            String systemPrompt = PromptBuilder.buildSystemPrompt();
            WorldKnowledge worldKnowledge = new WorldKnowledge(steve);
            String userPrompt = PromptBuilder.buildUserPrompt(steve, command, worldKnowledge);
            
            String provider = SteveConfig.AI_PROVIDER.get().toLowerCase();
            SteveMod.LOGGER.info("Requesting AI plan for Steve '{}' using {}: {}", steve.getSteveName(), provider, command);
            
            String response = getAIResponse(provider, systemPrompt, userPrompt);
            
            if (response == null) {
                SteveMod.LOGGER.error("Failed to get AI response for command: {}", command);
                return null;
            }            ResponseParser.ParsedResponse parsedResponse = ResponseParser.parseAIResponse(response);
            
            if (parsedResponse == null) {
                SteveMod.LOGGER.error("Failed to parse AI response");
                return null;
            }
            
            SteveMod.LOGGER.info("Plan: {} ({} tasks)", parsedResponse.getPlan(), parsedResponse.getTasks().size());
            
            return parsedResponse;
            
        } catch (Exception e) {
            SteveMod.LOGGER.error("Error planning tasks", e);
            return null;
        }
    }

    private String getAIResponse(String provider, String systemPrompt, String userPrompt) {
        String response = switch (provider) {
            case "ollama" -> ollamaClient.sendRequest(systemPrompt, userPrompt);
            case "longcat" -> longCatClient.sendRequest(systemPrompt, userPrompt);
            case "iflow" -> iflowClient.sendRequest(systemPrompt, userPrompt);
            case "deepseek" -> deepSeekClient.sendRequest(systemPrompt, userPrompt);
            case "openai" -> openAIClient.sendRequest(systemPrompt, userPrompt);
            case "gemini" -> geminiClient.sendRequest(systemPrompt, userPrompt);
            case "groq" -> groqClient.sendRequest(systemPrompt, userPrompt);
            default -> {
                SteveMod.LOGGER.warn("Unknown AI provider '{}', using LongCat", provider);
                yield longCatClient.sendRequest(systemPrompt, userPrompt);
            }
        };

        if (response == null && !provider.equals("longcat")) {
            SteveMod.LOGGER.warn("{} failed, trying LongCat as fallback", provider);
            response = longCatClient.sendRequest(systemPrompt, userPrompt);
        }

        return response;
    }

    /**
     * Asynchronously plans tasks for Steve using the configured LLM provider.
     *
     * <p>This method returns immediately with a CompletableFuture, allowing the game thread
     * to continue without blocking. The actual LLM call is executed on a separate thread pool
     * with full resilience patterns (circuit breaker, retry, rate limiting, caching).</p>
     *
     * <p><b>Non-blocking:</b> Game thread is never blocked</p>
     * <p><b>Resilient:</b> Automatic retry, circuit breaker, fallback on failure</p>
     * <p><b>Cached:</b> Repeated prompts may hit cache (40-60% hit rate)</p>
     *
     * @param steve   The Steve entity making the request
     * @param command The user command to plan
     * @return CompletableFuture that completes with the parsed response, or null on failure
     */
    public CompletableFuture<ResponseParser.ParsedResponse> planTasksAsync(SteveEntity steve, String command) {
        try {
            String systemPrompt = PromptBuilder.buildSystemPrompt();
            WorldKnowledge worldKnowledge = new WorldKnowledge(steve);
            String userPrompt = PromptBuilder.buildUserPrompt(steve, command, worldKnowledge);

            String provider = SteveConfig.AI_PROVIDER.get().toLowerCase();
            SteveMod.LOGGER.info("[Async] Requesting AI plan for Steve '{}' using {}: {}",
                steve.getSteveName(), provider, command);


            // Build params map with provider-specific model
            String modelForProvider = switch (provider) {
                case "ollama" -> SteveConfig.OLLAMA_MODEL.get();
                case "longcat" -> SteveConfig.LONGCAT_MODEL.get();
                case "iflow" -> SteveConfig.IFLOW_MODEL.get();
                case "deepseek" -> SteveConfig.DEEPSEEK_MODEL.get();
                case "openai" -> SteveConfig.OPENAI_MODEL.get();
                case "gemini" -> SteveConfig.GEMINI_MODEL.get();
                case "groq" -> SteveConfig.GROQ_MODEL.get();
                default -> SteveConfig.LONGCAT_MODEL.get();
            };
            
            Map<String, Object> params = Map.of(
                "systemPrompt", systemPrompt,
                "model", modelForProvider,
                "maxTokens", SteveConfig.MAX_TOKENS.get(),
                "temperature", SteveConfig.TEMPERATURE.get()
            );

            // Select async client based on provider
            AsyncLLMClient client = getAsyncClient(provider);

            // Null check - if all async clients failed to initialize, fall back to sync API
            if (client == null) {
                SteveMod.LOGGER.warn("[Async] No async clients available, falling back to sync API");
                return CompletableFuture.supplyAsync(() -> {
                    String response = getAIResponse(provider, systemPrompt, userPrompt);
                    if (response == null || response.isEmpty()) {
                        SteveMod.LOGGER.error("[Sync Fallback] Empty response from API");
                        return null;
                    }
                    ResponseParser.ParsedResponse parsed = ResponseParser.parseAIResponse(response);
                    if (parsed != null) {
                        SteveMod.LOGGER.info("[Sync Fallback] Plan received: {} ({} tasks)",
                            parsed.getPlan(), parsed.getTasks().size());
                    }
                    return parsed;
                }).exceptionally(throwable -> {
                    SteveMod.LOGGER.error("[Sync Fallback] Error planning tasks: {}", throwable.getMessage());
                    return null;
                });
            }

            // Execute async request
            return client.sendAsync(userPrompt, params)
                .thenApply(response -> {
                    String content = response.getContent();
                    if (content == null || content.isEmpty()) {
                        SteveMod.LOGGER.error("[Async] Empty response from LLM");
                        return null;
                    }

                    ResponseParser.ParsedResponse parsed = ResponseParser.parseAIResponse(content);
                    if (parsed == null) {
                        SteveMod.LOGGER.error("[Async] Failed to parse AI response");
                        return null;
                    }

                    SteveMod.LOGGER.info("[Async] Plan received: {} ({} tasks, {}ms, {} tokens, cache: {})",
                        parsed.getPlan(),
                        parsed.getTasks().size(),
                        response.getLatencyMs(),
                        response.getTokensUsed(),
                        response.isFromCache());

                    return parsed;
                })
                .exceptionally(throwable -> {
                    SteveMod.LOGGER.error("[Async] Error planning tasks: {}", throwable.getMessage());
                    return null;
                });

        } catch (Exception e) {
            SteveMod.LOGGER.error("[Async] Error setting up task planning", e);
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Returns the appropriate async client based on provider config.
     * Returns null if the client is not available.
     *
     * @param provider Provider name ("openai", "groq", "gemini", "deepseek")
     * @return Resilient async client, or null if not available
     */
    private AsyncLLMClient getAsyncClient(String provider) {
        AsyncLLMClient client = switch (provider) {
            case "ollama" -> asyncOllamaClient;
            case "longcat" -> asyncLongCatClient;
            case "iflow" -> asyncIFlowClient;
            case "deepseek" -> asyncDeepSeekClient;
            case "openai" -> asyncOpenAIClient;
            case "gemini" -> asyncGeminiClient;
            case "groq" -> asyncGroqClient;
            default -> {
                SteveMod.LOGGER.warn("[Async] Unknown provider '{}', trying LongCat as fallback", provider);
                yield asyncLongCatClient;
            }
        };
        
        // Null check - if preferred client is null, try fallback options
        if (client == null) {
            SteveMod.LOGGER.warn("[Async] Client for provider '{}' is null, trying fallbacks", provider);
            // Try fallback order: longcat -> ollama -> iflow -> deepseek -> openai -> gemini -> groq
            if (asyncLongCatClient != null) return asyncLongCatClient;
            if (asyncOllamaClient != null) return asyncOllamaClient;
            if (asyncIFlowClient != null) return asyncIFlowClient;
            if (asyncDeepSeekClient != null) return asyncDeepSeekClient;
            if (asyncOpenAIClient != null) return asyncOpenAIClient;
            if (asyncGeminiClient != null) return asyncGeminiClient;
            if (asyncGroqClient != null) return asyncGroqClient;
        }
        return client;
    }

    /**
     * Returns the LLM cache for monitoring.
     *
     * @return LLM cache instance
     */
    public LLMCache getLLMCache() {
        return llmCache;
    }

    /**
     * Checks if the specified provider's async client is healthy.
     *
     * @param provider Provider name
     * @return true if healthy (circuit breaker not OPEN)
     */
    public boolean isProviderHealthy(String provider) {
        return getAsyncClient(provider).isHealthy();
    }

    public boolean validateTask(Task task) {
        String action = task.getAction();
        
        return switch (action) {
            case "pathfind" -> task.hasParameters("x", "y", "z");
            case "mine" -> task.hasParameters("block", "quantity");
            case "place" -> task.hasParameters("block", "x", "y", "z");
            case "craft" -> task.hasParameters("item", "quantity");
            case "attack" -> task.hasParameters("target");
            case "follow" -> task.hasParameters("player");
            case "gather" -> task.hasParameters("resource", "quantity");
            case "build" -> task.hasParameters("structure", "blocks", "dimensions");
            default -> {
                SteveMod.LOGGER.warn("Unknown action type: {}", action);
                yield false;
            }
        };
    }

    public List<Task> validateAndFilterTasks(List<Task> tasks) {
        return tasks.stream()
            .filter(this::validateTask)
            .toList();
    }
}

