package org.mavai.punit.statistics.conformance;

import static org.mavai.punit.api.criterion.Criteria.meeting;
import org.mavai.punit.api.criterion.Criteria;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mavai.outcome.Outcome;
import org.mavai.punit.api.Contract;
import org.mavai.punit.api.FactorBundle;
import org.mavai.punit.api.LatencyResult;
import org.mavai.punit.api.PercentileKey;
import org.mavai.punit.api.ServiceContractOutcome;
import org.mavai.punit.api.TestIntent;
import org.mavai.punit.api.TokenTracker;
import org.mavai.punit.api.spec.CriterionResult;
import org.mavai.punit.api.spec.EvaluationContext;
import org.mavai.punit.api.spec.PercentileLatency;
import org.mavai.punit.api.spec.SampleSummary;
import org.mavai.punit.api.spec.TerminationReason;
import org.mavai.punit.api.spec.Verdict;
import org.mavai.punit.statistics.LatencyStatistics;
import org.mavai.punit.statistics.LatencyThresholdDeriver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Conformance tests for latency statistics: empirical percentile estimation,
 * summary statistics, and threshold derivation from baseline data.
 *
 * @see <a href="https://github.com/javai-org/javai-R">javai-R</a>
 */
@DisplayName("Latency conformance (javai-R)")
class LatencyConformanceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CONFORMANCE_DIR = "/conformance/";

    private static JsonNode loadSuite(String filename) {
        try (InputStream is = LatencyConformanceTest.class.getResourceAsStream(CONFORMANCE_DIR + filename)) {
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

    private static double[] toDoubleArray(JsonNode node) {
        if (node.isArray()) {
            double[] values = new double[node.size()];
            for (int i = 0; i < node.size(); i++) {
                values[i] = node.get(i).asDouble();
            }
            return values;
        }
        // Single scalar value
        return new double[]{ node.asDouble() };
    }

    @Nested
    @DisplayName("latency_percentile — nearest-rank percentile")
    class Percentile {

        @TestFactory
        @DisplayName("Nearest-rank (ceiling) percentile estimation")
        Collection<DynamicTest> cases() {
            JsonNode suite = loadSuite("latency_percentile.json");
            double tolerance = suite.get("tolerance").asDouble();

            var tests = new ArrayList<DynamicTest>();
            for (JsonNode c : suite.get("cases")) {
                var inputs = c.get("inputs");
                if (!inputs.has("percentile")) {
                    continue; // summary case, handled by the Summary nested class
                }

                String name = c.get("name").asText();
                var expected = c.get("expected");

                tests.add(DynamicTest.dynamicTest(name, () -> {
                    double[] latencies = toDoubleArray(inputs.get("latencies"));
                    double p = inputs.get("percentile").asDouble();

                    double result = LatencyStatistics.nearestRankPercentile(latencies, p);

                    assertThat(result)
                            .as("percentile value")
                            .isCloseTo(expected.get("value").asDouble(), within(tolerance));
                }));
            }
            return tests;
        }
    }

    @Nested
    @DisplayName("latency_percentile — summary statistics")
    class Summary {

        @TestFactory
        @DisplayName("Mean and maximum")
        Collection<DynamicTest> cases() {
            JsonNode suite = loadSuite("latency_percentile.json");
            double tolerance = suite.get("tolerance").asDouble();

            var tests = new ArrayList<DynamicTest>();
            for (JsonNode c : suite.get("cases")) {
                var inputs = c.get("inputs");
                if (inputs.has("percentile")) {
                    continue; // percentile case, handled by the Percentile nested class
                }

                String name = c.get("name").asText();
                var expected = c.get("expected");

                tests.add(DynamicTest.dynamicTest(name, () -> {
                    double[] latencies = toDoubleArray(inputs.get("latencies"));

                    assertThat(LatencyStatistics.mean(latencies))
                            .as("mean")
                            .isCloseTo(expected.get("mean").asDouble(), within(tolerance));

                    assertThat(LatencyStatistics.max(latencies))
                            .as("max")
                            .isCloseTo(expected.get("max").asDouble(), within(tolerance));
                }));
            }
            return tests;
        }
    }

    @Nested
    @DisplayName("latency_threshold")
    class Threshold {

        @TestFactory
        @DisplayName("Exact binomial order-statistic upper bound on the baseline percentile")
        Collection<DynamicTest> cases() {
            JsonNode suite = loadSuite("latency_threshold.json");
            double tolerance = suite.get("tolerance").asDouble();

            var tests = new ArrayList<DynamicTest>();
            for (JsonNode c : suite.get("cases")) {
                String name = c.get("name").asText();
                var inputs = c.get("inputs");
                var expected = c.get("expected");

                tests.add(DynamicTest.dynamicTest(name, () -> {
                    double[] baselineLatencies = toDoubleArray(inputs.get("baseline_latencies"));
                    double p = inputs.get("p").asDouble();
                    double confidence = inputs.get("confidence").asDouble();

                    LatencyThresholdDeriver.Threshold result =
                            LatencyThresholdDeriver.derive(baselineLatencies, p, confidence);

                    assertThat(result.rank())
                            .as("rank (k)")
                            .isEqualTo(expected.get("rank").asInt());
                    assertThat(result.threshold())
                            .as("threshold (t_{(k)})")
                            .isCloseTo(expected.get("threshold").asDouble(), within(tolerance));
                    assertThat(result.baselinePercentile())
                            .as("baseline percentile (Q(p))")
                            .isCloseTo(expected.get("baseline_percentile").asDouble(), within(tolerance));
                    assertThat(result.n())
                            .as("n")
                            .isEqualTo(expected.get("n").asInt());
                }));
            }
            return tests;
        }
    }

    /**
     * Conformance against the bootstrap-comparison suite — the one whose
     * `expected` section publishes the binomial fields alongside the
     * informational bootstrap fields. Per the suite's own description, the
     * conformance fields ({@code rank}, {@code threshold},
     * {@code baseline_percentile}, {@code n}) are integer-valued or are
     * specific elements of the integer-valued {@code baseline_latencies}
     * array, so the suite carries {@code tolerance: 0} and we check exact
     * equality. The fields {@code bootstrap_upper}, {@code point_estimate},
     * and {@code diff} are informational and are not asserted as
     * conformance targets here — they are exercised separately by the
     * conservatism sanity check below.
     */
    @Nested
    @DisplayName("latency_threshold_bootstrap (binomial side; bootstrap fields informational)")
    class BootstrapComparison {

        @TestFactory
        @DisplayName("Exact binomial order-statistic bound matches across the bootstrap-comparison baselines")
        Collection<DynamicTest> cases() {
            JsonNode suite = loadSuite("latency_threshold_bootstrap.json");

            var tests = new ArrayList<DynamicTest>();
            for (JsonNode c : suite.get("cases")) {
                String name = c.get("name").asText();
                var inputs = c.get("inputs");
                var expected = c.get("expected");

                tests.add(DynamicTest.dynamicTest(name, () -> {
                    double[] baselineLatencies = toDoubleArray(inputs.get("baseline_latencies"));
                    double p = inputs.get("p").asDouble();
                    double confidence = inputs.get("confidence").asDouble();

                    LatencyThresholdDeriver.Threshold result =
                            LatencyThresholdDeriver.derive(baselineLatencies, p, confidence);

                    assertThat(result.rank())
                            .as("rank (k) — exact equality")
                            .isEqualTo(expected.get("rank").asInt());
                    assertThat(result.threshold())
                            .as("threshold (t_{(k)}) — exact equality")
                            .isEqualTo(expected.get("threshold").asDouble());
                    assertThat(result.baselinePercentile())
                            .as("baseline percentile (Q(p)) — exact equality")
                            .isEqualTo(expected.get("baseline_percentile").asDouble());
                    assertThat(result.n())
                            .as("n — exact equality")
                            .isEqualTo(expected.get("n").asInt());
                }));
            }
            return tests;
        }

        /**
         * Binomial-conservatism sanity check on the published fixture
         * itself: the binomial threshold is conservative by construction
         * relative to the bootstrap upper bound at the same confidence
         * level, so for every published case the binomial threshold must
         * be greater than or equal to the bootstrap upper bound. If this
         * property ever flips on a future fixture release, the failing
         * case signals either a fixture defect or a methodology drift —
         * fail loudly so neither slips past silently.
         *
         * This is a property check on the oracle's own publication, not
         * a check against {@code LatencyThresholdDeriver}.
         */
        @Test
        @DisplayName("Binomial-conservatism sanity: threshold >= bootstrap_upper holds for every published case")
        void binomialBoundIsAtLeastBootstrapUpper() {
            JsonNode suite = loadSuite("latency_threshold_bootstrap.json");
            for (JsonNode c : suite.get("cases")) {
                String name = c.get("name").asText();
                double threshold = c.get("expected").get("threshold").asDouble();
                double bootstrapUpper = c.get("expected").get("bootstrap_upper").asDouble();
                assertThat(threshold)
                        .as("case=%s: binomial threshold must be >= bootstrap upper "
                                + "(binomial bound is conservative by construction)", name)
                        .isGreaterThanOrEqualTo(bootstrapUpper);
            }
        }
    }

    /**
     * End-to-end conformance against the bootstrap fixture, driven
     * through the production evaluation path
     * ({@code PercentileLatency.evaluate} reading a baseline
     * {@code LatencyStatistics}) rather than calling
     * {@code LatencyThresholdDeriver} in isolation. Guards against
     * a class of regression where the deriver remains correct on its
     * own but a refactor detaches it from the hot path or rewires the
     * detail-map value.
     *
     * <p>For non-saturated cases the test asserts exact equality of
     * the {@code threshold.<p>} detail-map entry against the fixture's
     * {@code expected.threshold}, and verdict PASS (the synthetic
     * observed latencies are well below threshold).
     *
     * <p>For saturated cases (companion §12.4.2) the test exercises
     * both intents: under VERIFICATION → INCONCLUSIVE with
     * {@code saturated.<p>=true}; under SMOKE → advisory PASS with
     * the same flag set and the threshold equal to the fixture's
     * advisory value at the saturation ceiling.
     */
    @Nested
    @DisplayName("latency_threshold_bootstrap (production path: PercentileLatency.evaluate)")
    class ProductionPath {

        @TestFactory
        @DisplayName("Threshold derived on the production path matches the fixture, per intent")
        Collection<DynamicTest> cases() {
            JsonNode suite = loadSuite("latency_threshold_bootstrap.json");

            var tests = new ArrayList<DynamicTest>();
            for (JsonNode c : suite.get("cases")) {
                String name = c.get("name").asText();
                var inputs = c.get("inputs");
                var expected = c.get("expected");

                tests.add(DynamicTest.dynamicTest(name, () -> {
                    long[] baselineLatenciesMs = toLongArray(inputs.get("baseline_latencies"));
                    double p = inputs.get("p").asDouble();
                    double confidence = inputs.get("confidence").asDouble();
                    long expectedThreshold = expected.get("threshold").asLong();
                    boolean saturated = expected.get("saturated").asBoolean();
                    PercentileKey key = percentileKeyFor(p);

                    org.mavai.punit.api.spec.LatencyStatistics baseline =
                            buildBaseline(baselineLatenciesMs);
                    PercentileLatency<String> criterion =
                            PercentileLatency.empirical(confidence, key);

                    // VERIFICATION: saturated → INCONCLUSIVE with the
                    // saturation flag; non-saturated → PASS with
                    // threshold.<p> equal to fixture's expected.threshold.
                    CriterionResult verification = criterion.evaluate(
                            evaluationContext(baseline, TestIntent.VERIFICATION));
                    if (saturated) {
                        assertThat(verification.verdict())
                                .as("case=%s VERIFICATION verdict (saturated)", name)
                                .isEqualTo(Verdict.INCONCLUSIVE);
                        assertThat(verification.detail())
                                .as("case=%s saturated.%s flag", name, key.detailKey())
                                .containsEntry("saturated." + key.detailKey(), true);
                    } else {
                        assertThat(verification.verdict())
                                .as("case=%s VERIFICATION verdict (non-saturated)", name)
                                .isEqualTo(Verdict.PASS);
                        assertThat(verification.detail())
                                .as("case=%s threshold.%s on production path", name, key.detailKey())
                                .containsEntry("threshold." + key.detailKey(), expectedThreshold);
                    }

                    // SMOKE on the saturated case: advisory PASS on
                    // t_{(n)}, threshold.<p> present and equal to the
                    // fixture's published threshold, saturated flag
                    // still surfaced.
                    if (saturated) {
                        CriterionResult smoke = criterion.evaluate(
                                evaluationContext(baseline, TestIntent.SMOKE));
                        assertThat(smoke.verdict())
                                .as("case=%s SMOKE verdict (saturated, advisory)", name)
                                .isEqualTo(Verdict.PASS);
                        assertThat(smoke.detail())
                                .as("case=%s SMOKE threshold.%s (advisory)", name, key.detailKey())
                                .containsEntry("threshold." + key.detailKey(), expectedThreshold);
                        assertThat(smoke.detail())
                                .as("case=%s SMOKE saturated.%s flag", name, key.detailKey())
                                .containsEntry("saturated." + key.detailKey(), true);
                    }
                }));
            }
            return tests;
        }

        private PercentileKey percentileKeyFor(double p) {
            if (p == 0.95) return PercentileKey.P95;
            if (p == 0.99) return PercentileKey.P99;
            throw new IllegalArgumentException(
                    "fixture case asserts percentile " + p
                            + ", which has no PercentileKey on the production surface");
        }

        private org.mavai.punit.api.spec.LatencyStatistics buildBaseline(long[] sortedAscMs) {
            // sortedLatenciesMs is the only field the deriver reads;
            // percentile point estimates are reporting metadata. Fill
            // them honestly from the sorted vector so the baseline
            // object is internally consistent.
            LatencyResult percentiles = new LatencyResult(
                    Duration.ofMillis(nearestRankMs(sortedAscMs, 0.50)),
                    Duration.ofMillis(nearestRankMs(sortedAscMs, 0.90)),
                    Duration.ofMillis(nearestRankMs(sortedAscMs, 0.95)),
                    Duration.ofMillis(nearestRankMs(sortedAscMs, 0.99)),
                    sortedAscMs.length);
            return new org.mavai.punit.api.spec.LatencyStatistics(
                    percentiles, sortedAscMs, sortedAscMs.length);
        }

        private long nearestRankMs(long[] sortedAsc, double p) {
            int index = (int) Math.ceil(p * sortedAsc.length) - 1;
            index = Math.max(0, Math.min(index, sortedAsc.length - 1));
            return sortedAsc[index];
        }

        private EvaluationContext<String, org.mavai.punit.api.spec.LatencyStatistics>
                evaluationContext(
                        org.mavai.punit.api.spec.LatencyStatistics baseline,
                        TestIntent intent) {
            int testSampleCount = Math.max(1, baseline.sampleCount() / 2);
            SampleSummary<String> summary = buildSummary(testSampleCount);
            String identity = "sha256:test-fixed-identity";
            return new EvaluationContext<>() {
                @Override public SampleSummary<String> summary() { return summary; }
                @Override public Optional<org.mavai.punit.api.spec.LatencyStatistics> baseline() {
                    return Optional.of(baseline);
                }
                @Override public FactorBundle factors() {
                    return FactorBundle.of(new Object());
                }
                @Override public String testInputsIdentity() { return identity; }
                @Override public Optional<String> baselineInputsIdentity() {
                    return Optional.of(identity);
                }
                @Override public TestIntent intent() { return intent; }
            };
        }

        private SampleSummary<String> buildSummary(int sampleCount) {
            // Synthetic observed latencies safely below every published
            // bootstrap-case threshold (smallest threshold across the
            // four cases is 419 ms); the test asserts on the deriver's
            // threshold value flowing through evaluate, not on the
            // observation-vs-threshold comparison.
            LatencyResult observed = new LatencyResult(
                    Duration.ofMillis(1),
                    Duration.ofMillis(1),
                    Duration.ofMillis(1),
                    Duration.ofMillis(1),
                    sampleCount);
            var outcomes = new ArrayList<ServiceContractOutcome<?, String>>(sampleCount);
            for (int i = 0; i < sampleCount; i++) {
                outcomes.add(new ServiceContractOutcome<>(
                        Outcome.ok("ok"), STUB_CONTRACT, List.of(),
                        0L, Duration.ofMillis(1)));
            }
            return new SampleSummary<>(
                    outcomes,
                    Duration.ofMillis(1),
                    sampleCount, 0, 0L, 0,
                    observed,
                    TerminationReason.COMPLETED,
                    List.of(),
                    Map.of(), LatencyResult.empty(), List.of());
        }

        private long[] toLongArray(JsonNode node) {
            if (!node.isArray()) {
                throw new IllegalArgumentException(
                        "baseline_latencies must be a JSON array");
            }
            long[] out = new long[node.size()];
            for (int i = 0; i < node.size(); i++) {
                out[i] = node.get(i).asLong();
            }
            return out;
        }
    }

    private static final Contract<Object, String> STUB_CONTRACT = new Contract<>() {
        @Override public Outcome<String> invoke(Object input, TokenTracker tracker) {
            return Outcome.ok("ok");
        }
        @Override public Criteria<String> criteria() {
            return meeting().<String>zeroFailures();
        }

    };
}
