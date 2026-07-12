package org.mavai.punit.statistics.conformance;

import com.fasterxml.jackson.databind.JsonNode;
import org.mavai.punit.api.PercentileKey;
import org.mavai.punit.api.TestIntent;
import org.mavai.punit.api.spec.CriterionResult;
import org.mavai.punit.api.spec.PercentileLatency;
import org.mavai.punit.api.spec.Verdict;
import org.mavai.punit.internal.engine.emit.LatencySection;
import org.mavai.punit.statistics.BinomialProportionEstimator;
import org.mavai.punit.statistics.DerivationContext;
import org.mavai.punit.statistics.DerivedThreshold;
import org.mavai.punit.statistics.LatencyStatistics;
import org.mavai.punit.statistics.LatencyThresholdDeriver;
import org.mavai.punit.statistics.OperationalApproach;
import org.mavai.punit.statistics.RiskDrivenSizingCalculator;
import org.mavai.punit.statistics.SampleSizeCalculator;
import org.mavai.punit.statistics.SampleSizeRequirement;
import org.mavai.punit.statistics.TestVerdictEvaluator;
import org.mavai.punit.statistics.ThresholdDeriver;
import org.mavai.punit.statistics.VerdictWithConfidence;
import org.mavai.punit.statistics.VerificationFeasibilityEvaluator;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mavai.punit.statistics.conformance.OracleAssert.assertOracle;

/**
 * The registry of every conformance check punit runs against the mavai-R
 * fixtures — one {@link CaseCheck} per fixture case (occasionally two,
 * when a case is verified on both a component surface and the production
 * verdict path).
 *
 * <p>Two consumers execute the same checks:
 *
 * <ul>
 *   <li>the per-suite display tests ({@code ProportionConformanceTest},
 *       {@code LatencyConformanceTest}, {@code DecisionRuleConformanceTest})
 *       turn each check into a {@code DynamicTest} for per-case reporting;</li>
 *   <li>{@code ConformanceCoverageTest} re-runs the whole catalog with a
 *       collecting {@link ConformanceRecorder} and diffs the recorded
 *       {@code (suite, case, binding-field)} triples against the
 *       manifest's obligations. Because the coverage test re-executes the
 *       checks itself, its verdict is deterministic regardless of test
 *       ordering, filtering, or Gradle fork configuration — there is no
 *       shared mutable ledger to lose.</li>
 * </ul>
 */
final class ConformanceCatalog {

    @FunctionalInterface
    interface Check {
        void run(ConformanceRecorder recorder) throws Exception;
    }

    record CaseCheck(String suite, String caseName, Check check) { }

    private static final BinomialProportionEstimator ESTIMATOR = new BinomialProportionEstimator();
    private static final ThresholdDeriver THRESHOLD_DERIVER = new ThresholdDeriver();
    private static final SampleSizeCalculator SAMPLE_SIZE_CALCULATOR = new SampleSizeCalculator();
    private static final RiskDrivenSizingCalculator RISK_DRIVEN_SIZING = new RiskDrivenSizingCalculator();
    private static final TestVerdictEvaluator VERDICT_EVALUATOR = new TestVerdictEvaluator();

    private ConformanceCatalog() { }

    /** Every check in the catalog — the coverage test's execution plan. */
    static List<CaseCheck> all() {
        List<CaseCheck> checks = new ArrayList<>();
        checks.addAll(wilsonCi());
        checks.addAll(wilsonLower());
        checks.addAll(thresholdDerivation());
        checks.addAll(powerAnalysis());
        checks.addAll(riskDrivenSizing());
        checks.addAll(feasibility());
        checks.addAll(verdict());
        checks.addAll(latencyPercentileValues());
        checks.addAll(latencyPercentileSummaries());
        checks.addAll(latencyThreshold());
        checks.addAll(latencyThresholdBootstrap());
        checks.addAll(latencyThresholdBootstrapProductionPath());
        checks.addAll(latencyPercentileEmissionMinimums());
        checks.addAll(latencyBoundExistenceMinimums());
        checks.addAll(regressionDecisionDerivation());
        checks.addAll(regressionDecisionProductionVerdicts());
        return checks;
    }

    // ── wilson_ci ───────────────────────────────────────────────────

    static List<CaseCheck> wilsonCi() {
        JsonNode suite = ConformanceFixtures.load("wilson_ci.json");
        double tolerance = suite.get("tolerance").asDouble();
        List<CaseCheck> checks = new ArrayList<>();
        for (JsonNode c : suite.get("cases")) {
            checks.add(new CaseCheck("wilson_ci", c.get("name").asText(), recorder -> {
                var inputs = c.get("inputs");
                var result = ESTIMATOR.estimate(
                        inputs.get("successes").asInt(),
                        inputs.get("trials").asInt(),
                        inputs.get("confidence").asDouble());
                assertOracle(recorder, "wilson_ci", c, "lower", result.lowerBound(), tolerance);
                assertOracle(recorder, "wilson_ci", c, "upper", result.upperBound(), tolerance);
                assertOracle(recorder, "wilson_ci", c, "point", result.pointEstimate(), tolerance);
            }));
        }
        return checks;
    }

    // ── wilson_lower ────────────────────────────────────────────────

    static List<CaseCheck> wilsonLower() {
        JsonNode suite = ConformanceFixtures.load("wilson_lower.json");
        double tolerance = suite.get("tolerance").asDouble();
        List<CaseCheck> checks = new ArrayList<>();
        for (JsonNode c : suite.get("cases")) {
            checks.add(new CaseCheck("wilson_lower", c.get("name").asText(), recorder -> {
                var inputs = c.get("inputs");
                double result = ESTIMATOR.lowerBound(
                        inputs.get("successes").asInt(),
                        inputs.get("trials").asInt(),
                        inputs.get("confidence").asDouble());
                assertOracle(recorder, "wilson_lower", c, "lower_bound", result, tolerance);
            }));
        }
        return checks;
    }

    // ── threshold_derivation ────────────────────────────────────────

    static List<CaseCheck> thresholdDerivation() {
        JsonNode suite = ConformanceFixtures.load("threshold_derivation.json");
        double tolerance = suite.get("tolerance").asDouble();
        List<CaseCheck> checks = new ArrayList<>();
        for (JsonNode c : suite.get("cases")) {
            String approach = c.get("approach").asText();
            checks.add(new CaseCheck("threshold_derivation", c.get("name").asText(), recorder -> {
                var inputs = c.get("inputs");
                int baselineSuccesses = inputs.get("baseline_successes").asInt();
                int baselineTrials = inputs.get("baseline_trials").asInt();

                if ("sample_size_first".equals(approach)) {
                    DerivedThreshold result = THRESHOLD_DERIVER.deriveSampleSizeFirst(
                            baselineTrials, baselineSuccesses,
                            inputs.get("test_samples").asInt(),
                            inputs.get("confidence").asDouble());
                    assertOracle(recorder, "threshold_derivation", c, "threshold",
                            result.value(), tolerance);
                    // The real-valued Wilson lower bound at n_test IS the
                    // derived threshold in the sample-size-first construction.
                    assertOracle(recorder, "threshold_derivation", c, "wilson_lower_real",
                            result.value(), tolerance);
                    // The integer cutoff and achieved size are the binding
                    // decision artefacts of the regression procedure; the
                    // derivation must produce them alongside the real-valued
                    // threshold. Looked up reflectively so this check compiles
                    // (and fails red, not red-compile) while the deriver does
                    // not yet expose them — the Java analogue of baseltest's
                    // getattr(result, "cutoff", None).
                    assertOracle(recorder, "threshold_derivation", c, "cutoff_integer",
                            derivedArtefact(result, "cutoff"));
                    assertOracle(recorder, "threshold_derivation", c, "achieved_size",
                            derivedArtefact(result, "achievedSize"), tolerance);
                } else if ("threshold_first".equals(approach)) {
                    // testSamples is not used in the threshold-first implied
                    // confidence computation but is required by the API; use
                    // baseline trials as a reasonable value.
                    DerivedThreshold result = THRESHOLD_DERIVER.deriveThresholdFirst(
                            baselineTrials, baselineSuccesses, baselineTrials,
                            inputs.get("threshold").asDouble());
                    assertOracle(recorder, "threshold_derivation", c, "implied_confidence",
                            result.context().confidence(), tolerance);
                    assertOracle(recorder, "threshold_derivation", c, "is_sound",
                            result.isStatisticallySound());
                }
            }));
        }
        return checks;
    }

    // ── power_analysis ──────────────────────────────────────────────

    static List<CaseCheck> powerAnalysis() {
        JsonNode suite = ConformanceFixtures.load("power_analysis.json");
        double tolerance = suite.get("tolerance").asDouble();
        List<CaseCheck> checks = new ArrayList<>();
        for (JsonNode c : suite.get("cases")) {
            checks.add(new CaseCheck("power_analysis", c.get("name").asText(), recorder -> {
                var inputs = c.get("inputs");
                double baselineRate = inputs.get("baseline_rate").asDouble();
                double minDetectableEffect = inputs.get("min_detectable_effect").asDouble();
                double confidence = inputs.get("confidence").asDouble();

                SampleSizeRequirement result = SAMPLE_SIZE_CALCULATOR.calculateForPower(
                        baselineRate, minDetectableEffect, confidence,
                        inputs.get("power").asDouble());
                assertOracle(recorder, "power_analysis", c, "required_samples",
                        result.requiredSamples());

                double achievedPower = SAMPLE_SIZE_CALCULATOR.calculateAchievedPower(
                        result.requiredSamples(), baselineRate, minDetectableEffect, confidence);
                assertOracle(recorder, "power_analysis", c, "achieved_power",
                        achievedPower, tolerance);
            }));
        }
        return checks;
    }

    // ── risk_driven_sizing ──────────────────────────────────────────

    /**
     * Sizing against the moving acceptance floor. Three case groups,
     * discriminated by the {@code approach} field: required-n cases bind
     * the minimal sample size plus the floor and achieved power at that
     * size; power-at cases bind the floor and self-consistent power at a
     * fixed candidate size; detectable-rate cases bind the inversion.
     * The floor is asserted through the same Wilson-from-rate machinery
     * the calculator itself reuses, so the shared-z requirement is
     * exercised on the production path.
     */
    static List<CaseCheck> riskDrivenSizing() {
        JsonNode suite = ConformanceFixtures.load("risk_driven_sizing.json");
        double tolerance = suite.get("tolerance").asDouble();
        List<CaseCheck> checks = new ArrayList<>();
        for (JsonNode c : suite.get("cases")) {
            checks.add(new CaseCheck("risk_driven_sizing", c.get("name").asText(),
                    recorder -> assertRiskDrivenSizingCase(recorder, c, tolerance)));
        }
        return checks;
    }

    private static void assertRiskDrivenSizingCase(
            ConformanceRecorder recorder, JsonNode c, double tolerance) {
        switch (c.get("approach").asText()) {
            case "required_n" -> assertRequiredSampleSizeCase(recorder, c, tolerance);
            case "power_at" -> assertPowerAtCandidateSizeCase(recorder, c, tolerance);
            case "detectable_rate" -> assertDetectableRateInversionCase(recorder, c, tolerance);
            default -> throw new IllegalStateException(
                    "unknown sizing approach '%s' in case '%s'".formatted(
                            c.get("approach").asText(), c.get("name").asText()));
        }
    }

    private static void assertRequiredSampleSizeCase(
            ConformanceRecorder recorder, JsonNode c, double tolerance) {
        var inputs = c.get("inputs");
        double baselineRate = inputs.get("baseline_rate").asDouble();
        double confidence = inputs.get("confidence").asDouble();
        double minimumAcceptableRate = inputs.get("minimum_acceptable_rate").asDouble();
        int requiredSamples = RISK_DRIVEN_SIZING.requiredSamples(
                baselineRate, minimumAcceptableRate, confidence,
                inputs.get("target_power").asDouble());
        assertOracle(recorder, "risk_driven_sizing", c, "required_n", requiredSamples);
        assertOracle(recorder, "risk_driven_sizing", c, "floor",
                ESTIMATOR.lowerBoundFromRate(baselineRate, requiredSamples, confidence),
                tolerance);
        assertOracle(recorder, "risk_driven_sizing", c, "achieved_power",
                RISK_DRIVEN_SIZING.powerAt(requiredSamples, baselineRate,
                        minimumAcceptableRate, confidence),
                tolerance);
    }

    private static void assertPowerAtCandidateSizeCase(
            ConformanceRecorder recorder, JsonNode c, double tolerance) {
        var inputs = c.get("inputs");
        double baselineRate = inputs.get("baseline_rate").asDouble();
        double confidence = inputs.get("confidence").asDouble();
        int testSamples = inputs.get("test_samples").asInt();
        assertOracle(recorder, "risk_driven_sizing", c, "floor",
                ESTIMATOR.lowerBoundFromRate(baselineRate, testSamples, confidence),
                tolerance);
        assertOracle(recorder, "risk_driven_sizing", c, "power",
                RISK_DRIVEN_SIZING.powerAt(testSamples, baselineRate,
                        inputs.get("minimum_acceptable_rate").asDouble(), confidence),
                tolerance);
    }

    private static void assertDetectableRateInversionCase(
            ConformanceRecorder recorder, JsonNode c, double tolerance) {
        var inputs = c.get("inputs");
        assertOracle(recorder, "risk_driven_sizing", c, "detectable_rate",
                RISK_DRIVEN_SIZING.detectableRate(
                        inputs.get("test_samples").asInt(),
                        inputs.get("baseline_rate").asDouble(),
                        inputs.get("confidence").asDouble(),
                        inputs.get("target_power").asDouble()),
                tolerance);
    }

    // ── feasibility ─────────────────────────────────────────────────

    static List<CaseCheck> feasibility() {
        JsonNode suite = ConformanceFixtures.load("feasibility.json");
        List<CaseCheck> checks = new ArrayList<>();
        for (JsonNode c : suite.get("cases")) {
            checks.add(new CaseCheck("feasibility", c.get("name").asText(), recorder -> {
                var inputs = c.get("inputs");
                var result = VerificationFeasibilityEvaluator.evaluate(
                        inputs.get("sample_size").asInt(),
                        inputs.get("target_proportion").asDouble(),
                        inputs.get("confidence").asDouble());
                assertOracle(recorder, "feasibility", c, "feasible", result.feasible());
                assertOracle(recorder, "feasibility", c, "minimum_samples", result.minimumSamples());
                assertOracle(recorder, "feasibility", c, "criterion",
                        result.criterion().toLowerCase().replaceAll("[\\s-]+", "_"));
            }));
        }
        return checks;
    }

    // ── verdict ─────────────────────────────────────────────────────

    static List<CaseCheck> verdict() {
        JsonNode suite = ConformanceFixtures.load("verdict.json");
        double tolerance = suite.get("tolerance").asDouble();
        List<CaseCheck> checks = new ArrayList<>();
        for (JsonNode c : suite.get("cases")) {
            checks.add(new CaseCheck("verdict", c.get("name").asText(), recorder -> {
                var inputs = c.get("inputs");
                int successes = inputs.get("successes").asInt();
                int trials = inputs.get("trials").asInt();
                double threshold = inputs.get("threshold").asDouble();
                double confidence = inputs.get("confidence").asDouble();

                var context = new DerivationContext(threshold, trials, trials, confidence);
                var derivedThreshold = new DerivedThreshold(
                        threshold, OperationalApproach.SAMPLE_SIZE_FIRST, context);

                VerdictWithConfidence verdict = VERDICT_EVALUATOR.evaluate(
                        successes, trials, derivedThreshold);
                assertOracle(recorder, "verdict", c, "passed", verdict.passed());
                assertOracle(recorder, "verdict", c, "observed_rate",
                        verdict.observedRate(), tolerance);

                double observedRate = (double) successes / trials;
                double z = ESTIMATOR.zTestStatistic(observedRate, threshold, trials);
                assertOracle(recorder, "verdict", c, "test_statistic", z, tolerance);
                assertOracle(recorder, "verdict", c, "p_value",
                        ESTIMATOR.oneSidedPValue(z), tolerance);
                // false_positive_probability in the reference data is alpha = 1 - confidence.
                assertOracle(recorder, "verdict", c, "false_positive_probability",
                        1.0 - confidence, tolerance);
            }));
        }
        return checks;
    }

    // ── latency_percentile ──────────────────────────────────────────

    static List<CaseCheck> latencyPercentileValues() {
        JsonNode suite = ConformanceFixtures.load("latency_percentile.json");
        double tolerance = suite.get("tolerance").asDouble();
        List<CaseCheck> checks = new ArrayList<>();
        for (JsonNode c : suite.get("cases")) {
            if (!c.get("inputs").has("percentile")) {
                continue; // summary case
            }
            checks.add(new CaseCheck("latency_percentile", c.get("name").asText(), recorder -> {
                double[] latencies = ConformanceFixtures.toDoubleArray(c.get("inputs").get("latencies"));
                double result = LatencyStatistics.nearestRankPercentile(
                        latencies, c.get("inputs").get("percentile").asDouble());
                assertOracle(recorder, "latency_percentile", c, "value", result, tolerance);
            }));
        }
        return checks;
    }

    static List<CaseCheck> latencyPercentileSummaries() {
        JsonNode suite = ConformanceFixtures.load("latency_percentile.json");
        double tolerance = suite.get("tolerance").asDouble();
        List<CaseCheck> checks = new ArrayList<>();
        for (JsonNode c : suite.get("cases")) {
            if (c.get("inputs").has("percentile")) {
                continue; // percentile case
            }
            checks.add(new CaseCheck("latency_percentile", c.get("name").asText(), recorder -> {
                double[] latencies = ConformanceFixtures.toDoubleArray(c.get("inputs").get("latencies"));
                assertOracle(recorder, "latency_percentile", c, "mean",
                        LatencyStatistics.mean(latencies), tolerance);
                assertOracle(recorder, "latency_percentile", c, "max",
                        LatencyStatistics.max(latencies), tolerance);
            }));
        }
        return checks;
    }

    // ── latency_threshold ───────────────────────────────────────────

    static List<CaseCheck> latencyThreshold() {
        JsonNode suite = ConformanceFixtures.load("latency_threshold.json");
        double tolerance = suite.get("tolerance").asDouble();
        List<CaseCheck> checks = new ArrayList<>();
        for (JsonNode c : suite.get("cases")) {
            checks.add(new CaseCheck("latency_threshold", c.get("name").asText(), recorder -> {
                var inputs = c.get("inputs");
                LatencyThresholdDeriver.Threshold result = LatencyThresholdDeriver.derive(
                        ConformanceFixtures.toDoubleArray(inputs.get("baseline_latencies")),
                        inputs.get("p").asDouble(),
                        inputs.get("confidence").asDouble());
                assertOracle(recorder, "latency_threshold", c, "rank", result.rank());
                assertOracle(recorder, "latency_threshold", c, "threshold",
                        result.threshold(), tolerance);
                assertOracle(recorder, "latency_threshold", c, "baseline_percentile",
                        result.baselinePercentile(), tolerance);
                assertOracle(recorder, "latency_threshold", c, "n", result.n());
            }));
        }
        return checks;
    }

    // ── latency_threshold_bootstrap (binomial side) ─────────────────

    /**
     * Conformance against the bootstrap-comparison suite's binding
     * fields. The conformance fields are integer-valued or specific
     * elements of the integer-valued baseline array, so the suite
     * carries {@code tolerance: 0} and equality is exact. The
     * {@code bootstrap_upper} / {@code point_estimate} / {@code diff}
     * fields are manifest-classified informational (no bootstrap method
     * is implemented, deliberately) and are not conformance targets.
     */
    static List<CaseCheck> latencyThresholdBootstrap() {
        JsonNode suite = ConformanceFixtures.load("latency_threshold_bootstrap.json");
        List<CaseCheck> checks = new ArrayList<>();
        for (JsonNode c : suite.get("cases")) {
            checks.add(new CaseCheck("latency_threshold_bootstrap", c.get("name").asText(), recorder -> {
                var inputs = c.get("inputs");
                LatencyThresholdDeriver.Threshold result = LatencyThresholdDeriver.derive(
                        ConformanceFixtures.toDoubleArray(inputs.get("baseline_latencies")),
                        inputs.get("p").asDouble(),
                        inputs.get("confidence").asDouble());
                assertOracle(recorder, "latency_threshold_bootstrap", c, "rank", result.rank());
                assertOracle(recorder, "latency_threshold_bootstrap", c, "threshold", result.threshold());
                assertOracle(recorder, "latency_threshold_bootstrap", c, "baseline_percentile",
                        result.baselinePercentile());
                assertOracle(recorder, "latency_threshold_bootstrap", c, "n", result.n());
                assertOracle(recorder, "latency_threshold_bootstrap", c, "saturated", result.saturated());
                // The unclamped rank k_raw is a binding field the deriver
                // does not yet expose — reflective lookup, red until it does.
                assertOracle(recorder, "latency_threshold_bootstrap", c, "k_raw",
                        reflectiveAccessor(result, "kRaw"));
            }));
        }
        return checks;
    }

    /**
     * End-to-end conformance against the bootstrap fixture, driven
     * through the production evaluation path
     * ({@code PercentileLatency.evaluate} reading a baseline
     * {@code LatencyStatistics}) rather than calling
     * {@code LatencyThresholdDeriver} in isolation. Guards against a
     * class of regression where the deriver remains correct on its own
     * but a refactor detaches it from the hot path or rewires the
     * detail-map value.
     */
    static List<CaseCheck> latencyThresholdBootstrapProductionPath() {
        JsonNode suite = ConformanceFixtures.load("latency_threshold_bootstrap.json");
        List<CaseCheck> checks = new ArrayList<>();
        for (JsonNode c : suite.get("cases")) {
            checks.add(new CaseCheck("latency_threshold_bootstrap", c.get("name").asText(), recorder -> {
                var inputs = c.get("inputs");
                var expected = c.get("expected");
                long[] baselineLatenciesMs = ConformanceFixtures.toLongArray(inputs.get("baseline_latencies"));
                double confidence = inputs.get("confidence").asDouble();
                long expectedThreshold = expected.get("threshold").asLong();
                boolean saturated = expected.get("saturated").asBoolean();
                PercentileKey key = LatencyProductionPath.percentileKeyFor(inputs.get("p").asDouble());

                var baseline = LatencyProductionPath.buildBaseline(baselineLatenciesMs);
                PercentileLatency<String> criterion = PercentileLatency.empirical(confidence, key);

                CriterionResult verification = criterion.evaluate(
                        LatencyProductionPath.evaluationContext(baseline, TestIntent.VERIFICATION));
                if (saturated) {
                    assertThat(verification.verdict())
                            .as("VERIFICATION verdict (saturated)")
                            .isEqualTo(Verdict.INCONCLUSIVE);
                    assertThat(verification.detail())
                            .as("saturated.%s flag", key.detailKey())
                            .containsEntry("saturated." + key.detailKey(), true);
                } else {
                    assertThat(verification.verdict())
                            .as("VERIFICATION verdict (non-saturated)")
                            .isEqualTo(Verdict.PASS);
                    assertThat(verification.detail())
                            .as("threshold.%s on production path", key.detailKey())
                            .containsEntry("threshold." + key.detailKey(), expectedThreshold);
                }

                if (saturated) {
                    CriterionResult smoke = criterion.evaluate(
                            LatencyProductionPath.evaluationContext(baseline, TestIntent.SMOKE));
                    assertThat(smoke.verdict())
                            .as("SMOKE verdict (saturated, advisory)")
                            .isEqualTo(Verdict.PASS);
                    assertThat(smoke.detail())
                            .as("SMOKE threshold.%s (advisory)", key.detailKey())
                            .containsEntry("threshold." + key.detailKey(), expectedThreshold);
                    assertThat(smoke.detail())
                            .as("SMOKE saturated.%s flag", key.detailKey())
                            .containsEntry("saturated." + key.detailKey(), true);
                }
            }));
        }
        return checks;
    }

    // ── latency_percentile_minimums ─────────────────────────────────

    static List<CaseCheck> latencyPercentileEmissionMinimums() {
        JsonNode suite = ConformanceFixtures.load("latency_percentile_minimums.json");
        List<CaseCheck> checks = new ArrayList<>();
        for (JsonNode c : suite.get("cases")) {
            if (!"emission_non_degeneracy".equals(c.get("approach").asText())) {
                continue;
            }
            checks.add(new CaseCheck("latency_percentile_minimums", c.get("name").asText(), recorder -> {
                double p = c.get("inputs").get("percentile").asDouble();
                String label = "p" + Math.round(p * 100);
                assertOracle(recorder, "latency_percentile_minimums", c,
                        "minimum_contributing_samples", LatencySection.minimumSamplesFor(label));
            }));
        }
        return checks;
    }

    /**
     * The bound-existence minimums mark the deriver's saturation
     * boundary: non-saturated at the published minimum, saturated just
     * below it. The flip assertions are the semantic check that the
     * published {@code minimum_baseline_samples} equals the deriver's
     * own floor, so the field is recorded here.
     */
    static List<CaseCheck> latencyBoundExistenceMinimums() {
        JsonNode suite = ConformanceFixtures.load("latency_percentile_minimums.json");
        List<CaseCheck> checks = new ArrayList<>();
        for (JsonNode c : suite.get("cases")) {
            if (!"bound_existence".equals(c.get("approach").asText())) {
                continue;
            }
            checks.add(new CaseCheck("latency_percentile_minimums", c.get("name").asText(), recorder -> {
                recorder.record("latency_percentile_minimums", c.get("name").asText(),
                        "minimum_baseline_samples");
                double p = c.get("inputs").get("percentile").asDouble();
                double confidence = c.get("inputs").get("confidence").asDouble();
                int minimum = c.get("expected").get("minimum_baseline_samples").asInt();
                assertThat(LatencyThresholdDeriver.derive(ascending(minimum), p, confidence)
                        .saturated())
                        .as("non-saturated bound at the published minimum n=%d", minimum)
                        .isFalse();
                assertThat(LatencyThresholdDeriver.derive(ascending(minimum - 1), p, confidence)
                        .saturated())
                        .as("saturation just below the published minimum, n=%d", minimum - 1)
                        .isTrue();
            }));
        }
        return checks;
    }

    // ── regression_decision ─────────────────────────────────────────

    /**
     * The derivation the framework performs on the empirical path must
     * produce the binding decision artefacts: the real-valued threshold
     * is a report obligation, the integer cutoff and achieved size are
     * the decision; the displayed rate is §-mandated {@code c/n}.
     */
    static List<CaseCheck> regressionDecisionDerivation() {
        JsonNode suite = ConformanceFixtures.load("regression_decision.json");
        double tolerance = suite.get("tolerance").asDouble();
        List<CaseCheck> checks = new ArrayList<>();
        for (JsonNode c : suite.get("cases")) {
            if (!"REGRESSION".equals(c.get("procedure").asText())) {
                continue;
            }
            checks.add(new CaseCheck("regression_decision", c.get("name").asText(), recorder -> {
                var inputs = c.get("inputs");
                DerivedThreshold derived = THRESHOLD_DERIVER.deriveSampleSizeFirst(
                        inputs.get("baseline_trials").asInt(),
                        inputs.get("baseline_successes").asInt(),
                        inputs.get("test_samples").asInt(),
                        inputs.get("confidence").asDouble());
                assertOracle(recorder, "regression_decision", c, "threshold_real",
                        derived.value(), tolerance);
                assertOracle(recorder, "regression_decision", c, "cutoff_integer",
                        derivedArtefact(derived, "cutoff"));
                assertOracle(recorder, "regression_decision", c, "displayed_rate",
                        derivedArtefact(derived, "displayedRate"), tolerance);
                assertOracle(recorder, "regression_decision", c, "achieved_size",
                        derivedArtefact(derived, "achievedSize"), tolerance);
            }));
        }
        return checks;
    }

    /**
     * The scenario suite through the production verdict path — a real
     * probabilistic test driven the way a user would drive it (baseline
     * resolution, threshold derivation inside the framework, engine
     * sampling, criterion evaluation), not a test-side recomposition of
     * the formulae. See {@link DecisionRuleProductionPath}.
     */
    static List<CaseCheck> regressionDecisionProductionVerdicts() {
        JsonNode suite = ConformanceFixtures.load("regression_decision.json");
        double tolerance = suite.get("tolerance").asDouble();
        List<CaseCheck> checks = new ArrayList<>();
        for (JsonNode c : suite.get("cases")) {
            String procedure = c.get("procedure").asText();
            checks.add(new CaseCheck("regression_decision", c.get("name").asText(), recorder -> {
                var inputs = c.get("inputs");
                int testSamples = inputs.get("test_samples").asInt();
                double confidence = inputs.get("confidence").asDouble();
                int observedSuccesses = inputs.get("observed_successes").asInt();
                if ("REGRESSION".equals(procedure)) {
                    var judged = DecisionRuleProductionPath.judgeRegression(
                            inputs.get("baseline_successes").asInt(),
                            inputs.get("baseline_trials").asInt(),
                            testSamples, confidence, observedSuccesses);
                    assertOracle(recorder, "regression_decision", c, "verdict",
                            judged.verdict().name());
                } else {
                    var judged = DecisionRuleProductionPath.judgeCompliance(
                            inputs.get("threshold").asDouble(),
                            testSamples, confidence, observedSuccesses);
                    assertOracle(recorder, "regression_decision", c, "verdict",
                            judged.verdict().name());
                    // §3.2/§3.6: the test sample's own Wilson lower bound is
                    // the compliance decision artefact; a conformant verdict
                    // surfaces it. Absent from the production detail today →
                    // a red missing-capability assertion.
                    assertOracle(recorder, "regression_decision", c, "wilson_lower",
                            judged.detail().get("wilsonLower"), tolerance);
                }
            }));
        }
        return checks;
    }

    // ── helpers ─────────────────────────────────────────────────────

    /**
     * Reflective lookup of a decision artefact the statistics package
     * does not yet expose — returns {@code null} (→ a clear red
     * missing-capability assertion) instead of failing to compile. The
     * Java analogue of baseltest's {@code getattr(result, name, None)}:
     * the red-then-green discipline wants these tests on the branch
     * before any production change exists for them to call.
     */
    private static Object derivedArtefact(DerivedThreshold derived, String accessor) {
        return reflectiveAccessor(derived, accessor);
    }

    private static Object reflectiveAccessor(Object target, String accessor) {
        try {
            var method = target.getClass().getMethod(accessor);
            Object value = method.invoke(target);
            if (value instanceof OptionalInt oi) {
                return oi.isPresent() ? oi.getAsInt() : null;
            }
            if (value instanceof OptionalDouble od) {
                return od.isPresent() ? od.getAsDouble() : null;
            }
            if (value instanceof java.util.Optional<?> o) {
                return o.orElse(null);
            }
            return value;
        } catch (NoSuchMethodException e) {
            return null;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static double[] ascending(int n) {
        double[] values = new double[n];
        for (int i = 0; i < n; i++) {
            values[i] = i + 1;
        }
        return values;
    }
}
