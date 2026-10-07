package org.mavai.punit.statistics.conformance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import com.fasterxml.jackson.databind.JsonNode;

import org.mavai.punit.api.TestIntent;
import org.mavai.punit.api.spec.CriterionResult;
import org.mavai.punit.api.spec.LatencyStatistics;
import org.mavai.punit.api.spec.ProbabilisticTestResult;
import org.mavai.punit.api.spec.Verdict;
import org.mavai.punit.api.spec.AssertionEnforcement;
import org.mavai.punit.api.spec.EnforcementMode;
import org.mavai.punit.api.spec.VerdictComposition;
import org.mavai.punit.internal.engine.emit.LatencySection;
import org.mavai.punit.statistics.BinomialProportionEstimator;
import org.mavai.punit.statistics.ComplianceRule;
import org.mavai.punit.statistics.DecisionRule;
import org.mavai.punit.statistics.LatencyRules;
import org.mavai.punit.statistics.Methodology;
import org.mavai.punit.statistics.RegressionRule;
import org.mavai.punit.statistics.RegressionSizing;
import org.mavai.punit.statistics.VerificationFeasibilityEvaluator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mavai.punit.statistics.conformance.OracleAssert.assertOracle;

/**
 * The registry of every conformance check punit runs against the mavai-R
 * fixtures — one {@link CaseCheck} per fixture case.
 *
 * <p>Two consumers execute the same checks: the per-suite display test
 * ({@code ConformanceTest}) turns each check into a {@code DynamicTest};
 * {@code ConformanceCoverageTest} re-runs the whole catalog with a
 * collecting {@link ConformanceRecorder} and diffs the recorded
 * {@code (suite, case, binding-field)} triples against the manifest's
 * obligations.
 *
 * <p>The decision suites are evaluated through the production paths
 * ({@link ProductionPath}): configuration errors by the engine's pre-run
 * refusal, pass-rate verdicts by the engine end to end, latency verdicts
 * by the criterion's evaluation step. The decision-rule layer and the
 * primitives are checked on the statistics package directly.
 */
final class ConformanceCatalog {

    @FunctionalInterface
    interface Check {
        void run(ConformanceRecorder recorder) throws Exception;
    }

    record CaseCheck(String suite, String caseName, Check check) { }

    private static final BinomialProportionEstimator ESTIMATOR = new BinomialProportionEstimator();

    private ConformanceCatalog() { }

    /** Every check in the catalog — the coverage test's execution plan. */
    static List<CaseCheck> all() {
        List<CaseCheck> checks = new ArrayList<>();
        checks.addAll(wilsonCi());
        checks.addAll(wilsonLower());
        checks.addAll(thresholdDerivation());
        checks.addAll(regressionDecision());
        checks.addAll(complianceDecision());
        checks.addAll(feasibility());
        checks.addAll(powerAnalysis());
        checks.addAll(riskDrivenSizing());
        checks.addAll(latencyPercentile());
        checks.addAll(latencyPercentileMinimums());
        checks.addAll(latencyThreshold());
        checks.addAll(latencyComplianceDecision());
        checks.addAll(verdict());
        return checks;
    }

    private static List<CaseCheck> suite(String suite, CaseAssertion assertion) {
        JsonNode file = ConformanceFixtures.load(suite + ".json");
        double tolerance = file.get("tolerance").asDouble();
        List<CaseCheck> checks = new ArrayList<>();
        for (JsonNode c : file.get("cases")) {
            checks.add(new CaseCheck(suite, c.get("name").asText(),
                    recorder -> assertion.check(recorder, c, tolerance)));
        }
        return checks;
    }

    @FunctionalInterface
    private interface CaseAssertion {
        void check(ConformanceRecorder recorder, JsonNode c, double tolerance) throws Exception;
    }

    // ── Descriptive primitives ──────────────────────────────────────

    static List<CaseCheck> wilsonCi() {
        return suite("wilson_ci", (recorder, c, tol) -> {
            var in = c.get("inputs");
            var result = ESTIMATOR.estimate(
                    in.get("successes").asInt(), in.get("trials").asInt(),
                    in.get("confidence").asDouble());
            assertOracle(recorder, "wilson_ci", c, "point", result.pointEstimate(), tol);
            assertOracle(recorder, "wilson_ci", c, "lower", result.lowerBound(), tol);
            assertOracle(recorder, "wilson_ci", c, "upper", result.upperBound(), tol);
        });
    }

    static List<CaseCheck> wilsonLower() {
        return suite("wilson_lower", (recorder, c, tol) -> {
            var in = c.get("inputs");
            assertOracle(recorder, "wilson_lower", c, "lower_bound",
                    ESTIMATOR.lowerBound(in.get("successes").asInt(), in.get("trials").asInt(),
                            in.get("confidence").asDouble()), tol);
        });
    }

    // ── regression/fisher ───────────────────────────────────────────

    /**
     * The decision-rule layer: the cutoff (with the configuration error
     * judged by the engine's refusal) and the threshold-first inversion.
     */
    static List<CaseCheck> thresholdDerivation() {
        String s = "threshold_derivation";
        return suite(s, (recorder, c, tol) -> {
            var in = c.get("inputs");
            int kb = in.get("baseline_successes").asInt();
            int nb = in.get("baseline_trials").asInt();
            int nt = in.get("test_samples").asInt();
            if ("threshold_first".equals(c.get("approach").asText())) {
                var implied = RegressionRule.impliedAlpha(kb, nb, nt, in.get("declared_cutoff").asInt());
                assertOracle(recorder, s, c, "implied_alpha", implied.alpha(), tol);
                assertOracle(recorder, s, c, "is_sound", implied.isSound());
                return;
            }
            double alpha = in.get("alpha").asDouble();
            ProbabilisticTestResult result = ProductionPath.run(
                    List.of(new ProductionPath.Baseline("c", kb, nb, alpha)), nt, 0,
                    TestIntent.VERIFICATION);
            List<String> errors = ProductionPath.errors(result);
            assertOracle(recorder, s, c, "configuration_error", errors);
            if (!errors.isEmpty()) {
                assertOracle(recorder, s, c, "cutoff_integer", null);
                return;
            }
            RegressionRule.Derivation d = RegressionRule.derive(kb, nb, nt, alpha);
            assertOracle(recorder, s, c, "cutoff_integer", d.cutoff());
            assertOracle(recorder, s, c, "threshold_real", d.thresholdReal(), tol);
            assertOracle(recorder, s, c, "displayed_rate", d.displayedRate(), tol);
            assertOracle(recorder, s, c, "size_at_assumed_common_rate", d.sizeAtAssumedCommonRate(), tol);
        });
    }

    /** PASS iff the observed count meets the Fisher cutoff, as the engine judges it. */
    static List<CaseCheck> regressionDecision() {
        String s = "regression_decision";
        return suite(s, (recorder, c, tol) -> {
            var in = c.get("inputs");
            ProbabilisticTestResult result = ProductionPath.run(
                    List.of(new ProductionPath.Baseline("c",
                            in.get("baseline_successes").asInt(), in.get("baseline_trials").asInt(),
                            in.get("alpha").asDouble())),
                    in.get("test_samples").asInt(), in.get("observed_successes").asInt(),
                    TestIntent.VERIFICATION);
            List<String> errors = ProductionPath.errors(result);
            assertOracle(recorder, s, c, "configuration_error", errors);
            if (!errors.isEmpty()) {
                assertOracle(recorder, s, c, "cutoff_integer", null);
                assertOracle(recorder, s, c, "verdict", null);
                return;
            }
            Map<?, ?> d = ProductionPath.decision(result, "c");
            assertThat(d.get("decisionRule")).isEqualTo(DecisionRule.REGRESSION_FISHER.id());
            assertOracle(recorder, s, c, "cutoff_integer", d.get("cutoff"));
            assertOracle(recorder, s, c, "verdict", result.verdict().name());
            assertOracle(recorder, s, c, "threshold_real", d.get("threshold"), tol);
            assertOracle(recorder, s, c, "displayed_rate", d.get("displayedRate"), tol);
            assertOracle(recorder, s, c, "size_at_assumed_common_rate",
                    d.get("sizeAtAssumedCommonRate"), tol);
        });
    }

    // ── compliance/exact-binomial ───────────────────────────────────

    static List<CaseCheck> complianceDecision() {
        String s = "compliance_decision";
        return suite(s, (recorder, c, tol) -> {
            var in = c.get("inputs");
            ProbabilisticTestResult result = ProductionPath.run(
                    List.of(new ProductionPath.Requirement("c",
                            in.get("threshold").asDouble(), in.get("alpha").asDouble())),
                    in.get("test_samples").asInt(), in.get("observed_successes").asInt(),
                    TestIntent.valueOf(in.get("intent").asText()));
            List<String> errors = ProductionPath.errors(result);
            assertOracle(recorder, s, c, "configuration_error", errors);
            if (!errors.isEmpty()) {
                for (String field : List.of("k_min", "pass_possible", "verdict")) {
                    assertOracle(recorder, s, c, field, null);
                }
                return;
            }
            Map<?, ?> d = ProductionPath.decision(result, "c");
            assertThat(d.get("decisionRule")).isEqualTo(DecisionRule.COMPLIANCE_EXACT_BINOMIAL.id());
            assertOracle(recorder, s, c, "k_min", d.get("kMin"));
            assertOracle(recorder, s, c, "pass_possible", d.get("passPossible"));
            assertOracle(recorder, s, c, "verdict", result.verdict().name());
            assertOracle(recorder, s, c, "false_compliance", d.get("falseCompliance"), tol);
            assertOracle(recorder, s, c, "clopper_pearson_lower", d.get("clopperPearsonLower"), tol);
        });
    }

    static List<CaseCheck> feasibility() {
        String s = "feasibility";
        return suite(s, (recorder, c, tol) -> {
            var in = c.get("inputs");
            var result = VerificationFeasibilityEvaluator.evaluate(
                    in.get("sample_size").asInt(), in.get("target_proportion").asDouble(),
                    Methodology.confidenceFromAlpha(in.get("alpha").asDouble()));
            assertOracle(recorder, s, c, "feasible", result.feasible());
            assertOracle(recorder, s, c, "minimum_samples", result.minimumSamples());
            assertOracle(recorder, s, c, "criterion", result.criterion());
        });
    }

    // ── Power and sizing ────────────────────────────────────────────

    static List<CaseCheck> powerAnalysis() {
        String s = "power_analysis";
        return suite(s, (recorder, c, tol) -> {
            var in = c.get("inputs");
            switch (c.get("approach").asText()) {
                case "compliance_sizing" -> {
                    OptionalDouble declared = in.has("alternative_rate")
                            ? OptionalDouble.of(in.get("alternative_rate").asDouble())
                            : OptionalDouble.empty();
                    ComplianceRule.Sizing sizing = ComplianceRule.size(
                            in.get("threshold").asDouble(), in.get("min_detectable_effect").asDouble(),
                            in.get("alpha").asDouble(), in.get("power").asDouble(), declared,
                            ComplianceRule.DEFAULT_SIZING_HORIZON);
                    assertOracle(recorder, s, c, "required_samples", sizing.requiredSamples());
                    assertOracle(recorder, s, c, "achieved_power", sizing.achievedPower(), tol);
                    assertOracle(recorder, s, c, "alternative_rate", sizing.alternative().rate(), tol);
                    assertOracle(recorder, s, c, "alternative_kind", sizing.alternative().kind().name());
                    assertOracle(recorder, s, c, "first_crossing", sizing.firstCrossing());
                }
                case "regression_power" -> assertOracle(recorder, s, c, "design_power",
                        RegressionRule.designPower(
                                in.get("baseline_trials").asInt(), in.get("test_samples").asInt(),
                                in.get("alpha").asDouble(), in.get("baseline_rate").asDouble(),
                                in.get("baseline_rate").asDouble()
                                        - in.get("min_detectable_effect").asDouble()), tol);
                case "regression_resolved_power" -> {
                    int kb = in.get("baseline_successes").asInt();
                    int nb = in.get("baseline_trials").asInt();
                    int nt = in.get("test_samples").asInt();
                    double alpha = in.get("alpha").asDouble();
                    assertOracle(recorder, s, c, "cutoff_integer", RegressionRule.cutoff(kb, nb, nt, alpha));
                    assertOracle(recorder, s, c, "resolved_test_power", RegressionRule.resolvedPower(
                            kb, nb, nt, alpha, in.get("design_alternative_rate").asDouble()), tol);
                }
                case "regression_mdd" -> assertOracle(recorder, s, c, "minimum_detectable_degradation",
                        RegressionRule.minimumDetectableDegradation(
                                in.get("baseline_trials").asInt(), in.get("test_samples").asInt(),
                                in.get("alpha").asDouble(), in.get("baseline_rate").asDouble(),
                                in.get("power").asDouble()), tol);
                default -> throw new IllegalStateException("unknown approach in " + c.get("name"));
            }
        });
    }

    static List<CaseCheck> riskDrivenSizing() {
        String s = "risk_driven_sizing";
        return suite(s, (recorder, c, tol) -> {
            var in = c.get("inputs");
            double rate = in.has("baseline_rate")
                    ? in.get("baseline_rate").asDouble()
                    : in.get("baseline_successes").asDouble() / in.get("baseline_trials").asInt();
            int nb = in.get("baseline_trials").asInt();
            double alpha = in.get("alpha").asDouble();
            Optional<RegressionSizing.Refusal> refusal = RegressionSizing.checkDomain(
                    rate, nb,
                    in.has("design_alternative_rate")
                            ? OptionalDouble.of(in.get("design_alternative_rate").asDouble())
                            : OptionalDouble.empty(),
                    in.has("test_samples")
                            ? OptionalInt.of(in.get("test_samples").asInt())
                            : OptionalInt.empty());
            if (refusal.isPresent()) {
                assertRefused(recorder, c, refusal.get());
                return;
            }
            switch (c.get("approach").asText()) {
                case "required_n" -> {
                    var sized = RegressionSizing.designRequiredSamples(
                            rate, nb, in.get("design_alternative_rate").asDouble(), alpha,
                            in.get("target_power").asDouble());
                    if (sized.isEmpty()) {
                        assertRefused(recorder, c, RegressionSizing.Refusal.BASELINE_TOO_SMALL);
                        return;
                    }
                    assertOracle(recorder, s, c, "required_n", sized.get().requiredSamples());
                    assertOracle(recorder, s, c, "achieved_power", sized.get().power(), tol);
                }
                case "power_at" -> assertOracle(recorder, s, c, "power",
                        RegressionSizing.designPowerAt(in.get("test_samples").asInt(), rate, nb,
                                in.get("design_alternative_rate").asDouble(), alpha), tol);
                case "detectable_rate" -> assertOracle(recorder, s, c, "detectable_rate",
                        RegressionSizing.designDetectableRate(in.get("test_samples").asInt(), rate, nb,
                                alpha, in.get("target_power").asDouble()), tol);
                case "resolved_required_n" -> {
                    var sized = RegressionSizing.resolvedSizing(
                            in.get("baseline_successes").asInt(), nb,
                            in.get("design_alternative_rate").asDouble(), alpha,
                            in.get("target_power").asDouble());
                    if (sized.isEmpty()) {
                        assertRefused(recorder, c, RegressionSizing.Refusal.BASELINE_TOO_SMALL);
                        return;
                    }
                    assertOracle(recorder, s, c, "required_n", sized.get().requiredSamples());
                    assertOracle(recorder, s, c, "resolved_power", sized.get().power(), tol);
                    assertOracle(recorder, s, c, "first_crossing", sized.get().firstCrossing());
                }
                case "resolved_power_at" -> {
                    int kb = in.get("baseline_successes").asInt();
                    int nt = in.get("test_samples").asInt();
                    assertOracle(recorder, s, c, "cutoff_integer", RegressionRule.cutoff(kb, nb, nt, alpha));
                    assertOracle(recorder, s, c, "resolved_power", RegressionRule.resolvedPower(
                            kb, nb, nt, alpha, in.get("design_alternative_rate").asDouble()), tol);
                }
                default -> throw new IllegalStateException("unknown approach in " + c.get("name"));
            }
            assertOracle(recorder, s, c, "sizing_gate", "ADMIT");
        });
    }

    /** A refused design: its category, and no number for any other field. */
    private static void assertRefused(
            ConformanceRecorder recorder, JsonNode c, RegressionSizing.Refusal refusal) {
        String s = "risk_driven_sizing";
        assertOracle(recorder, s, c, "sizing_gate", "REFUSE");
        assertOracle(recorder, s, c, "refusal_category", refusal.name());
        c.get("expected").fieldNames().forEachRemaining(field -> {
            if (!field.equals("sizing_gate") && !field.equals("refusal_category")) {
                assertOracle(recorder, s, c, field, null);
            }
        });
    }

    // ── Latency ─────────────────────────────────────────────────────

    static List<CaseCheck> latencyPercentile() {
        String s = "latency_percentile";
        return suite(s, (recorder, c, tol) -> {
            double[] latencies = ConformanceFixtures.toDoubleArray(c.get("inputs").get("latencies"));
            if (c.get("expected").has("value")) {
                assertOracle(recorder, s, c, "value",
                        nearestRankPercentile(latencies, c.get("inputs").get("percentile").asDouble()), tol);
            }
            if (c.get("expected").has("mean")) {
                assertOracle(recorder, s, c, "mean",
                        org.mavai.punit.statistics.LatencyStatistics.mean(latencies), tol);
                assertOracle(recorder, s, c, "max",
                        org.mavai.punit.statistics.LatencyStatistics.max(latencies), tol);
            }
        });
    }

    /**
     * Exact equality throughout (tolerance 0). The emission minimums are
     * read from the artefact writers' own gate.
     */
    static List<CaseCheck> latencyPercentileMinimums() {
        String s = "latency_percentile_minimums";
        return suite(s, (recorder, c, tol) -> {
            var in = c.get("inputs");
            switch (c.get("approach").asText()) {
                case "emission_non_degeneracy" -> assertOracle(recorder, s, c,
                        "minimum_contributing_samples",
                        LatencySection.minimumSamplesFor(ProductionPath.key(
                                in.get("percentile").asDouble()).detailKey()));
                case "nondegeneracy_planning" -> {
                    var p = LatencyRules.planNondegeneracy(in.get("percentile").asDouble(),
                            in.get("planned_samples").asInt(), in.get("baseline_success_rate").asDouble());
                    assertOracle(recorder, s, c, "expected_test_samples", p.expectedTestSamples());
                    assertOracle(recorder, s, c, "minimum_contributing_samples",
                            p.minimumContributingSamples());
                    assertOracle(recorder, s, c, "warning", p.warning());
                    assertOracle(recorder, s, c, "planned_samples_needed", p.plannedSamplesNeeded());
                }
                case "nondegeneracy_decision" -> {
                    var d = LatencyRules.decideNondegeneracy(in.get("percentile").asDouble(),
                            in.get("test_samples").asInt(),
                            "VERIFICATION".equals(in.get("intent").asText()),
                            "explicit".equals(in.get("threshold_source").asText())
                                    ? LatencyRules.ThresholdSource.EXPLICIT
                                    : LatencyRules.ThresholdSource.BASELINE_DERIVED);
                    assertOracle(recorder, s, c, "applies", d.applies());
                    assertOracle(recorder, s, c, "degenerate", d.degenerate());
                    assertOracle(recorder, s, c, "outcome", d.outcome().name());
                }
                case "precedence_existence" -> {
                    OptionalInt rank = LatencyRules.precedenceRank(in.get("baseline_trials").asInt(),
                            in.get("test_samples").asInt(), in.get("percentile").asDouble(),
                            in.get("alpha").asDouble());
                    assertOracle(recorder, s, c, "saturated", rank.isEmpty());
                    assertOracle(recorder, s, c, "rank", rank);
                }
                case "precedence_planning" -> {
                    var p = LatencyRules.planPrecedence(in.get("baseline_trials").asInt(),
                            in.get("planned_samples").asInt(), in.get("baseline_success_rate").asDouble(),
                            in.get("percentile").asDouble(), in.get("alpha").asDouble());
                    assertOracle(recorder, s, c, "expected_test_samples", p.expectedTestSamples());
                    assertOracle(recorder, s, c, "warning", p.warning());
                    assertOracle(recorder, s, c, "planning_rank", p.planningRank());
                    assertOracle(recorder, s, c, "minimum_baseline_trials", p.minimumBaselineTrials());
                }
                default -> throw new IllegalStateException("unknown approach in " + c.get("name"));
            }
        });
    }

    /**
     * The design rule judged by the engine's pre-run refusal on the two
     * samplings; the precedence rank on the successful latencies.
     */
    static List<CaseCheck> latencyThreshold() {
        String s = "latency_threshold";
        return suite(s, (recorder, c, tol) -> {
            var in = c.get("inputs");
            double p = in.get("p").asDouble();
            double alpha = in.get("alpha").asDouble();
            double[] baseline = ConformanceFixtures.toDoubleArray(in.get("baseline_latencies"));
            LatencyStatistics stats = ProductionPath.latencyBaseline(
                    baseline, in.get("baseline_samples").asInt());
            List<String> errors = ProductionPath.latencyRefusal(
                    ProductionPath.baselineDerivedDeclaration(p, alpha),
                    in.get("planned_samples").asInt(), TestIntent.VERIFICATION, Optional.of(stats));
            assertOracle(recorder, s, c, "configuration_error", errors);
            if (!errors.isEmpty()) {
                for (String field : List.of("rank", "threshold", "saturated")) {
                    assertOracle(recorder, s, c, field, null);
                }
                return;
            }
            var t = LatencyRules.derivePrecedenceThreshold(baseline, in.get("test_samples").asInt(), p, alpha);
            assertOracle(recorder, s, c, "rank", t.rank());
            assertOracle(recorder, s, c, "threshold", t.threshold(), tol);
            assertOracle(recorder, s, c, "saturated", t.saturated());
            assertOracle(recorder, s, c, "breach_probability", t.breachProbability(), tol);
            assertOracle(recorder, s, c, "test_rank", t.testRank());
            assertOracle(recorder, s, c, "n", t.n());
            assertOracle(recorder, s, c, "baseline_percentile", t.baselinePercentile(), tol);
        });
    }

    /**
     * Refusal by the engine's pre-run hook on the planned samples; the
     * verdict by the criterion's evaluation on the successful latencies.
     */
    static List<CaseCheck> latencyComplianceDecision() {
        String s = "latency_compliance_decision";
        return suite(s, (recorder, c, tol) -> {
            var in = c.get("inputs");
            double p = in.get("percentile").asDouble();
            long threshold = in.get("threshold_ms").asLong();
            double alpha = in.get("alpha").asDouble();
            TestIntent intent = TestIntent.valueOf(in.get("intent").asText());
            List<String> errors = ProductionPath.latencyRefusal(
                    ProductionPath.explicitDeclaration(p, threshold, alpha),
                    in.get("planned_samples").asInt(), intent, Optional.empty());
            assertOracle(recorder, s, c, "configuration_error", errors);
            if (!errors.isEmpty()) {
                for (String field : List.of("test_samples", "within_threshold", "y_min",
                        "pass_possible", "verdict")) {
                    assertOracle(recorder, s, c, field, null);
                }
                return;
            }
            double[] latencies = ConformanceFixtures.toDoubleArray(in.get("latencies"));
            CriterionResult r = ProductionPath.decide(
                    ProductionPath.explicit(p, threshold, alpha), latencies, null, intent);
            String k = ProductionPath.key(p).detailKey();
            Map<String, Object> d = r.detail();
            assertThat(d.get("decisionRule." + k))
                    .isEqualTo(DecisionRule.LATENCY_COMPLIANCE_EXACT_BINOMIAL.id());
            assertOracle(recorder, s, c, "test_samples", d.get("successfulSamples"));
            assertOracle(recorder, s, c, "within_threshold", d.get("withinThreshold." + k));
            assertOracle(recorder, s, c, "y_min", d.get("requiredWithin." + k));
            assertOracle(recorder, s, c, "pass_possible", d.containsKey("requiredWithin." + k));
            assertOracle(recorder, s, c, "verdict", d.get("verdict." + k));
            assertOracle(recorder, s, c, "false_compliance", d.get("falseCompliance." + k), tol);
            assertOracle(recorder, s, c, "clopper_pearson_lower", d.get("clopperPearsonLower." + k), tol);
            assertOracle(recorder, s, c, "observed_percentile_ms", d.get("observed." + k), tol);
            assertOracle(recorder, s, c, "raw_percentile_pass", d.get("rawPercentilePass." + k));
        });
    }

    // ── Verdicts and their composition ──────────────────────────────

    static List<CaseCheck> verdict() {
        String s = "verdict";
        return suite(s, (recorder, c, tol) -> {
            String approach = c.has("approach") ? c.get("approach").asText() : "";
            switch (approach) {
                case "test_verdict" -> testVerdict(recorder, c, tol);
                case "two_criteria" -> twoCriteria(recorder, c, tol);
                default -> singleCriterion(recorder, c, tol);
            }
        });
    }

    private static ProductionPath.Bar bar(JsonNode in, String id, String alphaKey) {
        return in.has("baseline_trials")
                ? new ProductionPath.Baseline(id, in.get("baseline_successes").asInt(),
                        in.get("baseline_trials").asInt(), in.get(alphaKey).asDouble())
                : new ProductionPath.Requirement(id, in.get("threshold").asDouble(),
                        in.get(alphaKey).asDouble());
    }

    private static TestIntent intent(JsonNode in) {
        return in.has("intent") ? TestIntent.valueOf(in.get("intent").asText()) : TestIntent.VERIFICATION;
    }

    private static void singleCriterion(ConformanceRecorder recorder, JsonNode c, double tol) {
        String s = "verdict";
        var in = c.get("inputs");
        ProbabilisticTestResult result = ProductionPath.run(
                List.of(bar(in, "c", "alpha")), in.get("trials").asInt(), in.get("successes").asInt(),
                intent(in));
        List<String> errors = ProductionPath.errors(result);
        assertOracle(recorder, s, c, "configuration_error", errors);
        assertOracle(recorder, s, c, "observed_rate",
                in.get("successes").asDouble() / in.get("trials").asInt(), tol);
        if (!errors.isEmpty()) {
            assertOracle(recorder, s, c, "verdict", null);
            return;
        }
        assertOracle(recorder, s, c, "verdict", result.verdict().name());
        assertThat(ProductionPath.decision(result, "c").get("decisionRule"))
                .isEqualTo(c.get("decisionRule").asText());
    }

    /**
     * A requirement and a baseline on the same postconditions: two
     * criteria, refused whole when any part is invalid, otherwise each
     * decided by its own rule and composed structurally.
     */
    private static void twoCriteria(ConformanceRecorder recorder, JsonNode c, double tol) {
        String s = "verdict";
        var in = c.get("inputs");
        String complianceId = in.get("compliance_criterion").asText();
        String regressionId = in.get("regression_criterion").asText();
        ProbabilisticTestResult result = ProductionPath.run(
                List.of(new ProductionPath.Requirement(complianceId, in.get("threshold").asDouble(),
                                in.get("compliance_alpha").asDouble()),
                        new ProductionPath.Baseline(regressionId, in.get("baseline_successes").asInt(),
                                in.get("baseline_trials").asInt(), in.get("regression_alpha").asDouble())),
                in.get("trials").asInt(), in.get("successes").asInt(), intent(in));
        List<String> errors = ProductionPath.errors(result);
        assertOracle(recorder, s, c, "configuration_error", errors);
        assertOracle(recorder, s, c, "observed_rate",
                in.get("successes").asDouble() / in.get("trials").asInt(), tol);
        if (!errors.isEmpty()) {
            assertOracle(recorder, s, c, "verdict", null);
            assertOracle(recorder, s, c, "criteria", List.of());
            assertOracle(recorder, s, c, "triggering_criteria", List.of());
            assertOracle(recorder, s, c, "false_compliance_envelope", null);
            assertOracle(recorder, s, c, "false_degradation_signal_envelope", null);
            return;
        }
        assertOracle(recorder, s, c, "verdict", result.verdict().name());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (var row : result.perCriterionEvaluation().perCriterionVerdicts()) {
            Map<?, ?> d = ProductionPath.decision(result, row.criterionId());
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("criterion_id", row.criterionId());
            r.put("procedure", DecisionRule.COMPLIANCE_EXACT_BINOMIAL.id().equals(d.get("decisionRule"))
                    ? "COMPLIANCE" : "REGRESSION");
            r.put("decisionRule", d.get("decisionRule"));
            r.put("alpha", d.get("alpha"));
            r.put("verdict", row.verdict().name());
            rows.add(r);
        }
        assertOracle(recorder, s, c, "criteria", rows, tol);
        VerdictComposition composition = result.composition().orElseThrow();
        assertOracle(recorder, s, c, "triggering_criteria", composition.triggering().stream()
                .map(VerdictComposition.Trigger::id).toList());
        assertOracle(recorder, s, c, "false_compliance_envelope",
                composition.falseComplianceEnvelope(), tol);
        assertOracle(recorder, s, c, "false_degradation_signal_envelope",
                composition.falseDegradationSignalEnvelope(), tol);
    }

    /**
     * {@code V_test}: the functional criterion through the engine, every
     * latency constraint through the criterion's evaluation, composed by
     * the rule the engine composes with over the dimensions the case's
     * {@code advisory} setting leaves enforced. An advisory dimension is
     * decided exactly as an enforced one.
     */
    private static void testVerdict(ConformanceRecorder recorder, JsonNode c, double tol) {
        String s = "verdict";
        var in = c.get("inputs");
        List<VerdictComposition.Decided> criteria = new ArrayList<>();
        List<Map<String, Object>> criteriaRows = new ArrayList<>();
        if (in.has("functional")) {
            var f = in.get("functional");
            String id = f.get("criterion_id").asText();
            ProbabilisticTestResult result = ProductionPath.run(
                    List.of(bar(f, id, "alpha")), f.get("trials").asInt(), f.get("successes").asInt(),
                    TestIntent.VERIFICATION);
            var row = result.perCriterionEvaluation().perCriterionVerdicts().get(0);
            Map<?, ?> d = ProductionPath.decision(result, id);
            criteria.add(new VerdictComposition.Decided(row.criterionId(), row.verdict(),
                    DecisionRule.fromId(String.valueOf(d.get("decisionRule"))),
                    OptionalDouble.of(((Number) d.get("alpha")).doubleValue())));
            criteriaRows.add(Map.of("criterion_id", row.criterionId(), "verdict", row.verdict().name()));
        }
        List<VerdictComposition.Decided> constraints = new ArrayList<>();
        List<Map<String, Object>> constraintRows = new ArrayList<>();
        for (JsonNode constraint : in.get("latency_constraints")) {
            String id = constraint.get("constraint_id").asText();
            double p = constraint.get("percentile").asDouble();
            double alpha = constraint.get("alpha").asDouble();
            boolean explicit = "explicit".equals(constraint.get("source").asText());
            double[] latencies = ConformanceFixtures.toDoubleArray(constraint.get("latencies"));
            LatencyStatistics baseline = explicit ? null : ProductionPath.latencyBaseline(
                    ConformanceFixtures.toDoubleArray(constraint.get("baseline_latencies")),
                    constraint.get("baseline_latencies").size());
            CriterionResult r = ProductionPath.decide(explicit
                            ? ProductionPath.explicit(p, constraint.get("threshold_ms").asLong(), alpha)
                            : ProductionPath.baselineDerived(p, alpha),
                    latencies, baseline, TestIntent.VERIFICATION);
            String k = ProductionPath.key(p).detailKey();
            Verdict v = Verdict.valueOf(String.valueOf(r.detail().get("verdict." + k)));
            String rule = String.valueOf(r.detail().get("decisionRule." + k));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("constraint_id", id);
            row.put("source", constraint.get("source").asText());
            row.put("decisionRule", rule);
            row.put("verdict", v.name());
            constraintRows.add(row);
            constraints.add(new VerdictComposition.Decided(id, v, DecisionRule.fromId(rule),
                    OptionalDouble.of(alpha)));
        }
        List<String> advisory = new ArrayList<>();
        in.get("advisory").forEach(dimension -> advisory.add(dimension.asText()));
        VerdictComposition composition = VerdictComposition.compose(criteria, constraints,
                AssertionEnforcement.parse(String.join(",", advisory)));
        assertOracle(recorder, s, c, "criteria", criteriaRows);
        assertOracle(recorder, s, c, "latency_constraints", constraintRows);
        assertOracle(recorder, s, c, "rate_verdict", composition.rateVerdict().map(Enum::name));
        assertOracle(recorder, s, c, "latency_verdict", composition.latencyVerdict().map(Enum::name));
        assertOracle(recorder, s, c, "functional_mode", composition.functionalMode().map(EnforcementMode::label));
        assertOracle(recorder, s, c, "latency_mode", composition.latencyMode().map(EnforcementMode::label));
        assertOracle(recorder, s, c, "test_verdict", composition.testVerdict().name());
        assertOracle(recorder, s, c, "triggering", composition.triggering().stream()
                .map(t -> Map.of("kind", t.kind().name().toLowerCase(java.util.Locale.ROOT),
                        "id", t.id()))
                .toList());
    }

    private static double nearestRankPercentile(double[] latencies, double p) {
        return org.mavai.punit.statistics.LatencyStatistics.nearestRankPercentile(latencies, p);
    }
}
