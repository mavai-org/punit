package org.mavai.punit.api;

import java.time.Duration;
import java.util.OptionalLong;

/**
 * The percentiles a latency criterion can assert against. Each value
 * knows how to project a {@link LatencySpec}'s per-percentile ceiling
 * and a {@link LatencyResult}'s observed percentile.
 *
 * <p>Lives at the {@code api} root because both the contract-side
 * authoring surface ({@code api.criterion.LatencyDecl}) and the
 * spec-side evaluator ({@code api.spec.PercentileLatency}) reference
 * it; keeping it here avoids a dependency cycle between the two
 * packages.
 */
public enum PercentileKey {

    P50 {
        @Override public OptionalLong ceilingMillis(LatencySpec spec) { return spec.p50Millis(); }
        @Override public Duration observed(LatencyResult result) { return result.p50(); }
        @Override public String detailKey() { return "p50"; }
        @Override public double value() { return 0.50; }
    },
    P90 {
        @Override public OptionalLong ceilingMillis(LatencySpec spec) { return spec.p90Millis(); }
        @Override public Duration observed(LatencyResult result) { return result.p90(); }
        @Override public String detailKey() { return "p90"; }
        @Override public double value() { return 0.90; }
    },
    P95 {
        @Override public OptionalLong ceilingMillis(LatencySpec spec) { return spec.p95Millis(); }
        @Override public Duration observed(LatencyResult result) { return result.p95(); }
        @Override public String detailKey() { return "p95"; }
        @Override public double value() { return 0.95; }
    },
    P99 {
        @Override public OptionalLong ceilingMillis(LatencySpec spec) { return spec.p99Millis(); }
        @Override public Duration observed(LatencyResult result) { return result.p99(); }
        @Override public String detailKey() { return "p99"; }
        @Override public double value() { return 0.99; }
    };

    /** The asserted ceiling from a LatencySpec, when that percentile is asserted. */
    public abstract OptionalLong ceilingMillis(LatencySpec spec);

    /** The observed duration from a LatencyResult at this percentile. */
    public abstract Duration observed(LatencyResult result);

    /** The stable short-form key used in evaluation-detail map keys. */
    public abstract String detailKey();

    /** The percentile as a number in (0, 1) — e.g. 0.95 for P95. */
    public abstract double value();
}
