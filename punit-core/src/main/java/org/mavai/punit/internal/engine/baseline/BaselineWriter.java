package org.mavai.punit.internal.engine.baseline;

import static org.mavai.punit.internal.engine.baseline.BaselineSchema.CRITERION_BERNOULLI_PASS_RATE;
import static org.mavai.punit.internal.engine.baseline.BaselineSchema.CRITERION_PERCENTILE_LATENCY;
import static org.mavai.punit.internal.engine.baseline.BaselineSchema.FIELD_COVARIATES;
import static org.mavai.punit.internal.engine.baseline.BaselineSchema.FIELD_EXPIRES_AT;
import static org.mavai.punit.internal.engine.baseline.BaselineSchema.FIELD_EXPIRES_IN_DAYS;
import static org.mavai.punit.internal.engine.baseline.BaselineSchema.FIELD_FACTORS_FINGERPRINT;
import static org.mavai.punit.internal.engine.baseline.BaselineSchema.FIELD_GENERATED_AT;
import static org.mavai.punit.internal.engine.baseline.BaselineSchema.FIELD_INPUTS_IDENTITY;
import static org.mavai.punit.internal.engine.baseline.BaselineSchema.FIELD_METHOD_NAME;
import static org.mavai.punit.internal.engine.baseline.BaselineSchema.FIELD_OBSERVED_PASS_RATE;
import static org.mavai.punit.internal.engine.baseline.BaselineSchema.FIELD_PERCENTILES;
import static org.mavai.punit.internal.engine.baseline.BaselineSchema.FIELD_SAMPLE_COUNT;
import static org.mavai.punit.internal.engine.baseline.BaselineSchema.FIELD_SCHEMA_VERSION;
import static org.mavai.punit.internal.engine.baseline.BaselineSchema.FIELD_STATISTICS;
import static org.mavai.punit.internal.engine.baseline.BaselineSchema.FIELD_USE_CASE_ID;
import static org.mavai.punit.internal.engine.baseline.BaselineSchema.PERCENTILE_KEY_P50;
import static org.mavai.punit.internal.engine.baseline.BaselineSchema.PERCENTILE_KEY_P90;
import static org.mavai.punit.internal.engine.baseline.BaselineSchema.PERCENTILE_KEY_P95;
import static org.mavai.punit.internal.engine.baseline.BaselineSchema.PERCENTILE_KEY_P99;
import static org.mavai.punit.internal.engine.baseline.BaselineSchema.SCHEMA_VERSION_VALUE;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.mavai.punit.api.LatencyResult;
import org.mavai.punit.api.spec.BaselineStatistics;
import org.mavai.punit.api.spec.LatencyStatistics;
import org.mavai.punit.api.spec.NormativeJudgement;
import org.mavai.punit.api.spec.PassRateStatistics;
import org.mavai.punit.api.spec.PerCriterionPassRateStatistics;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Serialises a {@link BaselineRecord} to the
 * {@code punit-baseline-2} YAML schema and writes it to disk.
 *
 * <p>Filename is derived from the record itself
 * ({@link BaselineRecord#filename()}). The writer creates the parent
 * directory if it does not exist.
 *
 * <p>Two unsupported {@link BaselineStatistics} flavours are rejected
 * at write time with an {@link IllegalStateException}: the writer's
 * job is to produce a file the reader can fully parse, so silently
 * dropping unknown statistics kinds would create asymmetry. New
 * statistics kinds extend {@link #serialiseStatisticsEntry} alongside
 * a matching read path in {@link BaselineReader}.
 */
public final class BaselineWriter {

    /**
     * Writes {@code record} to {@code baselineDir} under its canonical
     * filename. Returns the path the file was written to.
     */
    public Path write(BaselineRecord record, Path baselineDir) throws IOException {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(baselineDir, "baselineDir");
        Files.createDirectories(baselineDir);
        Path file = baselineDir.resolve(record.filename());
        Files.writeString(file, toYaml(record), StandardCharsets.UTF_8);
        return file;
    }

    /**
     * Serialises {@code record} to the YAML schema as a string.
     *
     * <p>The MEASURE baseline carries aggregate signal only —
     * pass-rate statistics, latency percentiles, covariate profile,
     * footprint, fingerprint. Per-sample diagnostic detail (the
     * result-projection shape) lives on EXPLORE / OPTIMIZE artefacts;
     * MEASURE per-sample failure context is emitted to {@code System.err}
     * as the engine processes each sample (one {@code [PUNIT-FAIL]}
     * line per failed clause), keeping the baseline file size constant
     * in sample count and keeping the integrity fingerprint over
     * content the resolver actually reads.
     */
    public String toYaml(BaselineRecord record) {
        Objects.requireNonNull(record, "record");
        String body = yaml().dump(toYamlMap(record));
        // Append the SHA-256 of the body as the last field. The
        // reader recomputes the digest over the same prefix at load
        // time and surfaces a verdict warning when the file has been
        // modified since the measure produced it.
        return BaselineIntegrity.appendFingerprint(body);
    }

    private Map<String, Object> toYamlMap(BaselineRecord record) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put(FIELD_SCHEMA_VERSION, SCHEMA_VERSION_VALUE);
        root.put(FIELD_USE_CASE_ID, record.serviceContractId());
        root.put(FIELD_METHOD_NAME, record.methodName());
        root.put(FIELD_FACTORS_FINGERPRINT, record.factorsFingerprint());
        root.put(FIELD_INPUTS_IDENTITY, record.inputsIdentity());
        root.put(FIELD_SAMPLE_COUNT, record.sampleCount());
        root.put(FIELD_GENERATED_AT, DateTimeFormatter.ISO_INSTANT.format(record.generatedAt()));

        // Baseline expiration baseline expiration: emit only when a window was
        // declared. expiresInDays is authoritative; expiresAt is the
        // derived generatedAt + window, written for human readers and
        // recomputed (not trusted) on load. Positioned before the
        // appended contentFingerprint so both fall under the integrity
        // hash.
        if (record.expiresInDays() > 0) {
            root.put(FIELD_EXPIRES_IN_DAYS, record.expiresInDays());
            root.put(FIELD_EXPIRES_AT, DateTimeFormatter.ISO_INSTANT.format(
                    record.generatedAt().plus(Duration.ofDays(record.expiresInDays()))));
        }

        // The legacy criterion-named "percentile-latency" entry is
        // no longer emitted to YAML — the canonical location for
        // latency data is the top-level `latency:` block written
        // below. The in-memory map keeps the entry for the
        // PercentileLatency criterion's lookup; the reader
        // re-synthesises it from the top-level block on load.
        Map<String, Object> stats = new LinkedHashMap<>();
        Map<String, NormativeJudgement> judgementsByCriterionId = new LinkedHashMap<>();
        for (NormativeJudgement judgement : record.normativeJudgements()) {
            judgementsByCriterionId.put(judgement.criterionId(), judgement);
        }
        record.statisticsByCriterionName().forEach((name, value) -> {
            if (value instanceof LatencyStatistics) {
                return; // suppressed; lives in the latency: block instead
            }
            stats.put(name, serialiseStatisticsEntry(name, value, judgementsByCriterionId));
        });
        root.put(FIELD_STATISTICS, stats);

        if (!record.covariateProfile().isEmpty()) {
            // Insertion order of the profile is the service contract's
            // covariate-declaration order — preserved on the way out
            // so the filename hashes appear in the same order.
            root.put(FIELD_COVARIATES,
                    new LinkedHashMap<>(record.covariateProfile().values()));
        }
        // Latency block — passing-only percentiles + population
        // indicator. Emitted top-level (not under statistics). The
        // block is omitted entirely when zero samples passed;
        // individual percentiles within it are omitted per the
        // minimum-samples rule (5 / 10 / 20 / 100 for p50 / p90 /
        // p95 / p99).
        LatencyIndicator latency = record.latencyIndicator();
        if (latency.hasData()) {
            org.mavai.punit.internal.engine.emit.LatencySection.blockFor(
                    latency.passingPercentiles(),
                    latency.sortedPassingLatenciesMs(),
                    latency.contributingSamples(),
                    latency.totalSamples())
                .ifPresent(block -> root.put("latency", block));
        }
        return root;
    }

    private Map<String, Object> serialiseStatisticsEntry(
            String criterionName, BaselineStatistics statistics,
            Map<String, NormativeJudgement> judgementsByCriterionId) {
        if (statistics instanceof PerCriterionPassRateStatistics perCriterion) {
            return serialisePerCriterionPassRate(perCriterion, judgementsByCriterionId);
        }
        if (statistics instanceof LatencyStatistics latency) {
            return serialiseLatency(latency);
        }
        throw new IllegalStateException(
                "Unsupported baseline statistics kind for criterion '" + criterionName
                        + "': " + statistics.getClass().getName()
                        + " — extend BaselineWriter and BaselineReader together to add support.");
    }

    private Map<String, Object> serialisePerCriterionPassRate(
            PerCriterionPassRateStatistics stats,
            Map<String, NormativeJudgement> judgementsByCriterionId) {
        Map<String, Object> criteriaBlock = new LinkedHashMap<>();
        for (Map.Entry<String, PassRateStatistics> e : stats.byCriterion().entrySet()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put(FIELD_OBSERVED_PASS_RATE, e.getValue().observedPassRate());
            entry.put(FIELD_SAMPLE_COUNT, e.getValue().sampleCount());
            // Additive, documentary marker: the measure run's
            // judgement of this criterion against its stipulated
            // threshold. Emitted only for normative criteria; the
            // reader (and thus every resolver / derivation consumer)
            // ignores it. Positioned inside the criterion row, before
            // the appended contentFingerprint, so it falls under the
            // integrity hash like every other written field.
            NormativeJudgement judgement = judgementsByCriterionId.get(e.getKey());
            if (judgement != null) {
                entry.put(BaselineSchema.FIELD_NORMATIVE_JUDGEMENT,
                        serialiseNormativeJudgement(judgement));
            }
            criteriaBlock.put(e.getKey(), entry);
        }
        Map<String, Object> outer = new LinkedHashMap<>();
        outer.put(BaselineSchema.FIELD_CRITERIA, criteriaBlock);
        return outer;
    }

    private Map<String, Object> serialiseNormativeJudgement(NormativeJudgement judgement) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put(BaselineSchema.FIELD_JUDGEMENT_STATE,
                judgement.judgement().state().name().toLowerCase(java.util.Locale.ROOT));
        entry.put(BaselineSchema.FIELD_STIPULATED_THRESHOLD,
                judgement.judgement().stipulatedThreshold());
        entry.put(BaselineSchema.FIELD_JUDGEMENT_CONFIDENCE,
                judgement.judgement().confidence());
        return entry;
    }

    private Map<String, Object> serialiseLatency(LatencyStatistics stats) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put(FIELD_SAMPLE_COUNT, stats.sampleCount());

        LatencyResult percentiles = stats.percentiles();
        Map<String, Object> percentileMap = new LinkedHashMap<>();
        percentileMap.put(PERCENTILE_KEY_P50, isoDuration(percentiles.p50()));
        percentileMap.put(PERCENTILE_KEY_P90, isoDuration(percentiles.p90()));
        percentileMap.put(PERCENTILE_KEY_P95, isoDuration(percentiles.p95()));
        percentileMap.put(PERCENTILE_KEY_P99, isoDuration(percentiles.p99()));
        entry.put(FIELD_PERCENTILES, percentileMap);
        return entry;
    }

    private static String isoDuration(Duration duration) {
        return duration.toString();
    }

    private static Yaml yaml() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        return new Yaml(options);
    }

    /**
     * The criterion-name keys this writer recognises. Useful for
     * tests and for callers that build {@link BaselineRecord}
     * instances programmatically.
     */
    public static final class CriterionNames {
        private CriterionNames() { }
        public static final String BERNOULLI_PASS_RATE = CRITERION_BERNOULLI_PASS_RATE;
        public static final String PERCENTILE_LATENCY = CRITERION_PERCENTILE_LATENCY;
    }
}
