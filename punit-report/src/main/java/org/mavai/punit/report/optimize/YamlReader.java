package org.mavai.punit.report.optimize;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.mavai.punit.internal.engine.emit.LatencySection;
import org.mavai.punit.report.CriterionResult;
import org.mavai.punit.statistics.LatencyStatistics;
import org.yaml.snakeyaml.Yaml;

/**
 * Reads one optimization YAML file into an {@link OptimizationRun}.
 *
 * <p>Read-only and tolerant: a file whose {@code schemaVersion} is not the
 * optimization schema is rejected ({@link Optional#empty()}), and every
 * field is optional at the parse level — a malformed or partial document
 * degrades to sensible defaults rather than throwing. Latency percentiles
 * are computed from each iteration's passing-sample vector using the
 * framework's nearest-rank method, gated by the same minimum-sample
 * thresholds the producer applies, so a percentile that cannot be reliably
 * estimated carries the percentile-unavailable sentinel.
 */
final class YamlReader {

    /** The canonical optimize interchange schema version this reader accepts. */
    private static final String OPTIMIZATION_SCHEMA = "mavai-optimize-1";

    /**
     * Parses a single optimization YAML document.
     *
     * @param in the YAML document stream
     * @return the parsed run, or empty if the document is not an
     *         optimization-schema file or cannot be read as a mapping
     */
    @SuppressWarnings("unchecked")
    Optional<OptimizationRun> read(InputStream in) {
        Object loaded = new Yaml().load(in);
        if (!(loaded instanceof Map)) {
            return Optional.empty();
        }
        Map<String, Object> root = (Map<String, Object>) loaded;
        if (!OPTIMIZATION_SCHEMA.equals(asString(root.get("schemaVersion")))) {
            return Optional.empty();
        }
        if (!(root.get("iterations") instanceof List)) {
            return Optional.empty();
        }

        String service = asString(root.get("serviceContractId"));
        String experimentId = asString(root.get("experimentId"));
        String objective = asString(root.get("objective"));

        List<Iteration> iterations = new ArrayList<>();
        for (Object element : (List<Object>) root.get("iterations")) {
            iterations.add(iteration(mapOf(element)));
        }

        return Optional.of(new OptimizationRun(service, experimentId, objective,
                iterations, convergence(mapOf(root.get("convergence")))));
    }

    // ── Iteration ──────────────────────────────────────────────────────────

    private static Iteration iteration(Map<String, Object> entry) {
        Map<String, Object> execution = mapOf(entry.get("execution"));
        Map<String, Object> statistics = mapOf(entry.get("statistics"));
        Map<String, Object> cost = mapOf(entry.get("cost"));
        Map<String, Object> latency = mapOf(entry.get("latency"));

        long[] sorted = sortedLatencies(latency.get("sortedPassingLatenciesMs"));
        return new Iteration(
                asInt(entry.get("iteration"), 0),
                mapOf(entry.get("factors")),
                asDouble(entry.get("score"), 0.0),
                asDouble(statistics.get("observed"), 0.0),
                asInt(statistics.get("successes"), 0),
                asInt(statistics.get("failures"), 0),
                asString(execution.get("terminationReason")),
                percentile(sorted, "p50", 0.50),
                percentile(sorted, "p95", 0.95),
                percentile(sorted, "p99", 0.99),
                asLong(cost.get("avgTimePerSampleMs"), 0L),
                sumTokens(mapOf(entry.get("resultProjection"))),
                sorted,
                criteria(mapOf(statistics.get("criteria"))));
    }

    private static Convergence convergence(Map<String, Object> raw) {
        return new Convergence(
                asInt(raw.get("totalIterations"), 0),
                asInt(raw.get("bestIteration"), -1),
                asDouble(raw.get("bestScore"), 0.0),
                asString(raw.get("terminationReason")));
    }

    // ── Latency ──────────────────────────────────────────────────────────

    private static long[] sortedLatencies(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return new long[0];
        }
        long[] values = new long[list.size()];
        for (int i = 0; i < list.size(); i++) {
            values[i] = asLong(list.get(i), 0L);
        }
        java.util.Arrays.sort(values);
        return values;
    }

    /**
     * Computes a percentile over the passing-sample latencies using the
     * framework's nearest-rank method, or returns the unavailable
     * sentinel when the sample count is below the percentile's minimum.
     */
    private static long percentile(long[] sortedMs, String label, double p) {
        if (sortedMs.length < LatencySection.minimumSamplesFor(label)) {
            return LatencySection.PERCENTILE_UNAVAILABLE_MS;
        }
        double[] asDoubles = new double[sortedMs.length];
        for (int i = 0; i < sortedMs.length; i++) {
            asDoubles[i] = sortedMs[i];
        }
        return Math.round(LatencyStatistics.nearestRankPercentile(asDoubles, p));
    }

    // ── Cost ─────────────────────────────────────────────────────────────

    /**
     * Sums {@code tokensUsed} across the per-sample projection. Returns the
     * unavailable sentinel when no sample carries a token count.
     */
    private static long sumTokens(Map<String, Object> resultProjection) {
        long sum = 0;
        boolean any = false;
        for (Object sample : resultProjection.values()) {
            if (sample instanceof Map<?, ?> sampleMap) {
                Object tokens = sampleMap.get("tokensUsed");
                if (tokens instanceof Number n) {
                    sum += n.longValue();
                    any = true;
                }
            }
        }
        return any ? sum : LatencySection.PERCENTILE_UNAVAILABLE_MS;
    }

    // ── Criteria ─────────────────────────────────────────────────────────

    private static Map<String, CriterionResult> criteria(Map<String, Object> raw) {
        Map<String, CriterionResult> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            Map<String, Object> row = mapOf(entry.getValue());
            result.put(entry.getKey(), new CriterionResult(
                    entry.getKey(),
                    asDouble(row.get("observedPassRate"), 0.0),
                    asInt(row.get("pass"), 0),
                    asInt(row.get("fail"), 0),
                    asInt(row.get("conditionFail"), 0),
                    asInt(row.get("transformFail"), 0)));
        }
        return result;
    }

    // ── Coercion helpers ──────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapOf(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : new LinkedHashMap<>();
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static double asDouble(Object value, double fallback) {
        return value instanceof Number n ? n.doubleValue() : fallback;
    }

    private static long asLong(Object value, long fallback) {
        return value instanceof Number n ? n.longValue() : fallback;
    }

    private static int asInt(Object value, int fallback) {
        return value instanceof Number n ? n.intValue() : fallback;
    }
}
