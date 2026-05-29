package org.mavai.punit.internal.engine;

import java.util.concurrent.atomic.AtomicLong;

import org.mavai.punit.api.TokenTracker;

/**
 * Thread-safe in-memory {@link TokenTracker} used by the framework
 * during a sampling run. One instance is created per run and threaded
 * through every {@code apply} call.
 *
 * <p>Internal to the engine — authors interact only with the
 * {@link TokenTracker} interface.
 */
public final class InMemoryTokenTracker implements TokenTracker {

    private final AtomicLong total = new AtomicLong();

    @Override
    public void recordTokens(long n) {
        if (n < 0) {
            throw new IllegalArgumentException("token count must be non-negative, got " + n);
        }
        total.addAndGet(n);
    }

    @Override
    public long totalTokens() {
        return total.get();
    }
}
