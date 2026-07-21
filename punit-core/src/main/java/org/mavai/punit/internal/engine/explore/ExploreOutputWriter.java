package org.mavai.punit.internal.engine.explore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.mavai.punit.api.FactorBundle;
import org.mavai.punit.api.spec.PerConfigSummary;
import org.mavai.punit.api.spec.SampleSummary;
import org.mavai.punit.internal.engine.emit.EmittedKeys;
import org.mavai.punit.internal.engine.emit.FailureDistributions;
import org.mavai.punit.internal.engine.emit.LatencySection;
import org.mavai.punit.internal.engine.emit.ResultProjections;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Serialises one EXPLORE configuration's outcome to the mavai
 * family's canonical exploration interchange schema and resolves
 * its readable-stem filename.
 *
 * <p>Both methods are pure — the writer performs no I/O. The
 * {@link org.mavai.punit.internal.runtime.ExploreEmitter EXPLORE emitter}
 * orchestrates persistence (writing to disk or to an in-memory
 * sink for tests).
 *
 * <p>Emits the mavai family's canonical exploration interchange
 * schema: {@code factors:} block in factor-record declaration order,
 * descriptive statistics only (no inferential statistics), small
 * sample counts accepted without warning. The {@code configuration:}
 * field carries the configuration's display name — the same
 * human-readable stem {@link #filenameFor(FactorBundle)} derives for
 * the filename — so consumers identify configurations from the
 * document body, never by parsing filenames.
 */
// mavai-ref: JVI-8CHB31R — do not remove (resolves in mavai-orchestrator)
public final class ExploreOutputWriter {

    /** Schema-version value carried in every emitted file. */
    public static final String SCHEMA_VERSION = "mavai-explore-1";

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
     *                  {@code serviceContractId:} field).
     * @param factorBundle the configuration's factor bundle (becomes
     *                     the {@code factors:} block and, via
     *                     {@link #filenameFor(FactorBundle)}, the
     *                     {@code configuration:} display name).
     * @param entry the per-config summary plus planned sample count.
     * @return YAML matching the canonical exploration interchange schema.
     */
    public String writeYaml(String serviceContractId, FactorBundle factorBundle, PerConfigSummary<?, ?> entry) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("serviceContractId", serviceContractId);
        root.put("configuration", filenameFor(factorBundle));
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
            block.put(EmittedKeys.bound(e.name()), e.value().yamlValue());
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
        // Sequence of {condition, count} entries — first-failing-
        // condition attribution over the trials, so entry counts sum
        // to the failures total. Never a mapping keyed by free-text
        // identity (artefact key discipline).
        block.put("failureDistribution", FailureDistributions.fromTrials(summary.trials()));
        // Per-criterion decomposition — required by the interchange
        // schema, one entry per declared criterion (including the
        // single-criterion case; a contract cannot run without at
        // least one declared criterion). Mirrors the per-criterion
        // shape that MEASURE writes into baselines so a reader can
        // compare explore vs measure emissions of the same contract
        // criterion-by-criterion. conditionFail / transformFail are
        // permitted informational extras beyond the canonical fields.
        Map<String, Object> criteria = new LinkedHashMap<>();
        for (org.mavai.punit.api.spec.CriterionSampleCounts counts
                : summary.criterionSampleCounts()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("observedPassRate", counts.observedPassRate());
            row.put("pass", counts.pass());
            row.put("fail", counts.fail());
            row.put("conditionFail", counts.conditionFail());
            row.put("transformFail", counts.transformFail());
            criteria.put(EmittedKeys.bound(counts.criterionId()), row);
        }
        block.put("criteria", criteria);
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


    static String canonicalValueForFilename(org.mavai.punit.api.FactorValue value) {
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
