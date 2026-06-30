package org.mavai.punit.report.optimize;

import java.util.Map;

/**
 * One OPTIMIZE-experiment iteration — a single step in which a mutator
 * produced a factor bundle and a scorer rated the result.
 *
 * <p>Every field is read from, or trivially derived from, one
 * {@code iterations[]} entry of an optimization YAML file. The headline
 * metric is {@link #score}; the latency percentiles use the framework's
 * nearest-rank method over the passing-sample latencies, carrying the
 * percentile-unavailable sentinel when too few (or no) samples passed.
 *
 * @param index              zero-based iteration number (the comparison axis)
 * @param factors            the iteration's full factor bundle (declaration order)
 * @param score              the scorer's value for this iteration (headline metric)
 * @param observedRate       observed pass rate in {@code [0, 1]} (secondary)
 * @param successes          passing sample count
 * @param failures           failing sample count
 * @param terminationReason  why sampling stopped (e.g. {@code COMPLETED})
 * @param p50Ms              median passing latency, or the unavailable sentinel
 * @param p95Ms              95th-percentile passing latency, or the sentinel
 * @param p99Ms              99th-percentile passing latency, or the sentinel
 * @param avgTimePerSampleMs average wall-clock time per sample
 * @param totalTokens        summed token usage, or the unavailable sentinel
 * @param sortedLatenciesMs  ascending passing-sample latencies (may be empty)
 * @param criteria           per-criterion results, keyed by criterion id
 */
record Iteration(
        int index,
        Map<String, Object> factors,
        double score,
        double observedRate,
        int successes,
        int failures,
        String terminationReason,
        long p50Ms,
        long p95Ms,
        long p99Ms,
        long avgTimePerSampleMs,
        long totalTokens,
        long[] sortedLatenciesMs,
        Map<String, CriterionResult> criteria) {

    /** Total samples that produced an outcome (passing or failing). */
    int sampleCount() {
        return successes + failures;
    }

    /** A no-outcome iteration (zero passing and zero failing samples). */
    boolean hasNoSamples() {
        return sampleCount() == 0;
    }
}
