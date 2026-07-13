package org.mavai.punit.verdict;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.mavai.punit.api.TestIntent;
import org.mavai.punit.api.ThresholdOrigin;
import org.mavai.punit.api.spec.Verdict;
import org.mavai.punit.verdict.TokenMode;
import org.mavai.punit.internal.engine.budget.SharedBudgetMonitor;
import org.mavai.punit.internal.engine.budget.SharedBudgetMonitor.Scope;
import org.mavai.punit.api.BudgetExhaustedBehavior;
import org.mavai.punit.api.PacingConfiguration;
import org.mavai.punit.verdict.ExpirationPolicy;
import org.mavai.punit.verdict.TerminationReason;
import org.mavai.punit.internal.engine.spec.model.ExecutionSpecification;
import org.mavai.punit.statistics.BinomialProportionEstimator;
import org.mavai.punit.statistics.transparent.BaselineData;
import org.mavai.punit.verdict.ProbabilisticTestVerdict.*;
import org.mavai.punit.verdict.ProbabilisticTestVerdictBuilder.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class ProbabilisticTestVerdictBuilderTest {

    @Nested
    @DisplayName("Identity")
    class IdentityTests {

        @Test
        void buildsIdentityWithAllFields() {
            ProbabilisticTestVerdict verdict = minimalBuilder()
                    .identity("MyTestClass", "shouldPass", "payment-gateway")
                    .build();

            assertThat(verdict.identity().className()).isEqualTo("MyTestClass");
            assertThat(verdict.identity().methodName()).isEqualTo("shouldPass");
            assertThat(verdict.identity().serviceContractId()).contains("payment-gateway");
        }

        @Test
        void buildsIdentityWithoutServiceContractId() {
            ProbabilisticTestVerdict verdict = minimalBuilder()
                    .identity("MyTestClass", "shouldPass", null)
                    .build();

            assertThat(verdict.identity().serviceContractId()).isEmpty();
        }

        @Test
        void rejectsBlankClassName() {
            assertThatThrownBy(() -> new TestIdentity("", "method", Optional.empty()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsBlankMethodName() {
            assertThatThrownBy(() -> new TestIdentity("Class", "", Optional.empty()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Execution Summary")
    class ExecutionSummaryTests {

        @Test
        void capturesExecutionMetrics() {
            ProbabilisticTestVerdict verdict = minimalBuilder()
                    .execution(200, 200, 192, 8, 0.92, 0.96, 4520)
                    .build();

            ExecutionSummary exec = verdict.execution();
            assertThat(exec.plannedSamples()).isEqualTo(200);
            assertThat(exec.samplesExecuted()).isEqualTo(200);
            assertThat(exec.successes()).isEqualTo(192);
            assertThat(exec.failures()).isEqualTo(8);
            assertThat(exec.minPassRate()).isEqualTo(0.92);
            assertThat(exec.observedPassRate()).isEqualTo(0.96);
            assertThat(exec.elapsedMs()).isEqualTo(4520);
        }

        @Test
        void capturesMultiplierWhenPresent() {
            ProbabilisticTestVerdict verdict = minimalBuilder()
                    .appliedMultiplier(2.5)
                    .build();

            assertThat(verdict.execution().appliedMultiplier()).contains(2.5);
        }

        @Test
        void multiplierIsEmptyByDefault() {
            ProbabilisticTestVerdict verdict = minimalBuilder().build();

            assertThat(verdict.execution().appliedMultiplier()).isEmpty();
        }

        @Test
        void capturesIntentAndConfidence() {
            ProbabilisticTestVerdict verdict = minimalBuilder()
                    .intent(TestIntent.SMOKE, 0.99)
                    .build();

            assertThat(verdict.execution().intent()).isEqualTo(TestIntent.SMOKE);
            assertThat(verdict.execution().resolvedConfidence()).isEqualTo(0.99);
        }

        @Test
        void defaultsToVerificationIntent() {
            ProbabilisticTestVerdict verdict = minimalBuilder().build();

            assertThat(verdict.execution().intent()).isEqualTo(TestIntent.VERIFICATION);
            assertThat(verdict.execution().resolvedConfidence()).isEqualTo(0.95);
        }
    }

    @Nested
    @DisplayName("Functional Dimension")
    class FunctionalDimensionTests {

        @Test
        void capturesFunctionalResults() {
            ProbabilisticTestVerdict verdict = minimalBuilder()
                    .functionalDimension(95, 5)
                    .build();

            assertThat(verdict.functional()).isPresent();
            FunctionalDimension dim = verdict.functional().get();
            assertThat(dim.successes()).isEqualTo(95);
            assertThat(dim.failures()).isEqualTo(5);
            assertThat(dim.passRate()).isEqualTo(0.95);
        }

        @Test
        void functionalIsEmptyWhenNotAsserted() {
            ProbabilisticTestVerdict verdict = minimalBuilder().build();

            assertThat(verdict.functional()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Latency Dimension")
    class LatencyDimensionTests {

        @Test
        void capturesLatencyResults() {
            LatencyInput input = new LatencyInput(
                    195, 200, false, null,
                    120, 340, 420, 810, 1250,
                    List.of("Advisory note")
            );

            ProbabilisticTestVerdict verdict = minimalBuilder()
                    .latencyDimension(input)
                    .build();

            assertThat(verdict.latency()).isPresent();
            LatencyDimension dim = verdict.latency().get();
            assertThat(dim.successfulSamples()).isEqualTo(195);
            assertThat(dim.totalSamples()).isEqualTo(200);
            assertThat(dim.skipped()).isFalse();
            assertThat(dim.skipReason()).isEmpty();
            assertThat(dim.p95Ms()).isEqualTo(420);
            assertThat(dim.caveats()).containsExactly("Advisory note");
        }

        @Test
        void capturesSkippedLatency() {
            LatencyInput input = new LatencyInput(
                    0, 200, true, "Insufficient successful samples",
                    -1, -1, -1, -1, -1,
                    List.of()
            );

            ProbabilisticTestVerdict verdict = minimalBuilder()
                    .latencyDimension(input)
                    .build();

            LatencyDimension dim = verdict.latency().get();
            assertThat(dim.skipped()).isTrue();
            assertThat(dim.skipReason()).contains("Insufficient successful samples");
        }

        @Test
        void latencyIsEmptyWhenNotAsserted() {
            ProbabilisticTestVerdict verdict = minimalBuilder().build();

            assertThat(verdict.latency()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Statistical Analysis")
    class StatisticalAnalysisTests {

        @Test
        void computesStatisticsForPassingTest() {
            ProbabilisticTestVerdict verdict = minimalBuilder()
                    .execution(100, 100, 95, 5, 0.90, 0.95, 1000)
                    .intent(TestIntent.VERIFICATION, 0.95)
                    .build();

            StatisticalAnalysis stats = verdict.statistics();
            double expectedWilsonLower =
                    new BinomialProportionEstimator().lowerBound(95, 100, 0.95);
            assertThat(stats.confidenceLevel()).isEqualTo(0.95);
            assertThat(stats.standardError()).isGreaterThan(0.0);
            assertThat(stats.wilsonLower())
                    .isGreaterThan(0.0)
                    .isLessThan(0.95)        // strictly below the observed rate
                    .isCloseTo(expectedWilsonLower, within(1e-9));
            assertThat(stats.testStatistic()).isPresent();
            assertThat(stats.pValue()).isPresent();
        }

        @Test
        void handlesZeroSamplesGracefully() {
            ProbabilisticTestVerdict verdict = minimalBuilder()
                    .execution(100, 0, 0, 0, 0.90, 0.0, 0)
                    .build();

            StatisticalAnalysis stats = verdict.statistics();
            assertThat(stats.standardError()).isEqualTo(0.0);
            assertThat(stats.wilsonLower()).isEqualTo(0.0);
            assertThat(stats.testStatistic()).isEmpty();
            assertThat(stats.pValue()).isEmpty();
        }

        @Test
        void includesBaselineSummaryWhenSpecDriven() {
            BaselineData baseline = BaselineData.fromSpec("spec.yaml", Instant.now(), 1000, 940);

            ProbabilisticTestVerdict verdict = minimalBuilder()
                    .execution(100, 100, 95, 5, 0.92, 0.95, 1000)
                    .baseline(baseline)
                    .build();

            StatisticalAnalysis stats = verdict.statistics();
            assertThat(stats.baseline()).isPresent();
            BaselineSummary bs = stats.baseline().get();
            assertThat(bs.baselineSamples()).isEqualTo(1000);
            assertThat(bs.baselineSuccesses()).isEqualTo(940);
            assertThat(bs.baselineRate()).isCloseTo(0.94, org.assertj.core.api.Assertions.within(0.001));
            assertThat(stats.thresholdDerivation()).isPresent();
        }

        @Test
        void noBaselineSummaryForInlineThreshold() {
            ProbabilisticTestVerdict verdict = minimalBuilder().build();

            assertThat(verdict.statistics().baseline()).isEmpty();
            assertThat(verdict.statistics().thresholdDerivation()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Covariate Status and PUnit Verdict")
    class CovariateAndVerdictTests {

        @Test
        void alignedCovariatesWithPassYieldsPass() {
            ProbabilisticTestVerdict verdict = minimalBuilder()
                    .criterionVerdict(Verdict.PASS)
                    .build();

            assertThat(verdict.covariates().aligned()).isTrue();
            assertThat(verdict.covariates().misalignments()).isEmpty();
            assertThat(verdict.punitVerdict()).isEqualTo(PUnitVerdict.PASS);
        }

        @Test
        void alignedCovariatesWithFailYieldsFail() {
            ProbabilisticTestVerdict verdict = minimalBuilder()
                    .criterionVerdict(Verdict.FAIL)
                    .build();

            assertThat(verdict.punitVerdict()).isEqualTo(PUnitVerdict.FAIL);
        }

        @Test
        void misalignedCovariatesYieldsInconclusive() {
            ProbabilisticTestVerdict verdict = minimalBuilder()
                    .misalignments(List.of(
                            new MisalignmentInput("model", "gpt-4", "gpt-3.5")))
                    .criterionVerdict(Verdict.PASS)
                    .build();

            assertThat(verdict.covariates().aligned()).isFalse();
            assertThat(verdict.covariates().misalignments()).hasSize(1);
            assertThat(verdict.covariates().misalignments().getFirst().covariateKey()).isEqualTo("model");
            assertThat(verdict.punitVerdict()).isEqualTo(PUnitVerdict.INCONCLUSIVE);
        }

        @Test
        void inconclusiveOverridesStatisticalFail() {
            ProbabilisticTestVerdict verdict = minimalBuilder()
                    .misalignments(List.of(
                            new MisalignmentInput("model", "gpt-4", "gpt-3.5")))
                    .criterionVerdict(Verdict.FAIL)
                    .build();

            assertThat(verdict.punitVerdict()).isEqualTo(PUnitVerdict.INCONCLUSIVE);
        }
    }

    @Nested
    @DisplayName("JUnit Passed")
    class JunitPassedTests {

        @Test
        void junitPassedReflectsRawFact() {
            ProbabilisticTestVerdict passing = minimalBuilder().junitPassed(true).build();
            ProbabilisticTestVerdict failing = minimalBuilder().junitPassed(false).build();

            assertThat(passing.junitPassed()).isTrue();
            assertThat(failing.junitPassed()).isFalse();
        }

        @Test
        void junitFailWithPUnitPassIsLegitimate() {
            ProbabilisticTestVerdict verdict = minimalBuilder()
                    .junitPassed(false)
                    .criterionVerdict(Verdict.PASS)
                    .build();

            assertThat(verdict.junitPassed()).isFalse();
            assertThat(verdict.punitVerdict()).isEqualTo(PUnitVerdict.PASS);
        }
    }

    @Nested
    @DisplayName("Cost Summary")
    class CostSummaryTests {

        @Test
        void capturesMethodLevelCost() {
            ProbabilisticTestVerdict verdict = minimalBuilder()
                    .cost(500, 10000, 1000, TokenMode.STATIC)
                    .build();

            CostSummary cost = verdict.cost();
            assertThat(cost.methodTokensConsumed()).isEqualTo(500);
            assertThat(cost.methodTimeBudgetMs()).isEqualTo(10000);
            assertThat(cost.methodTokenBudget()).isEqualTo(1000);
            assertThat(cost.tokenMode()).isEqualTo(TokenMode.STATIC);
        }

        @Test
        void snapshotsClassBudget() {
            SharedBudgetMonitor classBudget = new SharedBudgetMonitor(
                    Scope.CLASS, 30000, 5000, BudgetExhaustedBehavior.FAIL);

            ProbabilisticTestVerdict verdict = minimalBuilder()
                    .sharedBudgets(classBudget, null)
                    .build();

            assertThat(verdict.cost().classBudget()).isPresent();
            BudgetSnapshot snapshot = verdict.cost().classBudget().get();
            assertThat(snapshot.timeBudgetMs()).isEqualTo(30000);
            assertThat(snapshot.tokenBudget()).isEqualTo(5000);
        }

        @Test
        void sharedBudgetsAreEmptyWhenNotConfigured() {
            ProbabilisticTestVerdict verdict = minimalBuilder().build();

            assertThat(verdict.cost().classBudget()).isEmpty();
            assertThat(verdict.cost().suiteBudget()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Pacing Summary")
    class PacingSummaryTests {

        @Test
        void capturesPacingConfiguration() {
            PacingConfiguration pacing = new PacingConfiguration(
                    10.0, 0, 0, 1, 0, 100, 1, 0, 10.0);

            ProbabilisticTestVerdict verdict = minimalBuilder()
                    .pacing(pacing)
                    .build();

            assertThat(verdict.pacing()).isPresent();
            PacingSummary ps = verdict.pacing().get();
            assertThat(ps.maxRequestsPerSecond()).isEqualTo(10.0);
            assertThat(ps.effectiveRps()).isEqualTo(10.0);
        }

        @Test
        void pacingIsEmptyWhenNotConfigured() {
            ProbabilisticTestVerdict verdict = minimalBuilder().build();

            assertThat(verdict.pacing()).isEmpty();
        }

        @Test
        void pacingIsEmptyForNoPacingConfig() {
            ProbabilisticTestVerdict verdict = minimalBuilder()
                    .pacing(PacingConfiguration.noPacing())
                    .build();

            assertThat(verdict.pacing()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Spec Provenance")
    class SpecProvenanceTests {

        @Test
        void capturesProvenanceWithOriginAndContract() {
            ProbabilisticTestVerdict verdict = minimalBuilder()
                    .provenance(ThresholdOrigin.SLA, "SLA-PAY-001", "payment-gateway.yaml")
                    .build();

            assertThat(verdict.provenance()).isPresent();
            SpecProvenance prov = verdict.provenance().get();
            assertThat(prov.thresholdOriginName()).isEqualTo("SLA");
            assertThat(prov.contractRef()).isEqualTo("SLA-PAY-001");
            assertThat(prov.specFilename()).isEqualTo("payment-gateway.yaml");
        }

        @Test
        void provenanceIsEmptyForUnspecifiedOriginWithNoContract() {
            ProbabilisticTestVerdict verdict = minimalBuilder()
                    .provenance(ThresholdOrigin.UNSPECIFIED, null, null)
                    .build();

            assertThat(verdict.provenance()).isEmpty();
        }

        @Test
        void provenancePresentForUnspecifiedOriginWithContract() {
            ProbabilisticTestVerdict verdict = minimalBuilder()
                    .provenance(ThresholdOrigin.UNSPECIFIED, "REF-001", null)
                    .build();

            assertThat(verdict.provenance()).isPresent();
        }

        @Test
        void capturesExpirationInfoWhenSpecPresent() {
            Instant baselineEnd = Instant.now().minus(Duration.ofDays(5));
            ExecutionSpecification spec = ExecutionSpecification.builder()
                    .serviceContractId("test-uc")
                    .empiricalBasis(1000, 940, baselineEnd)
                    .expirationPolicy(30, baselineEnd)
                    .build();

            ProbabilisticTestVerdict verdict = minimalBuilder()
                    .provenance(ThresholdOrigin.SLA, "SLA-001", "spec.yaml")
                    .spec(spec)
                    .build();

            SpecProvenance prov = verdict.provenance().get();
            assertThat(prov.expiration()).isPresent();
            assertThat(prov.expiration().get().expiresAt()).isPresent();
        }
    }

    @Nested
    @DisplayName("Termination")
    class TerminationTests {

        @Test
        void defaultsToCompleted() {
            ProbabilisticTestVerdict verdict = minimalBuilder().build();

            assertThat(verdict.termination().reason()).isEqualTo(TerminationReason.COMPLETED);
            assertThat(verdict.termination().details()).isEmpty();
        }

        @Test
        void capturesEarlyTermination() {
            ProbabilisticTestVerdict verdict = minimalBuilder()
                    .termination(TerminationReason.IMPOSSIBILITY,
                            "Needed 90 successes, maximum possible is 85")
                    .build();

            assertThat(verdict.termination().reason()).isEqualTo(TerminationReason.IMPOSSIBILITY);
            assertThat(verdict.termination().details()).isPresent();
            assertThat(verdict.termination().details().get()).contains("Needed 90 successes");
        }
    }

    @Nested
    @DisplayName("SE/Z Separation — builder wiring")
    class SEZSeparationTests {

        @Test
        @DisplayName("computes SE from observed rate, not threshold")
        void computesSEFromObservedRate_notThreshold() {
            // Scenario A: p̂=0.96, π₀=0.9374, n=100
            // SE(p̂) = √(0.96 × 0.04 / 100) ≈ 0.0196
            // SE(π₀) = √(0.9374 × 0.0626 / 100) ≈ 0.0242
            ProbabilisticTestVerdict verdict = new ProbabilisticTestVerdictBuilder()
                    .identity("Test", "method", null)
                    .execution(100, 100, 96, 4, 0.9374, 0.96, 150)
                    .junitPassed(true)
                    .criterionVerdict(Verdict.PASS)
                    .build();

            double se = verdict.statistics().standardError();
            assertThat(se).as("SE should use observed rate 0.96, not threshold 0.9374")
                    .isCloseTo(0.0196, org.assertj.core.api.Assertions.within(0.001));
            assertThat(se).as("SE must not be computed from threshold")
                    .isNotCloseTo(0.0242, org.assertj.core.api.Assertions.within(0.001));
        }

        @Test
        @DisplayName("computes Z from threshold, not observed rate")
        void computesZFromThreshold_notObservedRate() {
            // Scenario A: p̂=0.96, π₀=0.9374, n=100
            // Z with SE₀ = (0.96 - 0.9374) / √(0.9374 × 0.0626 / 100) ≈ 0.933
            // Z with SE(p̂) = (0.96 - 0.9374) / √(0.96 × 0.04 / 100) ≈ 1.153
            ProbabilisticTestVerdict verdict = new ProbabilisticTestVerdictBuilder()
                    .identity("Test", "method", null)
                    .execution(100, 100, 96, 4, 0.9374, 0.96, 150)
                    .junitPassed(true)
                    .criterionVerdict(Verdict.PASS)
                    .build();

            double z = verdict.statistics().testStatistic().orElseThrow();
            assertThat(z).as("Z should use SE₀ (from threshold), not SE(p̂)")
                    .isCloseTo(0.933, org.assertj.core.api.Assertions.within(0.01));
            assertThat(z).as("Z must not use SE derived from observed rate")
                    .isNotCloseTo(1.153, org.assertj.core.api.Assertions.within(0.01));
        }
    }

    @Nested
    @DisplayName("Caveats")
    class CaveatTests {

        @Test
        void addsCovariatesCaveatWhenMisaligned() {
            ProbabilisticTestVerdict verdict = minimalBuilder()
                    .misalignments(List.of(
                            new MisalignmentInput("model", "gpt-4", "gpt-3.5")))
                    .build();

            assertThat(verdict.statistics().caveats())
                    .anyMatch(c -> c.contains("Covariate mismatch"));
        }

        @Test
        void noCaveatsWhenAligned() {
            ProbabilisticTestVerdict verdict = minimalBuilder().build();

            assertThat(verdict.statistics().caveats()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Correlation ID and Metadata")
    class EnvelopeTests {

        @Test
        void generatesCorrelationIdAutomatically() {
            ProbabilisticTestVerdict verdict = minimalBuilder().build();

            assertThat(verdict.correlationId()).startsWith("v:");
            assertThat(verdict.correlationId()).hasSize(8); // "v:" + 6 hex chars
        }

        @Test
        void acceptsExplicitCorrelationId() {
            ProbabilisticTestVerdict verdict = minimalBuilder()
                    .correlationId("v:custom")
                    .build();

            assertThat(verdict.correlationId()).isEqualTo("v:custom");
        }

        @Test
        void capturesTimestamp() {
            Instant before = Instant.now();
            ProbabilisticTestVerdict verdict = minimalBuilder().build();
            Instant after = Instant.now();

            assertThat(verdict.timestamp()).isBetween(before, after);
        }

        @Test
        void everyVerdictCarriesTheSizingDisclosureEntries() {
            ProbabilisticTestVerdict verdict = minimalBuilder().build();

            assertThat(verdict.environmentMetadata())
                    .containsEntry("sizing-approach", "threshold-first")
                    .containsEntry("sizing-declared-samples", "100")
                    .containsEntry("sizing-declared-min-pass-rate", "0.9");
        }

        @Test
        void empiricalProvenanceDisclosesTheSampleSizeFirstApproach() {
            ProbabilisticTestVerdict verdict = minimalBuilder()
                    .provenance(ThresholdOrigin.EMPIRICAL, null, "svc.yaml")
                    .build();

            assertThat(verdict.environmentMetadata())
                    .containsEntry("sizing-approach", "sample-size-first")
                    .containsEntry("sizing-declared-confidence", "0.95");
        }

        @Test
        void aDownsizedRunDisclosesTheTradeAgainstItsBaseline() {
            ProbabilisticTestVerdict verdict = minimalBuilder()
                    .provenance(ThresholdOrigin.EMPIRICAL, null, "svc.yaml")
                    .baseline(new BaselineData("svc.yaml",
                            Instant.parse("2026-07-01T00:00:00Z"), 1000, 960))
                    .build();

            assertThat(verdict.environmentMetadata())
                    .containsKey("sizing-detectable-rate")
                    .containsEntry("sizing-saved-fraction", "0.9")
                    .containsKey("sizing-time-saved-ms");
        }

        @Test
        void adapterSuppliedSizingBaselineFeedsTheDownsizingDisclosure() {
            ProbabilisticTestVerdict verdict = minimalBuilder()
                    .provenance(ThresholdOrigin.EMPIRICAL, null, "svc.yaml")
                    .sizingBaseline(1000, 0.96)
                    .build();

            assertThat(verdict.environmentMetadata())
                    .containsEntry("sizing-baseline-samples", "1000")
                    .containsKey("sizing-detectable-rate")
                    .containsEntry("sizing-saved-fraction", "0.9");
        }

        @Test
        void capturesEnvironmentMetadata() {
            ProbabilisticTestVerdict verdict = minimalBuilder()
                    .environmentMetadata(Map.of("host", "prod-01", "region", "eu-west-1"))
                    .build();

            assertThat(verdict.environmentMetadata())
                    .containsEntry("host", "prod-01")
                    .containsEntry("region", "eu-west-1")
                    .containsEntry("sizing-approach", "threshold-first");
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private ProbabilisticTestVerdictBuilder minimalBuilder() {
        return new ProbabilisticTestVerdictBuilder()
                .identity("TestClass", "testMethod", null)
                .execution(100, 100, 95, 5, 0.90, 0.95, 1000)
                .junitPassed(true)
                .criterionVerdict(Verdict.PASS);
    }
}
