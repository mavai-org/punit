package org.mavai.punit.lm;

import java.util.Map;
import java.util.Set;

/**
 * One language-model service configuration: its complete covariate
 * values, validated and resolved from a definition's
 * {@code configuration:} record. {@code null} means undeclared;
 * {@code maxTokens} is always resolved (the format-normative default
 * when unstated) because a silent, provider-chosen ceiling would make
 * the same file mean different populations.
 */
public record LanguageModelParameters(
        String systemPrompt,
        String provider,
        Set<String> capabilities,
        String model,
        Double temperature,
        Double topP,
        String thinking,
        Boolean promptCaching,
        Map<String, Object> responseSchema,
        int maxTokens) {

    /** The output-token ceiling when {@code max-tokens:} is unstated — format-normative. */
    public static final int DEFAULT_MAX_TOKENS = 4096;

    /** The largest ceiling the non-streaming adapters carry without risking a timeout. */
    public static final int MAX_TOKENS_CEILING = 16000;

    /** Below this, adaptive thinking consumes the whole budget and the answer truncates. */
    public static final int THINKING_MIN_MAX_TOKENS = 1024;

    public boolean adaptiveThinking() {
        return "adaptive".equals(thinking);
    }

    LanguageModelParameters withoutResponseSchema() {
        return new LanguageModelParameters(systemPrompt, provider, capabilities, model,
                temperature, topP, thinking, promptCaching, null, maxTokens);
    }

    LanguageModelParameters withoutPromptCaching() {
        return new LanguageModelParameters(systemPrompt, provider, capabilities, model,
                temperature, topP, thinking, null, responseSchema, maxTokens);
    }

    LanguageModelParameters withoutThinking() {
        return new LanguageModelParameters(systemPrompt, provider, capabilities, model,
                temperature, topP, null, promptCaching, responseSchema, maxTokens);
    }
}
