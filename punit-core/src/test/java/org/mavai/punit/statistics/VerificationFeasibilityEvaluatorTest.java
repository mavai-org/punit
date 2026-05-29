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
    @DisplayName("Feasibility at default confidence (0.95, α = 0.05, z ≈ 1.645)")
    class DefaultConfidence {

        // z = Φ⁻¹(0.95) ≈ 1.6449
        // z² ≈ 2.706
        // N_min = ⌈p₀ · z² / (1 - p₀)⌉

        @Test
        @DisplayName("p₀=0.50, N=5: feasible (N_min ≈ 3)")
        void lowTarget_smallN_feasible() {
            // N_min = ⌈0.50 × 2.706 / 0.50⌉ = ⌈2.706⌉ = 3
            FeasibilityResult result = VerificationFeasibilityEvaluator.evaluate(5, 0.50, 0.95);

            assertThat(result.feasible()).isTrue();
            assertThat(result.minimumSamples()).isEqualTo(3);
            assertThat(result.configuredSamples()).isEqualTo(5);
            assertThat(result.target()).isEqualTo(0.50);
            assertThat(result.configuredAlpha()).isCloseTo(0.05, org.assertj.core.data.Offset.offset(0.001));
            assertThat(result.criterion()).isEqualTo(FeasibilityResult.CRITERION);
        }

        @Test
        @DisplayName("p₀=0.90, N=30: feasible (N_min ≈ 25)")
        void moderateTarget_sufficientN_feasible() {
            // N_min = ⌈0.90 × 2.706 / 0.10⌉ = ⌈24.35⌉ = 25
            FeasibilityResult result = VerificationFeasibilityEvaluator.evaluate(30, 0.90, 0.95);

            assertThat(result.feasible()).isTrue();
            assertThat(result.minimumSamples()).isEqualTo(25);
        }

        @Test
        @DisplayName("p₀=0.90, N=20: NOT feasible (N_min ≈ 25)")
        void moderateTarget_insufficientN_notFeasible() {
            // N_min = ⌈0.90 × 2.706 / 0.10⌉ = ⌈24.35⌉ = 25
            FeasibilityResult result = VerificationFeasibilityEvaluator.evaluate(20, 0.90, 0.95);

            assertThat(result.feasible()).isFalse();
            assertThat(result.minimumSamples()).isEqualTo(25);
            assertThat(result.configuredSamples()).isEqualTo(20);
        }

        @Test
        @DisplayName("p₀=0.95, N=55: feasible (N_min ≈ 52)")
        void highTarget_sufficientN_feasible() {
            // N_min = ⌈0.95 × 2.706 / 0.05⌉ = ⌈51.42⌉ = 52
            FeasibilityResult result = VerificationFeasibilityEvaluator.evaluate(55, 0.95, 0.95);

            assertThat(result.feasible()).isTrue();
            assertThat(result.minimumSamples()).isEqualTo(52);
        }

        @Test
        @DisplayName("p₀=0.95, N=40: NOT feasible (N_min ≈ 52)")
        void highTarget_insufficientN_notFeasible() {
            // N_min = ⌈0.95 × 2.706 / 0.05⌉ = ⌈51.42⌉ = 52
            FeasibilityResult result = VerificationFeasibilityEvaluator.evaluate(40, 0.95, 0.95);

            assertThat(result.feasible()).isFalse();
            assertThat(result.minimumSamples()).isEqualTo(52);
        }

        @Test
        @DisplayName("p₀=0.9999, N=100: NOT feasible (N_min ≈ 27,055)")
        void extremeTarget_smallN_notFeasible() {
            // N_min = ⌈0.9999 × 2.706 / 0.0001⌉ = ⌈27,057⌉ ≈ 27,055 (exact varies with z precision)
            FeasibilityResult result = VerificationFeasibilityEvaluator.evaluate(100, 0.9999, 0.95);

            assertThat(result.feasible()).isFalse();
            assertThat(result.minimumSamples()).isGreaterThan(25000);
        }
    }

    @Nested
    @DisplayName("Feasibility at stricter confidence (0.99, α = 0.01, z ≈ 2.326)")
    class StricterConfidence {

        // z = Φ⁻¹(0.99) ≈ 2.3263
        // z² ≈ 5.412
        // N_min = ⌈p₀ · z² / (1 - p₀)⌉

        @Test
        @DisplayName("p₀=0.90, N=50: feasible (N_min ≈ 49)")
        void moderateTarget_sufficientN_feasible() {
            // N_min = ⌈0.90 × 5.412 / 0.10⌉ = ⌈48.71⌉ = 49
            FeasibilityResult result = VerificationFeasibilityEvaluator.evaluate(50, 0.90, 0.99);

            assertThat(result.feasible()).isTrue();
            assertThat(result.minimumSamples()).isEqualTo(49);
        }

        @Test
        @DisplayName("p₀=0.90, N=45: NOT feasible (N_min ≈ 49)")
        void moderateTarget_insufficientN_notFeasible() {
            // N_min = ⌈0.90 × 5.412 / 0.10⌉ = ⌈48.71⌉ = 49
            FeasibilityResult result = VerificationFeasibilityEvaluator.evaluate(45, 0.90, 0.99);

            assertThat(result.feasible()).isFalse();
            assertThat(result.minimumSamples()).isEqualTo(49);
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("Very low target (p₀=0.01): very small N_min")
        void veryLowTarget() {
            // N_min = ⌈0.01 × 2.706 / 0.99⌉ = ⌈0.027⌉ = 1
            FeasibilityResult result = VerificationFeasibilityEvaluator.evaluate(1, 0.01, 0.95);

            assertThat(result.feasible()).isTrue();
            assertThat(result.minimumSamples()).isEqualTo(1);
        }

        @Test
        @DisplayName("Very high target (p₀=0.999): large N_min")
        void veryHighTarget() {
            // N_min = ⌈0.999 × 2.706 / 0.001⌉ = ⌈2703.3⌉ = 2704
            FeasibilityResult result = VerificationFeasibilityEvaluator.evaluate(100, 0.999, 0.95);

            assertThat(result.feasible()).isFalse();
            assertThat(result.minimumSamples()).isGreaterThan(2700);
        }

        @Test
        @DisplayName("samples=1 is feasible only for very low targets")
        void singleSample_lowTarget() {
            // With N=1 and confidence=0.95, Wilson lower bound for k=1 is quite low
            FeasibilityResult lowTarget = VerificationFeasibilityEvaluator.evaluate(1, 0.01, 0.95);
            assertThat(lowTarget.feasible()).isTrue();

            FeasibilityResult highTarget = VerificationFeasibilityEvaluator.evaluate(1, 0.50, 0.95);
            assertThat(highTarget.feasible()).isFalse();
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
        @DisplayName("criterion is always the Wilson score description")
        void criterionIsConsistent() {
            FeasibilityResult result = VerificationFeasibilityEvaluator.evaluate(100, 0.90, 0.95);
            assertThat(result.criterion()).isEqualTo("Wilson score one-sided lower bound");
        }

        @Test
        @DisplayName("ASSUMPTION constant is documented")
        void assumptionIsDocumented() {
            assertThat(FeasibilityResult.ASSUMPTION).isEqualTo("i.i.d. Bernoulli trials");
        }
    }

}
