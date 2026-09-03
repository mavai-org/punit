package org.mavai.punit.lm;

import java.util.Map;
import java.util.Set;

/**
 * One language-model service configuration: its complete covariate
 * values, validated and resolved from a definition's
 * {@code configuration:} record. {@code null} means undeclared;
 * {@code maxTokens} and {@code deadlineMs} are always resolved (their
 * defaults when unstated) because a silent, reader-chosen value would
 * make the same file mean different populations.
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
        int maxTokens,
        int deadlineMs) {

    /** The output-token ceiling when {@code max-tokens:} is unstated — format-normative. */
    public static final int DEFAULT_MAX_TOKENS = 4096;

    /**
     * How long this reader waits for one response before recording a
     * failed delivery, when {@code deadline-ms:} is unstated: ten
     * minutes.
     *
     * <p>Unlike {@link #DEFAULT_MAX_TOKENS} this value is not
     * format-normative — the services format requires every reader to
     * state a finite default and forbids an unbounded wait, but leaves
     * the number to each reader's judgement about its own transport.
     * punit's judgement is to match the family's Python reader rather
     * than to exercise that latitude: the two invoke the same services,
     * often from the same authored file, and since the deadline is part
     * of the service's identity a divergent default would let one file
     * name two populations depending on which reader opened it.
     *
     * <p>Ten minutes is generous by design. The largest configurations
     * in family use are slowest to first byte, and a deadline that
     * manufactures failed deliveries is worse than the hang it replaced
     * — it converts a visible stall into an invisible bias.
     */
    public static final int DEFAULT_DEADLINE_MS = 600_000;

    /** The largest ceiling the non-streaming adapters carry without risking a timeout. */
    public static final int MAX_TOKENS_CEILING = 16000;

    /** Below this, adaptive thinking consumes the whole budget and the answer truncates. */
    public static final int THINKING_MIN_MAX_TOKENS = 1024;

    public boolean adaptiveThinking() {
        return "adaptive".equals(thinking);
    }

    LanguageModelParameters withoutResponseSchema() {
        return new LanguageModelParameters(systemPrompt, provider, capabilities, model,
                temperature, topP, thinking, promptCaching, null, maxTokens, deadlineMs);
    }

    LanguageModelParameters withoutPromptCaching() {
        return new LanguageModelParameters(systemPrompt, provider, capabilities, model,
                temperature, topP, thinking, null, responseSchema, maxTokens, deadlineMs);
    }

    LanguageModelParameters withoutThinking() {
        return new LanguageModelParameters(systemPrompt, provider, capabilities, model,
                temperature, topP, null, promptCaching, responseSchema, maxTokens, deadlineMs);
    }
}
