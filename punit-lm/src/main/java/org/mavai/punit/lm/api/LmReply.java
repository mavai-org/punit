package org.mavai.punit.lm.api;

import java.util.Objects;
import java.util.Optional;

/**
 * One language-model response: the text, and the token usage the
 * vendor reported beside it. A language model responds with more than
 * a string — token counts feed cost accounting and reporting — so the
 * programmatic surface carries them first-class. Usage is
 * absent-tolerant: a vendor (or gateway) that omits it yields a reply
 * whose {@link #usage()} is empty, never a failure.
 *
 * @param text the response text, exactly as the adapter extracted it
 * @param usage the reported token usage, when the vendor stated it
 */
public record LmReply(String text, Optional<TokenUsage> usage) {

    public LmReply {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(usage, "usage");
    }

    /** A reply with no reported usage. */
    public static LmReply of(String text) {
        return new LmReply(text, Optional.empty());
    }

    /** A reply with reported input/output token counts. */
    public static LmReply of(String text, long inputTokens, long outputTokens) {
        return new LmReply(text, Optional.of(new TokenUsage(inputTokens, outputTokens)));
    }

    /**
     * The reported token counts for one exchange.
     *
     * @param inputTokens tokens the request consumed (prompt side)
     * @param outputTokens tokens the response produced
     */
    public record TokenUsage(long inputTokens, long outputTokens) {

        /** Input plus output — the exchange's total. */
        public long totalTokens() {
            return inputTokens + outputTokens;
        }
    }
}
