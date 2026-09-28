package org.mavai.punit.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.mavai.punit.statistics.VerificationFeasibilityEvaluator.FeasibilityResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link VerificationFeasibilityEvaluator}.
 *
 * <p>Each test is self-contained with worked values in the name and comments,
 * per Req 12b: "The test itself must be very easy to navigate, with each test
 * fully self-contained and self-documenting."
 */
class VerificationFeasibilityEvaluatorTest {

    @Nested
    @DisplayName("Feasibility under compliance/exact-binomial: a pass is possible iff p₀^N ≤ α")
    class ExactFeasibility {

        // N_min = ⌈log α / log p₀⌉ (Statistical Companion §5.7.1).

        @Test
        @DisplayName("p₀=0.50, α=0.05: N_min = 5, and N=5 is feasible")
        void fairCoin() {
            FeasibilityResult result = VerificationFeasibilityEvaluator.evaluate(5, 0.50, 0.95);

            assertThat(result.feasible()).isTrue();
            assertThat(result.minimumSamples()).isEqualTo(5);
            assertThat(result.configuredSamples()).isEqualTo(5);
            assertThat(result.target()).isEqualTo(0.50);
            assertThat(result.configuredAlpha()).isEqualTo(0.05);
            assertThat(result.criterion()).isEqualTo("exact_binomial_pass_possible");
        }

        @Test
        @DisplayName("the companion's table at α=0.05: 0.90 → 29, 0.95 → 59, 0.99 → 299, 0.995 → 598, 0.999 → 2995, 0.9999 → 29956")
        void companionTable() {
            assertThat(VerificationFeasibilityEvaluator.evaluate(1, 0.90, 0.95).minimumSamples()).isEqualTo(29);
            assertThat(VerificationFeasibilityEvaluator.evaluate(1, 0.95, 0.95).minimumSamples()).isEqualTo(59);
            assertThat(VerificationFeasibilityEvaluator.evaluate(1, 0.99, 0.95).minimumSamples()).isEqualTo(299);
            assertThat(VerificationFeasibilityEvaluator.evaluate(1, 0.995, 0.95).minimumSamples()).isEqualTo(598);
            assertThat(VerificationFeasibilityEvaluator.evaluate(1, 0.999, 0.95).minimumSamples()).isEqualTo(2995);
            assertThat(VerificationFeasibilityEvaluator.evaluate(1, 0.9999, 0.95).minimumSamples()).isEqualTo(29956);
        }

        @Test
        @DisplayName("p₀=0.995, N=477: not feasible — no outcome of 477 can demonstrate 99.5%")
        void headlineCaseIsInfeasible() {
            FeasibilityResult result = VerificationFeasibilityEvaluator.evaluate(477, 0.995, 0.95);

            assertThat(result.feasible()).isFalse();
            assertThat(result.minimumSamples()).isEqualTo(598);
        }

        @Test
        @DisplayName("a stricter α raises N_min: p₀=0.90 at α=0.01 needs 44")
        void stricterAlpha() {
            FeasibilityResult result = VerificationFeasibilityEvaluator.evaluate(43, 0.90, 0.99);

            assertThat(result.feasible()).isFalse();
            assertThat(result.minimumSamples()).isEqualTo(44);
            assertThat(result.configuredAlpha()).isEqualTo(0.01);
        }

        @Test
        @DisplayName("a very low requirement is feasible at one sample; p₀=0.50 is not")
        void singleSample() {
            assertThat(VerificationFeasibilityEvaluator.evaluate(1, 0.01, 0.95).feasible()).isTrue();
            assertThat(VerificationFeasibilityEvaluator.evaluate(1, 0.50, 0.95).feasible()).isFalse();
        }
    }

    @Nested
    @DisplayName("N_min computation verification")
    class NMinVerification {

        @Test
        @DisplayName("evaluate(N_min, p₀, confidence) is feasible")
        void minimumSamples_isFeasible() {
            FeasibilityResult result = VerificationFeasibilityEvaluator.evaluate(1, 0.90, 0.95);
            int nMin = result.minimumSamples();

            FeasibilityResult atMin = VerificationFeasibilityEvaluator.evaluate(nMin, 0.90, 0.95);
            assertThat(atMin.feasible()).isTrue();
        }

        @Test
        @DisplayName("evaluate(N_min - 1, p₀, confidence) is NOT feasible")
        void belowMinimumSamples_isNotFeasible() {
            FeasibilityResult result = VerificationFeasibilityEvaluator.evaluate(1, 0.90, 0.95);
            int nMin = result.minimumSamples();

            // N_min should be > 1 for p₀=0.90, so nMin-1 is valid
            assertThat(nMin).isGreaterThan(1);
            FeasibilityResult belowMin = VerificationFeasibilityEvaluator.evaluate(nMin - 1, 0.90, 0.95);
            assertThat(belowMin.feasible()).isFalse();
        }

        @Test
        @DisplayName("N_min boundary holds across multiple targets")
        void nMinBoundary_multipleTargets() {
            double[] targets = {0.50, 0.80, 0.90, 0.95, 0.99};
            double confidence = 0.95;

            for (double target : targets) {
                FeasibilityResult initial = VerificationFeasibilityEvaluator.evaluate(1, target, confidence);
                int nMin = initial.minimumSamples();

                FeasibilityResult atMin = VerificationFeasibilityEvaluator.evaluate(nMin, target, confidence);
                assertThat(atMin.feasible())
                        .as("N_min=%d should be feasible for target=%.2f", nMin, target)
                        .isTrue();

                if (nMin > 1) {
                    FeasibilityResult belowMin = VerificationFeasibilityEvaluator.evaluate(nMin - 1, target, confidence);
                    assertThat(belowMin.feasible())
                            .as("N_min-1=%d should NOT be feasible for target=%.2f", nMin - 1, target)
                            .isFalse();
                }
            }
        }
    }

    @Nested
    @DisplayName("Invalid inputs")
    class InvalidInputs {

        @Test
        @DisplayName("samples <= 0 throws IllegalArgumentException")
        void zeroSamples() {
            assertThatThrownBy(() -> VerificationFeasibilityEvaluator.evaluate(0, 0.90, 0.95))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("samples must be > 0");
        }

        @Test
        @DisplayName("negative samples throws IllegalArgumentException")
        void negativeSamples() {
            assertThatThrownBy(() -> VerificationFeasibilityEvaluator.evaluate(-1, 0.90, 0.95))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("samples must be > 0");
        }

        @Test
        @DisplayName("target = 0.0 throws IllegalArgumentException")
        void targetZero() {
            assertThatThrownBy(() -> VerificationFeasibilityEvaluator.evaluate(100, 0.0, 0.95))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("target must be in (0, 1)");
        }

        @Test
        @DisplayName("target = 1.0 throws IllegalArgumentException")
        void targetOne() {
            assertThatThrownBy(() -> VerificationFeasibilityEvaluator.evaluate(100, 1.0, 0.95))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("target must be in (0, 1)");
        }

        @Test
        @DisplayName("confidence = 0.0 throws IllegalArgumentException")
        void confidenceZero() {
            assertThatThrownBy(() -> VerificationFeasibilityEvaluator.evaluate(100, 0.90, 0.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("confidence must be in (0, 1)");
        }

        @Test
        @DisplayName("confidence = 1.0 throws IllegalArgumentException")
        void confidenceOne() {
            assertThatThrownBy(() -> VerificationFeasibilityEvaluator.evaluate(100, 0.90, 1.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("confidence must be in (0, 1)");
        }
    }

    @Nested
    @DisplayName("FeasibilityResult properties")
    class ResultProperties {

        @Test
        @DisplayName("criterion names the exact binomial pass-possible check")
        void criterionIsConsistent() {
            FeasibilityResult result = VerificationFeasibilityEvaluator.evaluate(100, 0.90, 0.95);
            assertThat(result.criterion()).isEqualTo("exact_binomial_pass_possible");
        }

        @Test
        @DisplayName("ASSUMPTION constant is documented")
        void assumptionIsDocumented() {
            assertThat(FeasibilityResult.ASSUMPTION).isEqualTo("i.i.d. Bernoulli trials");
        }
    }

}
