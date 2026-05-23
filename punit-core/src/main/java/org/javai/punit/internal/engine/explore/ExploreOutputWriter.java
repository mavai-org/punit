package org.javai.punit.internal.engine.explore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.javai.punit.api.FactorBundle;
import org.javai.punit.api.spec.FailureCount;
import org.javai.punit.api.spec.PerConfigSummary;
import org.javai.punit.api.spec.SampleSummary;
import org.javai.punit.internal.engine.emit.LatencySection;
import org.javai.punit.internal.engine.emit.ResultProjections;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Serialises one EXPLORE configuration's outcome to the
 * explore-output YAML schema and resolves its readable-stem
 * filename.
 *
 * <p>Both methods are pure — the writer performs no I/O. The
 * {@link org.javai.punit.internal.runtime.ExploreEmitter EXPLORE emitter}
 * orchestrates persistence (writing to disk or to an in-memory
 * sink for tests).
 *
 * <p>Schema follows the canonical explore-output YAML shape:
 * {@code factors:} block in factor-record declaration order,
 * descriptive statistics only (no inferential statistics), small
 * sample counts accepted without warning. Per-sample result
 * projections are not emitted here yet — when the trial path threads
 * projection metadata through, a {@code resultProjection:} block can
 * be appended additively.
 */
public final class ExploreOutputWriter {

    /** Schema-version value carried in every emitted file. */
    public static final String SCHEMA_VERSION = "punit-spec-1";

    /** Maximum unsanitised canonical-value length before truncation kicks in. */
    private static final int MAX_RAW_LENGTH = 32;

    /** Truncated prefix length when the value exceeds {@link #MAX_RAW_LENGTH}. */
    private static final int TRUNCATED_PREFIX_LENGTH = 24;

    /** Stem used when the factor record is empty (no components). */
    private static final String EMPTY_BUNDLE_STEM = "no-factors";

    /**
     * Build the explore-output YAML for one configuration. Pure -
     * no I/O.
     *
     * @param serviceContractId the service contract identifier (becomes the
     *                  {@code useCaseId:} field).
     * @param factorBundle the configuration's factor bundle (becomes
     *                     the {@code factors:} block).
     * @param entry the per-config summary plus planned sample count.
     * @return YAML matching the canonical explore-output schema.
     */
    public String writeYaml(String serviceContractId, FactorBundle factorBundle, PerConfigSummary<?, ?> entry) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("useCaseId", serviceContractId);
        root.put("generatedAt", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        root.put("factors", factorsBlock(factorBundle));
        root.put("execution", executionBlock(entry));
        root.put("statistics", statisticsBlock(entry.summary()));
        root.put("cost", costBlock(entry.summary()));
        // Latency block - passing-only percentiles + population
        // indicator. Inserted before resultProjection so the
        // aggregate latency precedes the per-sample timing data.
        // Omitted entirely when zero samples passed.
        LatencySection.blockFor(entry.summary())
                .ifPresent(block -> root.put("latency", block));
        root.put("resultProjection", ResultProjections.resultProjectionMap(entry.summary().trials()));

        String dump = yaml().dump(root);
        return ResultProjections.injectAnchorComments(
                dump, ResultProjections.anchorsFor(entry.summary().trials()));
    }

    /**
     * Compute the readable-stem filename (without extension) for a
     * factor bundle.
     *
     * <p>For each entry in declaration order: produce
     * {@code {fieldName}-{canonicalValue}}, optionally truncated and
     * suffixed with a 4-char SHA-256 hash if the value is long.
     * Segments are joined with {@code _}; the whole result is
     * sanitised (non-alphanumeric/{@code .-_} replaced with
     * {@code _}, runs of {@code _} collapsed).
     */
    public String filenameFor(FactorBundle factorBundle) {
        if (factorBundle.isEmpty()) {
            return EMPTY_BUNDLE_STEM;
        }
        StringBuilder stem = new StringBuilder();
        boolean first = true;
        for (FactorBundle.Entry e : factorBundle.entries()) {
            if (!first) {
                stem.append('_');
            }
            first = false;
            stem.append(e.name()).append('-').append(canonicalValueForFilename(e.value()));
        }
        return sanitise(stem.toString());
    }

    private static Map<String, Object> factorsBlock(FactorBundle bundle) {
        Map<String, Object> block = new LinkedHashMap<>();
        for (FactorBundle.Entry e : bundle.entries()) {
            block.put(e.name(), e.value().yamlValue());
        }
        return block;
    }

    private static Map<String, Object> executionBlock(PerConfigSummary<?, ?> entry) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("samplesPlanned", entry.samplesPlanned());
        block.put("samplesExecuted", entry.summary().total());
        block.put("terminationReason", entry.summary().terminationReason().name());
        return block;
    }

    private static Map<String, Object> statisticsBlock(SampleSummary<?> summary) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("observed", summary.passRate());
        block.put("successes", summary.successes());
        block.put("failures", summary.failures());
        Map<String, Object> failureDistribution = new LinkedHashMap<>();
        for (Map.Entry<String, FailureCount> entry : summary.failuresByPostcondition().entrySet()) {
            failureDistribution.put(entry.getKey(), entry.getValue().count());
        }
        block.put("failureDistribution", failureDistribution);
        // Per-criterion decomposition (only when the run produced
        // methodology-level criterion counts — empty for contracts
        // that declared no explicit criteria() value).
        // Mirrors the per-criterion shape that MEASURE writes into
        // baselines so a reader can compare explore vs measure
        // emissions of the same contract criterion-by-criterion.
        if (!summary.criterionSampleCounts().isEmpty()) {
            Map<String, Object> criteria = new LinkedHashMap<>();
            for (org.javai.punit.api.spec.CriterionSampleCounts counts
                    : summary.criterionSampleCounts()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("observedPassRate", counts.observedPassRate());
                row.put("pass", counts.pass());
                row.put("fail", counts.fail());
                row.put("conditionFail", counts.conditionFail());
                row.put("transformFail", counts.transformFail());
                criteria.put(counts.criterionId(), row);
            }
            block.put("criteria", criteria);
        }
        return block;
    }

    private static Map<String, Object> costBlock(SampleSummary<?> summary) {
        Map<String, Object> block = new LinkedHashMap<>();
        Duration elapsed = summary.elapsed();
        long totalMs = elapsed.toMillis();
        block.put("totalTimeMs", totalMs);
        int total = summary.total();
        block.put("avgTimePerSampleMs", total == 0 ? 0L : totalMs / total);
        return block;
    }


    static String canonicalValueForFilename(org.javai.punit.api.FactorValue value) {
        Object yaml = value.yamlValue();
        String raw = String.valueOf(yaml);
        if (raw.length() > MAX_RAW_LENGTH) {
            String prefix = sanitise(raw.substring(0, TRUNCATED_PREFIX_LENGTH));
            return prefix + "-" + sha256HexPrefix(raw, 4);
        }
        return raw;
    }

    static String sanitise(String input) {
        StringBuilder out = new StringBuilder(input.length());
        char prev = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            char emit = (Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_') ? c : '_';
            if (emit == '_' && prev == '_') {
                continue;
            }
            out.append(emit);
            prev = emit;
        }
        return out.toString();
    }

    static String sha256HexPrefix(String input, int hexChars) {
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 must be supported by every JRE", e);
        }
        StringBuilder hex = new StringBuilder(hexChars);
        for (int i = 0; i < (hexChars + 1) / 2 && i < digest.length; i++) {
            int b = digest[i] & 0xff;
            hex.append(HEX[b >>> 4]);
            if (hex.length() < hexChars) {
                hex.append(HEX[b & 0x0f]);
            }
        }
        return hex.toString();
    }

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private static Yaml yaml() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        return new Yaml(options);
    }
}
