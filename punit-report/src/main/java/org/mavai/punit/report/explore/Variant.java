package org.mavai.punit.report.explore;

import java.util.Map;

/**
 * One Explore-experiment variant — a single factor combination (model,
 * temperature, system prompt, …) evaluated against a service contract.
 *
 * <p>Every field is read from, or trivially derived from, one
 * exploration YAML file. The latency percentiles use the framework's
 * nearest-rank method over the passing-sample latencies; an unavailable
 * percentile (too few contributing samples, or none) carries the
 * framework's percentile-unavailable sentinel so the renderer can show a
 * dash rather than a misleading number.
 *
 * @param factors            the variant's full factor map (declaration order)
 * @param label              the short label distinguishing this variant
 *                           within its service (the factors that differ
 *                           across the set)
 * @param observedRate       overall observed pass rate in {@code [0, 1]}
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
record Variant(
        Map<String, Object> factors,
        String label,
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

    /** A no-outcome variant (zero passing and zero failing samples). */
    boolean hasNoSamples() {
        return sampleCount() == 0;
    }

    /** Returns a copy of this variant with a different display label. */
    Variant withLabel(String newLabel) {
        return new Variant(factors, newLabel, observedRate, successes, failures,
                terminationReason, p50Ms, p95Ms, p99Ms, avgTimePerSampleMs,
                totalTokens, sortedLatenciesMs, criteria);
    }
}
