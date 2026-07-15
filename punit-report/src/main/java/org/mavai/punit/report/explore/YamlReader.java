package org.mavai.punit.report.explore;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.mavai.punit.internal.engine.emit.LatencySection;
import org.mavai.punit.report.CriterionResult;
import org.mavai.punit.statistics.LatencyStatistics;
import org.yaml.snakeyaml.Yaml;

/**
 * Reads one exploration YAML file into a {@link Variant}.
 *
 * <p>The reader is read-only and tolerant: a file whose
 * {@code schemaVersion} is not the exploration schema is rejected
 * ({@link Optional#empty()}), and every field it consumes is optional at
 * the parse level — a malformed or partial variant degrades to sensible
 * defaults rather than throwing. Latency percentiles are computed from
 * the passing-sample vector using the framework's nearest-rank method,
 * gated by the same minimum-sample thresholds the producer applies, so a
 * percentile that cannot be reliably estimated carries the
 * percentile-unavailable sentinel.
 *
 * <p>The {@code label} is left as the empty string here — it depends on
 * the other variants of the same service and is assigned by
 * {@link ReportGenerator} once the full set is known.
 */
final class YamlReader {

    /** The canonical exploration interchange schema version this reader accepts. */
    private static final String EXPLORATION_SCHEMA = "mavai-explore-1";

    /**
     * Parses a single exploration YAML document.
     *
     * @param in the YAML document stream
     * @return the parsed variant, or empty if the document is not an
     *         exploration-schema file or cannot be read as a mapping
     */
    @SuppressWarnings("unchecked")
    Optional<ParsedVariant> read(InputStream in) {
        Object loaded = new Yaml().load(in);
        if (!(loaded instanceof Map)) {
            return Optional.empty();
        }
        Map<String, Object> root = (Map<String, Object>) loaded;
        if (!EXPLORATION_SCHEMA.equals(asString(root.get("schemaVersion")))) {
            return Optional.empty();
        }

        String service = asString(root.get("serviceContractId"));
        Map<String, Object> factors = mapOf(root.get("factors"));
        Map<String, Object> execution = mapOf(root.get("execution"));
        Map<String, Object> statistics = mapOf(root.get("statistics"));
        Map<String, Object> cost = mapOf(root.get("cost"));
        Map<String, Object> latency = mapOf(root.get("latency"));

        double observedRate = asDouble(statistics.get("observed"), 0.0);
        int successes = asInt(statistics.get("successes"), 0);
        int failures = asInt(statistics.get("failures"), 0);
        String terminationReason = asString(execution.get("terminationReason"));
        long avgTimePerSampleMs = asLong(cost.get("avgTimePerSampleMs"), 0L);

        long[] sorted = sortedLatencies(latency.get("sortedPassingLatenciesMs"));
        long p50 = percentile(sorted, "p50", 0.50);
        long p95 = percentile(sorted, "p95", 0.95);
        long p99 = percentile(sorted, "p99", 0.99);

        long totalTokens = sumTokens(mapOf(root.get("resultProjection")));
        Map<String, CriterionResult> criteria = criteria(mapOf(statistics.get("criteria")));

        Variant variant = new Variant(factors, "", observedRate, successes, failures,
                terminationReason, p50, p95, p99, avgTimePerSampleMs, totalTokens,
                sorted, criteria);
        return Optional.of(new ParsedVariant(service, variant));
    }

    /** A variant paired with the service it belongs to. */
    record ParsedVariant(String service, Variant variant) {
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
     * Sums {@code tokensUsed} across the per-sample projection. Returns
     * the unavailable sentinel when no sample carries a token count
     * (token tracking was off).
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
