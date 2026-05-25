package org.javai.punit.api;

import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * A typed declaration of how fast, and how concurrently, the engine is
 * permitted to invoke a service contract.
 *
 * <p>Pacing is a property of the service under test, not of a specific
 * experiment or probabilistic test exercising it. Every test of the
 * same service should respect the same rate limit, so authors declare
 * pacing on their {@link ServiceContract} implementation via
 * {@link ServiceContract#pacing()} rather than on a spec builder.
 *
 * <h2>Composition rule — most restrictive wins</h2>
 *
 * <p>Multiple knobs on the same record imply an effective minimum
 * inter-sample delay. {@link #effectiveMinDelayMillis()} returns the
 * maximum of the delays implied by each configured knob:
 *
 * <ul>
 *   <li>{@code maxRequestsPerSecond} → floor of {@code 1000 / rate}</li>
 *   <li>{@code maxRequestsPerMinute} → floor of {@code 60_000 / rate}</li>
 *   <li>{@code minMillisPerSample} → its own value</li>
 * </ul>
 *
 * <p>{@code maxConcurrent} is an orthogonal permit count rather than a
 * delay; the shipped serial executor treats it as informational
 * (concurrency {@code 1} is always {@code ≤} the cap).
 *
 * <p>Non-positive values on any knob are rejected at build time.
 *
 * @param maxRequestsPerSecond optional ceiling on the rate of sample
 *                             starts per wall-clock second
 * @param maxRequestsPerMinute optional ceiling on the rate of sample
 *                             starts per wall-clock minute
 * @param minMillisPerSample optional floor on the inter-sample delay,
 *                           in milliseconds
 * @param maxConcurrent optional cap on the number of in-flight samples
 */
// javai-ref: JVI-PC8GT83 — do not remove (resolves in javai-orchestrator)
// javai-ref: JVI-P1GG550 — do not remove (resolves in javai-orchestrator)
// javai-ref: JVI-M1TXHHQ — do not remove (resolves in javai-orchestrator)
// javai-ref: JVI-FNJKJT5 — do not remove (resolves in javai-orchestrator)
public record Pacing(
        OptionalDouble maxRequestsPerSecond,
        OptionalDouble maxRequestsPerMinute,
        OptionalLong minMillisPerSample,
        OptionalInt maxConcurrent) {

    private static final Pacing UNLIMITED = new Pacing(
            OptionalDouble.empty(),
            OptionalDouble.empty(),
            OptionalLong.empty(),
            OptionalInt.empty());

    public Pacing {
        Objects.requireNonNull(maxRequestsPerSecond, "maxRequestsPerSecond");
        Objects.requireNonNull(maxRequestsPerMinute, "maxRequestsPerMinute");
        Objects.requireNonNull(minMillisPerSample, "minMillisPerSample");
        Objects.requireNonNull(maxConcurrent, "maxConcurrent");
        if (maxRequestsPerSecond.isPresent() && maxRequestsPerSecond.getAsDouble() <= 0) {
            throw new IllegalArgumentException(
                    "maxRequestsPerSecond must be > 0, got " + maxRequestsPerSecond.getAsDouble());
        }
        if (maxRequestsPerMinute.isPresent() && maxRequestsPerMinute.getAsDouble() <= 0) {
            throw new IllegalArgumentException(
                    "maxRequestsPerMinute must be > 0, got " + maxRequestsPerMinute.getAsDouble());
        }
        if (minMillisPerSample.isPresent() && minMillisPerSample.getAsLong() <= 0) {
            throw new IllegalArgumentException(
                    "minMillisPerSample must be > 0, got " + minMillisPerSample.getAsLong());
        }
        if (maxConcurrent.isPresent() && maxConcurrent.getAsInt() <= 0) {
            throw new IllegalArgumentException(
                    "maxConcurrent must be > 0, got " + maxConcurrent.getAsInt());
        }
    }

    /** The "no pacing" sentinel — every knob empty. */
    public static Pacing unlimited() {
        return UNLIMITED;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * The effective minimum delay between sample starts, in
     * milliseconds, computed as the maximum of the delays implied by
     * each configured rate knob and the explicit
     * {@code minMillisPerSample} floor.
     *
     * @return the effective inter-sample delay in millis; {@code 0}
     *         when no rate knob is configured
     */
    public long effectiveMinDelayMillis() {
        long delay = 0L;
        if (maxRequestsPerSecond.isPresent()) {
            delay = Math.max(delay, (long) Math.floor(1000.0 / maxRequestsPerSecond.getAsDouble()));
        }
        if (maxRequestsPerMinute.isPresent()) {
            delay = Math.max(delay, (long) Math.floor(60_000.0 / maxRequestsPerMinute.getAsDouble()));
        }
        if (minMillisPerSample.isPresent()) {
            delay = Math.max(delay, minMillisPerSample.getAsLong());
        }
        return delay;
    }

    /** Fluent builder for {@link Pacing}. */
    public static final class Builder {
        private OptionalDouble maxRequestsPerSecond = OptionalDouble.empty();
        private OptionalDouble maxRequestsPerMinute = OptionalDouble.empty();
        private OptionalLong minMillisPerSample = OptionalLong.empty();
        private OptionalInt maxConcurrent = OptionalInt.empty();

        private Builder() {}

        public Builder maxRequestsPerSecond(double rate) {
            this.maxRequestsPerSecond = OptionalDouble.of(rate);
            return this;
        }

        public Builder maxRequestsPerMinute(double rate) {
            this.maxRequestsPerMinute = OptionalDouble.of(rate);
            return this;
        }

        public Builder minMillisPerSample(long millis) {
            this.minMillisPerSample = OptionalLong.of(millis);
            return this;
        }

        public Builder maxConcurrent(int permits) {
            this.maxConcurrent = OptionalInt.of(permits);
            return this;
        }

        public Pacing build() {
            return new Pacing(maxRequestsPerSecond, maxRequestsPerMinute,
                    minMillisPerSample, maxConcurrent);
        }
    }
}
