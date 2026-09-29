package org.mavai.punit.statistics.conformance;

import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestFactory;

/**
 * Per-case conformance tests against the mavai-R fixtures, one nested
 * group per suite. The check bodies live in {@link ConformanceCatalog},
 * shared with the manifest-driven coverage check
 * ({@code ConformanceCoverageTest}); this class provides the per-case
 * reporting.
 *
 * @see <a href="https://github.com/mavai-org/mavai-R">mavai-R</a>
 */
@DisplayName("Conformance (mavai-R)")
class ConformanceTest {

    private static Collection<DynamicTest> dynamicTests(List<ConformanceCatalog.CaseCheck> checks) {
        return checks.stream()
                .map(check -> DynamicTest.dynamicTest(
                        check.caseName(),
                        () -> check.check().run(ConformanceRecorder.NO_OP)))
                .toList();
    }

    @Nested
    @DisplayName("wilson_ci — the descriptive two-sided Wilson interval")
    class WilsonCi {
        @TestFactory
        Collection<DynamicTest> cases() {
            return dynamicTests(ConformanceCatalog.wilsonCi());
        }
    }

    @Nested
    @DisplayName("wilson_lower — the descriptive one-sided Wilson lower bound")
    class WilsonLower {
        @TestFactory
        Collection<DynamicTest> cases() {
            return dynamicTests(ConformanceCatalog.wilsonLower());
        }
    }

    @Nested
    @DisplayName("threshold_derivation — the regression cutoff and the threshold-first inversion")
    class ThresholdDerivation {
        @TestFactory
        Collection<DynamicTest> cases() {
            return dynamicTests(ConformanceCatalog.thresholdDerivation());
        }
    }

    @Nested
    @DisplayName("regression_decision — regression/fisher through the engine")
    class RegressionDecision {
        @TestFactory
        Collection<DynamicTest> cases() {
            return dynamicTests(ConformanceCatalog.regressionDecision());
        }
    }

    @Nested
    @DisplayName("compliance_decision — compliance/exact-binomial through the engine")
    class ComplianceDecision {
        @TestFactory
        Collection<DynamicTest> cases() {
            return dynamicTests(ConformanceCatalog.complianceDecision());
        }
    }

    @Nested
    @DisplayName("feasibility — can a normative design pass at all")
    class Feasibility {
        @TestFactory
        Collection<DynamicTest> cases() {
            return dynamicTests(ConformanceCatalog.feasibility());
        }
    }

    @Nested
    @DisplayName("power_analysis — exact power and sizing against the rules")
    class PowerAnalysis {
        @TestFactory
        Collection<DynamicTest> cases() {
            return dynamicTests(ConformanceCatalog.powerAnalysis());
        }
    }

    @Nested
    @DisplayName("risk_driven_sizing — design and resolved sizing of the regression rule")
    class RiskDrivenSizing {
        @TestFactory
        Collection<DynamicTest> cases() {
            return dynamicTests(ConformanceCatalog.riskDrivenSizing());
        }
    }

    @Nested
    @DisplayName("latency_percentile — nearest-rank percentile, mean and maximum")
    class LatencyPercentile {
        @TestFactory
        Collection<DynamicTest> cases() {
            return dynamicTests(ConformanceCatalog.latencyPercentile());
        }
    }

    @Nested
    @DisplayName("latency_percentile_minimums — non-degeneracy and precedence existence")
    class LatencyPercentileMinimums {
        @TestFactory
        Collection<DynamicTest> cases() {
            return dynamicTests(ConformanceCatalog.latencyPercentileMinimums());
        }
    }

    @Nested
    @DisplayName("latency_threshold — latency/precedence")
    class LatencyThreshold {
        @TestFactory
        Collection<DynamicTest> cases() {
            return dynamicTests(ConformanceCatalog.latencyThreshold());
        }
    }

    @Nested
    @DisplayName("latency_compliance_decision — latency/compliance-exact-binomial")
    class LatencyComplianceDecision {
        @TestFactory
        Collection<DynamicTest> cases() {
            return dynamicTests(ConformanceCatalog.latencyComplianceDecision());
        }
    }

    @Nested
    @DisplayName("verdict — criteria, the refusal, and the test verdict")
    class VerdictSuite {
        @TestFactory
        Collection<DynamicTest> cases() {
            return dynamicTests(ConformanceCatalog.verdict());
        }
    }
}
