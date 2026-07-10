package org.mavai.punit.statistics.conformance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestFactory;

import java.util.Collection;
import java.util.List;

/**
 * Conformance tests for binomial proportion statistics: Wilson score intervals,
 * threshold derivation, power analysis, feasibility, and verdict evaluation.
 *
 * <p>The check bodies live in {@link ConformanceCatalog}, shared with the
 * manifest-driven coverage check ({@code ConformanceCoverageTest}); this
 * class provides the per-case test reporting.
 *
 * @see <a href="https://github.com/mavai-org/mavai-R">mavai-R</a>
 */
@DisplayName("Proportion conformance (mavai-R)")
class ProportionConformanceTest {

    private static Collection<DynamicTest> dynamicTests(List<ConformanceCatalog.CaseCheck> checks) {
        return checks.stream()
                .map(check -> DynamicTest.dynamicTest(
                        check.caseName(),
                        () -> check.check().run(ConformanceRecorder.NO_OP)))
                .toList();
    }

    @Nested
    @DisplayName("wilson_ci")
    class WilsonCi {

        @TestFactory
        @DisplayName("Two-sided Wilson score confidence intervals")
        Collection<DynamicTest> cases() {
            return dynamicTests(ConformanceCatalog.wilsonCi());
        }
    }

    @Nested
    @DisplayName("wilson_lower")
    class WilsonLower {

        @TestFactory
        @DisplayName("One-sided Wilson score lower bound")
        Collection<DynamicTest> cases() {
            return dynamicTests(ConformanceCatalog.wilsonLower());
        }
    }

    @Nested
    @DisplayName("threshold_derivation")
    class ThresholdDerivation {

        @TestFactory
        @DisplayName("Sample-size-first (with binding decision artefacts) and threshold-first derivation")
        Collection<DynamicTest> cases() {
            return dynamicTests(ConformanceCatalog.thresholdDerivation());
        }
    }

    @Nested
    @DisplayName("power_analysis")
    class PowerAnalysis {

        @TestFactory
        @DisplayName("Sample size calculation via power analysis")
        Collection<DynamicTest> cases() {
            return dynamicTests(ConformanceCatalog.powerAnalysis());
        }
    }

    @Nested
    @DisplayName("feasibility")
    class Feasibility {

        @TestFactory
        @DisplayName("Verification feasibility checking")
        Collection<DynamicTest> cases() {
            return dynamicTests(ConformanceCatalog.feasibility());
        }
    }

    @Nested
    @DisplayName("verdict")
    class Verdict {

        @TestFactory
        @DisplayName("Pass/fail verdict evaluation with z-test statistics")
        Collection<DynamicTest> cases() {
            return dynamicTests(ConformanceCatalog.verdict());
        }
    }
}
