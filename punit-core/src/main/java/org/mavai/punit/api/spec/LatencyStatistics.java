package org.mavai.punit.api.spec;

import java.util.Arrays;
import java.util.Objects;

import org.mavai.punit.api.LatencyResult;

/**
 * Latency baseline, read by empirical variants of
 * {@code PercentileLatency}.
 *
 * <p>Carries the full sorted vector of passing-sample latencies in
 * milliseconds — required by Statistical Companion §12.4.2's exact
 * binomial order-statistic upper confidence bound. The percentile
 * point estimates remain for reporting.
 *
 * @param percentiles      the baseline's observed p50 / p90 / p95 / p99 — point estimates
 * @param sortedLatenciesMs sorted (ascending) passing-sample latencies in milliseconds;
 *                          length matches {@code sampleCount}
 * @param sampleCount      the baseline's total invocation count
 */
public record LatencyStatistics(
        LatencyResult percentiles,
        long[] sortedLatenciesMs,
        @Override int sampleCount) implements BaselineStatistics {

    public LatencyStatistics {
        Objects.requireNonNull(percentiles, "percentiles");
        Objects.requireNonNull(sortedLatenciesMs, "sortedLatenciesMs");
        if (sampleCount < 0) {
            throw new IllegalArgumentException(
                    "sampleCount must be non-negative, got " + sampleCount);
        }
        if (sortedLatenciesMs.length != sampleCount) {
            throw new IllegalArgumentException(
                    "sortedLatenciesMs length (" + sortedLatenciesMs.length
                            + ") must equal sampleCount (" + sampleCount + ")");
        }
        // Defensive copy + validate sorted ascending.
        sortedLatenciesMs = sortedLatenciesMs.clone();
        for (int i = 1; i < sortedLatenciesMs.length; i++) {
            if (sortedLatenciesMs[i] < sortedLatenciesMs[i - 1]) {
                throw new IllegalArgumentException(
                        "sortedLatenciesMs must be sorted ascending");
            }
        }
    }

    /** Convenience: construct from raw (unsorted) latencies. */
    public static LatencyStatistics of(
            LatencyResult percentiles, long[] latenciesMs, int sampleCount) {
        long[] sorted = latenciesMs.clone();
        Arrays.sort(sorted);
        return new LatencyStatistics(percentiles, sorted, sampleCount);
    }

    /**
     * Test convenience: fabricate a sorted vector consistent with the
     * supplied percentile point estimates. Each rank-bucket is filled
     * with the next-higher percentile's value so that
     * {@code nearestRankPercentile(sorted, p)} reproduces the supplied
     * {@code Q(p)}. Production code goes through {@link #of(LatencyResult, long[], int)};
     * this entry point is for unit tests that previously constructed
     * {@code new LatencyStatistics(percentiles, n)} without raw data.
     */
    public static LatencyStatistics fromPercentiles(
            LatencyResult percentiles, int sampleCount) {
        long[] sorted = new long[sampleCount];
        long p50 = percentiles.p50().toMillis();
        long p90 = percentiles.p90().toMillis();
        long p95 = percentiles.p95().toMillis();
        long p99 = percentiles.p99().toMillis();
        int r50 = Math.max(1, (int) Math.ceil(0.50 * sampleCount));
        int r90 = Math.max(r50, (int) Math.ceil(0.90 * sampleCount));
        int r95 = Math.max(r90, (int) Math.ceil(0.95 * sampleCount));
        int r99 = Math.max(r95, (int) Math.ceil(0.99 * sampleCount));
        for (int i = 0; i < sampleCount; i++) {
            int rank = i + 1;
            long v;
            if (rank <= r50) v = p50;
            else if (rank <= r90) v = p90;
            else if (rank <= r95) v = p95;
            else if (rank <= r99) v = p99;
            else v = p99;
            sorted[i] = v;
        }
        // Force monotonic ascending: any percentile may legitimately
        // be equal to a lower one (degenerate distributions), but
        // never below it. The constructor's strict-ascending check
        // is satisfied because we built bucket-wise.
        return new LatencyStatistics(percentiles, sorted, sampleCount);
    }

    /** Compact accessor returning a defensive copy. */
    @Override
    public long[] sortedLatenciesMs() {
        return sortedLatenciesMs.clone();
    }
}
