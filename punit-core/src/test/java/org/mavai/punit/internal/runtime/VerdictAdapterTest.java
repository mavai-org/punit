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
import org.mavai.punit.api.covariate.CovariateProfile;
import org.mavai.punit.api.spec.CriterionResult;
import org.mavai.punit.api.spec.CriterionRole;
import org.mavai.punit.api.spec.EngineRunSummary;
import org.mavai.punit.api.spec.EvaluatedCriterion;
import org.mavai.punit.api.spec.FailureCount;
import org.mavai.punit.api.spec.FailureExemplar;
import org.mavai.punit.api.spec.InconclusiveReasons;
import org.mavai.punit.api.spec.PerCriterionEvaluation;
import org.mavai.punit.api.spec.ProbabilisticTestResult;
import org.mavai.punit.api.spec.Verdict;
import org.mavai.punit.verdict.TerminationReason;
import org.mavai.punit.verdict.TokenMode;
import org.mavai.punit.verdict.PUnitVerdict;
import org.mavai.punit.verdict.ProbabilisticTestVerdict;
import org.mavai.punit.verdict.RunMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("VerdictAdapter")
class VerdictAdapterTest {

    private record Factors() { }

    @Nested
    @DisplayName("identity and envelope")
    class IdentityAndEnvelope {

        @Test
        @DisplayName("populates className/methodName/serviceContractId from metadata")
        void identityFromMetadata() {
            ProbabilisticTestVerdict verdict = VerdictAdapter.adapt(
                    minimalResult(Verdict.PASS),
                    new RunMetadata(
                            "com.example.MyTest", "shouldPass",
                            Optional.of("payment-gateway"),
                            Optional.of("v:abc123"),
                            Map.of("region", "EU")));

            assertThat(verdict.identity().className()).isEqualTo("com.example.MyTest");
            assertThat(verdict.identity().methodName()).isEqualTo("shouldPass");
            assertThat(verdict.identity().serviceContractId()).contains("payment-gateway");
            assertThat(verdict.correlationId()).isEqualTo("v:abc123");
            assertThat(verdict.environmentMetadata()).containsEntry("region", "EU");
        }

        @Test
        @DisplayName("absent correlationId triggers builder's UUID-fragment generation")
        void absentCorrelationIdGetsGenerated() {
            ProbabilisticTestVerdict verdict = VerdictAdapter.adapt(
                    minimalResult(Verdict.PASS),
                    RunMetadata.of("com.example.MyTest", "shouldPass"));

            assertThat(verdict.correlationId()).isNotEmpty().startsWith("v:");
        }
    }

    @Nested
    @DisplayName("execution and intent")
    class ExecutionAndIntent {

        @Test
        @DisplayName("threads samples/successes/failures/elapsed and computes observed pass rate")
        void executionFields() {
            ProbabilisticTestResult result = resultWithEngineSummary(
                    Verdict.PASS,
                    new EngineRunSummary(
                            100, 95, 90, 5, 1500L, 0L, 0,
                            LatencyResult.empty(),
                            org.mavai.punit.api.spec.TerminationReason.COMPLETED,
                            0.95,
                            Optional.empty()));

            ProbabilisticTestVerdict verdict = adapt(result);

            assertThat(verdict.execution().plannedSamples()).isEqualTo(100);
            assertThat(verdict.execution().samplesExecuted()).isEqualTo(95);
            assertThat(verdict.execution().successes()).isEqualTo(90);
            assertThat(verdict.execution().failures()).isEqualTo(5);
            assertThat(verdict.execution().elapsedMs()).isEqualTo(1500L);
            // observed = 90/95 ≈ 0.9474
            assertThat(verdict.execution().observedPassRate())
                    .isCloseTo(0.9474, org.assertj.core.data.Offset.offset(0.001));
            assertThat(verdict.execution().resolvedConfidence()).isEqualTo(0.95);
        }

        @Test
        @DisplayName("threshold pulled from PassRate criterion's detail map")
        void thresholdFromCriterionDetail() {
            ProbabilisticTestResult result = resultWithCriteria(
                    Verdict.PASS,
                    Map.of("threshold", 0.99, "origin", "SLA"));

            ProbabilisticTestVerdict verdict = adapt(result);

            assertThat(verdict.execution().minPassRate()).isEqualTo(0.99);
        }

        @Test
        @DisplayName("intent is preserved")
        void intentPreserved() {
            ProbabilisticTestResult result = new ProbabilisticTestResult(
                    Verdict.PASS, FactorBundle.of(new Factors()),
                    List.of(), TestIntent.SMOKE, List.of(),
                    CovariateAlignment.none(), Optional.empty(), Map.of(),
                    EngineRunSummary.empty(), PerCriterionEvaluation.empty());

            ProbabilisticTestVerdict verdict = adapt(result);

            assertThat(verdict.execution().intent()).isEqualTo(TestIntent.SMOKE);
        }
    }

    @Nested
    @DisplayName("verdict mapping")
    class VerdictMapping {

        @Test
        @DisplayName("PASS verdict with aligned covariates produces PUnitVerdict.PASS")
        void passVerdict() {
            ProbabilisticTestVerdict verdict = adapt(minimalResult(Verdict.PASS));

            assertThat(verdict.punitVerdict()).isEqualTo(PUnitVerdict.PASS);
        }

        @Test
        @DisplayName("FAIL verdict with aligned covariates produces PUnitVerdict.FAIL")
        void failVerdict() {
            ProbabilisticTestVerdict verdict = adapt(minimalResult(Verdict.FAIL));

            assertThat(verdict.punitVerdict()).isEqualTo(PUnitVerdict.FAIL);
        }

        @Test
        @DisplayName("INCONCLUSIVE with the 'no baseline available' criterion discriminant "
                + "renders that reason on the verdict (verdict-reason vocabulary)")
        void inconclusiveNoBaselineAvailable() {
            ProbabilisticTestVerdict verdict = adapt(inconclusiveWithReason(
                    InconclusiveReasons.NO_BASELINE_AVAILABLE));

            assertThat(verdict.punitVerdict()).isEqualTo(PUnitVerdict.INCONCLUSIVE);
            assertThat(verdict.verdictReason()).isEqualTo("no baseline available");
        }

        @Test
        @DisplayName("INCONCLUSIVE with the 'baseline inputs mismatch' discriminant renders "
                + "that reason on the verdict")
        void inconclusiveBaselineInputsMismatch() {
            ProbabilisticTestVerdict verdict = adapt(inconclusiveWithReason(
                    InconclusiveReasons.BASELINE_INPUTS_MISMATCH));

            assertThat(verdict.verdictReason()).isEqualTo("baseline inputs mismatch");
        }

        @Test
        @DisplayName("INCONCLUSIVE with the 'baseline sample size exceeded' discriminant "
                + "renders that reason on the verdict")
        void inconclusiveBaselineSampleSizeExceeded() {
            ProbabilisticTestVerdict verdict = adapt(inconclusiveWithReason(
                    InconclusiveReasons.BASELINE_SAMPLE_SIZE_EXCEEDED));

            assertThat(verdict.verdictReason()).isEqualTo("baseline sample size exceeded");
        }

        @Test
        @DisplayName("INCONCLUSIVE with the 'insufficient evidence' discriminant renders "
                + "that reason on the verdict")
        void inconclusiveInsufficientEvidenceExplicit() {
            // Explicit discriminant — the criterion stamped INSUFFICIENT_EVIDENCE
            // rather than leaving the detail map untouched. Same behaviour as
            // the no-discriminant fallback, but exercised via the named path.
            ProbabilisticTestVerdict verdict = adapt(inconclusiveWithReason(
                    InconclusiveReasons.INSUFFICIENT_EVIDENCE));

            assertThat(verdict.verdictReason()).isEqualTo("insufficient evidence");
        }

        @Test
        @DisplayName("INCONCLUSIVE with no discriminant on the criterion-result detail "
                + "map falls back to 'insufficient evidence' — the catch-all "
                + "(backward compat for criteria pre-dating the vocabulary expansion)")
        void inconclusiveNoDiscriminantFallsBack() {
            // No criterion result on the test result — the builder's
            // criterionResults list is empty.
            ProbabilisticTestVerdict verdict = adapt(minimalResult(Verdict.INCONCLUSIVE));

            assertThat(verdict.punitVerdict()).isEqualTo(PUnitVerdict.INCONCLUSIVE);
            assertThat(verdict.verdictReason()).isEqualTo("insufficient evidence");
        }

        @Test
        @DisplayName("INCONCLUSIVE with aligned covariates produces PUnitVerdict.INCONCLUSIVE "
                + "with reason 'insufficient evidence' (verdict-fidelity regression)")
        void inconclusiveSurvivesAlignedCovariates() {
            // The reproducer: a criterion that returns Verdict.INCONCLUSIVE
            // for a non-covariate reason (no baseline, sample-size violation,
            // identity mismatch) must not silently collapse to FAIL just
            // because the run's covariates happen to be aligned. The
            // punit verdict must be consistent with the statistical
            // analysis and the verdict reason must be consistent with
            // the verdict enum.
            ProbabilisticTestVerdict verdict = adapt(minimalResult(Verdict.INCONCLUSIVE));

            assertThat(verdict.covariates().aligned())
                    .as("covariate alignment is the *common* case the bug masked")
                    .isTrue();
            assertThat(verdict.punitVerdict()).isEqualTo(PUnitVerdict.INCONCLUSIVE);
            assertThat(verdict.verdictReason())
                    .as("verdict reason consistent with verdict enum value")
                    .isEqualTo("insufficient evidence");
        }

        @Test
        @DisplayName("INCONCLUSIVE with covariate misalignment produces PUnitVerdict.INCONCLUSIVE")
        void inconclusiveVerdict() {
            CovariateProfile baseline = CovariateProfile.of(Map.of("model", "gpt-4"));
            CovariateProfile observed = CovariateProfile.of(Map.of("model", "gpt-4o"));
            CovariateAlignment misaligned = CovariateAlignment.compute(observed, baseline);

            ProbabilisticTestResult result = new ProbabilisticTestResult(
                    Verdict.INCONCLUSIVE, FactorBundle.of(new Factors()),
                    List.of(), TestIntent.VERIFICATION, List.of(),
                    misaligned, Optional.empty(), Map.of(),
                    EngineRunSummary.empty(), PerCriterionEvaluation.empty());

            ProbabilisticTestVerdict verdict = adapt(result);

            assertThat(verdict.punitVerdict()).isEqualTo(PUnitVerdict.INCONCLUSIVE);
            assertThat(verdict.covariates().aligned()).isFalse();
            assertThat(verdict.covariates().misalignments()).hasSize(1);
            assertThat(verdict.covariates().misalignments().get(0).covariateKey())
                    .isEqualTo("model");
        }
    }

    @Nested
    @DisplayName("latency dimension")
    class LatencyDimension {

        @Test
        @DisplayName("populates observed percentiles when latency has samples (≥ 100 → all four emit)")
        void populatesPercentiles() {
            LatencyResult lat = new LatencyResult(
                    Duration.ofMillis(120), Duration.ofMillis(340),
                    Duration.ofMillis(420), Duration.ofMillis(810),
                    100);
            ProbabilisticTestResult result = resultWithEngineSummary(
                    Verdict.PASS,
                    new EngineRunSummary(
                            100, 100, 100, 0, 1000L, 0L, 0,
                            lat,
                            org.mavai.punit.api.spec.TerminationReason.COMPLETED,
                            0.95,
                            Optional.empty()));

            ProbabilisticTestVerdict verdict = adapt(result);

            assertThat(verdict.latency()).isPresent();
            assertThat(verdict.latency().get().p50Ms()).isEqualTo(120L);
            assertThat(verdict.latency().get().p90Ms()).isEqualTo(340L);
            assertThat(verdict.latency().get().p95Ms()).isEqualTo(420L);
            assertThat(verdict.latency().get().p99Ms()).isEqualTo(810L);
            assertThat(verdict.latency().get().maxMs()).isEqualTo(-1L);
        }

        @Test
        @DisplayName("minimum-samples rule: at n=50 contributing, p99Ms is the omit-sentinel")
        void omitsP99BelowThreshold() {
            // 50 contributing samples → p50 (≥ 1) and p90 (≥ 10)
            // and p95 (≥ 20) all emit; p99 (≥ 100) does not.
            LatencyResult lat = new LatencyResult(
                    Duration.ofMillis(120), Duration.ofMillis(340),
                    Duration.ofMillis(420), Duration.ofMillis(810),
                    50);
            ProbabilisticTestResult result = resultWithEngineSummary(
                    Verdict.PASS,
                    new EngineRunSummary(
                            50, 50, 50, 0, 1000L, 0L, 0,
                            lat,
                            org.mavai.punit.api.spec.TerminationReason.COMPLETED,
                            0.95,
                            Optional.empty()));

            ProbabilisticTestVerdict verdict = adapt(result);

            assertThat(verdict.latency()).isPresent();
            assertThat(verdict.latency().get().p50Ms()).isEqualTo(120L);
            assertThat(verdict.latency().get().p90Ms()).isEqualTo(340L);
            assertThat(verdict.latency().get().p95Ms()).isEqualTo(420L);
            // p99 must be the -1L sentinel — renderer translates to "-".
            assertThat(verdict.latency().get().p99Ms()).isEqualTo(-1L);
        }

        @Test
        @DisplayName("omits latency when sampleCount is zero")
        void skipsLatencyWhenEmpty() {
            ProbabilisticTestVerdict verdict = adapt(minimalResult(Verdict.PASS));

            assertThat(verdict.latency()).isEmpty();
        }
    }

    @Nested
    @DisplayName("postcondition failures")
    class PostconditionFailures {

        @Test
        @DisplayName("threads histogram through to verdict")
        void histogramThreaded() {
            LinkedHashMap<String, FailureCount> hist = new LinkedHashMap<>();
            hist.put("Response not empty", new FailureCount(2, List.of(
                    new FailureExemplar("instr-1", "blank"))));
            hist.put("Valid JSON", new FailureCount(8, List.of()));

            ProbabilisticTestResult result = new ProbabilisticTestResult(
                    Verdict.FAIL, FactorBundle.of(new Factors()),
                    List.of(), TestIntent.VERIFICATION, List.of(),
                    CovariateAlignment.none(), Optional.empty(), hist,
                    EngineRunSummary.empty(), PerCriterionEvaluation.empty());

            ProbabilisticTestVerdict verdict = adapt(result);

            assertThat(verdict.postconditionFailures()).hasSize(2);
            assertThat(verdict.postconditionFailures()).containsKeys(
                    "Response not empty", "Valid JSON");
            assertThat(verdict.postconditionFailures().get("Valid JSON").count())
                    .isEqualTo(8);
        }

        @Test
        @DisplayName("empty histogram yields empty postconditionFailures map")
        void emptyHistogram() {
            ProbabilisticTestVerdict verdict = adapt(minimalResult(Verdict.PASS));

            assertThat(verdict.postconditionFailures()).isEmpty();
        }
    }

    @Nested
    @DisplayName("provenance")
    class Provenance {

        @Test
        @DisplayName("populates from contractRef + threshold origin + baseline filename")
        void populatesProvenance() {
            ProbabilisticTestResult base = resultWithCriteria(
                    Verdict.PASS,
                    Map.of("threshold", 0.99, "origin", "SLA"));
            ProbabilisticTestResult result = new ProbabilisticTestResult(
                    base.verdict(), base.factors(), base.criterionResults(),
                    base.intent(), base.warnings(),
                    base.covariates(),
                    Optional.of("Payment Provider SLA v2.3, Section 4.1"),
                    base.failuresByPostcondition(),
                    new EngineRunSummary(
                            50, 50, 50, 0, 100L, 0L, 0,
                            LatencyResult.empty(),
                            org.mavai.punit.api.spec.TerminationReason.COMPLETED,
                            0.95,
                            Optional.of("payment-gateway-1fbf-54c6-86a6.yaml")),
                    PerCriterionEvaluation.empty());

            ProbabilisticTestVerdict verdict = adapt(result);

            assertThat(verdict.provenance()).isPresent();
            assertThat(verdict.provenance().get().thresholdOriginName()).isEqualTo("SLA");
            assertThat(verdict.provenance().get().contractRef())
                    .isEqualTo("Payment Provider SLA v2.3, Section 4.1");
            assertThat(verdict.provenance().get().specFilename())
                    .isEqualTo("payment-gateway-1fbf-54c6-86a6.yaml");
        }

        @Test
        @DisplayName("omits provenance when no origin, contractRef, or filename")
        void omitsWhenAbsent() {
            ProbabilisticTestVerdict verdict = adapt(minimalResult(Verdict.PASS));

            assertThat(verdict.provenance()).isEmpty();
        }
    }

    @Nested
    @DisplayName("termination reason mapping")
    class TerminationMapping {

        @Test
        @DisplayName("COMPLETED → COMPLETED")
        void completed() {
            ProbabilisticTestVerdict verdict = adapt(resultWithTermination(
                    org.mavai.punit.api.spec.TerminationReason.COMPLETED));
            assertThat(verdict.termination().reason()).isEqualTo(TerminationReason.COMPLETED);
        }

        @Test
        @DisplayName("TIME_BUDGET → METHOD_TIME_BUDGET_EXHAUSTED")
        void timeBudget() {
            ProbabilisticTestVerdict verdict = adapt(resultWithTermination(
                    org.mavai.punit.api.spec.TerminationReason.TIME_BUDGET));
            assertThat(verdict.termination().reason())
                    .isEqualTo(TerminationReason.METHOD_TIME_BUDGET_EXHAUSTED);
        }

        @Test
        @DisplayName("TOKEN_BUDGET → METHOD_TOKEN_BUDGET_EXHAUSTED")
        void tokenBudget() {
            ProbabilisticTestVerdict verdict = adapt(resultWithTermination(
                    org.mavai.punit.api.spec.TerminationReason.TOKEN_BUDGET));
            assertThat(verdict.termination().reason())
                    .isEqualTo(TerminationReason.METHOD_TOKEN_BUDGET_EXHAUSTED);
        }

        @Test
        @DisplayName("IMPOSSIBILITY → IMPOSSIBILITY")
        void impossibility() {
            ProbabilisticTestVerdict verdict = adapt(resultWithTermination(
                    org.mavai.punit.api.spec.TerminationReason.IMPOSSIBILITY));
            assertThat(verdict.termination().reason())
                    .isEqualTo(TerminationReason.IMPOSSIBILITY);
        }

        @Test
        @DisplayName("SUCCESS_GUARANTEED → SUCCESS_GUARANTEED")
        void successGuaranteed() {
            ProbabilisticTestVerdict verdict = adapt(resultWithTermination(
                    org.mavai.punit.api.spec.TerminationReason.SUCCESS_GUARANTEED));
            assertThat(verdict.termination().reason())
                    .isEqualTo(TerminationReason.SUCCESS_GUARANTEED);
        }
    }

    @Nested
    @DisplayName("cost")
    class Cost {

        @Test
        @DisplayName("threads tokensConsumed; budgets default to zero / TokenMode.NONE")
        void costFromEngineSummary() {
            ProbabilisticTestResult result = resultWithEngineSummary(
                    Verdict.PASS,
                    new EngineRunSummary(
                            10, 10, 10, 0, 100L, 1234L, 0,
                            LatencyResult.empty(),
                            org.mavai.punit.api.spec.TerminationReason.COMPLETED,
                            0.95,
                            Optional.empty()));

            ProbabilisticTestVerdict verdict = adapt(result);

            assertThat(verdict.cost().methodTokensConsumed()).isEqualTo(1234L);
            assertThat(verdict.cost().methodTimeBudgetMs()).isZero();
            assertThat(verdict.cost().methodTokenBudget()).isZero();
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private static ProbabilisticTestVerdict adapt(ProbabilisticTestResult result) {
        return VerdictAdapter.adapt(
                result,
                RunMetadata.of("com.example.MyTest", "shouldPass"));
    }

    private static ProbabilisticTestResult minimalResult(Verdict v) {
        return new ProbabilisticTestResult(
                v, FactorBundle.of(new Factors()),
                List.of(), TestIntent.VERIFICATION, List.of(),
                CovariateAlignment.none(), Optional.empty(), Map.of(),
                EngineRunSummary.empty(), PerCriterionEvaluation.empty());
    }

    /**
     * Build a {@link ProbabilisticTestResult} whose verdict is
     * INCONCLUSIVE and whose single criterion result carries the
     * given discriminant on its detail map under
     * {@link InconclusiveReasons#DETAIL_KEY}. Aligned covariates —
     * the case the bug masked.
     */
    private static ProbabilisticTestResult inconclusiveWithReason(String reasonValue) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put(InconclusiveReasons.DETAIL_KEY, reasonValue);
        EvaluatedCriterion ec = new EvaluatedCriterion(
                new CriterionResult("pass-rate", Verdict.INCONCLUSIVE,
                        "synthetic — see test fixture", detail),
                CriterionRole.REQUIRED);
        return new ProbabilisticTestResult(
                Verdict.INCONCLUSIVE, FactorBundle.of(new Factors()),
                List.of(ec), TestIntent.VERIFICATION, List.of(),
                CovariateAlignment.none(), Optional.empty(), Map.of(),
                EngineRunSummary.empty(), PerCriterionEvaluation.empty());
    }

    private static ProbabilisticTestResult resultWithEngineSummary(
            Verdict v, EngineRunSummary engine) {
        return new ProbabilisticTestResult(
                v, FactorBundle.of(new Factors()),
                List.of(), TestIntent.VERIFICATION, List.of(),
                CovariateAlignment.none(), Optional.empty(), Map.of(),
                engine, PerCriterionEvaluation.empty());
    }

    private static ProbabilisticTestResult resultWithCriteria(
            Verdict v, Map<String, Object> detail) {
        CriterionResult cr = new CriterionResult(
                "bernoulli-pass-rate",
                v,
                "stub explanation",
                detail);
        return new ProbabilisticTestResult(
                v, FactorBundle.of(new Factors()),
                List.of(new EvaluatedCriterion(cr, CriterionRole.REQUIRED)),
                TestIntent.VERIFICATION, List.of(),
                CovariateAlignment.none(), Optional.empty(), Map.of(),
                EngineRunSummary.empty(), PerCriterionEvaluation.empty());
    }

    private static ProbabilisticTestResult resultWithTermination(
            org.mavai.punit.api.spec.TerminationReason reason) {
        return resultWithEngineSummary(
                Verdict.PASS,
                new EngineRunSummary(
                        50, 50, 50, 0, 100L, 0L, 0,
                        LatencyResult.empty(),
                        reason,
                        0.95,
                        Optional.empty()));
    }
}
