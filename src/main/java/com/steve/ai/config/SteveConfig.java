package com.steve.ai.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class SteveConfig {
    public static final ForgeConfigSpec SPEC;
    
    // AI Provider selection
    public static final ForgeConfigSpec.ConfigValue<String> AI_PROVIDER;
    public static final ForgeConfigSpec.IntValue MAX_TOKENS;
    public static final ForgeConfigSpec.DoubleValue TEMPERATURE;
    
    // LongCat
    public static final ForgeConfigSpec.ConfigValue<String> LONGCAT_API_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> LONGCAT_MODEL;

    // DeepSeek
    public static final ForgeConfigSpec.ConfigValue<String> DEEPSEEK_API_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> DEEPSEEK_MODEL;

    // OpenAI
    public static final ForgeConfigSpec.ConfigValue<String> OPENAI_API_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> OPENAI_MODEL;

    // Claude (Anthropic)
    public static final ForgeConfigSpec.ConfigValue<String> CLAUDE_API_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> CLAUDE_MODEL;

    // Gemini
    public static final ForgeConfigSpec.ConfigValue<String> GEMINI_API_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> GEMINI_MODEL;

    // Groq
    public static final ForgeConfigSpec.ConfigValue<String> GROQ_API_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> GROQ_MODEL;

    // Ollama (Local)
    public static final ForgeConfigSpec.ConfigValue<String> OLLAMA_HOST;
    public static final ForgeConfigSpec.ConfigValue<String> OLLAMA_MODEL;

    // iFlow (Deprecated: service will be shut down on April 17, 2026 Beijing Time)
    public static final ForgeConfigSpec.ConfigValue<String> IFLOW_API_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> IFLOW_MODEL;
    
    // Behavior
    public static final ForgeConfigSpec.IntValue ACTION_TICK_DELAY;
    public static final ForgeConfigSpec.BooleanValue ENABLE_CHAT_RESPONSES;
    public static final ForgeConfigSpec.IntValue MAX_ACTIVE_STEVES;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("AI API Configuration").push("ai");
        
        AI_PROVIDER = builder
            .comment("AI provider to use: 'ollama', 'longcat', 'deepseek', 'openai', 'claude', 'gemini', 'groq' (FASTEST, FREE), or 'iflow' (shutting down April 17, 2026)")
            .define("provider", "longcat");
        
        MAX_TOKENS = builder
            .comment("Maximum tokens per API request (applies to all LLM providers)")
            .defineInRange("maxTokens", 8000, 100, 65536);
        
        TEMPERATURE = builder
            .comment("Temperature for AI responses (0.0-2.0, lower is more deterministic)")
            .defineInRange("temperature", 0.7, 0.0, 2.0);
        
        builder.pop();

        builder.comment("LongCat API Configuration").push("longcat");
        
        LONGCAT_API_KEY = builder
            .comment("Your LongCat API key (get from: https://longcat.chat/platform/api_keys)")
            .define("apiKey", "");
        
        LONGCAT_MODEL = builder
            .comment("LongCat model to use (LongCat-Flash-Chat, LongCat-Flash-Thinking, LongCat-Flash-Omni-2603, LongCat-Flash-Lite)")
            .define("model", "LongCat-Flash-Thinking");
        
        builder.pop();

        builder.comment("DeepSeek API Configuration").push("deepseek");
        
        DEEPSEEK_API_KEY = builder
            .comment("Your DeepSeek API key (get from: https://platform.deepseek.com/api_keys)")
            .define("apiKey", "");
        
        DEEPSEEK_MODEL = builder
            .comment("DeepSeek model to use (deepseek-chat, deepseek-reasoner)")
            .define("model", "deepseek-chat");
        
        builder.pop();

        builder.comment("OpenAI API Configuration").push("openai");
        
        OPENAI_API_KEY = builder
            .comment("Your OpenAI API key (get from: https://platform.openai.com/api-keys)")
            .define("apiKey", "");
        
        OPENAI_MODEL = builder
            .comment("OpenAI model to use (gpt-5.4, gpt-5.4-mini, gpt-5.4-nano)")
            .define("model", "gpt-5.4-mini");
        
        builder.pop();

        builder.comment("Anthropic Claude API Configuration").push("claude");
        
        CLAUDE_API_KEY = builder
            .comment("Your Claude API key (get from: https://console.anthropic.com/settings/keys)")
            .define("apiKey", "");
        
        CLAUDE_MODEL = builder
            .comment("Claude model to use (claude-opus-4-6, claude-sonnet-4-6, claude-haiku-4-5)")
            .define("model", "claude-opus-4-6");
        
        builder.pop();

        builder.comment("Google Gemini API Configuration").push("gemini");
        
        GEMINI_API_KEY = builder
            .comment("Your Gemini API key (get from: https://aistudio.google.com/apikey)")
            .define("apiKey", "");
        
        GEMINI_MODEL = builder
            .comment("Gemini model to use (gemini-3.1-pro-preview, gemini-3-flash-preview, gemini-3.1-flash-lite-preview)")
            .define("model", "gemini-3-flash-preview");
        
        builder.pop();

        builder.comment("Groq API Configuration (FASTEST, FREE tier available)").push("groq");
        
        GROQ_API_KEY = builder
            .comment("Your Groq API key (get from: https://console.groq.com/keys)")
            .define("apiKey", "");
        
        GROQ_MODEL = builder
            .comment("Groq model to use (llama-3.1-8b-instant, llama-3.1-70b-versatile, mixtral-8x7b-32768)")
            .define("model", "llama-3.1-8b-instant");
        
        builder.pop();

        builder.comment("Ollama Local LLM Configuration (runs locally, no API key needed)").push("ollama");
        
        OLLAMA_HOST = builder
            .comment("Ollama server address (default: http://localhost:11434)")
            .define("host", "http://localhost:11434");
        
        OLLAMA_MODEL = builder
            .comment("Ollama model to use (qwen3.5:4b, qwen3.5:latest, glm-4.7-flash, qwen3.5:35b)")
            .define("model", "qwen3.5:4b");
        
        builder.pop();

        // WARNING: iFlow API will be officially shut down on April 17, 2026 (Beijing Time)
        builder.comment("iFlow API Configuration (DEPRECATED: service shutting down April 17, 2026 Beijing Time)").push("iflow");
        
        IFLOW_API_KEY = builder
            .comment("Your iFlow API key (get from: https://platform.iflow.cn/profile?tab=apiKey)")
            .define("apiKey", "");
        
        IFLOW_MODEL = builder
            .comment("iFlow model to use (glm-4.6, kimi-k2, qwen3-coder-plus, qwen3-max, qwen3-vl-plus, deepseek-v3.2, tstars2.0, iflow-rome-30ba3b)")
            .define("model", "glm-4.6");
        
        builder.pop();

        builder.comment("Steve Behavior Configuration").push("behavior");
        
        ACTION_TICK_DELAY = builder
            .comment("Ticks between action checks (20 ticks = 1 second)")
            .defineInRange("actionTickDelay", 20, 1, 100);
        
        ENABLE_CHAT_RESPONSES = builder
            .comment("Allow Steves to respond in chat")
            .define("enableChatResponses", true);
        
        MAX_ACTIVE_STEVES = builder
            .comment("Maximum number of Steves that can be active simultaneously")
            .defineInRange("maxActiveSteves", 10, 1, 50);
        
        builder.pop();

        SPEC = builder.build();
    }
}
