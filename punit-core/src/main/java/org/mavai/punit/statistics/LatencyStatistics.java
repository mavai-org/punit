package org.mavai.punit.statistics;

import java.util.Arrays;
import java.util.Objects;

/**
 * Precise double-valued computations for latency distributions.
 *
 * <p>This class provides the fundamental statistical operations for latency
 * analysis at full floating-point precision: nearest-rank percentile, mean,
 * and maximum. These operations underpin the framework's latency dimension
 * and are validated against mavai-R reference data in the conformance test
 * suite.
 *
 * <p>Percentiles use the nearest-rank (ceiling) method to match R's
 * {@code quantile(type = 1)} behaviour. This is distinct from interpolation-based
 * methods (such as Apache Commons Math's default Type R-7 estimator).
 *
 * @see LatencyDistribution
 */
public final class LatencyStatistics {

    private LatencyStatistics() {}

    /**
     * Computes the empirical percentile using the nearest-rank (ceiling) method.
     *
     * <p>For percentile {@code p} with {@code n} observations:
     * <pre>
     * index = ceil(p * n) - 1, clamped to [0, n-1]
     * result = sorted[index]
     * </pre>
     *
     * @param latencies the observed latency values (need not be sorted)
     * @param p the percentile level in (0, 1]
     * @return the percentile value
     */
    // javai-ref: JVI-CHSD1WP — do not remove (resolves in javai-orchestrator)
    public static double nearestRankPercentile(double[] latencies, double p) {
        Objects.requireNonNull(latencies, "latencies must not be null");
        if (latencies.length == 0) {
            throw new IllegalArgumentException("latencies must not be empty");
        }
        if (p <= 0.0 || p > 1.0) {
            throw new IllegalArgumentException("percentile must be in (0, 1]");
        }

        double[] sorted = latencies.clone();
        Arrays.sort(sorted);

        int index = (int) Math.ceil(p * sorted.length) - 1;
        index = Math.max(0, Math.min(index, sorted.length - 1));
        return sorted[index];
    }

    /**
     * Computes the arithmetic mean of latency observations.
     *
     * @param latencies the observed latency values (must not be empty)
     * @return the mean
     */
    public static double mean(double[] latencies) {
        Objects.requireNonNull(latencies, "latencies must not be null");
        if (latencies.length == 0) {
            throw new IllegalArgumentException("latencies must not be empty");
        }

        double sum = 0;
        for (double v : latencies) {
            sum += v;
        }
        return sum / latencies.length;
    }

    /**
     * Returns the maximum value from latency observations.
     *
     * @param latencies the observed latency values (must not be empty)
     * @return the maximum value
     */
    public static double max(double[] latencies) {
        Objects.requireNonNull(latencies, "latencies must not be null");
        if (latencies.length == 0) {
            throw new IllegalArgumentException("latencies must not be empty");
        }

        double max = latencies[0];
        for (int i = 1; i < latencies.length; i++) {
            if (latencies[i] > max) {
                max = latencies[i];
            }
        }
        return max;
    }
}
