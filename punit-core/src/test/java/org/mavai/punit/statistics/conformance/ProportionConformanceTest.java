package org.mavai.punit.statistics.conformance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mavai.punit.statistics.BinomialProportionEstimator;
import org.mavai.punit.statistics.DerivationContext;
import org.mavai.punit.statistics.DerivedThreshold;
import org.mavai.punit.statistics.OperationalApproach;
import org.mavai.punit.statistics.ProportionEstimate;
import org.mavai.punit.statistics.SampleSizeCalculator;
import org.mavai.punit.statistics.SampleSizeRequirement;
import org.mavai.punit.statistics.TestVerdictEvaluator;
import org.mavai.punit.statistics.ThresholdDeriver;
import org.mavai.punit.statistics.VerdictWithConfidence;
import org.mavai.punit.statistics.VerificationFeasibilityEvaluator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Conformance tests for binomial proportion statistics: Wilson score intervals,
 * threshold derivation, power analysis, feasibility, and verdict evaluation.
 *
 * @see <a href="https://github.com/javai-org/javai-R">javai-R</a>
 */
@DisplayName("Proportion conformance (javai-R)")
class ProportionConformanceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CONFORMANCE_DIR = "/conformance/";

    private static final BinomialProportionEstimator ESTIMATOR = new BinomialProportionEstimator();
    private static final ThresholdDeriver THRESHOLD_DERIVER = new ThresholdDeriver();
    private static final SampleSizeCalculator SAMPLE_SIZE_CALCULATOR = new SampleSizeCalculator();
    private static final TestVerdictEvaluator VERDICT_EVALUATOR = new TestVerdictEvaluator();

    private static JsonNode loadSuite(String filename) {
        try (InputStream is = ProportionConformanceTest.class.getResourceAsStream(CONFORMANCE_DIR + filename)) {
            if (is == null) {
                throw new IllegalStateException(
                        "Conformance data not found: " + CONFORMANCE_DIR + filename
                                + ". Run the Gradle downloadConformanceData task or check the checked-in fallback files.");
            }
            return MAPPER.readTree(is);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read conformance suite: " + filename, e);
        }
    }

    @Nested
    @DisplayName("wilson_ci")
    class WilsonCi {

        @TestFactory
        @DisplayName("Two-sided Wilson score confidence intervals")
        Collection<DynamicTest> cases() {
            JsonNode suite = loadSuite("wilson_ci.json");
            double tolerance = suite.get("tolerance").asDouble();

            var tests = new ArrayList<DynamicTest>();
            for (JsonNode c : suite.get("cases")) {
                String name = c.get("name").asText();
                var inputs = c.get("inputs");
                var expected = c.get("expected");

                tests.add(DynamicTest.dynamicTest(name, () -> {
                    int successes = inputs.get("successes").asInt();
                    int trials = inputs.get("trials").asInt();
                    double confidence = inputs.get("confidence").asDouble();

                    ProportionEstimate result = ESTIMATOR.estimate(successes, trials, confidence);

                    assertThat(result.lowerBound())
                            .as("lower bound")
                            .isCloseTo(expected.get("lower").asDouble(), within(tolerance));
                    assertThat(result.upperBound())
                            .as("upper bound")
                            .isCloseTo(expected.get("upper").asDouble(), within(tolerance));
                    assertThat(result.pointEstimate())
                            .as("point estimate")
                            .isCloseTo(expected.get("point").asDouble(), within(tolerance));
                }));
            }
            return tests;
        }
    }

    @Nested
    @DisplayName("wilson_lower")
    class WilsonLower {

        @TestFactory
        @DisplayName("One-sided Wilson score lower bound")
        Collection<DynamicTest> cases() {
            JsonNode suite = loadSuite("wilson_lower.json");
            double tolerance = suite.get("tolerance").asDouble();

            var tests = new ArrayList<DynamicTest>();
            for (JsonNode c : suite.get("cases")) {
                String name = c.get("name").asText();
                var inputs = c.get("inputs");
                var expected = c.get("expected");

                tests.add(DynamicTest.dynamicTest(name, () -> {
                    int successes = inputs.get("successes").asInt();
                    int trials = inputs.get("trials").asInt();
                    double confidence = inputs.get("confidence").asDouble();

                    double result = ESTIMATOR.lowerBound(successes, trials, confidence);

                    assertThat(result)
                            .as("lower bound")
                            .isCloseTo(expected.get("lower_bound").asDouble(), within(tolerance));
                }));
            }
            return tests;
        }
    }

    @Nested
    @DisplayName("threshold_derivation")
    class ThresholdDerivation {

        @TestFactory
        @DisplayName("Sample-size-first and threshold-first derivation")
        Collection<DynamicTest> cases() {
            JsonNode suite = loadSuite("threshold_derivation.json");
            double tolerance = suite.get("tolerance").asDouble();

            var tests = new ArrayList<DynamicTest>();
            for (JsonNode c : suite.get("cases")) {
                String name = c.get("name").asText();
                String approach = c.get("approach").asText();
                var inputs = c.get("inputs");
                var expected = c.get("expected");

                tests.add(DynamicTest.dynamicTest(name, () -> {
                    int baselineSuccesses = inputs.get("baseline_successes").asInt();
                    int baselineTrials = inputs.get("baseline_trials").asInt();

                    if ("sample_size_first".equals(approach)) {
                        int testSamples = inputs.get("test_samples").asInt();
                        double confidence = inputs.get("confidence").asDouble();

                        DerivedThreshold result = THRESHOLD_DERIVER.deriveSampleSizeFirst(
                                baselineTrials, baselineSuccesses, testSamples, confidence);

                        assertThat(result.value())
                                .as("threshold")
                                .isCloseTo(expected.get("threshold").asDouble(), within(tolerance));

                    } else if ("threshold_first".equals(approach)) {
                        double threshold = inputs.get("threshold").asDouble();

                        // testSamples is not used in the threshold-first implied confidence computation
                        // but is required by the API; use baseline trials as a reasonable value
                        DerivedThreshold result = THRESHOLD_DERIVER.deriveThresholdFirst(
                                baselineTrials, baselineSuccesses, baselineTrials, threshold);

                        assertThat(result.context().confidence())
                                .as("implied confidence")
                                .isCloseTo(expected.get("implied_confidence").asDouble(), within(tolerance));
                        assertThat(result.isStatisticallySound())
                                .as("is sound")
                                .isEqualTo(expected.get("is_sound").asBoolean());
                    }
                }));
            }
            return tests;
        }
    }

    @Nested
    @DisplayName("power_analysis")
    class PowerAnalysis {

        @TestFactory
        @DisplayName("Sample size calculation via power analysis")
        Collection<DynamicTest> cases() {
            JsonNode suite = loadSuite("power_analysis.json");
            double tolerance = suite.get("tolerance").asDouble();

            var tests = new ArrayList<DynamicTest>();
            for (JsonNode c : suite.get("cases")) {
                String name = c.get("name").asText();
                var inputs = c.get("inputs");
                var expected = c.get("expected");

                tests.add(DynamicTest.dynamicTest(name, () -> {
                    double baselineRate = inputs.get("baseline_rate").asDouble();
                    double minDetectableEffect = inputs.get("min_detectable_effect").asDouble();
                    double confidence = inputs.get("confidence").asDouble();
                    double power = inputs.get("power").asDouble();

                    SampleSizeRequirement result = SAMPLE_SIZE_CALCULATOR.calculateForPower(
                            baselineRate, minDetectableEffect, confidence, power);

                    assertThat(result.requiredSamples())
                            .as("required samples")
                            .isEqualTo(expected.get("required_samples").asInt());

                    double achievedPower = SAMPLE_SIZE_CALCULATOR.calculateAchievedPower(
                            result.requiredSamples(), baselineRate, minDetectableEffect, confidence);

                    assertThat(achievedPower)
                            .as("achieved power")
                            .isCloseTo(expected.get("achieved_power").asDouble(), within(tolerance));
                }));
            }
            return tests;
        }
    }

    @Nested
    @DisplayName("feasibility")
    class Feasibility {

        @TestFactory
        @DisplayName("Verification feasibility checking")
        Collection<DynamicTest> cases() {
            JsonNode suite = loadSuite("feasibility.json");

            var tests = new ArrayList<DynamicTest>();
            for (JsonNode c : suite.get("cases")) {
                String name = c.get("name").asText();
                var inputs = c.get("inputs");
                var expected = c.get("expected");

                tests.add(DynamicTest.dynamicTest(name, () -> {
                    double targetProportion = inputs.get("target_proportion").asDouble();
                    int sampleSize = inputs.get("sample_size").asInt();
                    double confidence = inputs.get("confidence").asDouble();

                    var result = VerificationFeasibilityEvaluator.evaluate(
                            sampleSize, targetProportion, confidence);

                    assertThat(result.feasible())
                            .as("feasible")
                            .isEqualTo(expected.get("feasible").asBoolean());
                    assertThat(result.minimumSamples())
                            .as("minimum samples")
                            .isEqualTo(expected.get("minimum_samples").asInt());
                    assertThat(result.criterion().toLowerCase().replaceAll("[\\s-]+", "_"))
                            .as("criterion")
                            .isEqualTo(expected.get("criterion").asText());
                }));
            }
            return tests;
        }
    }

    @Nested
    @DisplayName("verdict")
    class Verdict {

        @TestFactory
        @DisplayName("Pass/fail verdict evaluation with z-test statistics")
        Collection<DynamicTest> cases() {
            JsonNode suite = loadSuite("verdict.json");
            double tolerance = suite.get("tolerance").asDouble();

            var tests = new ArrayList<DynamicTest>();
            for (JsonNode c : suite.get("cases")) {
                String name = c.get("name").asText();
                var inputs = c.get("inputs");
                var expected = c.get("expected");

                tests.add(DynamicTest.dynamicTest(name, () -> {
                    int successes = inputs.get("successes").asInt();
                    int trials = inputs.get("trials").asInt();
                    double threshold = inputs.get("threshold").asDouble();
                    double confidence = inputs.get("confidence").asDouble();

                    // Build a DerivedThreshold to pass to the evaluator
                    var context = new DerivationContext(threshold, trials, trials, confidence);
                    var derivedThreshold = new DerivedThreshold(
                            threshold, OperationalApproach.SAMPLE_SIZE_FIRST, context);

                    VerdictWithConfidence verdict = VERDICT_EVALUATOR.evaluate(
                            successes, trials, derivedThreshold);

                    assertThat(verdict.passed())
                            .as("passed")
                            .isEqualTo(expected.get("passed").asBoolean());
                    assertThat(verdict.observedRate())
                            .as("observed rate")
                            .isCloseTo(expected.get("observed_rate").asDouble(), within(tolerance));

                    // z-test statistic and p-value via BinomialProportionEstimator
                    double observedRate = (double) successes / trials;
                    double z = ESTIMATOR.zTestStatistic(observedRate, threshold, trials);
                    double pValue = ESTIMATOR.oneSidedPValue(z);

                    assertThat(z)
                            .as("test statistic")
                            .isCloseTo(expected.get("test_statistic").asDouble(), within(tolerance));
                    assertThat(pValue)
                            .as("p-value")
                            .isCloseTo(expected.get("p_value").asDouble(), within(tolerance));

                    // false_positive_probability in the reference data is alpha = 1 - confidence
                    assertThat(1.0 - confidence)
                            .as("false positive probability (alpha)")
                            .isCloseTo(expected.get("false_positive_probability").asDouble(), within(tolerance));
                }));
            }
            return tests;
        }
    }
}
