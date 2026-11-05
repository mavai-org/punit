package org.mavai.punit.decl.internal.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.mavai.punit.decl.BindingFactory;
import org.mavai.punit.decl.Check;
import org.mavai.punit.decl.ContractConfigurationException;
import org.mavai.punit.decl.Transform;
import org.mavai.punit.decl.internal.model.ContractDeclaration;
import org.mavai.punit.decl.internal.parser.ContractParser;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/**
 * Loader conformance against the family's published declarative-format
 * corpus — the oracle for the contract and services formats, fetched
 * from the latest mavai-R release by the {@code fetchPublishedFormats}
 * build task (local override: {@code -PformatsDir}).
 *
 * <p>The corpus is instance files with expected outcomes, classified by
 * {@code manifest.yaml}. The manifest's binding obligations are the
 * outcome (loads / refused) and, for refusals, the <em>category</em>;
 * refusal message wording is informational — each framework speaks its
 * own author vocabulary — so the mapping from each category to this
 * reader's own message lives here, in {@code CATEGORY_MESSAGES}.
 *
 * <p>The conformance load is the format layer: for a contract file,
 * parsing plus the load-time construction walk (views resolve, every
 * selection expression compiles, criteria validate eagerly);
 * service-binding resolution is environment, not format — the corpus
 * holds under an empty user-registration environment plus each case's
 * {@code requires:} list, provisioned by {@link CorpusBindings}. For a
 * services file, the full resolution against exactly those
 * registrations.
 *
 * <p>Selective assertion fails the build: the run records every case it
 * asserted and diffs that against the manifest's full obligation, and
 * the diff mechanism is itself exercised by mutation. A case this reader
 * does not yet meet may be a declared exemption ({@link #EXEMPTIONS}):
 * named with its amendment and owning bundle, asserted inverted, and
 * reported and counted on every run — never a silent omission.
 */
// mavai-ref: JVI-YM9A27C — do not remove (resolves in mavai-orchestrator)
@DisplayName("Declarative-format conformance (published corpus)")
class FormatConformanceTest {

    private static final Path FORMATS = Path.of(System.getProperty("punit.formats.dir"));
    private static final Path CORPUS = FORMATS.resolve("corpus");

    private static final Map<String, Map<String, Object>> ENTRIES = manifestEntries();

    /**
     * The corpus's required registrations (the manifest's
     * {@code requires:} names). Steppers and the pass-rate scorer are
     * reader built-ins; the transform, check, and user type below are
     * the corpus's only host-code requirements.
     */
    static class CorpusBindings {

        @Transform("basket-judge")
        Map<String, Object> basketJudge(String response) {
            return Map.of("namesUnique", "true");
        }

        @Check("looks-right")
        boolean looksRight(String subject) {
            return true;
        }

        @BindingFactory("triage")
        Function<String, String> triage(String tone, double certainty) {
            return request -> "category: billing";
        }
    }

    /**
     * category → a fragment of THIS reader's refusal message.
     * Informational by the manifest's contract (categories bind, wording
     * does not); the fragment check is what makes a wrong-reason refusal
     * fail rather than pass.
     */
    private static final Map<String, String> CATEGORY_MESSAGES = Map.ofEntries(
            // ── mavai-contract/1 ──────────────────────────────────
            Map.entry("format-identifier", "`format:` must be 'mavai-contract/1'"),
            Map.entry("missing-required-key", "missing required key"),
            Map.entry("unknown-key", "not part of mavai-contract/1"),
            Map.entry("reserved-seam-key", "reserved by the mavai contract format"),
            Map.entry("withdrawn-sizing-key", "the contract carries the claim"),
            Map.entry("withdrawn-run-mode-key", "`kind:` was withdrawn"),
            Map.entry("threshold-range", "`threshold:` must be a number in (0, 1)"),
            Map.entry("threshold-empirical-reserved", "`threshold: empirical` is reserved"),
            Map.entry("tolerate-with-threshold", "belongs on an empirical criterion"),
            Map.entry("criterion-confidence-with-threshold",
                    "`confidence:` on a criterion belongs to an empirical"),
            Map.entry("threshold-origin-vocabulary", "provenance category"),
            Map.entry("criterion-without-form", "declares no postcondition form"),
            Map.entry("postcondition-form-cardinality", "exactly one form"),
            Map.entry("optional-slack-malformed",
                    "`optional-slack:` takes a non-negative whole count"),
            Map.entry("optional-operand", "required is the default, not a spelling"),
            Map.entry("optional-on-parses", "hard-fails the trial regardless"),
            Map.entry("default-view-unresolvable", "no resolvable default view"),
            Map.entry("path-on-non-string-form", "string and value-comparison forms only"),
            Map.entry("parses-with-in", "takes no `in:`"),
            Map.entry("parses-in-expected", "criterion-level form"),
            Map.entry("raw-view-declared", "reserved name of the untransformed response"),
            Map.entry("inputs-empty", "`inputs:` must be a non-empty list"),
            Map.entry("criteria-empty", "`criteria:` must be a non-empty list"),
            Map.entry("input-list-mixed", "not a mix"),
            Map.entry("input-part-unknown", "unknown input part"),
            Map.entry("input-entry-extra-key", "single-key mapping"),
            Map.entry("intent-vocabulary", "unknown `intent"),
            Map.entry("confidence-range", "`confidence:` must be a number in (0, 1)"),
            Map.entry("latency-shape-contradiction", "contradictory"),
            Map.entry("latency-without-bounds", "declares no bounds"),
            Map.entry("latency-ceiling-not-positive", "positive whole number of milliseconds"),
            Map.entry("latency-percentile-vocabulary", "unknown percentile"),
            Map.entry("view-undeclared", "names an undeclared view"),
            Map.entry("parses-view-undeclared", "`parses:` references a declared view"),
            Map.entry("expected-requires-single-criterion", "exactly one criteria entry"),
            Map.entry("selection-expression-invalid", "is not a valid JSONPath"),
            Map.entry("latency-ceilings-decreasing", "non-decreasing"),
            Map.entry("input-file-unreadable", "cannot read input file"),
            Map.entry("set-form-without-path", "requires a `path:` under a declared view"),
            Map.entry("value-operand-malformed", "takes a number or a numeric string"),
            Map.entry("is-null-operand", "takes the literal `true`"),
            Map.entry("set-operand-empty", "non-empty list of scalar values"),
            Map.entry("is-operand-not-boolean", "`is:` takes a boolean"),
            Map.entry("roots-block-malformed", "`roots:` must be a non-empty mapping"),
            Map.entry("roots-name-shape", "must match [a-z][a-z0-9-]*"),
            Map.entry("roots-value-malformed", "root `corpus:`"),
            Map.entry("roots-reference-undeclared", "references an undeclared root"),
            Map.entry("roots-dead-declaration", "referenced by nothing in the file"),
            Map.entry("roots-directory-missing", "not an existing directory"),
            Map.entry("set-of-without-optional", "— say that"),
            Map.entry("set-of-min-present-malformed", "a bare fraction is never guessed at"),
            Map.entry("set-of-lists-overlap", "in both `required:` and `optional:`"),
            Map.entry("set-of-min-present-bounds", "the `optional:` list's distinct size"),
            Map.entry("zero-failures-with-threshold",
                    "a zero-failures criterion has no rate to require"),
            Map.entry("zero-failures-with-tolerate", "no baseline risk claim to protect"),
            Map.entry("zero-failures-with-confidence", "no confidence level to set"),
            Map.entry("zero-failures-not-boolean", "`zero-failures:` takes a boolean"),
            // ── mavai-services/1 ──────────────────────────────────
            Map.entry("services-format-identifier", "`format:` must be 'mavai-services/1'"),
            Map.entry("services-block-missing", "`services:` must be a non-empty mapping"),
            Map.entry("configuration-missing", "a `configuration:` block is required"),
            // punit refuses a parameter beside `configuration:` as an
            // unknown definition key — one refusal, both categories; the
            // category still binds, the wording is punit's own.
            Map.entry("parameter-outside-configuration",
                    "a definition holds `type:` and its `configuration:` block"),
            Map.entry("definition-unknown-key",
                    "a definition holds `type:` and its `configuration:` block"),
            Map.entry("top-level-unknown-key", "the services file has unknown key"),
            Map.entry("lm-system-prompt-missing", "`system-prompt:` is required"),
            Map.entry("lm-configuration-unknown-key",
                    "language-model configuration has unknown key"),
            Map.entry("lm-provider-vocabulary", "unknown `provider"),
            Map.entry("lm-thinking-vocabulary", "`thinking:` must be one of"),
            Map.entry("lm-top-p-range", "`top-p:` must be a number in (0, 1]"),
            Map.entry("lm-prompt-caching-type", "`prompt-caching:` must be a boolean"),
            Map.entry("lm-max-tokens-range", "`max-tokens:` must be a whole number"),
            Map.entry("lm-deadline-range", "`deadline-ms:` must be a whole number"),
            Map.entry("lm-capabilities-vocabulary", "unknown capability"),
            Map.entry("explorations-empty", "`explorations:` must be a non-empty list"),
            Map.entry("exploration-entry-null-value", "declares no value"),
            Map.entry("optimization-entry-unknown-key", "an optimization entry accepts"),
            Map.entry("optimization-stepper-missing", "`stepper:` is required"),
            Map.entry("optimization-max-iterations-missing", "`max-iterations:` is required"),
            Map.entry("optimization-max-iterations-not-positive",
                    "`max-iterations:` must be a positive integer"),
            Map.entry("optimization-id-shape", "letters, digits, dots"),
            Map.entry("type-unresolved", "unknown `type:"),
            Map.entry("exploration-duplicate-point", "distinct covariate point"),
            Map.entry("optimization-duplicate-id", "is already used"),
            Map.entry("optimization-id-required-when-multiple", "`id:` is required when"),
            Map.entry("optimization-initial-restates-baseline", "merely restates"),
            Map.entry("lm-system-prompt-file-malformed", "file form is `{file: <path>}`"),
            Map.entry("services-roots-reference-undeclared", "references an undeclared root"));

    /**
     * A declared exemption (the family's format-conformance rule on
     * declared exemptions, 2026-11-03): one corpus case this reader does not yet meet,
     * naming its category ({@code (loads)} for a must-load case), the
     * format amendment it belongs to, the open bundle that owes it, and
     * the date it was declared.
     */
    record Exemption(String category, String amendment, String bundle, String declared) {}

    /**
     * The declared exemptions — corpus obligations this reader does not
     * yet meet. An exempted case is asserted <em>inverted</em>: the day
     * the reader starts meeting it, its test fails and the entry must be
     * removed, so the list only shrinks, truthfully. Exemptions are
     * reported and counted on every run ({@link #exemptionReport}),
     * never silent; any failure not declared here fails the build; and a
     * reader with exemptions does not claim full conformance at its pin.
     * Never add an entry without its owning bundle.
     */
    static final Map<String, Exemption> EXEMPTIONS = Map.ofEntries(
            Map.entry("services-run-controls.yaml", new Exemption("(loads)", "mavai-services/1 run controls (amendment 2026-10-13)", "work/2026-10-13-punit-declarative-resource-controls", "2026-11-03")),
            Map.entry("services-run-controls-bare.yaml", new Exemption("(loads)", "mavai-services/1 run controls (amendment 2026-10-13)", "work/2026-10-13-punit-declarative-resource-controls", "2026-11-03")),
            Map.entry("services-run-controls-pacing.yaml", new Exemption("(loads)", "mavai-services/1 run controls (amendment 2026-10-13)", "work/2026-10-13-punit-declarative-resource-controls", "2026-11-03")),
            Map.entry("services-run-controls-budget.yaml", new Exemption("(loads)", "mavai-services/1 run controls (amendment 2026-10-13)", "work/2026-10-13-punit-declarative-resource-controls", "2026-11-03")),
            Map.entry("pacing-empty.yaml", new Exemption("pacing-empty", "mavai-services/1 run controls (amendment 2026-10-13)", "work/2026-10-13-punit-declarative-resource-controls", "2026-11-03")),
            Map.entry("pacing-rate-not-positive.yaml", new Exemption("pacing-rate-not-positive", "mavai-services/1 run controls (amendment 2026-10-13)", "work/2026-10-13-punit-declarative-resource-controls", "2026-11-03")),
            Map.entry("pacing-unknown-key.yaml", new Exemption("pacing-unknown-key", "mavai-services/1 run controls (amendment 2026-10-13)", "work/2026-10-13-punit-declarative-resource-controls", "2026-11-03")),
            Map.entry("budget-limit-missing.yaml", new Exemption("budget-limit-missing", "mavai-services/1 run controls (amendment 2026-10-13)", "work/2026-10-13-punit-declarative-resource-controls", "2026-11-03")),
            Map.entry("budget-limit-not-positive.yaml", new Exemption("budget-limit-not-positive", "mavai-services/1 run controls (amendment 2026-10-13)", "work/2026-10-13-punit-declarative-resource-controls", "2026-11-03")),
            Map.entry("budget-on-exhausted-vocabulary.yaml", new Exemption("budget-on-exhausted-vocabulary", "mavai-services/1 run controls (amendment 2026-10-13)", "work/2026-10-13-punit-declarative-resource-controls", "2026-11-03")),
            Map.entry("budget-unknown-key.yaml", new Exemption("budget-unknown-key", "mavai-services/1 run controls (amendment 2026-10-13)", "work/2026-10-13-punit-declarative-resource-controls", "2026-11-03")),
            Map.entry("budget-in-configuration.yaml", new Exemption("budget-in-configuration", "mavai-services/1 run controls (amendment 2026-10-13)", "work/2026-10-13-punit-declarative-resource-controls", "2026-11-03")),
            Map.entry("budget-in-exploration.yaml", new Exemption("budget-in-exploration", "mavai-services/1 run controls (amendment 2026-10-13)", "work/2026-10-13-punit-declarative-resource-controls", "2026-11-03")),
            Map.entry("budget-in-optimization-initial.yaml", new Exemption("budget-in-optimization-initial", "mavai-services/1 run controls (amendment 2026-10-13)", "work/2026-10-13-punit-declarative-resource-controls", "2026-11-03")),
            Map.entry("stepper-linear-sweep-zero-step.yaml", new Exemption("stepper-linear-sweep-zero-step", "mavai-services/1 built-in steppers (amendment 2026-10-13)", "work/2026-10-13-punit-numeric-steppers", "2026-11-03")),
            Map.entry("stepper-refining-grid-bounds.yaml", new Exemption("stepper-refining-grid-bounds", "mavai-services/1 built-in steppers (amendment 2026-10-13)", "work/2026-10-13-punit-numeric-steppers", "2026-11-03")),
            Map.entry("stepper-refining-grid-min-step.yaml", new Exemption("stepper-refining-grid-min-step", "mavai-services/1 built-in steppers (amendment 2026-10-13)", "work/2026-10-13-punit-numeric-steppers", "2026-11-03")),
            Map.entry("stepper-config-unknown-key.yaml", new Exemption("stepper-config-unknown-key", "mavai-services/1 built-in steppers (amendment 2026-10-13)", "work/2026-10-13-punit-numeric-steppers", "2026-11-03")),
            Map.entry("stepper-refining-grid-prefer.yaml", new Exemption("stepper-refining-grid-prefer", "mavai-services/1 built-in steppers (amendment 2026-10-13)", "work/2026-10-13-punit-numeric-steppers", "2026-11-03")),
            Map.entry("stepper-refining-grid-objective.yaml", new Exemption("stepper-refining-grid-objective", "mavai-services/1 built-in steppers (amendment 2026-10-13)", "work/2026-10-13-punit-numeric-steppers", "2026-11-03")),
            Map.entry("stepper-refining-grid-scorer.yaml", new Exemption("stepper-refining-grid-scorer", "mavai-services/1 built-in steppers (amendment 2026-10-13)", "work/2026-10-13-punit-numeric-steppers", "2026-11-03")));

    // ── The drive ─────────────────────────────────────────────────

    /** Drives the format-layer load for one corpus case; throws its refusal. */
    private static void load(Map<String, Object> entry) {
        String directory = "loads".equals(entry.get("outcome")) ? "valid" : "invalid";
        Path path = CORPUS.resolve(directory).resolve((String) entry.get("file"));
        String text = read(path);
        BindingsRegistry registry = BindingsRegistry.of(CorpusBindings.class);
        if ("mavai-contract/1".equals(entry.get("format"))) {
            // Parse, then the load-time construction of views and criteria
            // — where transform names resolve and every selection
            // expression compiles eagerly. Service-binding resolution and
            // run-mode sizing are deliberately not driven: they are
            // environment and invocation concerns, outside the format
            // layer the corpus binds.
            ContractDeclaration declaration = ContractParser.parse(text, path);
            StockViews views = new StockViews(declaration, registry);
            CriteriaCompiler compiler = new CriteriaCompiler(
                    declaration, views, new AtomicReference<>(), Map.of(), null, registry);
            compiler.validateEagerly();
            compiler.compile();
        } else {
            ServicesResolver.resolve(FormatConformanceTest.class, path, registry,
                    ServicesResolver.Posture.STRICT);
        }
    }

    /** One case's binding obligations, or its truthfully-inverted declared exemption. */
    private static void assertCase(Map<String, Object> entry) {
        assertCase(entry, EXEMPTIONS);
    }

    private static void assertCase(Map<String, Object> entry, Map<String, Exemption> exemptions) {
        String file = (String) entry.get("file");
        Exemption exemption = exemptions.get(file);
        if (exemption == null) {
            meetObligation(entry);
            return;
        }
        try {
            meetObligation(entry);
        } catch (AssertionError expectedlyUnmet) {
            return;
        }
        fail(file + ": obligation now met — remove its declared exemption ("
                + exemption.amendment() + ", owed by " + exemption.bundle() + ")");
    }

    /**
     * The exemption report every conformance run prints: the count of
     * asserted obligations, the count of declared exemptions, and each
     * exemption named with its category, amendment, owning bundle and
     * date — the declared-exemptions rule's "reported and counted".
     */
    static String exemptionReport(Map<String, Exemption> exemptions) {
        StringBuilder report = new StringBuilder("[PUNIT] format conformance: ")
                .append(ENTRIES.size() - exemptions.size()).append(" obligations met, ")
                .append(exemptions.size()).append(" declared exemptions");
        if (!exemptions.isEmpty()) {
            report.append(" — not full conformance at this pin");
        }
        exemptions.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> report.append("\n  exempt ").append(e.getKey())
                        .append(" [").append(e.getValue().category()).append("] ")
                        .append(e.getValue().amendment()).append(", owed by ")
                        .append(e.getValue().bundle()).append(", declared ")
                        .append(e.getValue().declared()));
        return report.toString();
    }

    /** One case's binding obligations: the outcome, and the category's refusal. */
    private static void meetObligation(Map<String, Object> entry) {
        if ("loads".equals(entry.get("outcome"))) {
            try {
                load(entry);
            } catch (ContractConfigurationException refusal) {
                fail(entry.get("file") + ": must load, but was refused: "
                        + refusal.getMessage());
            }
            return;
        }
        String category = (String) entry.get("category");
        String fragment = CATEGORY_MESSAGES.get(category);
        assertThat(fragment)
                .as("category '" + category + "' has a message mapping")
                .isNotNull();
        assertThatThrownBy(() -> load(entry))
                .as(entry.get("file") + ": refused as " + category)
                .isInstanceOf(ContractConfigurationException.class)
                .hasMessageContaining(fragment);
    }

    // ── The obligations ───────────────────────────────────────────

    @TestFactory
    @DisplayName("every corpus case meets its binding obligation")
    Stream<DynamicTest> corpusCases() {
        return ENTRIES.entrySet().stream().map(entry -> DynamicTest.dynamicTest(
                entry.getKey(), () -> assertCase(entry.getValue())));
    }

    @Test
    @DisplayName("every manifest obligation is asserted — the obligation diff")
    void everyManifestObligationIsAsserted() {
        // The dynamic tests above are generated from the manifest itself,
        // so a skipped case means a deselected test, not a silent gap;
        // this diff makes the obligation explicit and machine-checked in
        // one place.
        Set<String> asserted = new LinkedHashSet<>();
        for (Map.Entry<String, Map<String, Object>> entry : ENTRIES.entrySet()) {
            assertCase(entry.getValue());
            asserted.add(entry.getKey());
        }
        diffAgainstObligations(asserted);
        System.out.println(exemptionReport(EXEMPTIONS));
    }

    @Test
    @DisplayName("every declared exemption names a manifest case and its owning bundle")
    void everyExemptionIsWellFormed() {
        assertThat(ENTRIES.keySet()).containsAll(EXEMPTIONS.keySet());
        for (Map.Entry<String, Exemption> e : EXEMPTIONS.entrySet()) {
            Map<String, Object> entry = ENTRIES.get(e.getKey());
            String expected = "loads".equals(entry.get("outcome"))
                    ? "(loads)" : (String) entry.get("category");
            assertThat(e.getValue().category()).as(e.getKey()).isEqualTo(expected);
            assertThat(e.getValue().bundle()).as(e.getKey()).startsWith("work/");
            assertThat(e.getValue().amendment()).as(e.getKey()).isNotBlank();
            assertThat(e.getValue().declared()).as(e.getKey()).matches("\\d{4}-\\d{2}-\\d{2}");
        }
    }

    @Test
    @DisplayName("declared exemptions are reported and counted — never silent")
    void exemptionsAreReported() {
        String report = exemptionReport(EXEMPTIONS);
        assertThat(report).contains(EXEMPTIONS.size() + " declared exemptions");
        for (Map.Entry<String, Exemption> e : EXEMPTIONS.entrySet()) {
            assertThat(report).contains("exempt " + e.getKey())
                    .contains(e.getValue().bundle());
        }
        if (!EXEMPTIONS.isEmpty()) {
            assertThat(report).contains("not full conformance at this pin");
        }
    }

    @Test
    @DisplayName("an undeclared failure fails the build — the exemption list, mutation-tested")
    void undeclaredFailureFailsTheBuild() {
        if (EXEMPTIONS.isEmpty()) {
            return;
        }
        String withdrawn = EXEMPTIONS.keySet().stream().sorted().findFirst().orElseThrow();
        Map<String, Exemption> mutilated = new LinkedHashMap<>(EXEMPTIONS);
        mutilated.remove(withdrawn);
        // Withdrawn from the list, the unmet case is asserted directly and
        // fails — on its outcome, or on its category's missing mapping.
        assertThatThrownBy(() -> assertCase(ENTRIES.get(withdrawn), mutilated))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("no zero-failures case is exempted — the amendment this pin brings is met")
    void zeroFailuresCasesAreAsserted() {
        assertThat(EXEMPTIONS.keySet()).noneMatch(file -> file.contains("zero-failures"));
        assertThat(ENTRIES.keySet()).filteredOn(file -> file.contains("zero-failures")).hasSize(8);
    }

    @Test
    @DisplayName("selective assertion fails the build — the diff, mutation-tested")
    void selectiveAssertionFailsTheBuild() {
        String dropped = ENTRIES.keySet().iterator().next();
        Set<String> mutilated = new LinkedHashSet<>(ENTRIES.keySet());
        mutilated.remove(dropped);
        assertThatThrownBy(() -> diffAgainstObligations(mutilated))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(dropped);
    }

    @Test
    @DisplayName("the manifest mirrors the vendored tree")
    void manifestMirrorsTheVendoredTree() {
        Set<String> onDisk = new LinkedHashSet<>();
        for (String directory : List.of("valid", "invalid")) {
            try (Stream<Path> files = Files.list(CORPUS.resolve(directory))) {
                files.filter(p -> p.getFileName().toString().endsWith(".yaml"))
                        .forEach(p -> onDisk.add(p.getFileName().toString()));
            } catch (IOException error) {
                throw new UncheckedIOException(error);
            }
        }
        assertThat(onDisk).isEqualTo(ENTRIES.keySet());
    }

    @Test
    @DisplayName("every category has a case and a message mapping")
    void everyCategoryHasACaseAndAMessageMapping() {
        Map<String, Object> manifest = manifest();
        @SuppressWarnings("unchecked")
        Set<String> manifestCategories =
                ((Map<String, Object>) manifest.get("categories")).keySet();
        Set<String> exercised = new LinkedHashSet<>();
        Set<String> met = new LinkedHashSet<>();
        for (Map<String, Object> entry : ENTRIES.values()) {
            if ("refused".equals(entry.get("outcome"))) {
                exercised.add((String) entry.get("category"));
                if (!EXEMPTIONS.containsKey((String) entry.get("file"))) {
                    met.add((String) entry.get("category"));
                }
            }
        }
        assertThat(exercised).isEqualTo(manifestCategories);
        // A category whose every case is a declared exemption has no
        // message yet — its mapping arrives with the bundle that retires
        // the exemptions.
        assertThat(CATEGORY_MESSAGES.keySet()).isEqualTo(met);
    }

    // ── Plumbing ──────────────────────────────────────────────────

    /** The selective-assertion gate: every manifest case must have been asserted. */
    private static void diffAgainstObligations(Set<String> asserted) {
        Set<String> missing = new LinkedHashSet<>(ENTRIES.keySet());
        missing.removeAll(asserted);
        assertThat(missing)
                .as("format-conformance run did not assert every manifest obligation")
                .isEmpty();
    }

    private static Map<String, Map<String, Object>> manifestEntries() {
        Map<String, Object> manifest = manifest();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> corpus = (List<Map<String, Object>>) manifest.get("corpus");
        Map<String, Map<String, Object>> entries = new LinkedHashMap<>();
        for (Map<String, Object> entry : corpus) {
            entries.put((String) entry.get("file"), entry);
        }
        return entries;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> manifest() {
        return (Map<String, Object>) new Load(LoadSettings.builder().build())
                .loadFromString(read(FORMATS.resolve("manifest.yaml")));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }
}
