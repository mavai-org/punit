package org.mavai.punit.statistics;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Immutable value object representing the latency distribution of successful samples.
 *
 * <p>Computes standard percentile metrics (p50, p90, p95, p99) plus max and mean
 * from a collection of execution durations, and retains the sorted vector of
 * observations so baseline records (which carry the full sorted vector for
 * exact binomial threshold derivation) can be produced from it.
 *
 * <p>Percentiles use nearest-rank interpolation: for percentile p with n sorted
 * values, the index is {@code ceil(p * n) - 1}.
 */
public final class LatencyDistribution {

    private final long[] sortedLatenciesMs;
    private final long meanMs;
    private final long p50Ms;
    private final long p90Ms;
    private final long p95Ms;
    private final long p99Ms;
    private final long maxMs;

    private LatencyDistribution(long[] sortedLatenciesMs, long meanMs,
                                long p50Ms, long p90Ms, long p95Ms, long p99Ms, long maxMs) {
        this.sortedLatenciesMs = sortedLatenciesMs;
        this.meanMs = meanMs;
        this.p50Ms = p50Ms;
        this.p90Ms = p90Ms;
        this.p95Ms = p95Ms;
        this.p99Ms = p99Ms;
        this.maxMs = maxMs;
    }

    /**
     * Creates a {@code LatencyDistribution} from a list of execution durations.
     *
     * @param durations the durations of successful samples (must not be null or empty)
     * @return the computed latency distribution
     * @throws NullPointerException if durations is null
     * @throws IllegalArgumentException if durations is empty
     */
    public static LatencyDistribution fromDurations(List<Duration> durations) {
        Objects.requireNonNull(durations, "durations must not be null");
        if (durations.isEmpty()) {
            throw new IllegalArgumentException("durations must not be empty");
        }

        long[] millis = durations.stream()
                .mapToLong(Duration::toMillis)
                .toArray();

        return fromMillis(millis);
    }

    /**
     * Creates a {@code LatencyDistribution} from an array of millisecond values.
     *
     * @param millisValues the latency values in milliseconds (must not be null or empty)
     * @return the computed latency distribution
     * @throws NullPointerException if millisValues is null
     * @throws IllegalArgumentException if millisValues is empty
     */
    public static LatencyDistribution fromMillis(long[] millisValues) {
        Objects.requireNonNull(millisValues, "millisValues must not be null");
        if (millisValues.length == 0) {
            throw new IllegalArgumentException("millisValues must not be empty");
        }

        long[] sorted = millisValues.clone();
        Arrays.sort(sorted);

        int n = sorted.length;
        long mean = computeMean(sorted);

        return new LatencyDistribution(
                sorted,
                mean,
                percentile(sorted, 0.50),
                percentile(sorted, 0.90),
                percentile(sorted, 0.95),
                percentile(sorted, 0.99),
                sorted[n - 1]
        );
    }

    private static long computeMean(long[] sorted) {
        double sum = 0;
        for (long v : sorted) {
            sum += v;
        }
        return Math.round(sum / sorted.length);
    }

    /**
     * Computes a percentile using the nearest-rank method.
     *
     * <p>For percentile p with n sorted values, the index is {@code ceil(p * n) - 1},
     * clamped to [0, n-1].
     */
    private static long percentile(long[] sorted, double p) {
        int index = (int) Math.ceil(p * sorted.length) - 1;
        index = Math.max(0, Math.min(index, sorted.length - 1));
        return sorted[index];
    }

    public int sampleCount() {
        return sortedLatenciesMs.length;
    }

    /**
     * Returns a copy of the sorted observed latencies in milliseconds.
     *
     * @return fresh sorted array
     */
    public long[] sortedLatenciesMs() {
        return sortedLatenciesMs.clone();
    }

    public long meanMs() {
        return meanMs;
    }

    public long p50Ms() {
        return p50Ms;
    }

    public long p90Ms() {
        return p90Ms;
    }

    public long p95Ms() {
        return p95Ms;
    }

    public long p99Ms() {
        return p99Ms;
    }

    public long maxMs() {
        return maxMs;
    }

    @Override
    public String toString() {
        return "LatencyDistribution{" +
                "sampleCount=" + sortedLatenciesMs.length +
                ", meanMs=" + meanMs +
                ", p50=" + p50Ms +
                ", p90=" + p90Ms +
                ", p95=" + p95Ms +
                ", p99=" + p99Ms +
                ", max=" + maxMs +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LatencyDistribution that)) return false;
        return meanMs == that.meanMs
                && p50Ms == that.p50Ms
                && p90Ms == that.p90Ms
                && p95Ms == that.p95Ms
                && p99Ms == that.p99Ms
                && maxMs == that.maxMs
                && Arrays.equals(sortedLatenciesMs, that.sortedLatenciesMs);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(meanMs, p50Ms, p90Ms, p95Ms, p99Ms, maxMs)
                + Arrays.hashCode(sortedLatenciesMs);
    }
}
