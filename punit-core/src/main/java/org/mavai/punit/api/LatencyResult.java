package org.mavai.punit.api;

import java.time.Duration;
import java.util.Objects;

/**
 * Computed latency percentiles for a completed configuration's
 * samples.
 *
 * <p>Every run produces a {@code LatencyResult} regardless of whether
 * a {@link LatencySpec} was declared — computation is mandatory,
 * enforcement is optional. When no samples were observed, all
 * percentile durations are {@link Duration#ZERO} and
 * {@link #sampleCount()} is zero.
 *
 * @param p50 observed 50th-percentile latency
 * @param p90 observed 90th-percentile latency
 * @param p95 observed 95th-percentile latency
 * @param p99 observed 99th-percentile latency
 * @param sampleCount the number of samples the percentiles were
 *                    computed from
 */
public record LatencyResult(
        Duration p50,
        Duration p90,
        Duration p95,
        Duration p99,
        int sampleCount) {

    public LatencyResult {
        Objects.requireNonNull(p50, "p50");
        Objects.requireNonNull(p90, "p90");
        Objects.requireNonNull(p95, "p95");
        Objects.requireNonNull(p99, "p99");
        if (sampleCount < 0) {
            throw new IllegalArgumentException("sampleCount must be non-negative, got " + sampleCount);
        }
    }

    /** Convenience: an empty result for a zero-sample configuration. */
    public static LatencyResult empty() {
        return new LatencyResult(Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO, 0);
    }
}
