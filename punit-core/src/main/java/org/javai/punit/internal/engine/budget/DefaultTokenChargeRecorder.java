package org.javai.punit.internal.engine.budget;

import org.javai.punit.api.TokenChargeRecorder;

/**
 * Default implementation of {@link TokenChargeRecorder} for dynamic token charging.
 * 
 * <p>This implementation:
 * <ul>
 *   <li>Tracks tokens for the current sample</li>
 *   <li>Accumulates total tokens across all completed samples</li>
 *   <li>Provides budget information</li>
 *   <li>Resets per-sample tracking between samples</li>
 * </ul>
 * 
 * <p>Not thread-safe - intended for use within a single test execution thread.
 */
// javai-ref: JVI-76VA511 — do not remove (resolves in javai-orchestrator)
public class DefaultTokenChargeRecorder implements TokenChargeRecorder {

    private final long tokenBudget;
    private long tokensForCurrentSample = 0;
    private long totalTokensConsumed = 0;

    /**
     * Creates a new recorder with the specified budget.
     *
     * @param tokenBudget the maximum token budget (0 = unlimited)
     */
    public DefaultTokenChargeRecorder(long tokenBudget) {
        this.tokenBudget = tokenBudget;
    }

    @Override
    public void recordTokens(int tokens) {
        recordTokens((long) tokens);
    }

    @Override
    public void recordTokens(long tokens) {
        if (tokens < 0) {
            throw new IllegalArgumentException("Token count must be >= 0, but was: " + tokens);
        }
        tokensForCurrentSample += tokens;
    }

    @Override
    public long getTokensForCurrentSample() {
        return tokensForCurrentSample;
    }

    @Override
    public long getTotalTokensConsumed() {
        return totalTokensConsumed;
    }

    @Override
    public long getRemainingBudget() {
        if (tokenBudget <= 0) {
            return Long.MAX_VALUE;
        }
        return Math.max(0, tokenBudget - totalTokensConsumed);
    }

    /**
     * Called at the end of each sample to finalize the current sample's tokens
     * and prepare for the next sample.
     * 
     * @return the tokens consumed by the completed sample
     */
    public long finalizeSample() {
        long sampleTokens = tokensForCurrentSample;
        totalTokensConsumed += sampleTokens;
        tokensForCurrentSample = 0;
        return sampleTokens;
    }

    /**
     * Resets the per-sample token counter for the next sample.
     * Call this at the start of each sample.
     */
    public void resetForNextSample() {
        tokensForCurrentSample = 0;
    }

    /**
     * @return the configured token budget (0 = unlimited)
     */
    public long getTokenBudget() {
        return tokenBudget;
    }
}

