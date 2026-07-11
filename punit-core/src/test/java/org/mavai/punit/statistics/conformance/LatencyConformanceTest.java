package org.mavai.punit.statistics.conformance;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Conformance tests for latency statistics: empirical percentile estimation,
 * summary statistics, and threshold derivation from baseline data.
 *
 * <p>The check bodies live in {@link ConformanceCatalog}, shared with the
 * manifest-driven coverage check ({@code ConformanceCoverageTest}); this
 * class provides the per-case test reporting.
 *
 * @see <a href="https://github.com/mavai-org/mavai-R">mavai-R</a>
 */
@DisplayName("Latency conformance (mavai-R)")
class LatencyConformanceTest {

    private static Collection<DynamicTest> dynamicTests(List<ConformanceCatalog.CaseCheck> checks) {
        return checks.stream()
                .map(check -> DynamicTest.dynamicTest(
                        check.caseName(),
                        () -> check.check().run(ConformanceRecorder.NO_OP)))
                .toList();
    }

    @Nested
    @DisplayName("latency_percentile — nearest-rank percentile")
    class Percentile {

        @TestFactory
        @DisplayName("Nearest-rank (ceiling) percentile estimation")
        Collection<DynamicTest> cases() {
            return dynamicTests(ConformanceCatalog.latencyPercentileValues());
        }
    }

    @Nested
    @DisplayName("latency_percentile — summary statistics")
    class Summary {

        @TestFactory
        @DisplayName("Mean and maximum")
        Collection<DynamicTest> cases() {
            return dynamicTests(ConformanceCatalog.latencyPercentileSummaries());
        }
    }

    @Nested
    @DisplayName("latency_threshold")
    class Threshold {

        @TestFactory
        @DisplayName("Exact binomial order-statistic upper bound on the baseline percentile")
        Collection<DynamicTest> cases() {
            return dynamicTests(ConformanceCatalog.latencyThreshold());
        }
    }

    @Nested
    @DisplayName("latency_threshold_bootstrap (binomial side; bootstrap fields informational)")
    class BootstrapComparison {

        @TestFactory
        @DisplayName("Exact binomial order-statistic bound matches across the bootstrap-comparison baselines")
        Collection<DynamicTest> cases() {
            return dynamicTests(ConformanceCatalog.latencyThresholdBootstrap());
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
         * <p>This is a property check on the oracle's own publication, not
         * a check against {@code LatencyThresholdDeriver}.
         */
        @Test
        @DisplayName("Binomial-conservatism sanity: threshold >= bootstrap_upper holds for every published case")
        void binomialBoundIsAtLeastBootstrapUpper() {
            JsonNode suite = ConformanceFixtures.load("latency_threshold_bootstrap.json");
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

    @Nested
    @DisplayName("latency_threshold_bootstrap (production path: PercentileLatency.evaluate)")
    class ProductionPath {

        @TestFactory
        @DisplayName("Threshold derived on the production path matches the fixture, per intent")
        Collection<DynamicTest> cases() {
            return dynamicTests(ConformanceCatalog.latencyThresholdBootstrapProductionPath());
        }
    }

    @Nested
    @DisplayName("latency_percentile_minimums — per-percentile minimum sample sizes")
    class PercentileMinimums {

        @TestFactory
        @DisplayName("Emission non-degeneracy minimums match the published standard")
        Collection<DynamicTest> emissionMinimums() {
            var tests = dynamicTests(ConformanceCatalog.latencyPercentileEmissionMinimums());
            assertThat(tests).as("emission cases in the suite").hasSize(4);
            return tests;
        }

        @TestFactory
        @DisplayName("Bound-existence minimums mark the deriver's saturation boundary")
        Collection<DynamicTest> boundExistenceMinimums() {
            var tests = dynamicTests(ConformanceCatalog.latencyBoundExistenceMinimums());
            assertThat(tests).as("bound-existence cases in the suite").hasSize(8);
            return tests;
        }
    }
}
