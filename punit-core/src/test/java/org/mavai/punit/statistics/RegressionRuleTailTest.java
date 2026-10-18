package org.mavai.punit.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.apache.commons.statistics.distribution.HypergeometricDistribution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The one-sided Fisher p-value of {@code regression/fisher} (Statistical
 * Companion §3.4) far from the hypergeometric mode, where a single term of
 * the tail is smaller than a double can hold.
 *
 * <p>The reference sums the lower tail in log space over the whole support,
 * scaled by its largest term, so no term underflows and nothing here calls
 * the code under test to compute an expected value.
 */
@DisplayName("regression/fisher far from the mode")
class RegressionRuleTailTest {

    private static final double ALPHA = 0.05;

    @Nested
    @DisplayName("the p-value")
    class PValue {

        @Test
        @DisplayName("a test far above a low baseline has a p-value of about 1, not 0")
        void largeImprovementIsNotADegradation() {
            assertThat(RegressionRule.pValue(9000, 100, 10000, 10000))
                    .isCloseTo(1.0, within(1e-12));
        }

        @ParameterizedTest(name = "{0} of {1} against a test of {2}")
        @CsvSource({
                "10, 1000, 1000, 1",
                "2000, 10000, 1000, 1",
                "3000, 10000, 10000, 10",
        })
        @DisplayName("agrees with the reference across the whole support")
        void agreesWithReferenceAcrossSupport(
                int baselineSuccesses, int baselineTrials, int testSamples, int stride) {
            for (int k = 0; k <= testSamples; k += stride) {
                assertThat(RegressionRule.pValue(k, baselineSuccesses, baselineTrials, testSamples))
                        .as("test successes %d", k)
                        .isCloseTo(referencePValue(k, baselineSuccesses, baselineTrials, testSamples),
                                within(1e-12));
            }
        }

        @ParameterizedTest(name = "{0} of {1} against a test of {2}")
        @CsvSource({
                "10, 1000, 1000",
                "100, 10000, 10000",
                "3000, 10000, 10000",
        })
        @DisplayName("never decreases as the test count rises")
        void isMonotoneInTheTestCount(int baselineSuccesses, int baselineTrials, int testSamples) {
            double previous = 0.0;
            for (int k = 0; k <= testSamples; k++) {
                double p = RegressionRule.pValue(k, baselineSuccesses, baselineTrials, testSamples);
                assertThat(p).as("test successes %d", k).isGreaterThanOrEqualTo(previous - 1e-12);
                previous = p;
            }
        }
    }

    @Nested
    @DisplayName("the cutoff")
    class Cutoff {

        @ParameterizedTest(name = "{0} of {1} against a test of {2}: cutoff {3}")
        @CsvSource({
                "100, 10000, 10000, 78",
                "2000, 10000, 10000, 1907",
                "20000, 100000, 10000, 1931",
                "100, 10000, 1000, 5",
        })
        @DisplayName("is the reference cutoff at a low baseline rate and a large test")
        void matchesReferenceAtLowRates(
                int baselineSuccesses, int baselineTrials, int testSamples, int expected) {
            assertThat(referenceCutoff(baselineSuccesses, baselineTrials, testSamples))
                    .as("the reference itself")
                    .isEqualTo(expected);
            assertThat(RegressionRule.cutoff(baselineSuccesses, baselineTrials, testSamples, ALPHA))
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("the cutoffs design power and sizing sum over agree with the reference")
        void designCutoffsMatchReference() {
            int[] baselineCounts = {100, 101, 102};
            int[] cutoffs = RegressionRule.cutoffs(baselineCounts, 10000, 10000, ALPHA);
            for (int i = 0; i < baselineCounts.length; i++) {
                assertThat(cutoffs[i])
                        .as("%d of 10000", baselineCounts[i])
                        .isEqualTo(referenceCutoff(baselineCounts[i], 10000, 10000));
            }
        }
    }

    /** {@code P(X ≤ k)} summed in log space over the support, scaled by its largest term. */
    private static double referencePValue(
            int testSuccesses, int baselineSuccesses, int baselineTrials, int testSamples) {
        int population = baselineTrials + testSamples;
        int pooled = baselineSuccesses + testSuccesses;
        HypergeometricDistribution distribution =
                HypergeometricDistribution.of(population, pooled, testSamples);
        int lower = Math.max(0, testSamples + pooled - population);
        double largest = Double.NEGATIVE_INFINITY;
        for (int x = lower; x <= testSuccesses; x++) {
            largest = Math.max(largest, distribution.logProbability(x));
        }
        double scaled = 0.0;
        for (int x = lower; x <= testSuccesses; x++) {
            scaled += Math.exp(distribution.logProbability(x) - largest);
        }
        return Math.min(1.0, Math.exp(largest) * scaled);
    }

    /** The smallest test count whose reference p-value exceeds alpha, by bisection. */
    private static int referenceCutoff(int baselineSuccesses, int baselineTrials, int testSamples) {
        int low = -1;
        int high = testSamples;
        while (high - low > 1) {
            int mid = (low + high) >>> 1;
            if (referencePValue(mid, baselineSuccesses, baselineTrials, testSamples) <= ALPHA) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return high;
    }
}
