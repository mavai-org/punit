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

    @Nested
    @DisplayName("PassRate empirical path")
    class BernoulliEmpirical {

        @Test
        @DisplayName("renders the hypothesis, observed, and inference sections with Wilson lower bound")
        void rendersFullEmpiricalReport() {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("observed", 1.0);
            detail.put("threshold", 0.94);
            detail.put("origin", "EMPIRICAL");
            detail.put("successes", 50);
            detail.put("failures", 0);
            detail.put("total", 50);
            detail.put("confidence", 0.95);
            detail.put("wilsonLowerBound", 0.929);
            detail.put("baselineSampleCount", 1000);

            String rendered = TransparentStatsRenderer.render(
                    "shopping-basket.testInstructionTranslation",
                    result(Verdict.FAIL, criterion(
                            "bernoulli-pass-rate", Verdict.FAIL,
                            "observed=1.0000 (Wilson-95% lower=0.9290) vs threshold=0.9400 (origin=EMPIRICAL) over 50 samples",
                            detail)));

            assertThat(rendered)
                    .contains("STATISTICAL ANALYSIS — verdict: FAIL")
                    .contains("shopping-basket.testInstructionTranslation")
                    .contains("[REQUIRED] bernoulli-pass-rate → FAIL")
                    .contains("Hypothesis test")
                    .contains("H₀ (null):")
                    .contains("True pass rate π ≥ 0.9400")
                    .contains("H₁ (alternative):")
                    .contains("True pass rate π < 0.9400")
                    .contains("Test type:")
                    .contains("One-sided Wilson-score lower bound")
                    .contains("Observed data")
                    .contains("Sample size (n):")
                    .contains("50")
                    .contains("Successes (k):")
                    .contains("Observed rate (p̂):")
                    .contains("1.0000")
                    .contains("Inference")
                    .contains("Wilson 95% lower:")
                    .contains("0.9290")
                    .contains("Threshold:")
                    .contains("0.9400 (origin: EMPIRICAL)")
                    .contains("Baseline samples:")
                    .contains("1000")
                    .contains("Reasoning:")
                    .contains("0.9290 < 0.9400 ✗");
        }

        @Test
        @DisplayName("PASS verdict shows ✓ in the reasoning line")
        void passShowsCheckMark() {
            Map<String, Object> detail = Map.of(
                    "observed", 0.94,
                    "threshold", 0.85,
                    "origin", "EMPIRICAL",
                    "successes", 94,
                    "failures", 6,
                    "total", 100,
                    "confidence", 0.95,
                    "wilsonLowerBound", 0.873);

            String rendered = TransparentStatsRenderer.render(
                    "test", result(Verdict.PASS, criterion(
                            "bernoulli-pass-rate", Verdict.PASS, "...", detail)));

            assertThat(rendered)
                    .contains("STATISTICAL ANALYSIS — verdict: PASS")
                    .contains("0.8730 ≥ 0.8500 ✓");
        }
    }

    @Nested
    @DisplayName("PassRate contractual path")
    class BernoulliContractual {

        @Test
        @DisplayName("renders deterministic-comparison test type, no Wilson section")
        void rendersContractual() {
            Map<String, Object> detail = Map.of(
                    "observed", 0.94,
                    "threshold", 0.90,
                    "origin", ThresholdOrigin.SLA.name(),
                    "successes", 94,
                    "failures", 6,
                    "total", 100);

            String rendered = TransparentStatsRenderer.render(
                    "test", result(Verdict.PASS, criterion(
                            "bernoulli-pass-rate", Verdict.PASS, "...", detail)));

            assertThat(rendered)
                    .contains("Test type:")
                    .contains("Deterministic comparison (observed ≥ threshold)")
                    .contains("Threshold:")
                    .contains("0.9000 (origin: SLA)")
                    .contains("0.9400 ≥ 0.9000 ✓")
                    .doesNotContain("Wilson")
                    .doesNotContain("Baseline samples");
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
                                    new CriterionSampleCounts("response-not-empty", 100, 0, 0),
                                    1.0,
                                    0.85),
                            new PerCriterionVerdict(
                                    "valid-json",
                                    Verdict.PASS,
                                    new CriterionSampleCounts("valid-json", 90, 5, 5),
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
