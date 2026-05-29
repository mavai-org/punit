package org.mavai.punit.statistics;

import java.util.Arrays;
import java.util.Objects;

import org.apache.commons.statistics.distribution.BinomialDistribution;

/**
 * Derives latency thresholds from baseline data.
 *
 * <p>The canonical derivation (Statistical Companion &sect;12.4.2) is the
 * <b>exact binomial order-statistic upper confidence bound</b> on the baseline
 * percentile {@code Q(p)}:
 *
 * <pre>
 * tau = t_{(k)}
 * where k_raw = qbinom(1 - alpha, n_s, p) + 1,
 *       k     = max(ceil(p * n_s), k_raw)   when k_raw &lt;= n_s
 * </pre>
 *
 * <p>{@code t_{(k)}} is the {@code k}-th order statistic (1-indexed) of the sorted
 * baseline successful-response latencies. The threshold is therefore an
 * observed baseline latency value &mdash; integer-ms by construction.
 *
 * <p><b>Saturation.</b> When {@code k_raw > n_s} the construction's existence
 * condition (companion &sect;12.5.2.1) is not met — no finite-sample
 * distribution-free upper confidence bound on {@code Q(p)} is available at
 * the configured confidence. The deriver does <b>not</b> silently clamp
 * {@code k_raw} to {@code n_s}; instead it returns
 * {@link Threshold#saturated()} {@code true} carrying the advisory
 * value {@code t_{(n_s)}} (which is <b>not</b> an exact bound). Callers
 * decide what to do — under {@code VERIFICATION} the methodology requires
 * INCONCLUSIVE; under {@code SMOKE} the advisory may be reported.
 *
 * <p>This construction is non-parametric, distribution-free, and exact for any
 * continuous underlying latency distribution.
 */
// javai-ref: JVI-QVNG2SX — do not remove (resolves in javai-orchestrator)
public final class LatencyThresholdDeriver {

    private LatencyThresholdDeriver() {}

    /**
     * Derives a latency threshold using the exact binomial order-statistic
     * upper confidence bound.
     *
     * @param baselineLatencies observed successful-response latencies from the
     *                          baseline experiment (need not be sorted; must be
     *                          non-empty)
     * @param p                 percentile level in (0, 1) (e.g. 0.95)
     * @param confidence        one-sided confidence level in (0, 1) (e.g. 0.95)
     * @return the derived threshold, with rank, threshold value, point-estimate
     *         percentile, and sample count
     * @throws IllegalArgumentException if inputs are invalid
     */
    public static Threshold derive(double[] baselineLatencies, double p, double confidence) {
        Objects.requireNonNull(baselineLatencies, "baselineLatencies must not be null");
        if (baselineLatencies.length == 0) {
            throw new IllegalArgumentException("baselineLatencies must not be empty");
        }
        if (p <= 0.0 || p >= 1.0) {
            throw new IllegalArgumentException("p must be in (0, 1)");
        }
        if (confidence <= 0.0 || confidence >= 1.0) {
            throw new IllegalArgumentException("confidence must be in (0, 1)");
        }

        double[] sorted = baselineLatencies.clone();
        Arrays.sort(sorted);
        int n = sorted.length;
        double alpha = 1.0 - confidence;

        // Commons Statistics' BinomialDistribution.inverseCumulativeProbability(q)
        // returns the smallest x such that P(X <= x) >= q, matching R's qbinom(q, n, p).
        BinomialDistribution binomial = BinomialDistribution.of(n, p);
        int qb = binomial.inverseCumulativeProbability(1.0 - alpha);
        int kRaw = qb + 1;

        int pointRank = (int) Math.ceil(p * n);
        if (pointRank < 1) pointRank = 1;

        boolean saturated = kRaw > n;
        int k;
        double threshold;
        if (saturated) {
            // No finite-sample bound exists at this confidence
            // (companion §12.4.2 / §12.5.2.1). The advisory value
            // t_{(n)} is reported with saturated=true so the caller
            // (PercentileLatency.evaluate) can route VERIFICATION to
            // INCONCLUSIVE and SMOKE to an advisory PASS/FAIL. The
            // deriver MUST NOT silently clamp k_raw to n and present
            // t_{(n)} as an exact bound.
            k = n;
            threshold = sorted[n - 1];
        } else {
            k = Math.max(pointRank, kRaw);
            threshold = sorted[k - 1];
        }

        double baselinePercentile = LatencyStatistics.nearestRankPercentile(sorted, p);

        return new Threshold(k, threshold, baselinePercentile, n, saturated);
    }

    /**
     * Result of the exact binomial order-statistic threshold derivation.
     *
     * @param rank               the order-statistic rank {@code k} (1-indexed)
     * @param threshold          the {@code k}-th order statistic value
     * @param baselinePercentile the nearest-rank point estimate {@code Q(p)}
     * @param n                  the baseline sample count
     * @param saturated          {@code true} when {@code k_raw > n} — the
     *                           {@code threshold} is the advisory {@code t_{(n)}},
     *                           not an exact bound at the configured confidence
     */
    public record Threshold(
            int rank, double threshold, double baselinePercentile, int n, boolean saturated) {

        /** Back-compat constructor: non-saturated form. */
        public Threshold(int rank, double threshold, double baselinePercentile, int n) {
            this(rank, threshold, baselinePercentile, n, false);
        }
    }

    /**
     * Result of deriving a latency threshold for a percentile.
     *
     * @param label the percentile label (e.g., "p95")
     * @param thresholdMs the derived threshold in ms
     * @param baselineMs the baseline percentile value in ms
     * @param source description of the threshold source
     */
    public record DerivedThreshold(
            String label,
            long thresholdMs,
            long baselineMs,
            String source
    ) {
    }
}
