package org.mavai.punit.internal.engine.emit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.mavai.punit.api.LatencyResult;
import org.mavai.punit.api.spec.SampleSummary;

/**
 * Produces the normative {@code latency:} YAML block emitted by
 * the baseline spec, exploration rows, optimize iterations, and
 * the descriptive latency dimension of verdict records.
 *
 * <p>Every emitted block carries the population-indicator triple —
 * {@code basis} (currently always {@code passing-samples}),
 * {@code contributingSamples}, and {@code totalSamples} — alongside
 * the four percentiles. The percentiles are computed from passing
 * samples only; the indicator names that population so a reader
 * can verify it without external context.
 *
 * <p>When zero samples passed, no block is emitted —
 * {@link #blockFor(SampleSummary)} returns {@link Optional#empty()}.
 * The caller's containing structure (the YAML root, the iteration
 * entry, etc.) continues normally without a {@code latency:} key.
 *
 * <p>Pure — performs no I/O. The shape it returns is YAML-friendly
 * (a {@link LinkedHashMap} preserving insertion order) so callers
 * can drop it directly into the snakeyaml {@code dump} tree.
 */
public final class LatencySection {

    /** The only currently defined population basis. */
    public static final String BASIS_PASSING_SAMPLES = "passing-samples";

    /** Minimum contributing samples required to emit each percentile. */
    public static final int MIN_SAMPLES_P50 = 1;
    public static final int MIN_SAMPLES_P90 = 10;
    public static final int MIN_SAMPLES_P95 = 20;
    public static final int MIN_SAMPLES_P99 = 100;

    /**
     * Sentinel value carried in latency-percentile fields (e.g.
     * {@code p50Ms}, {@code p95Ms}, {@code p99Ms}, {@code maxMs}) to
     * mean "not reliably estimated" — the contributing-sample count
     * fell below this percentile's minimum, so no value is emitted.
     * Producers (the verdict adapter) write this; consumers
     * (text and HTML renderers) recognise it and substitute a dash
     * rather than the literal {@code -1}.
     */
    public static final long PERCENTILE_UNAVAILABLE_MS = -1L;

    private LatencySection() { }

    /**
     * Whether {@code contributingSamples} meets the
     * minimum-sample threshold for the given percentile.
     *
     * @param percentileLabel one of {@code "p50"}, {@code "p90"},
     *                        {@code "p95"}, {@code "p99"}.
     * @param contributingSamples the count of passing samples
     *                            available for the percentile
     *                            computation.
     * @return {@code true} if the count meets or exceeds the
     *         minimum for that percentile per the
     *         {@code n ≥ 1 / (1 − p)} rule.
     */
    public static boolean isPercentileEmittable(String percentileLabel, int contributingSamples) {
        return contributingSamples >= minimumSamplesFor(percentileLabel);
    }

    /** The minimum-contributing-samples threshold for a percentile label. */
    public static int minimumSamplesFor(String percentileLabel) {
        return switch (percentileLabel) {
            case "p50" -> MIN_SAMPLES_P50;
            case "p90" -> MIN_SAMPLES_P90;
            case "p95" -> MIN_SAMPLES_P95;
            case "p99" -> MIN_SAMPLES_P99;
            default -> throw new IllegalArgumentException(
                    "unknown percentile label: " + percentileLabel
                            + " (expected one of p50, p90, p95, p99)");
        };
    }

    /**
     * Build the {@code latency:} block from a sample summary's
     * passing-only latency result. Returns empty when no samples
     * passed.
     */
    public static Optional<Map<String, Object>> blockFor(SampleSummary<?> summary) {
        long[] sorted = summary.trials().stream()
                .filter(t -> t.outcome().value() instanceof org.mavai.outcome.Outcome.Ok)
                .mapToLong(t -> t.duration().toMillis())
                .sorted()
                .toArray();
        return blockFor(summary.passingLatencyResult(), sorted,
                summary.successes(), summary.total());
    }

    /** Overload taking pre-sorted passing latencies. */
    public static Optional<Map<String, Object>> blockFor(
            LatencyResult passingPercentiles, long[] sortedPassingLatenciesMs,
            int contributingSamples, int totalSamples) {
        Optional<Map<String, Object>> built =
                blockFor(passingPercentiles, contributingSamples, totalSamples);
        built.ifPresent(block -> {
            java.util.List<Long> asList = new java.util.ArrayList<>(sortedPassingLatenciesMs.length);
            for (long v : sortedPassingLatenciesMs) asList.add(v);
            block.put("sortedPassingLatenciesMs", asList);
        });
        return built;
    }

    /**
     * Build the {@code latency:} block from raw passing-only data.
     * Returns empty when {@code contributingSamples == 0}. Useful
     * for unit-testing the block shape without constructing a full
     * {@link SampleSummary}.
     */
    public static Optional<Map<String, Object>> blockFor(
            LatencyResult passingPercentiles, int contributingSamples, int totalSamples) {
        if (contributingSamples == 0) {
            return Optional.empty();
        }
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("basis", BASIS_PASSING_SAMPLES);
        block.put("contributingSamples", contributingSamples);
        block.put("totalSamples", totalSamples);
        // Omit each percentile key when contributingSamples is below
        // that percentile's minimum (1 / 10 / 20 / 100 for p50 / p90 /
        // p95 / p99). The artefact carries only the percentiles that
        // can be estimated reliably.
        if (isPercentileEmittable("p50", contributingSamples)) {
            block.put("p50Ms", passingPercentiles.p50().toMillis());
        }
        if (isPercentileEmittable("p90", contributingSamples)) {
            block.put("p90Ms", passingPercentiles.p90().toMillis());
        }
        if (isPercentileEmittable("p95", contributingSamples)) {
            block.put("p95Ms", passingPercentiles.p95().toMillis());
        }
        if (isPercentileEmittable("p99", contributingSamples)) {
            block.put("p99Ms", passingPercentiles.p99().toMillis());
        }
        return Optional.of(block);
    }
}
