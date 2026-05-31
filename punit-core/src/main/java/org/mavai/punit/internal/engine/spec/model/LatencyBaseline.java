package org.mavai.punit.internal.engine.spec.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * Latency baseline data recorded from a measure experiment.
 *
 * <p>This record carries the full sorted vector of successful-response latencies
 * (in milliseconds) alongside the mean and maximum for reporting. The sorted
 * vector is the canonical storage format required by the exact binomial
 * order-statistic threshold derivation (mavai-R STATISTICAL-COMPANION v1.1
 * &sect;12.4): thresholds are the {@code k}-th order statistic of this vector
 * for a rank {@code k} computed from the binomial upper confidence bound on
 * the baseline percentile.
 *
 * <p>Stored in the spec YAML under the {@code statistics.latency} section.
 *
 * @param sortedLatenciesMs successful-response latencies in ms, ascending
 * @param meanMs mean latency (reporting only)
 * @param maxMs maximum observed latency (reporting only)
 */
public record LatencyBaseline(
        long[] sortedLatenciesMs,
        long meanMs,
        long maxMs
) {
    /**
     * Validates the latency baseline and defensively copies the sorted vector.
     */
    public LatencyBaseline {
        Objects.requireNonNull(sortedLatenciesMs, "sortedLatenciesMs must not be null");
        if (meanMs < 0) {
            throw new IllegalArgumentException("meanMs must be non-negative");
        }
        if (maxMs < 0) {
            throw new IllegalArgumentException("maxMs must be non-negative");
        }
        sortedLatenciesMs = sortedLatenciesMs.clone();
        for (int i = 1; i < sortedLatenciesMs.length; i++) {
            if (sortedLatenciesMs[i] < sortedLatenciesMs[i - 1]) {
                throw new IllegalArgumentException("sortedLatenciesMs must be sorted ascending");
            }
        }
    }

    /**
     * Returns the number of baseline samples.
     *
     * @return the sample count
     */
    public int sampleCount() {
        return sortedLatenciesMs.length;
    }

    /**
     * Returns the sorted baseline vector as a {@code double[]} for use with
     * {@link org.mavai.punit.statistics.LatencyThresholdDeriver#derive(double[], double, double)}.
     *
     * @return a fresh array of the sorted latencies as doubles
     */
    public double[] sortedLatenciesAsDoubles() {
        double[] out = new double[sortedLatenciesMs.length];
        for (int i = 0; i < sortedLatenciesMs.length; i++) {
            out[i] = sortedLatenciesMs[i];
        }
        return out;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LatencyBaseline other)) return false;
        return meanMs == other.meanMs
                && maxMs == other.maxMs
                && Arrays.equals(sortedLatenciesMs, other.sortedLatenciesMs);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(meanMs, maxMs) + Arrays.hashCode(sortedLatenciesMs);
    }

    @Override
    public String toString() {
        return "LatencyBaseline{n=" + sortedLatenciesMs.length
                + ", meanMs=" + meanMs
                + ", maxMs=" + maxMs + "}";
    }
}
