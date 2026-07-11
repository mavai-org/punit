package org.mavai.punit.statistics.conformance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestFactory;

import java.util.Collection;
import java.util.List;

/**
 * Conformance against the composed decision rules (statistical companion
 * §3.4 regression procedure, §3.2/§3.6 compliance procedure) — the
 * {@code regression_decision} scenario suite, evaluated through the
 * production verdict path rather than a test-side recomposition of the
 * formulae.
 *
 * <p>For a baseline-derived test the binding decision artefact is the
 * integer cutoff: {@code p* = WilsonLower(baseline rate, n_test, α)},
 * {@code c = ⌈n_test · p*⌉}, PASS iff the raw observed success count
 * {@code K ≥ c}; the real-valued threshold, displayed rate {@code c/n},
 * and achieved size are report obligations. For a declared threshold the
 * test sample's own Wilson lower bound must clear it. The suite's
 * conflation-detector pair shares one observation across both procedures
 * with opposite verdicts, so applying either procedure's rule on the
 * other's path fails the suite even when every component computation is
 * arithmetically conformant.
 *
 * <p>The check bodies live in {@link ConformanceCatalog}, shared with the
 * manifest-driven coverage check ({@code ConformanceCoverageTest}); this
 * class provides the per-case test reporting.
 *
 * @see <a href="https://github.com/mavai-org/mavai-R">mavai-R</a>
 */
@DisplayName("Decision-rule conformance (mavai-R regression_decision)")
class DecisionRuleConformanceTest {

    private static Collection<DynamicTest> dynamicTests(List<ConformanceCatalog.CaseCheck> checks) {
        return checks.stream()
                .map(check -> DynamicTest.dynamicTest(
                        check.caseName(),
                        () -> check.check().run(ConformanceRecorder.NO_OP)))
                .toList();
    }

    @Nested
    @DisplayName("regression_decision — binding decision artefacts from the production deriver")
    class DerivationArtefacts {

        @TestFactory
        @DisplayName("Threshold, integer cutoff, displayed rate, and achieved size")
        Collection<DynamicTest> cases() {
            return dynamicTests(ConformanceCatalog.regressionDecisionDerivation());
        }
    }

    @Nested
    @DisplayName("regression_decision — verdicts through the production verdict path")
    class ProductionVerdicts {

        @TestFactory
        @DisplayName("Engine-judged verdict equals the oracle's, per procedure")
        Collection<DynamicTest> cases() {
            return dynamicTests(ConformanceCatalog.regressionDecisionProductionVerdicts());
        }
    }
}
