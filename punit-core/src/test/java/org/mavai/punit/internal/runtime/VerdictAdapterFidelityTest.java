package org.mavai.punit.internal.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.mavai.punit.api.TestIntent;
import org.mavai.punit.api.FactorBundle;
import org.mavai.punit.api.LatencyResult;
import org.mavai.punit.api.covariate.CovariateAlignment;
import org.mavai.punit.api.spec.CriterionResult;
import org.mavai.punit.api.spec.CriterionRole;
import org.mavai.punit.api.spec.EngineRunSummary;
import org.mavai.punit.api.spec.EvaluatedCriterion;
import org.mavai.punit.api.spec.FailureCount;
import org.mavai.punit.api.spec.FailureExemplar;
import org.mavai.punit.api.spec.PerCriterionEvaluation;
import org.mavai.punit.api.spec.ProbabilisticTestResult;
import org.mavai.punit.api.spec.Verdict;
import org.mavai.punit.verdict.TokenMode;
import org.mavai.punit.verdict.TerminationReason;
import org.mavai.punit.verdict.PUnitVerdict;
import org.mavai.punit.verdict.ProbabilisticTestVerdict;
import org.mavai.punit.verdict.RunMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Living documentation: each test pins one row of the verdict-XML
 * field-reference table in {@code USER-GUIDE.md} Part 11 to an
 * executable assertion. If a behaviour shifts and the doc gets out
 * of sync, this test fails first.
 *
 * <p>Distinct from {@link VerdictAdapterTest} which exercises
 * field-level mapping; this fidelity suite asserts what's
 * <em>defaulted</em>, not just what's mapped.
 */
@DisplayName("VerdictAdapter fidelity (USER-GUIDE Part 11 table)")
class VerdictAdapterFidelityTest {

    private record Factors() { }

    @Nested
    @DisplayName("source-derived fields")
    class SourceDerived {

        @Test
        @DisplayName("identity use-case-id falls back to className when use-case-id absent")
        void identityFallback() {
            ProbabilisticTestVerdict verdict = VerdictAdapter.adapt(
                    minimalResult(),
                    RunMetadata.of("com.example.MyTest", "shouldRun"));

            assertThat(verdict.identity().serviceContractId()).isEmpty();
            // VerdictXmlWriter falls back to className when serviceContractId is absent;
            // this is verified in VerdictXmlWriterTest. Here we pin the absence.
        }

        @Test
        @DisplayName("execution planned-samples comes from EngineRunSummary.plannedSamples")
        void plannedSamplesFromEngine() {
            ProbabilisticTestResult result = withEngine(new EngineRunSummary(
                    150, 100, 95, 5, 1500L, 0L, 0,
                    LatencyResult.empty(),
                    org.mavai.punit.api.spec.TerminationReason.TIME_BUDGET,
                    0.95, Optional.empty()));

            ProbabilisticTestVerdict verdict = adapt(result);

            assertThat(verdict.execution().plannedSamples()).isEqualTo(150);
            assertThat(verdict.execution().samplesExecuted()).isEqualTo(100);
        }

        @Test
        @DisplayName("provenance spec-filename comes from EngineRunSummary.baselineFilename")
        void provenanceSpecFilenameFromEngine() {
            ProbabilisticTestResult base = withCriterionDetail(
                    Map.of("threshold", 0.99, "origin", "EMPIRICAL"));
            ProbabilisticTestResult result = new ProbabilisticTestResult(
                    base.verdict(), base.factors(), base.criterionResults(),
                    base.intent(), base.warnings(), base.covariates(),
                    base.contractRef(), base.failuresByPostcondition(),
                    new EngineRunSummary(
                            10, 10, 10, 0, 100L, 0L, 0,
                            LatencyResult.empty(),
                            org.mavai.punit.api.spec.TerminationReason.COMPLETED,
                            0.95,
                            Optional.of("payment-gateway-1fbf-54c6-86a6.yaml")),
                    PerCriterionEvaluation.empty());

            ProbabilisticTestVerdict verdict = adapt(result);

            assertThat(verdict.provenance()).isPresent();
            assertThat(verdict.provenance().get().specFilename())
                    .isEqualTo("payment-gateway-1fbf-54c6-86a6.yaml");
        }

        @Test
        @DisplayName("postcondition-failures populated from result.failuresByPostcondition")
        void postconditionFailuresPopulated() {
            LinkedHashMap<String, FailureCount> hist = new LinkedHashMap<>();
            hist.put("Valid JSON", new FailureCount(3, List.of(
                    new FailureExemplar("input-1", "missing actions"))));
            ProbabilisticTestResult result = new ProbabilisticTestResult(
                    Verdict.FAIL, FactorBundle.of(new Factors()),
                    List.of(), TestIntent.VERIFICATION, List.of(),
                    CovariateAlignment.none(), Optional.empty(), hist,
                    EngineRunSummary.empty(), PerCriterionEvaluation.empty());

            ProbabilisticTestVerdict verdict = adapt(result);

            assertThat(verdict.postconditionFailures())
                    .containsKey("Valid JSON")
                    .extractingByKey("Valid JSON")
                    .extracting(FailureCount::count).isEqualTo(3);
        }

        @Test
        @DisplayName("latency observed populated when sampleCount > 0")
        void latencyObservedPopulated() {
            LatencyResult lat = new LatencyResult(
                    Duration.ofMillis(100), Duration.ofMillis(200),
                    Duration.ofMillis(300), Duration.ofMillis(500),
                    50);
            ProbabilisticTestResult result = withEngine(new EngineRunSummary(
                    50, 50, 50, 0, 1000L, 0L, 0, lat,
                    org.mavai.punit.api.spec.TerminationReason.COMPLETED,
                    0.95, Optional.empty()));

            ProbabilisticTestVerdict verdict = adapt(result);

            assertThat(verdict.latency()).isPresent();
            assertThat(verdict.latency().get().p50Ms()).isEqualTo(100L);
            assertThat(verdict.latency().get().p95Ms()).isEqualTo(300L);
        }
    }

    @Nested
    @DisplayName("defaulted (no source analogue)")
    class Defaulted {

        @Test
        @DisplayName("execution warmup not surfaced — ServiceContractAttributes default warmup=0")
        void warmupDefaulted() {
            ProbabilisticTestVerdict verdict = adapt(minimalResult());

            assertThat(verdict.execution().warmup()).isZero();
            assertThat(verdict.execution().serviceContractAttributes())
                    .isEqualTo(org.mavai.punit.api.ServiceContractAttributes.DEFAULT);
        }

        @Test
        @DisplayName("execution applied-multiplier absent")
        void appliedMultiplierAbsent() {
            ProbabilisticTestVerdict verdict = adapt(minimalResult());

            assertThat(verdict.execution().appliedMultiplier()).isEmpty();
        }

        @Test
        @DisplayName("cost budgets default to 0 (unlimited) and TokenMode.NONE")
        void costBudgetsDefaulted() {
            ProbabilisticTestResult result = withEngine(new EngineRunSummary(
                    10, 10, 10, 0, 100L, 1234L, 0,
                    LatencyResult.empty(),
                    org.mavai.punit.api.spec.TerminationReason.COMPLETED,
                    0.95, Optional.empty()));

            ProbabilisticTestVerdict verdict = adapt(result);

            assertThat(verdict.cost().methodTokensConsumed()).isEqualTo(1234L);
            assertThat(verdict.cost().methodTimeBudgetMs()).isZero();
            assertThat(verdict.cost().methodTokenBudget()).isZero();
            assertThat(verdict.cost().tokenMode()).isEqualTo(TokenMode.NONE);
            assertThat(verdict.cost().classBudget()).isEmpty();
            assertThat(verdict.cost().suiteBudget()).isEmpty();
        }

        @Test
        @DisplayName("pacing absent")
        void pacingAbsent() {
            ProbabilisticTestVerdict verdict = adapt(minimalResult());

            assertThat(verdict.pacing()).isEmpty();
        }

        @Test
        @DisplayName("correlation-id auto-generated when metadata supplies none")
        void correlationIdGenerated() {
            ProbabilisticTestVerdict verdict = VerdictAdapter.adapt(
                    minimalResult(),
                    RunMetadata.of("com.example.MyTest", "shouldRun"));

            assertThat(verdict.correlationId()).isNotEmpty().startsWith("v:");
        }

        @Test
        @DisplayName("junitPassed defaulted to true (no JUnit-pass concept on the result)")
        void junitPassedDefaulted() {
            ProbabilisticTestVerdict verdict = adapt(minimalResult());

            assertThat(verdict.junitPassed()).isTrue();
        }
    }

    @Nested
    @DisplayName("termination reason mapping (rows of the table)")
    class TerminationMapping {

        @Test
        @DisplayName("COMPLETED → COMPLETED")
        void completed() {
            assertMapping(
                    org.mavai.punit.api.spec.TerminationReason.COMPLETED,
                    TerminationReason.COMPLETED);
        }

        @Test
        @DisplayName("TIME_BUDGET → METHOD_TIME_BUDGET_EXHAUSTED")
        void timeBudget() {
            assertMapping(
                    org.mavai.punit.api.spec.TerminationReason.TIME_BUDGET,
                    TerminationReason.METHOD_TIME_BUDGET_EXHAUSTED);
        }

        @Test
        @DisplayName("TOKEN_BUDGET → METHOD_TOKEN_BUDGET_EXHAUSTED")
        void tokenBudget() {
            assertMapping(
                    org.mavai.punit.api.spec.TerminationReason.TOKEN_BUDGET,
                    TerminationReason.METHOD_TOKEN_BUDGET_EXHAUSTED);
        }

        private void assertMapping(
                org.mavai.punit.api.spec.TerminationReason reason,
                TerminationReason expectedCore) {
            ProbabilisticTestResult result = withEngine(new EngineRunSummary(
                    1, 1, 1, 0, 1L, 0L, 0,
                    LatencyResult.empty(), reason, 0.95, Optional.empty()));

            ProbabilisticTestVerdict verdict = adapt(result);

            assertThat(verdict.termination().reason()).isEqualTo(expectedCore);
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private static ProbabilisticTestVerdict adapt(ProbabilisticTestResult result) {
        return VerdictAdapter.adapt(
                result,
                RunMetadata.of("com.example.MyTest", "shouldRun"));
    }

    private static ProbabilisticTestResult minimalResult() {
        return new ProbabilisticTestResult(
                Verdict.PASS, FactorBundle.of(new Factors()),
                List.of(), TestIntent.VERIFICATION, List.of(),
                CovariateAlignment.none(), Optional.empty(), Map.of(),
                EngineRunSummary.empty(), PerCriterionEvaluation.empty());
    }

    private static ProbabilisticTestResult withEngine(EngineRunSummary engine) {
        return new ProbabilisticTestResult(
                Verdict.PASS, FactorBundle.of(new Factors()),
                List.of(), TestIntent.VERIFICATION, List.of(),
                CovariateAlignment.none(), Optional.empty(), Map.of(),
                engine, PerCriterionEvaluation.empty());
    }

    private static ProbabilisticTestResult withCriterionDetail(Map<String, Object> detail) {
        CriterionResult cr = new CriterionResult(
                "bernoulli-pass-rate", Verdict.PASS,
                "stub", detail);
        return new ProbabilisticTestResult(
                Verdict.PASS, FactorBundle.of(new Factors()),
                List.of(new EvaluatedCriterion(cr, CriterionRole.REQUIRED)),
                TestIntent.VERIFICATION, List.of(),
                CovariateAlignment.none(), Optional.empty(), Map.of(),
                EngineRunSummary.empty(), PerCriterionEvaluation.empty());
    }
}
