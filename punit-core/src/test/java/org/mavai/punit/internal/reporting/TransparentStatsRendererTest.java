package org.mavai.punit.internal.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.mavai.punit.api.TestIntent;
import org.mavai.punit.api.ThresholdOrigin;
import org.mavai.punit.api.spec.FailureCount;
import org.mavai.punit.api.spec.FailureExemplar;
import org.mavai.punit.api.FactorBundle;
import org.mavai.punit.api.covariate.CovariateAlignment;
import org.mavai.punit.api.covariate.CovariateProfile;
import org.mavai.punit.api.spec.CriterionResult;
import org.mavai.punit.api.spec.CriterionRole;
import org.mavai.punit.api.spec.CriterionSampleCounts;
import org.mavai.punit.api.spec.EngineRunSummary;
import org.mavai.punit.api.spec.EvaluatedCriterion;
import org.mavai.punit.api.spec.PerCriterionEvaluation;
import org.mavai.punit.api.spec.PerCriterionVerdict;
import org.mavai.punit.api.spec.ProbabilisticTestResult;
import org.mavai.punit.api.spec.Verdict;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TransparentStatsRenderer — verbose statistical breakdown")
class TransparentStatsRendererTest {

    private static ProbabilisticTestResult result(Verdict verdict, EvaluatedCriterion... evaluated) {
        return new ProbabilisticTestResult(
                verdict,
                FactorBundle.empty(),
                List.of(evaluated),
                TestIntent.VERIFICATION,
                List.of(),
                CovariateAlignment.none(), Optional.empty(), Map.of(),
                EngineRunSummary.empty(), PerCriterionEvaluation.empty());
    }

    private static EvaluatedCriterion criterion(
            String name, Verdict verdict, String explanation, Map<String, Object> detail) {
        return new EvaluatedCriterion(
                new CriterionResult(name, verdict, explanation, detail),
                CriterionRole.REQUIRED);
    }

    /** A pass-rate result carrying one criterion's decision artefacts, as PassRate publishes them. */
    private static EvaluatedCriterion passRate(Verdict verdict, Map<String, Object> decision) {
        Map<String, Object> detail = new LinkedHashMap<>(decision);
        detail.put("decisionsByCriterion", Map.of("c", decision));
        detail.put("verdictsByCriterion", Map.of("c", verdict.name()));
        return criterion("bernoulli-pass-rate", verdict, "...", detail);
    }

    @Nested
    @DisplayName("PassRate regression path (regression/fisher)")
    class BernoulliEmpirical {

        @Test
        @DisplayName("renders the hypotheses, the rule and alpha, the counts, the cutoff and the calibration statement")
        void rendersFullRegressionReport() {
            Map<String, Object> decision = new LinkedHashMap<>();
            decision.put("observed", 0.93);
            decision.put("successes", 93);
            decision.put("total", 100);
            decision.put("origin", "EMPIRICAL");
            decision.put("confidence", 0.95);
            decision.put("alpha", 0.05);
            decision.put("baselineSampleCount", 1000);
            decision.put("baselineSuccesses", 951);
            decision.put("decisionRule", "regression/fisher");
            decision.put("cutoff", 91);
            decision.put("displayedRate", 0.91);
            decision.put("sizeAtAssumedCommonRate", 0.0340);
            decision.put("minimumDetectableDegradation", 0.0756);

            String rendered = TransparentStatsRenderer.render(
                    "shopping-basket.testInstructionTranslation",
                    result(Verdict.PASS, passRate(Verdict.PASS, decision)));

            assertThat(rendered)
                    .contains("STATISTICAL ANALYSIS — test verdict: PASS")
                    .contains("shopping-basket.testInstructionTranslation")
                    .contains("[REQUIRED] bernoulli-pass-rate → PASS")
                    .contains("baseline and test share one success probability")
                    .contains("regression/fisher v1, alpha 0.05")
                    .contains("K = 93 of n = 100")
                    .contains("K_b = 951 of n_b = 1000")
                    .contains("PASS iff K ≥ c = 91")
                    .contains("both drawn afresh from an unchanged service")
                    .contains("at the assumed common rate")
                    .contains("inverts the design power");
        }
    }

    @Nested
    @DisplayName("PassRate compliance path (compliance/exact-binomial)")
    class BernoulliContractual {

        @Test
        @DisplayName("renders the requirement, k_min, the calibration statement and the Clopper–Pearson bound")
        void rendersContractual() {
            Map<String, Object> decision = new LinkedHashMap<>();
            decision.put("observed", 0.96);
            decision.put("successes", 96);
            decision.put("total", 100);
            decision.put("origin", ThresholdOrigin.SLA.name());
            decision.put("threshold", 0.90);
            decision.put("alpha", 0.05);
            decision.put("decisionRule", "compliance/exact-binomial");
            decision.put("kMin", 96);
            decision.put("passPossible", true);
            decision.put("falseCompliance", 0.0237);
            decision.put("clopperPearsonLower", 0.9109);

            String rendered = TransparentStatsRenderer.render(
                    "test", result(Verdict.PASS, passRate(Verdict.PASS, decision)));

            assertThat(rendered)
                    .contains("p ≤ 0.9000 (the requirement is not met)")
                    .contains("compliance/exact-binomial v1, alpha 0.05")
                    .contains("0.9000 (origin: SLA)")
                    .contains("PASS iff K ≥ k_min = 96")
                    .contains("falsely declare compliance")
                    .contains("decides nothing")
                    .doesNotContain("Wilson");
        }

        @Test
        @DisplayName("a design no outcome of which could pass says so")
        void rendersPassNotPossible() {
            Map<String, Object> decision = new LinkedHashMap<>();
            decision.put("observed", 1.0);
            decision.put("successes", 10);
            decision.put("total", 10);
            decision.put("origin", ThresholdOrigin.SLA.name());
            decision.put("threshold", 0.99);
            decision.put("alpha", 0.05);
            decision.put("decisionRule", "compliance/exact-binomial");
            decision.put("passPossible", false);
            decision.put("falseCompliance", 0.0);
            decision.put("clopperPearsonLower", 0.74);

            String rendered = TransparentStatsRenderer.render(
                    "test", result(Verdict.FAIL, passRate(Verdict.FAIL, decision)));

            assertThat(rendered).contains("no count of this size can pass");
        }
    }

    @Nested
    @DisplayName("Unknown criterion fallback")
    class UnknownCriterion {

        @Test
        @DisplayName("falls back to explanation + raw detail map")
        void fallback() {
            Map<String, Object> detail = Map.of(
                    "p50", "PT0.001S",
                    "p99", "PT0.020S");

            String rendered = TransparentStatsRenderer.render(
                    "test", result(Verdict.PASS, criterion(
                            "percentile-latency", Verdict.PASS,
                            "p50=1ms, p99=20ms within limits", detail)));

            assertThat(rendered)
                    .contains("[REQUIRED] percentile-latency → PASS")
                    .contains("Explanation:")
                    .contains("p50=1ms, p99=20ms within limits")
                    .contains("Detail")
                    .contains("p50:")
                    .contains("PT0.001S")
                    .contains("p99:")
                    .contains("PT0.020S");
        }
    }

    @Test
    @DisplayName("warnings render under a Notes section")
    void warningsRender() {
        ProbabilisticTestResult resultWithWarnings = new ProbabilisticTestResult(
                Verdict.INCONCLUSIVE,
                FactorBundle.empty(),
                List.of(criterion("bernoulli-pass-rate", Verdict.INCONCLUSIVE, "no baseline", Map.of())),
                TestIntent.VERIFICATION,
                List.of("rejected file-A — CONFIGURATION mismatch on region (current=APAC, baseline=EU)"),
                CovariateAlignment.none(), Optional.empty(), Map.of(),
                EngineRunSummary.empty(), PerCriterionEvaluation.empty());

        String rendered = TransparentStatsRenderer.render(
                "test", resultWithWarnings);

        assertThat(rendered)
                .contains("Notes")
                .contains("! rejected file-A — CONFIGURATION mismatch on region")
                .contains("Test intent: VERIFICATION");
    }

    @Nested
    @DisplayName("Covariate alignment rendering")
    class Covariates {

        private ProbabilisticTestResult resultWithAlignment(CovariateAlignment alignment) {
            return new ProbabilisticTestResult(
                    Verdict.PASS, FactorBundle.empty(),
                    List.of(criterion("bernoulli-pass-rate", Verdict.PASS, "...",
                            Map.of("observed", 0.95, "threshold", 0.85, "origin", "EMPIRICAL",
                                    "successes", 95, "failures", 5, "total", 100,
                                    "confidence", 0.95, "wilsonLowerBound", 0.90))),
                    TestIntent.VERIFICATION,
                    List.of(),
                    alignment,
                    Optional.empty(), Map.of(),
                    EngineRunSummary.empty(), PerCriterionEvaluation.empty());
        }

        @Test
        @DisplayName("aligned baseline + observed renders both profiles plus Aligned: yes")
        void alignedRenders() {
            CovariateProfile profile = CovariateProfile.of(new LinkedHashMap<>(Map.of(
                    "region", "EU",
                    "model_version", "v1")));
            CovariateAlignment alignment = CovariateAlignment.compute(profile, profile);

            String rendered = TransparentStatsRenderer.render(
                    "test", resultWithAlignment(alignment));

            assertThat(rendered)
                    .contains("Covariates")
                    .contains("Observed:")
                    .contains("region=EU")
                    .contains("Baseline:")
                    .contains("Aligned:")
                    .contains("yes")
                    .doesNotContain("Aligned:              no");
        }

        @Test
        @DisplayName("misaligned profiles list per-key differences")
        void misalignmentRenders() {
            CovariateProfile observed = CovariateProfile.of(new LinkedHashMap<>(Map.of(
                    "region", "APAC",
                    "model_version", "v1")));
            CovariateProfile baseline = CovariateProfile.of(new LinkedHashMap<>(Map.of(
                    "region", "EU",
                    "model_version", "v1")));
            CovariateAlignment alignment = CovariateAlignment.compute(observed, baseline);

            String rendered = TransparentStatsRenderer.render(
                    "test", resultWithAlignment(alignment));

            assertThat(rendered)
                    .contains("Observed:")
                    .contains("region=APAC")
                    .contains("Baseline:")
                    .contains("region=EU")
                    .contains("Aligned:")
                    .contains("no")
                    .contains("region:")
                    .contains("observed=APAC, baseline=EU");
        }

        @Test
        @DisplayName("empty alignment (no covariates declared, no baseline) renders no Covariates section")
        void emptyAlignmentRendersNothing() {
            String rendered = TransparentStatsRenderer.render(
                    "test", resultWithAlignment(CovariateAlignment.none()));

            assertThat(rendered).doesNotContain("Covariates");
        }

        @Test
        @DisplayName("observed-only (baseline empty) renders observed but no alignment line")
        void observedOnly() {
            CovariateProfile observed = CovariateProfile.of(Map.of("region", "EU"));
            CovariateAlignment alignment = CovariateAlignment.compute(
                    observed, CovariateProfile.empty());

            String rendered = TransparentStatsRenderer.render(
                    "test", resultWithAlignment(alignment));

            assertThat(rendered)
                    .contains("Covariates")
                    .contains("Observed:")
                    .contains("region=EU")
                    .doesNotContain("Baseline:");
        }
    }

    @Test
    @DisplayName("snapshotCriteria returns a name → detail map preserving order")
    void snapshotCriteria() {
        Map<String, Object> bernoulliDetail = Map.of("observed", 0.95, "total", 100);
        Map<String, Object> latencyDetail = Map.of("p99", "PT0.010S");

        ProbabilisticTestResult r = result(Verdict.PASS,
                criterion("bernoulli-pass-rate", Verdict.PASS, "ok", bernoulliDetail),
                criterion("percentile-latency", Verdict.PASS, "ok", latencyDetail));

        var snapshot = TransparentStatsRenderer.snapshotCriteria(r);

        assertThat(snapshot.keySet())
                .containsExactly("bernoulli-pass-rate", "percentile-latency");
        assertThat(snapshot.get("bernoulli-pass-rate"))
                .containsEntry("observed", 0.95)
                .containsEntry("total", 100);
        assertThat(snapshot.get("percentile-latency"))
                .containsEntry("p99", "PT0.010S");
    }

    @Nested
    @DisplayName("Postcondition failures rendering")
    class PostconditionFailures {

        private ProbabilisticTestResult resultWithHistogram(
                Map<String, FailureCount> hist) {
            return new ProbabilisticTestResult(
                    Verdict.FAIL, FactorBundle.empty(),
                    List.of(criterion("bernoulli-pass-rate", Verdict.FAIL,
                            "observed=0.65", Map.of())),
                    TestIntent.VERIFICATION,
                    List.of(),
                    CovariateAlignment.none(),
                    java.util.Optional.empty(),
                    hist,
                    EngineRunSummary.empty(), PerCriterionEvaluation.empty());
        }

        @Test
        @DisplayName("empty histogram → no Postcondition failures section")
        void emptyHistogramOmitsSection() {
            String rendered = TransparentStatsRenderer.render(
                    "test", resultWithHistogram(Map.of()));

            assertThat(rendered).doesNotContain("Postcondition failures");
        }

        @Test
        @DisplayName("non-empty histogram renders section with all retained exemplars")
        void rendersSection() {
            var hist = Map.of(
                    "Valid JSON", new FailureCount(8, List.of(
                            new FailureExemplar(
                                    "Add 2 apples", "trailing commentary"),
                            new FailureExemplar(
                                    "Clear the basket", "unexpected end of input"),
                            new FailureExemplar(
                                    "Remove the milk", "malformed brace"))));

            String rendered = TransparentStatsRenderer.render(
                    "test", resultWithHistogram(hist));

            assertThat(rendered).contains("Postcondition failures");
            assertThat(rendered).contains("Valid JSON — 8 failures");
            // All three retained exemplars are surfaced (engine cap of 3 per
            // clause; transparent stats shows them all — no further truncation).
            assertThat(rendered).contains("• Add 2 apples → trailing commentary");
            assertThat(rendered).contains("• Clear the basket → unexpected end of input");
            assertThat(rendered).contains("• Remove the milk → malformed brace");
        }

        @Test
        @DisplayName("clauses sorted by descending count")
        void sortedByDescendingCount() {
            var hist = Map.of(
                    "Less common", new FailureCount(2, List.of()),
                    "Most common", new FailureCount(10, List.of()),
                    "Middle", new FailureCount(5, List.of()));

            String rendered = TransparentStatsRenderer.render(
                    "test", resultWithHistogram(hist));

            int mostIdx = rendered.indexOf("Most common");
            int middleIdx = rendered.indexOf("Middle");
            int lessIdx = rendered.indexOf("Less common");

            assertThat(mostIdx).isPositive();
            assertThat(middleIdx).isGreaterThan(mostIdx);
            assertThat(lessIdx).isGreaterThan(middleIdx);
        }

        @Test
        @DisplayName("count of 1 uses singular 'failure', > 1 uses plural")
        void singularPluralAgreement() {
            var hist = Map.of(
                    "Single trip", new FailureCount(1, List.of()),
                    "Multi trip", new FailureCount(5, List.of()));

            String rendered = TransparentStatsRenderer.render(
                    "test", resultWithHistogram(hist));

            assertThat(rendered).contains("Single trip — 1 failure\n");
            assertThat(rendered).contains("Multi trip — 5 failures\n");
        }
    }

    @Nested
    @DisplayName("Per-criterion methodology block")
    class PerCriterionBlock {

        private static ProbabilisticTestResult resultWithEvaluation(
                Verdict overall, PerCriterionEvaluation evaluation) {
            return new ProbabilisticTestResult(
                    overall,
                    FactorBundle.empty(),
                    List.of(),
                    TestIntent.VERIFICATION,
                    List.of(),
                    CovariateAlignment.none(),
                    Optional.empty(),
                    Map.of(),
                    EngineRunSummary.empty(),
                    evaluation);
        }

        @Test
        @DisplayName("renders per-criterion rows with counts, observed rate, threshold")
        void rendersPerCriterionRows() {
            PerCriterionEvaluation eval = new PerCriterionEvaluation(
                    List.of(
                            new PerCriterionVerdict(
                                    "response-not-empty",
                                    Verdict.PASS,
                                    new CriterionSampleCounts("response-not-empty", 100, 0, 0, 0),
                                    1.0,
                                    0.85),
                            new PerCriterionVerdict(
                                    "valid-json",
                                    Verdict.PASS,
                                    new CriterionSampleCounts("valid-json", 90, 5, 5, 0),
                                    0.90,
                                    0.85)),
                    Verdict.PASS);

            String rendered = TransparentStatsRenderer.render(
                    "shopping-basket.test", resultWithEvaluation(Verdict.PASS, eval));

            assertThat(rendered)
                    .contains("Per-criterion verdicts")
                    .contains("response-not-empty → PASS")
                    .contains("valid-json → PASS")
                    .contains("pass=100, fail=0 (condition=0, transform=0), total=100")
                    .contains("pass=90, fail=10 (condition=5, transform=5), total=100")
                    .contains("Threshold:")
                    .contains("0.8500")
                    .contains("Composite:")
                    .contains("PASS");
        }

        @Test
        @DisplayName("no per-criterion block when evaluation is empty (back-compat path)")
        void emptyEvaluationOmitsBlock() {
            String rendered = TransparentStatsRenderer.render(
                    "test", resultWithEvaluation(Verdict.PASS, PerCriterionEvaluation.empty()));

            assertThat(rendered).doesNotContain("Per-criterion verdicts");
            assertThat(rendered).doesNotContain("Composite:");
        }
    }
}
