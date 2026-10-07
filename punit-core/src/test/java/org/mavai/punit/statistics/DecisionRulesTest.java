package org.mavai.punit.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;
import java.util.Random;

import org.apache.commons.numbers.fraction.BigFraction;
import org.apache.commons.statistics.distribution.HypergeometricDistribution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Hand-checkable properties of the decision rules. The reference cases
 * are in the conformance suites; these pin the arithmetic a reader can
 * verify on paper, and the exact-boundary convention at a boundary that
 * floating-point summation lands on either side of.
 */
@DisplayName("Decision rules")
class DecisionRulesTest {

    @Nested
    @DisplayName("regression/fisher")
    class Regression {

        @Test
        @DisplayName("the one-sided p-value is the hypergeometric lower tail")
        void pValueIsHypergeometricLowerTail() {
            Random random = new Random(20260928L);
            for (int i = 0; i < 500; i++) {
                int nb = 1 + random.nextInt(400);
                int kb = random.nextInt(nb + 1);
                int nt = 1 + random.nextInt(nb);
                int kt = random.nextInt(nt + 1);
                double expected = HypergeometricDistribution
                        .of(nb + nt, kb + kt, nt)
                        .cumulativeProbability(kt);
                assertThat(RegressionRule.pValue(kt, kb, nb, nt))
                        .as("kt=%d kb=%d nb=%d nt=%d", kt, kb, nb, nt)
                        .isCloseTo(expected, within(1e-9));
            }
        }

        @Test
        @DisplayName("2 of 4 against 12 of 12 sits exactly on alpha 0.05, and fails")
        void exactBoundaryFails() {
            assertThat(ExactBoundary.fisherPValue(2, 12, 12, 4))
                    .isEqualTo(BigFraction.of(1, 20));
            assertThat(RegressionRule.decide(2, 4, 12, 12, 0.05).verdict())
                    .isEqualTo(RuleVerdict.FAIL);
            assertThat(RegressionRule.decide(3, 4, 12, 12, 0.05).verdict())
                    .isEqualTo(RuleVerdict.PASS);
            assertThat(RegressionRule.cutoff(12, 12, 4, 0.05)).isEqualTo(3);
        }

        @Test
        @DisplayName("the cutoff is the smallest count whose p-value exceeds alpha")
        void cutoffSeparatesTheTail() {
            int nb = 200;
            int kb = 184;
            for (int nt = 20; nt <= 200; nt += 30) {
                int c = RegressionRule.cutoff(kb, nb, nt, 0.05);
                assertThat(RegressionRule.pValue(c, kb, nb, nt)).isGreaterThan(0.05);
                if (c > 0) {
                    assertThat(RegressionRule.pValue(c - 1, kb, nb, nt))
                            .isLessThanOrEqualTo(0.05);
                }
            }
        }

        @Test
        @DisplayName("the size at the assumed common rate is undefined at a baseline rate of 1")
        void sizeUndefinedAtPerfectBaseline() {
            assertThat(RegressionRule.sizeAtAssumedCommonRate(100, 100, 50, 0.05)).isEmpty();
            assertThat(RegressionRule.sizeAtAssumedCommonRate(90, 100, 50, 0.05)
                    .orElseThrow()).isBetween(0.0, 0.05);
        }
    }

    @Nested
    @DisplayName("compliance/exact-binomial")
    class Compliance {

        @Test
        @DisplayName("0.95 at alpha 0.05 is feasible from 59 samples, and needs all 59 to pass")
        void feasibilityMinimum() {
            assertThat(ComplianceRule.minimumFeasibleSamples(0.95, 0.05)).isEqualTo(59);
            assertThat(ComplianceRule.minimumPassingCount(0.95, 59, 0.05)).hasValue(59);
            assertThat(ComplianceRule.minimumPassingCount(0.95, 58, 0.05)).isEmpty();
        }

        @Test
        @DisplayName("0.999 at alpha 0.05 is feasible from 2995 samples")
        void feasibilityMinimumHighRequirement() {
            assertThat(ComplianceRule.minimumFeasibleSamples(0.999, 0.05)).isEqualTo(2995);
        }

        @Test
        @DisplayName("an undersized test fails with no passing count, certain before the run")
        void undersizedFailsWithoutPassingCount() {
            ComplianceRule.Decision undersized = ComplianceRule.decide(58, 58, 0.95, 0.05);
            assertThat(undersized.verdict()).isEqualTo(RuleVerdict.FAIL);
            assertThat(undersized.minimumPassing()).isEmpty();
            assertThat(ComplianceRule.decide(59, 59, 0.95, 0.05).verdict())
                    .isEqualTo(RuleVerdict.PASS);
            assertThat(ComplianceRule.decide(58, 59, 0.95, 0.05).verdict())
                    .isEqualTo(RuleVerdict.FAIL);
        }

        @Test
        @DisplayName("the Clopper-Pearson lower bound at n of n is alpha to the power 1/n")
        void clopperPearsonAllPassing() {
            assertThat(ComplianceRule.clopperPearsonLower(59, 59, 0.05))
                    .isCloseTo(Math.pow(0.05, 1.0 / 59), within(1e-12));
        }

        @Test
        @DisplayName("the false-compliance probability of a feasible design is at most alpha")
        void falseComplianceBounded() {
            OptionalInt kMin = ComplianceRule.minimumPassingCount(0.9, 300, 0.05);
            assertThat(ComplianceRule.falseCompliance(0.9, 300, kMin))
                    .isLessThanOrEqualTo(0.05);
        }
    }

    @Nested
    @DisplayName("latency rules")
    class Latency {

        @Test
        @DisplayName("the non-degeneracy minimums are 5, 10, 20 and 100")
        void nondegeneracyMinimums() {
            assertThat(LatencyRules.minimumContributingSamples(0.50)).isEqualTo(5);
            assertThat(LatencyRules.minimumContributingSamples(0.90)).isEqualTo(10);
            assertThat(LatencyRules.minimumContributingSamples(0.95)).isEqualTo(20);
            assertThat(LatencyRules.minimumContributingSamples(0.99)).isEqualTo(100);
        }

        @Test
        @DisplayName("the test rank is the nearest rank")
        void nearestRank() {
            assertThat(LatencyRules.nearestRank(20, 0.95)).isEqualTo(19);
            assertThat(LatencyRules.nearestRank(100, 0.95)).isEqualTo(95);
        }

        @Test
        @DisplayName("p95 over 20 test latencies against 20 baseline latencies is saturated")
        void saturated() {
            assertThat(LatencyRules.precedenceRank(20, 20, 0.95, 0.05)).isEmpty();
            assertThat(LatencyRules.precedenceRank(1000, 20, 0.95, 0.05)).isPresent();
        }

        @Test
        @DisplayName("the precedence rank keeps the breach probability within alpha")
        void precedenceRankWithinAlpha() {
            int rank = LatencyRules.precedenceRank(1000, 100, 0.95, 0.05).orElseThrow();
            assertThat(LatencyRules.breachProbability(1000, rank, 100, 0.95))
                    .isLessThanOrEqualTo(0.05);
            assertThat(LatencyRules.breachProbability(1000, rank - 1, 100, 0.95))
                    .isGreaterThan(0.05);
        }

        @Test
        @DisplayName("an explicit ceiling is decided by the count within it")
        void complianceByCount() {
            double[] fiftyEight = new double[58];
            Arrays.fill(fiftyEight, 100);
            assertThat(LatencyRules.evaluateCompliance(fiftyEight, 500, 0.95, 0.05).verdict())
                    .isEqualTo(RuleVerdict.INCONCLUSIVE);

            double[] fiftyNine = new double[59];
            Arrays.fill(fiftyNine, 100);
            assertThat(LatencyRules.evaluateCompliance(fiftyNine, 500, 0.95, 0.05).verdict())
                    .isEqualTo(RuleVerdict.PASS);

            fiftyNine[0] = 900;
            assertThat(LatencyRules.evaluateCompliance(fiftyNine, 500, 0.95, 0.05).verdict())
                    .isEqualTo(RuleVerdict.FAIL);
        }
    }

    @Nested
    @DisplayName("methodology")
    class MethodologyFacts {

        @Test
        @DisplayName("the methodology version is 1.6.0")
        void version() {
            assertThat(Methodology.VERSION).isEqualTo("1.6.0");
        }

        @Test
        @DisplayName("alpha is taken from the declared confidence in decimal")
        void alphaFromConfidence() {
            assertThat(Methodology.alphaFromConfidence(0.95)).isEqualTo(0.05);
            assertThat(Methodology.alphaFromConfidence(0.99)).isEqualTo(0.01);
        }

        @Test
        @DisplayName("configuration errors are reported in the fixed order")
        void configurationErrorOrder() {
            assertThat(ConfigurationError.ordered(List.of(
                    ConfigurationError.COMPLIANCE_INFEASIBLE,
                    ConfigurationError.TEST_LARGER_THAN_BASELINE)))
                    .containsExactly(
                            ConfigurationError.TEST_LARGER_THAN_BASELINE,
                            ConfigurationError.COMPLIANCE_INFEASIBLE);
        }
    }
}
