package org.mavai.punit.internal.engine.baseline;

import java.util.Arrays;
import java.util.Objects;

import org.mavai.punit.api.LatencyResult;

/**
 * Bundles a passing-only {@link LatencyResult} with the
 * population-indicator counts ({@code contributingSamples},
 * {@code totalSamples}) and the full sorted vector of passing-sample
 * latencies in milliseconds. Carried on a {@link BaselineRecord} so the
 * baseline writer can emit the normative {@code latency:} YAML
 * block from one self-contained value.
 *
 * <p>The sorted vector is required by Statistical Companion §12.4.2's
 * binomial order-statistic upper confidence bound on the baseline
 * quantile, which indexes order statistics directly.
 *
 * <p>{@link #empty()} represents the "no passing samples" case;
 * the writer omits the block entirely when this is the value.
 */
public record LatencyIndicator(
        LatencyResult passingPercentiles,
        long[] sortedPassingLatenciesMs,
        int contributingSamples,
        int totalSamples) {

    public LatencyIndicator {
        Objects.requireNonNull(passingPercentiles, "passingPercentiles");
        Objects.requireNonNull(sortedPassingLatenciesMs, "sortedPassingLatenciesMs");
        if (contributingSamples < 0) {
            throw new IllegalArgumentException(
                    "contributingSamples must be non-negative, got " + contributingSamples);
        }
        if (totalSamples < contributingSamples) {
            throw new IllegalArgumentException(
                    "totalSamples (" + totalSamples + ") must be >= contributingSamples ("
                            + contributingSamples + ")");
        }
        if (sortedPassingLatenciesMs.length != contributingSamples) {
            throw new IllegalArgumentException(
                    "sortedPassingLatenciesMs length (" + sortedPassingLatenciesMs.length
                            + ") must equal contributingSamples (" + contributingSamples + ")");
        }
        sortedPassingLatenciesMs = sortedPassingLatenciesMs.clone();
        for (int i = 1; i < sortedPassingLatenciesMs.length; i++) {
            if (sortedPassingLatenciesMs[i] < sortedPassingLatenciesMs[i - 1]) {
                throw new IllegalArgumentException(
                        "sortedPassingLatenciesMs must be sorted ascending");
            }
        }
    }

    /** The empty indicator — no passing samples, no block to emit. */
    public static LatencyIndicator empty() {
        return new LatencyIndicator(LatencyResult.empty(), new long[0], 0, 0);
    }

    /** Whether the indicator carries percentile data worth emitting. */
    public boolean hasData() {
        return contributingSamples > 0;
    }

    /** Defensive copy of the sorted vector. */
    @Override
    public long[] sortedPassingLatenciesMs() {
        return sortedPassingLatenciesMs.clone();
    }
}
