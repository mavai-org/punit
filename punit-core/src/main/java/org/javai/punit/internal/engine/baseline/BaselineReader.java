package org.javai.punit.internal.engine.baseline;

import static org.javai.punit.internal.engine.baseline.BaselineSchema.FIELD_COVARIATES;
import static org.javai.punit.internal.engine.baseline.BaselineSchema.FIELD_CRITERIA;
import static org.javai.punit.internal.engine.baseline.BaselineSchema.FIELD_EXPIRES_IN_DAYS;
import static org.javai.punit.internal.engine.baseline.BaselineSchema.FIELD_FACTORS_FINGERPRINT;
import static org.javai.punit.internal.engine.baseline.BaselineSchema.FIELD_GENERATED_AT;
import static org.javai.punit.internal.engine.baseline.BaselineSchema.FIELD_INPUTS_IDENTITY;
import static org.javai.punit.internal.engine.baseline.BaselineSchema.FIELD_METHOD_NAME;
import static org.javai.punit.internal.engine.baseline.BaselineSchema.FIELD_OBSERVED_PASS_RATE;
import static org.javai.punit.internal.engine.baseline.BaselineSchema.FIELD_PERCENTILES;
import static org.javai.punit.internal.engine.baseline.BaselineSchema.FIELD_SAMPLE_COUNT;
import static org.javai.punit.internal.engine.baseline.BaselineSchema.FIELD_SCHEMA_VERSION;
import static org.javai.punit.internal.engine.baseline.BaselineSchema.FIELD_STATISTICS;
import static org.javai.punit.internal.engine.baseline.BaselineSchema.FIELD_USE_CASE_ID;
import static org.javai.punit.internal.engine.baseline.BaselineSchema.PERCENTILE_KEY_P50;
import static org.javai.punit.internal.engine.baseline.BaselineSchema.PERCENTILE_KEY_P90;
import static org.javai.punit.internal.engine.baseline.BaselineSchema.PERCENTILE_KEY_P95;
import static org.javai.punit.internal.engine.baseline.BaselineSchema.PERCENTILE_KEY_P99;
import static org.javai.punit.internal.engine.baseline.BaselineSchema.SCHEMA_VERSION_PREVIOUS;
import static org.javai.punit.internal.engine.baseline.BaselineSchema.SCHEMA_VERSION_VALUE;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.javai.punit.api.LatencyResult;
import org.javai.punit.api.covariate.CovariateProfile;
import org.javai.punit.api.spec.BaselineStatistics;
import org.javai.punit.api.spec.LatencyStatistics;
import org.javai.punit.api.spec.PassRateStatistics;
import org.javai.punit.api.spec.PerCriterionPassRateStatistics;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Parses a {@code punit-baseline-2} YAML file into a
 * {@link BaselineRecord}. Strict on schema discriminator and on field
 * presence — a malformed file fails loudly rather than silently
 * producing a partial record that the resolver might match against.
 */
public final class BaselineReader {

    /** Reads and parses the file at {@code baselineFile}. */
    public BaselineRecord read(Path baselineFile) throws IOException {
        return load(baselineFile).record();
    }

    /**
     * Reads, parses, and integrity-verifies the file at
     * {@code baselineFile}. Returns the parsed {@link BaselineRecord}
     * paired with an optional integrity warning — empty when the
     * {@code contentFingerprint:} field's stored digest matches the
     * recomputed body digest, populated otherwise.
     *
     * <p>Integrity failure does <em>not</em> propagate as an
     * exception; the warning is reported through the verdict's
     * warnings list, not the test outcome. Parse failures still
     * throw — a malformed file is a different category from a
     * modified-but-well-formed file.
     */
    public LoadedBaseline load(Path baselineFile) throws IOException {
        Objects.requireNonNull(baselineFile, "baselineFile");
        String content = Files.readString(baselineFile, StandardCharsets.UTF_8);
        BaselineRecord record;
        try {
            record = parse(content);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Failed to parse baseline file " + baselineFile + ": " + e.getMessage(), e);
        }
        Optional<String> integrityWarning = BaselineIntegrity.verify(content, baselineFile);
        return new LoadedBaseline(record, integrityWarning);
    }

    /**
     * Bundles a parsed {@link BaselineRecord} with an optional
     * integrity warning surfaced by {@link BaselineIntegrity#verify}.
     * The resolver propagates the warning into the
     * {@link org.javai.punit.api.spec.BaselineLookup}'s notes when
     * this record is the selected candidate.
     */
    public record LoadedBaseline(BaselineRecord record, Optional<String> integrityWarning) {
        public LoadedBaseline {
            Objects.requireNonNull(record, "record");
            Objects.requireNonNull(integrityWarning, "integrityWarning");
        }
    }

    /** Parses {@code yaml} into a {@link BaselineRecord}. */
    public BaselineRecord parse(String yaml) {
        Objects.requireNonNull(yaml, "yaml");
        Map<String, Object> root = loadYaml(yaml);

        String version = requireString(root, FIELD_SCHEMA_VERSION);
        if (!SCHEMA_VERSION_VALUE.equals(version)) {
            if (SCHEMA_VERSION_PREVIOUS.equals(version)) {
                throw new IllegalArgumentException(
                        "Baseline file is at schema '" + SCHEMA_VERSION_PREVIOUS
                                + "', which predates the per-methodology-criterion shape"
                                + " introduced in '" + SCHEMA_VERSION_VALUE + "'."
                                + " Re-run the MEASURE experiment to regenerate the baseline"
                                + " in the current shape; the reader does not adapt across"
                                + " this schema break.");
            }
            throw new IllegalArgumentException(
                    "Unsupported schema version '" + version + "' (expected '"
                            + SCHEMA_VERSION_VALUE + "')");
        }

        String serviceContractId = requireString(root, FIELD_USE_CASE_ID);
        String methodName = requireString(root, FIELD_METHOD_NAME);
        String factorsFingerprint = requireString(root, FIELD_FACTORS_FINGERPRINT);
        String inputsIdentity = requireString(root, FIELD_INPUTS_IDENTITY);
        int sampleCount = requireInt(root, FIELD_SAMPLE_COUNT);
        Instant generatedAt = requireInstant(root, FIELD_GENERATED_AT);
        // Expiration: optional. Absent → 0 (no expiration).
        // expiresInDays is authoritative; the emitted expiresAt is a
        // derived convenience and is not read back here.
        int expiresInDays = root.containsKey(FIELD_EXPIRES_IN_DAYS)
                ? requireInt(root, FIELD_EXPIRES_IN_DAYS)
                : 0;

        Map<String, Object> statsMap = requireMap(root, FIELD_STATISTICS);
        Map<String, BaselineStatistics> entries = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : statsMap.entrySet()) {
            entries.put(e.getKey(), parseStatisticsEntry(e.getKey(), e.getValue()));
        }

        // Canonical location for latency: the top-level
        // `latency:` block. When present, it sources the
        // PercentileLatency criterion's in-memory entry under
        // "percentile-latency" and overrides any legacy entry that
        // happened to be in the statistics map (old-shape baselines
        // on disk continue to load via the existing loop above).
        LatencyIndicator latencyIndicator = parseLatencyIndicator(root);
        if (latencyIndicator.hasData()) {
            entries.put("percentile-latency", new LatencyStatistics(
                    latencyIndicator.passingPercentiles(),
                    latencyIndicator.sortedPassingLatenciesMs(),
                    latencyIndicator.contributingSamples()));
        }

        CovariateProfile profile = parseCovariates(root);

        return new BaselineRecord(
                serviceContractId, methodName, factorsFingerprint,
                inputsIdentity, sampleCount, generatedAt, entries, profile,
                latencyIndicator, expiresInDays);
    }

    /**
     * Parse the top-level {@code latency:} block (the canonical
     * location). Returns {@link LatencyIndicator#empty()} when the
     * block is absent — legacy baselines that carry latency only
     * under {@code statistics.percentile-latency} continue to load
     * via the regular statistics-map path.
     */
    private LatencyIndicator parseLatencyIndicator(Map<String, Object> root) {
        if (!root.containsKey("latency")) {
            return LatencyIndicator.empty();
        }
        Map<String, Object> block = requireMap(root, "latency");
        int contributing = requireInt(block, "contributingSamples");
        int total = requireInt(block, "totalSamples");
        Duration p50 = block.containsKey("p50Ms")
                ? Duration.ofMillis(requireInt(block, "p50Ms"))
                : Duration.ZERO;
        Duration p90 = block.containsKey("p90Ms")
                ? Duration.ofMillis(requireInt(block, "p90Ms"))
                : Duration.ZERO;
        Duration p95 = block.containsKey("p95Ms")
                ? Duration.ofMillis(requireInt(block, "p95Ms"))
                : Duration.ZERO;
        Duration p99 = block.containsKey("p99Ms")
                ? Duration.ofMillis(requireInt(block, "p99Ms"))
                : Duration.ZERO;
        LatencyResult result = new LatencyResult(p50, p90, p95, p99, contributing);
        long[] sorted = parseSortedLatencies(block, contributing);
        return new LatencyIndicator(result, sorted, contributing, total);
    }

    /**
     * Parse the {@code sortedPassingLatenciesMs:} list under the
     * {@code latency:} block. Legacy baselines that predate the
     * binomial-bound treatment ({@code DIR-LATENCY-EMPIRICAL-WIRE-DERIVER-punit})
     * may not carry this list; in that case we fall back to a
     * synthetic vector derived from the percentile point estimates,
     * which lets the deriver run but produces a coarser bound than
     * the full-vector form. A warning is the caller's responsibility
     * (saturation/feasibility surfaces in {@code DIR-LATENCY-SATURATION-AND-EXISTENCE-GATE-punit}).
     */
    private long[] parseSortedLatencies(Map<String, Object> block, int contributing) {
        if (!block.containsKey("sortedPassingLatenciesMs")) {
            return new long[contributing]; // synthetic-zero fallback; deriver will produce conservative bound
        }
        Object raw = block.get("sortedPassingLatenciesMs");
        if (!(raw instanceof List<?> list)) {
            throw new IllegalArgumentException(
                    "latency.sortedPassingLatenciesMs must be a list, got "
                            + (raw == null ? "null" : raw.getClass().getSimpleName()));
        }
        if (list.size() != contributing) {
            throw new IllegalArgumentException(
                    "latency.sortedPassingLatenciesMs length (" + list.size()
                            + ") must equal contributingSamples (" + contributing + ")");
        }
        long[] arr = new long[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Object v = list.get(i);
            if (!(v instanceof Number n)) {
                throw new IllegalArgumentException(
                        "latency.sortedPassingLatenciesMs[" + i + "] must be a number, got "
                                + (v == null ? "null" : v.getClass().getSimpleName()));
            }
            arr[i] = n.longValue();
        }
        return arr;
    }

    private CovariateProfile parseCovariates(Map<String, Object> root) {
        if (!root.containsKey(FIELD_COVARIATES)) {
            return CovariateProfile.empty();
        }
        Object raw = root.get(FIELD_COVARIATES);
        if (raw == null) {
            return CovariateProfile.empty();
        }
        if (!(raw instanceof Map<?, ?> nested)) {
            throw new IllegalArgumentException(
                    "Field '" + FIELD_COVARIATES + "' must be a mapping, got "
                            + raw.getClass().getSimpleName());
        }
        Map<String, Object> typed = asStringKeyedMap(nested, FIELD_COVARIATES);
        if (typed.isEmpty()) {
            return CovariateProfile.empty();
        }
        Map<String, String> values = new LinkedHashMap<>(typed.size());
        for (Map.Entry<String, Object> e : typed.entrySet()) {
            Object v = e.getValue();
            if (!(v instanceof String s)) {
                throw new IllegalArgumentException(
                        "Covariate '" + e.getKey() + "' must be a string, got "
                                + (v == null ? "null" : v.getClass().getSimpleName()));
            }
            values.put(e.getKey(), s);
        }
        return CovariateProfile.of(values);
    }

    private BaselineStatistics parseStatisticsEntry(String criterionName, Object raw) {
        if (!(raw instanceof Map<?, ?> mapRaw)) {
            throw new IllegalArgumentException(
                    "statistics entry '" + criterionName + "' must be a mapping, got "
                            + (raw == null ? "null" : raw.getClass().getSimpleName()));
        }
        Map<String, Object> entry = asStringKeyedMap(mapRaw, "statistics." + criterionName);

        if (entry.containsKey(FIELD_CRITERIA)) {
            Map<String, Object> criteriaMap = requireMap(entry, FIELD_CRITERIA);
            Map<String, PassRateStatistics> byCriterion = new LinkedHashMap<>();
            for (Map.Entry<String, Object> c : criteriaMap.entrySet()) {
                if (!(c.getValue() instanceof Map<?, ?> rowRaw)) {
                    throw new IllegalArgumentException(
                            "statistics." + criterionName + ".criteria." + c.getKey()
                                    + " must be a mapping, got "
                                    + (c.getValue() == null
                                            ? "null"
                                            : c.getValue().getClass().getSimpleName()));
                }
                Map<String, Object> row = asStringKeyedMap(rowRaw,
                        "statistics." + criterionName + ".criteria." + c.getKey());
                double observed = requireDouble(row, FIELD_OBSERVED_PASS_RATE);
                int rowSampleCount = requireInt(row, FIELD_SAMPLE_COUNT);
                byCriterion.put(c.getKey(),
                        new PassRateStatistics(observed, rowSampleCount));
            }
            return new PerCriterionPassRateStatistics(byCriterion);
        }
        if (entry.containsKey(FIELD_PERCENTILES)) {
            Map<String, Object> percentiles = requireMap(entry, FIELD_PERCENTILES);
            int sampleCount = requireInt(entry, FIELD_SAMPLE_COUNT);
            LatencyResult result = new LatencyResult(
                    parseDuration(percentiles, PERCENTILE_KEY_P50),
                    parseDuration(percentiles, PERCENTILE_KEY_P90),
                    parseDuration(percentiles, PERCENTILE_KEY_P95),
                    parseDuration(percentiles, PERCENTILE_KEY_P99),
                    sampleCount);
            return new LatencyStatistics(result, new long[sampleCount], sampleCount);
        }
        throw new IllegalArgumentException(
                "Unrecognised statistics entry shape for criterion '" + criterionName
                        + "' — expected a known discriminator field ('"
                        + FIELD_CRITERIA + "' or '" + FIELD_PERCENTILES + "')");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadYaml(String yaml) {
        Yaml parser = new Yaml(new SafeConstructor(new LoaderOptions()));
        Object parsed = parser.load(yaml);
        if (parsed == null) {
            throw new IllegalArgumentException("baseline file is empty");
        }
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(
                    "baseline file root must be a mapping, got "
                            + parsed.getClass().getSimpleName());
        }
        return asStringKeyedMap(map, "<root>");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asStringKeyedMap(Map<?, ?> map, String location) {
        Map<String, Object> out = new LinkedHashMap<>(map.size());
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (!(e.getKey() instanceof String key)) {
                throw new IllegalArgumentException(
                        "Non-string key at " + location + ": " + e.getKey());
            }
            out.put(key, e.getValue());
        }
        return out;
    }

    private static String requireString(Map<String, Object> map, String key) {
        Object v = require(map, key);
        if (!(v instanceof String s)) {
            throw new IllegalArgumentException(
                    "Field '" + key + "' must be a string, got " + v.getClass().getSimpleName());
        }
        return s;
    }

    private static int requireInt(Map<String, Object> map, String key) {
        Object v = require(map, key);
        if (v instanceof Integer i) {
            return i;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        throw new IllegalArgumentException(
                "Field '" + key + "' must be an integer, got " + v.getClass().getSimpleName());
    }

    private static double requireDouble(Map<String, Object> map, String key) {
        Object v = require(map, key);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        throw new IllegalArgumentException(
                "Field '" + key + "' must be numeric, got " + v.getClass().getSimpleName());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> requireMap(Map<String, Object> map, String key) {
        Object v = require(map, key);
        if (!(v instanceof Map<?, ?> nested)) {
            throw new IllegalArgumentException(
                    "Field '" + key + "' must be a mapping, got " + v.getClass().getSimpleName());
        }
        return asStringKeyedMap(nested, key);
    }

    private static Object require(Map<String, Object> map, String key) {
        if (!map.containsKey(key)) {
            throw new IllegalArgumentException("Missing required field '" + key + "'");
        }
        Object v = map.get(key);
        if (v == null) {
            throw new IllegalArgumentException("Field '" + key + "' must not be null");
        }
        return v;
    }

    private static Duration parseDuration(Map<String, Object> map, String key) {
        return Duration.parse(requireString(map, key));
    }

    private static Instant requireInstant(Map<String, Object> map, String key) {
        Object v = require(map, key);
        // SnakeYAML's SafeConstructor recognises ISO-8601 timestamps and decodes
        // them as java.util.Date; accept either that or a String.
        if (v instanceof java.util.Date d) {
            return d.toInstant();
        }
        if (v instanceof String s) {
            return Instant.parse(s);
        }
        throw new IllegalArgumentException(
                "Field '" + key + "' must be an ISO-8601 timestamp (string or date), got "
                        + v.getClass().getSimpleName());
    }
}
