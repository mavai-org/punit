package org.mavai.punit.api;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-run cost channel. The framework creates one {@code TokenTracker}
 * at the start of a run and threads it through every {@code apply}
 * call; the author's service-call body writes to it via
 * {@link #recordTokens(long)} when a sample's cost is known. The
 * framework derives per-sample tokens by diffing
 * {@link #totalTokens()} before and after each invocation.
 *
 * <h2>Token semantics</h2>
 *
 * <p>PUnit's notion of a "token" is deliberately open. LLM tokens are
 * the obvious case, but a token can be any unit of cost the author
 * chooses to track in their domain — dollars or cents for a paid
 * third-party API, request counts for a rate-limited service,
 * message bytes for a messaging-charge service, or upstream-service
 * quota units. The framework only requires that the same unit be
 * used consistently within a single run.
 *
 * <p>Authors that have no cost to record simply don't call
 * {@link #recordTokens(long)}; per-sample tokens are then reported as
 * zero. The interface has no notion of "absent" or "unknown" cost —
 * zero means zero, not missing.
 */
public interface TokenTracker {

    /**
     * Record the cost — in tokens — of work performed during the
     * current sample. The implementation accumulates the value into
     * {@link #totalTokens()}; callers may invoke this any number of
     * times within one sample (e.g. once per upstream request).
     *
     * @param n the number of tokens to add; must be non-negative
     * @throws IllegalArgumentException if {@code n} is negative
     */
    void recordTokens(long n);

    /**
     * @return the cumulative token count recorded across every
     *         {@code recordTokens} call so far in this run
     */
    long totalTokens();

    /**
     * Returns a fresh, thread-safe {@code TokenTracker} backed by an
     * atomic counter. Used by the framework to construct the
     * per-run tracker; equally usable from tests that need a simple
     * concrete instance without depending on engine internals.
     */
    static TokenTracker create() {
        return new TokenTracker() {
            private final AtomicLong total = new AtomicLong();

            @Override
            public void recordTokens(long n) {
                if (n < 0) {
                    throw new IllegalArgumentException(
                            "token count must be non-negative, got " + n);
                }
                total.addAndGet(n);
            }

            @Override
            public long totalTokens() {
                return total.get();
            }
        };
    }
}
