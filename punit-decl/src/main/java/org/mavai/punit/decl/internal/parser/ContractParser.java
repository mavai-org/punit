package org.mavai.punit.decl.internal.parser;

import static org.mavai.punit.decl.internal.parser.Yaml.fail;
import static org.mavai.punit.decl.internal.parser.Yaml.isUnitIntervalRate;
import static org.mavai.punit.decl.internal.parser.Yaml.requireMapping;
import static org.mavai.punit.decl.internal.parser.Yaml.requireString;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.mavai.punit.decl.ContractConfigurationException;
import org.mavai.punit.decl.internal.model.ContractDeclaration;
import org.mavai.punit.decl.internal.model.CriterionDeclaration;
import org.mavai.punit.decl.internal.model.DeclaredIntent;
import org.mavai.punit.decl.internal.model.FormDeclaration;
import org.mavai.punit.decl.internal.model.InputDeclaration;
import org.mavai.punit.decl.internal.model.LatencyDeclaration;
import org.mavai.punit.statistics.StatisticalDefaults;

/**
 * The {@code mavai-contract/1} parser: reads a contract file's text
 * into a structurally validated {@link ContractDeclaration}, refusing
 * every malformation, reserved construct, and contradiction at load
 * time — before any invocation, so a configuration defect never costs
 * a sample.
 */
public final class ContractParser {

    static final String SEAM_POINTER = "reserved by the mavai contract format for a future "
            + "version — see the format's extension seams documentation";

    private static final Set<String> TOP_LEVEL_KEYS = Set.of(
            "format", "contract", "service", "transforms", "inputs", "criteria",
            "intent", "confidence", "latency");
    private static final Set<String> RESERVED_TOP_LEVEL = Set.of("facets", "covariates", "budget");

    private ContractParser() {}

    /** Parses a contract file's text with no source location (file-sourced inputs refuse). */
    public static ContractDeclaration parse(String text) {
        return parse(text, null);
    }

    /**
     * Parses and structurally validates a contract file's text.
     *
     * @param text the file's text
     * @param sourcePath the file the text was read from — file-sourced
     *     input parts resolve relative to its directory — or
     *     {@code null}
     * @throws ContractConfigurationException on any malformation,
     *     reserved construct, or contradiction — always before any
     *     invocation
     */
    public static ContractDeclaration parse(String text, Path sourcePath) {
        Map<String, Object> data = requireMapping(Yaml.parse(text, "contract file"), "the contract file");
        checkTopLevelKeys(data);

        Object intentValue = data.getOrDefault("intent", DeclaredIntent.VERIFICATION.key());
        DeclaredIntent intent = DeclaredIntent.forKey(String.valueOf(intentValue));
        if (intent == null) {
            throw fail("unknown `intent: " + intentValue + "` — expected verification or smoke");
        }

        Map<String, String> views = parseTransforms(data);
        Path baseDir = sourcePath != null ? sourcePath.getParent() : null;
        List<InputDeclaration> inputs = InputsParser.parse(data.get("inputs"), views, baseDir);

        Object criteriaValue = data.get("criteria");
        if (!(criteriaValue instanceof List<?> entries) || entries.isEmpty()) {
            throw fail("`criteria:` must be a non-empty list of criterion entries");
        }
        List<CriterionDeclaration> criteria = new ArrayList<>();
        for (int index = 0; index < entries.size(); index++) {
            criteria.add(CriteriaParser.parse(entries.get(index), index, views));
        }
        Set<String> names = new HashSet<>();
        for (CriterionDeclaration criterion : criteria) {
            if (!names.add(criterion.name())) {
                throw fail("criterion names must be unique within the contract");
            }
        }
        boolean hasExpectations = inputs.stream().anyMatch(InputDeclaration::hasExpectations);
        if (hasExpectations && criteria.size() != 1) {
            throw fail("per-input `expected:` entries require exactly one criteria entry — with "
                    + "several criteria their owner would be ambiguous; move the expectations "
                    + "into the criterion entries");
        }
        for (CriterionDeclaration criterion : criteria) {
            if (criterion.forms().isEmpty()) {
                throw fail("criterion '" + criterion.name() + "' declares no postcondition form — "
                        + "every criterion declares at least one form of its own; per-input "
                        + "`expected:` entries supplement a criterion's forms, never replace them");
            }
        }

        boolean confidenceDeclared = data.containsKey("confidence");
        double confidence = StatisticalDefaults.DEFAULT_CONFIDENCE;
        if (confidenceDeclared) {
            Object confidenceValue = data.get("confidence");
            if (!isUnitIntervalRate(confidenceValue)) {
                throw fail("`confidence:` must be a number in (0, 1)");
            }
            confidence = ((Number) confidenceValue).doubleValue();
        }

        LatencyDeclaration latency = LatencyParser.parse(data);

        return new ContractDeclaration(
                requireString(data, "contract"),
                requireString(data, "service"),
                views,
                inputs,
                criteria,
                intent,
                confidence,
                confidenceDeclared,
                latency,
                sourcePath);
    }

    /** Reads and parses a contract file from disk. */
    public static ContractDeclaration load(Path path) {
        String text;
        try {
            text = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw fail("cannot read contract file " + path + ": " + error.getMessage(), error);
        }
        return parse(text, path);
    }

    private static void checkTopLevelKeys(Map<String, Object> data) {
        for (String key : data.keySet()) {
            switch (key) {
                case "kind" -> throw fail("`kind:` was withdrawn — the run mode is the "
                        + "invocation's: `assertPasses()` under `@ProbabilisticTest` judges; "
                        + "`run()` under `@Experiment` records");
                case "samples" -> throw fail("`samples:` is not a contract key — the contract "
                        + "carries the claim, the invocation carries the budget. Size the run "
                        + "with `.samples(N)` or `-Dpunit.samples.<contract-name>=N` (a test "
                        + "without either runs at the derived minimum; a measure requires one)");
                case "samples-per-config" -> throw fail("`samples-per-config:` is not a contract "
                        + "key — the contract carries the claim, the invocation carries the "
                        + "budget. Size the exploration with `.samplesPerConfig(N)` (default: 5 "
                        + "samples per configuration)");
                default -> {
                    if (RESERVED_TOP_LEVEL.contains(key)) {
                        throw fail("`" + key + ":` is " + SEAM_POINTER);
                    }
                    if (!TOP_LEVEL_KEYS.contains(key)) {
                        throw fail("unknown key `" + key + ":` — not part of "
                                + ContractDeclaration.FORMAT_IDENTIFIER);
                    }
                }
            }
        }
        for (String required : List.of("format", "contract", "service", "inputs", "criteria")) {
            if (!data.containsKey(required)) {
                throw fail("missing required key `" + required + ":`");
            }
        }
        if (!ContractDeclaration.FORMAT_IDENTIFIER.equals(data.get("format"))) {
            throw fail("`format:` must be '" + ContractDeclaration.FORMAT_IDENTIFIER + "', got '"
                    + data.get("format") + "'");
        }
    }

    private static Map<String, String> parseTransforms(Map<String, Object> data) {
        if (!data.containsKey("transforms")) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> block = requireMapping(data.get("transforms"), "`transforms:`");
        if (block.isEmpty()) {
            throw fail("`transforms:` must be a non-empty mapping when declared");
        }
        Map<String, String> views = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : block.entrySet()) {
            if (entry.getKey().equals(FormDeclaration.RAW_VIEW)) {
                throw fail("`raw` is the reserved name of the untransformed response and cannot "
                        + "be declared as a view");
            }
            if (!(entry.getValue() instanceof String transformation) || transformation.isEmpty()) {
                throw fail("view '" + entry.getKey() + "': the transformation must be a name — a "
                        + "stock one (json, xml, yaml) or a transformation registered in code");
            }
            views.put(entry.getKey(), transformation);
        }
        return views;
    }
}
