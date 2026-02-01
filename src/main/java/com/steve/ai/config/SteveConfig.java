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

    // Gemini
    public static final ForgeConfigSpec.ConfigValue<String> GEMINI_API_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> GEMINI_MODEL;

    // Groq
    public static final ForgeConfigSpec.ConfigValue<String> GROQ_API_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> GROQ_MODEL;
    
    // Behavior
    public static final ForgeConfigSpec.IntValue ACTION_TICK_DELAY;
    public static final ForgeConfigSpec.BooleanValue ENABLE_CHAT_RESPONSES;
    public static final ForgeConfigSpec.IntValue MAX_ACTIVE_STEVES;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("AI API Configuration").push("ai");
        
        AI_PROVIDER = builder
            .comment("AI provider to use: 'longcat', 'deepseek', 'openai', 'gemini', or 'groq' (FASTEST, FREE)")
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
            .comment("LongCat model to use (LongCat-Flash-Chat, LongCat-Flash-Thinking, LongCat-Flash-Thinking-2601)")
            .define("model", "LongCat-Flash-Thinking-2601");
        
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
            .comment("OpenAI model to use (gpt-5.2, gpt-5.2-codex, gpt-5-mini-2025-08-07, gpt-5-nano-2025-08-07)")
            .define("model", "gpt-5-mini-2025-08-07");
        
        builder.pop();

        builder.comment("Google Gemini API Configuration").push("gemini");
        
        GEMINI_API_KEY = builder
            .comment("Your Gemini API key (get from: https://aistudio.google.com/apikey)")
            .define("apiKey", "");
        
        GEMINI_MODEL = builder
            .comment("Gemini model to use (gemini-3-pro-preview, gemini-3-flash-preview, gemini-flash-lite-latest)")
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
