package org.mavai.punit.api;

import java.util.Objects;
import java.util.OptionalLong;

/**
 * Explicit latency-threshold declaration attached to a spec's builder.
 *
 * <p>Each percentile is optional; an unset percentile is not asserted
 * and contributes nothing to the latency verdict. A fully-unset record
 * created via {@link #disabled()} short-circuits latency evaluation
 * entirely.
 *
 * <p>Computed latency percentiles are always produced by the engine
 * (see {@link LatencyResult}); this record decides only which ones the
 * spec wishes to <em>enforce</em>.
 *
 * <p>Non-positive thresholds are rejected at build time.
 *
 * @param p50Millis optional 50th-percentile ceiling, in millis
 * @param p90Millis optional 90th-percentile ceiling, in millis
 * @param p95Millis optional 95th-percentile ceiling, in millis
 * @param p99Millis optional 99th-percentile ceiling, in millis
 */
// mavai-ref: JVI-NBPN769 — do not remove (resolves in mavai-orchestrator)
public record LatencySpec(
        OptionalLong p50Millis,
        OptionalLong p90Millis,
        OptionalLong p95Millis,
        OptionalLong p99Millis) {

    private static final LatencySpec DISABLED = new LatencySpec(
            OptionalLong.empty(),
            OptionalLong.empty(),
            OptionalLong.empty(),
            OptionalLong.empty());

    public LatencySpec {
        Objects.requireNonNull(p50Millis, "p50Millis");
        Objects.requireNonNull(p90Millis, "p90Millis");
        Objects.requireNonNull(p95Millis, "p95Millis");
        Objects.requireNonNull(p99Millis, "p99Millis");
        requirePositive("p50Millis", p50Millis);
        requirePositive("p90Millis", p90Millis);
        requirePositive("p95Millis", p95Millis);
        requirePositive("p99Millis", p99Millis);
    }

    private static void requirePositive(String name, OptionalLong v) {
        if (v.isPresent() && v.getAsLong() <= 0) {
            throw new IllegalArgumentException(
                    name + " must be > 0, got " + v.getAsLong());
        }
    }

    /** The "no latency assertion" sentinel. */
    public static LatencySpec disabled() {
        return DISABLED;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** True if at least one percentile threshold is asserted. */
    public boolean hasAnyThreshold() {
        return p50Millis.isPresent() || p90Millis.isPresent()
                || p95Millis.isPresent() || p99Millis.isPresent();
    }

    /** True if no percentile threshold is asserted. Equivalent to {@code !hasAnyThreshold()}. */
    public boolean isDisabled() {
        return !hasAnyThreshold();
    }

    /** Fluent builder for {@link LatencySpec}. */
    public static final class Builder {
        private OptionalLong p50Millis = OptionalLong.empty();
        private OptionalLong p90Millis = OptionalLong.empty();
        private OptionalLong p95Millis = OptionalLong.empty();
        private OptionalLong p99Millis = OptionalLong.empty();

        private Builder() {}

        public Builder p50Millis(long millis) {
            this.p50Millis = OptionalLong.of(millis);
            return this;
        }

        public Builder p90Millis(long millis) {
            this.p90Millis = OptionalLong.of(millis);
            return this;
        }

        public Builder p95Millis(long millis) {
            this.p95Millis = OptionalLong.of(millis);
            return this;
        }

        public Builder p99Millis(long millis) {
            this.p99Millis = OptionalLong.of(millis);
            return this;
        }

        public LatencySpec build() {
            return new LatencySpec(p50Millis, p90Millis, p95Millis, p99Millis);
        }
    }
}
