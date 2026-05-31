package org.mavai.punit.report.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.xml.stream.XMLStreamException;

import org.mavai.punit.api.TestIntent;
import org.mavai.punit.api.ServiceContractAttributes;
import org.mavai.punit.report.VerdictXmlWriter;
import org.mavai.punit.verdict.ExpirationStatus;
import org.mavai.punit.verdict.PUnitVerdict;
import org.mavai.punit.verdict.ProbabilisticTestVerdict;
import org.mavai.punit.verdict.ProbabilisticTestVerdict.BaselineSummary;
import org.mavai.punit.verdict.ProbabilisticTestVerdict.CostSummary;
import org.mavai.punit.verdict.ProbabilisticTestVerdict.CovariateStatus;
import org.mavai.punit.verdict.ProbabilisticTestVerdict.ExecutionSummary;
import org.mavai.punit.verdict.ProbabilisticTestVerdict.FunctionalDimension;
import org.mavai.punit.verdict.ProbabilisticTestVerdict.LatencyDimension;
import org.mavai.punit.verdict.ProbabilisticTestVerdict.Misalignment;
import org.mavai.punit.verdict.ProbabilisticTestVerdict.SpecProvenance;
import org.mavai.punit.verdict.ProbabilisticTestVerdict.StatisticalAnalysis;
import org.mavai.punit.verdict.ProbabilisticTestVerdict.TestIdentity;
import org.mavai.punit.verdict.ProbabilisticTestVerdict.Termination;
import org.mavai.punit.verdict.TerminationReason;
import org.mavai.punit.verdict.TokenMode;
import org.mavai.punit.api.spec.FailureCount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.Comparison;
import org.xmlunit.diff.ComparisonResult;
import org.xmlunit.diff.ComparisonType;
import org.xmlunit.diff.Diff;
import org.xmlunit.diff.DifferenceEvaluator;
import org.xmlunit.diff.DifferenceEvaluators;

/**
 * Cross-framework conformance test for the verdict-XML interchange
 * wire format. For each canonical fixture case published by the
 * mavai.org product family's verdict-XML reference suite, builds a
 * verdict matching the case's semantics, serialises it via
 * {@link VerdictXmlWriter}, and asserts the result is structurally
 * equivalent to the case's canonical {@code expected.xml}.
 *
 * <p>Comparison semantics:
 * <ul>
 *   <li>Whitespace, attribute order, namespace prefixes, and
 *       inter-element whitespace are ignored — covered by XMLUnit's
 *       {@code checkForSimilar} mode.</li>
 *   <li>Numerically-equivalent attribute values are treated as equal
 *       (e.g. {@code 0.9} ≡ {@code 0.9000}, {@code 1} ≡ {@code 1.0}) —
 *       this absorbs differences between fixture-canonical formatting
 *       and punit's emission convention without weakening the
 *       structural contract.</li>
 *   <li>Per-run metadata attributes (root {@code timestamp},
 *       {@code generator}, {@code correlation-id}) are ignored —
 *       they vary across runs and are not part of the structural
 *       wire contract.</li>
 *   <li>Element order is asserted strictly: where the schema
 *       declares an {@code <xs:all>} body, the canonical convention
 *       follows schema declaration order, and the writer must too.</li>
 * </ul>
 *
 * <p>The fixtures vendored under
 * {@code src/test/resources/rp07-conformance-fixtures/} are copies
 * of the orchestrator's canonical catalog fixtures. Refresh them
 * when the orchestrator publishes updates.
 */
@DisplayName("verdict-XML wire-format conformance against canonical reference fixtures")
class VerdictXmlConformanceTest {

    private static final String FIXTURE_RESOURCE_ROOT = "/rp07-conformance-fixtures/";

    private final VerdictXmlWriter writer = new VerdictXmlWriter();

    @TestFactory
    @DisplayName("emitted XML structurally equivalent to canonical expected.xml")
    Collection<DynamicTest> conformsToCanonicalFixtures() {
        return List.of(
                DynamicTest.dynamicTest("pass_functional_and_latency", () ->
                        assertConformance("pass_functional_and_latency",
                                passFunctionalAndLatency())),
                DynamicTest.dynamicTest("fail_with_statistical_analysis", () ->
                        assertConformance("fail_with_statistical_analysis",
                                failWithStatisticalAnalysis())),
                DynamicTest.dynamicTest("inconclusive_covariate_misalignment", () ->
                        assertConformance("inconclusive_covariate_misalignment",
                                inconclusiveCovariateMisalignment())),
                DynamicTest.dynamicTest("pass_baseline_derived_threshold", () ->
                        assertConformance("pass_baseline_derived_threshold",
                                passBaselineDerivedThreshold()))
        );
    }

    // ── Conformance comparison ────────────────────────────────────────────

    private void assertConformance(String caseId, ProbabilisticTestVerdict verdict)
            throws XMLStreamException, IOException {
        String emitted = serialise(verdict);
        String expected = loadExpected(caseId);

        Diff diff = DiffBuilder.compare(expected)
                .withTest(emitted)
                .ignoreWhitespace()
                .checkForSimilar()
                .withDifferenceEvaluator(conformanceEvaluator())
                .build();

        assertThat(diff.hasDifferences())
                .as("Case %s — emitted XML does not match canonical fixture:%n%s%n"
                        + "--- emitted ---%n%s%n--- expected ---%n%s",
                        caseId, diff, emitted, expected)
                .isFalse();
    }

    private String serialise(ProbabilisticTestVerdict verdict) throws XMLStreamException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.write(verdict, out);
        return out.toString(StandardCharsets.UTF_8);
    }

    private String loadExpected(String caseId) throws IOException {
        String path = FIXTURE_RESOURCE_ROOT + caseId + "/expected.xml";
        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Fixture not found on classpath: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Layered evaluator: start from the default comparison, then
     * relax for (a) per-run metadata attributes on the root, and
     * (b) numerically-equivalent attribute values.
     */
    private static DifferenceEvaluator conformanceEvaluator() {
        return DifferenceEvaluators.chain(
                DifferenceEvaluators.Default,
                VerdictXmlConformanceTest::ignorePerRunMetadata,
                VerdictXmlConformanceTest::treatNumericallyEqualAttributesAsEqual);
    }

    private static ComparisonResult ignorePerRunMetadata(Comparison c, ComparisonResult outcome) {
        if (outcome == ComparisonResult.EQUAL) return outcome;
        if (c.getType() != ComparisonType.ATTR_VALUE
                && c.getType() != ComparisonType.ATTR_NAME_LOOKUP) {
            return outcome;
        }
        String name = c.getControlDetails().getXPath();
        if (name == null) return outcome;
        if (name.endsWith("/@timestamp")
                || name.endsWith("/@generator")
                || name.endsWith("/@correlation-id")) {
            return ComparisonResult.EQUAL;
        }
        return outcome;
    }

    private static ComparisonResult treatNumericallyEqualAttributesAsEqual(
            Comparison c, ComparisonResult outcome) {
        if (outcome == ComparisonResult.EQUAL) return outcome;
        if (c.getType() != ComparisonType.ATTR_VALUE) return outcome;
        Object control = c.getControlDetails().getValue();
        Object test = c.getTestDetails().getValue();
        if (control == null || test == null) return outcome;
        try {
            double a = Double.parseDouble(control.toString());
            double b = Double.parseDouble(test.toString());
            if (a == b) {
                return ComparisonResult.EQUAL;
            }
        } catch (NumberFormatException ignored) {
            // not numeric — keep the original outcome
        }
        return outcome;
    }

    // ── Per-case verdict builders ─────────────────────────────────────────

    private static final Instant FIXED_TIMESTAMP = Instant.parse("2026-05-11T12:00:00Z");

    private ProbabilisticTestVerdict passFunctionalAndLatency() {
        TestIdentity identity = new TestIdentity(
                "ShoppingBasketTest", "meetsBaseline",
                Optional.of("shopping-basket"));
        ExecutionSummary execution = new ExecutionSummary(
                100, 100, 95, 5,
                0.9, 0.95, 200L,
                Optional.empty(), TestIntent.VERIFICATION, 0.95,
                ServiceContractAttributes.DEFAULT);
        FunctionalDimension functional = new FunctionalDimension(95, 5, 0.95);
        LatencyDimension latency = new LatencyDimension(
                95, 100, false, Optional.empty(),
                120, 340, 420, 810, -1,
                List.of());
        StatisticalAnalysis statistics = new StatisticalAnalysis(
                0.95, 0.0218, 0.8883,
                Optional.of(1.6667), Optional.of(0.9522),
                Optional.empty(), Optional.empty(),
                List.of());
        return verdict(identity, execution,
                Optional.of(functional), Optional.of(latency),
                statistics,
                CovariateStatus.allAligned(),
                Optional.of(new SpecProvenance(
                        "SLA", "", "",
                        Optional.empty(), Optional.empty())),
                new Termination(TerminationReason.COMPLETED, Optional.empty()),
                Map.of(),
                PUnitVerdict.PASS,
                "0.9500 >= 0.9000",
                "v:rp07-pass-1");
    }

    private ProbabilisticTestVerdict failWithStatisticalAnalysis() {
        TestIdentity identity = new TestIdentity(
                "ShoppingBasketTest", "meetsBaseline",
                Optional.of("shopping-basket"));
        ExecutionSummary execution = new ExecutionSummary(
                100, 100, 85, 15,
                0.9, 0.85, 180L,
                Optional.empty(), TestIntent.VERIFICATION, 0.95,
                ServiceContractAttributes.DEFAULT);
        FunctionalDimension functional = new FunctionalDimension(85, 15, 0.85);
        StatisticalAnalysis statistics = new StatisticalAnalysis(
                0.95, 0.0357, 0.7821,
                Optional.of(-1.6667), Optional.of(0.0478),
                Optional.empty(), Optional.empty(),
                List.of());
        Map<String, FailureCount> postconditionFailures = new LinkedHashMap<>();
        postconditionFailures.put("totals coherent", new FailureCount(9, List.of(
                exemplar("basket-with-discount-stack", "discount applied twice"),
                exemplar("basket-with-mixed-currency", "currency conversion off by rounding"))));
        postconditionFailures.put("discount within bounds", new FailureCount(6, List.of(
                exemplar("basket-with-vip-discount", "discount exceeds 50% cap"))));
        return verdict(identity, execution,
                Optional.of(functional), Optional.empty(),
                statistics,
                CovariateStatus.allAligned(),
                Optional.of(new SpecProvenance(
                        "SLA", "", "",
                        Optional.empty(), Optional.empty())),
                new Termination(TerminationReason.COMPLETED, Optional.empty()),
                postconditionFailures,
                PUnitVerdict.FAIL,
                "0.8500 < 0.9000",
                "v:rp07-fail-1");
    }

    private ProbabilisticTestVerdict inconclusiveCovariateMisalignment() {
        TestIdentity identity = new TestIdentity(
                "ModelTest", "shouldGenerateResponse",
                Optional.of("model-generation"));
        ExecutionSummary execution = new ExecutionSummary(
                100, 100, 93, 7,
                0.9, 0.93, 150L,
                Optional.empty(), TestIntent.VERIFICATION, 0.95,
                ServiceContractAttributes.DEFAULT);
        CovariateStatus covariates = new CovariateStatus(
                false,
                List.of(new Misalignment("region", "eu-west-1", "eu-west-2")),
                Map.of(), Map.of());
        StatisticalAnalysis statistics = new StatisticalAnalysis(
                0.95, 0.0, 0.0,
                Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(),
                List.of());
        return verdict(identity, execution,
                Optional.empty(), Optional.empty(),
                statistics,
                covariates,
                Optional.empty(),
                new Termination(TerminationReason.COMPLETED, Optional.empty()),
                Map.of(),
                PUnitVerdict.INCONCLUSIVE,
                "covariate misalignment",
                "v:rp07-inconclusive-1");
    }

    private ProbabilisticTestVerdict passBaselineDerivedThreshold() {
        TestIdentity identity = new TestIdentity(
                "ShoppingBasketTest", "meetsBaseline",
                Optional.of("shopping-basket"));
        ExecutionSummary execution = new ExecutionSummary(
                100, 100, 95, 5,
                0.9, 0.95, 200L,
                Optional.empty(), TestIntent.VERIFICATION, 0.95,
                ServiceContractAttributes.DEFAULT);
        FunctionalDimension functional = new FunctionalDimension(95, 5, 0.95);
        BaselineSummary baseline = new BaselineSummary(
                "ShoppingBasket.yaml",
                Instant.parse("2026-05-01T12:00:00Z"),
                1000, 920, 0.92, 0.9);
        StatisticalAnalysis statistics = new StatisticalAnalysis(
                0.95, 0.0218, 0.8883,
                Optional.of(1.6667), Optional.of(0.9522),
                Optional.empty(), Optional.of(baseline),
                List.of());
        SpecProvenance provenance = new SpecProvenance(
                "EMPIRICAL", "shopping-basket@1.0", "ShoppingBasket.yaml",
                Optional.empty(), Optional.empty());
        return verdict(identity, execution,
                Optional.of(functional), Optional.empty(),
                statistics,
                CovariateStatus.allAligned(),
                Optional.of(provenance),
                new Termination(TerminationReason.COMPLETED, Optional.empty()),
                Map.of(),
                PUnitVerdict.PASS,
                "0.9500 >= 0.9000",
                "v:rp07-pass-baseline-1");
    }

    // ── Construction helpers ──────────────────────────────────────────────

    private static ProbabilisticTestVerdict verdict(
            TestIdentity identity,
            ExecutionSummary execution,
            Optional<FunctionalDimension> functional,
            Optional<LatencyDimension> latency,
            StatisticalAnalysis statistics,
            CovariateStatus covariates,
            Optional<SpecProvenance> provenance,
            Termination termination,
            Map<String, FailureCount> postconditionFailures,
            PUnitVerdict punitVerdict,
            String verdictReason,
            String correlationId) {
        CostSummary cost = new CostSummary(
                0L, 0L, 0L, TokenMode.NONE,
                Optional.empty(), Optional.empty());
        return new ProbabilisticTestVerdict(
                correlationId, FIXED_TIMESTAMP,
                identity, execution,
                functional, latency, statistics,
                covariates, cost,
                Optional.empty(), provenance, termination,
                Map.of(), true, punitVerdict, verdictReason,
                postconditionFailures);
    }

    private static org.mavai.punit.api.spec.FailureExemplar exemplar(String input, String reason) {
        return new org.mavai.punit.api.spec.FailureExemplar(input, reason);
    }
}
