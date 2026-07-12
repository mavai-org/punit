package org.mavai.punit.statistics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@DisplayName("RiskDrivenSizingCalculator")
class RiskDrivenSizingCalculatorTest {

    private final RiskDrivenSizingCalculator calculator = new RiskDrivenSizingCalculator();

    @Nested
    @DisplayName("Required sample size")
    class RequiredSampleSize {

        @Test
        @DisplayName("returns the smallest size meeting the target power for the worked example")
        void returnsTheSmallestSizeMeetingTheTargetPowerForTheWorkedExample() {
            assertThat(calculator.requiredSamples(0.87, 0.84, 0.95, 0.80)).isEqualTo(891);
        }

        @Test
        @DisplayName("is minimal: the target power holds at the required size and fails one below")
        void isMinimalTargetPowerHoldsAtRequiredSizeAndFailsOneBelow() {
            assertThat(calculator.powerAt(891, 0.87, 0.84, 0.95))
                    .isGreaterThanOrEqualTo(0.80);
            assertThat(calculator.powerAt(890, 0.87, 0.84, 0.95))
                    .isLessThan(0.80);
        }

        @Test
        @DisplayName("sizes the scenario walkthrough tuple")
        void sizesTheScenarioWalkthroughTuple() {
            assertThat(calculator.requiredSamples(0.96, 0.93, 0.95, 0.80)).isEqualTo(405);
        }

        @Test
        @DisplayName("rejects a tolerance so tight the search ceiling is exceeded")
        void rejectsAToleranceSoTightTheSearchCeilingIsExceeded() {
            assertThatThrownBy(() -> calculator.requiredSamples(0.90, 0.8999999, 0.95, 0.80))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("too tight");
        }
    }

    @Nested
    @DisplayName("Self-consistent power")
    class SelfConsistentPower {

        @Test
        @DisplayName("increases with the sample size within the domain")
        void increasesWithTheSampleSizeWithinTheDomain() {
            double previous = 0.0;
            for (int candidate : new int[] {50, 150, 405, 826, 1200, 5000}) {
                double power = calculator.powerAt(candidate, 0.96, 0.93, 0.95);
                assertThat(power)
                        .as("power at n=%d exceeds power at the previous candidate", candidate)
                        .isGreaterThan(previous);
                previous = power;
            }
        }

        @Test
        @DisplayName("shows the fixed-threshold closed form understates the requirement")
        void showsTheFixedThresholdClosedFormUnderstatesTheRequirement() {
            // The fixed-threshold seed for the worked example lands at 826
            // samples; against the moving floor that size falls short of the
            // 80% detection target.
            assertThat(calculator.powerAt(826, 0.87, 0.84, 0.95)).isLessThan(0.80);
        }
    }

    @Nested
    @DisplayName("Detectable rate")
    class DetectableRate {

        @Test
        @DisplayName("round-trips: power at the detectable rate meets the target, a nudge above falls below")
        void roundTripsPowerAtTheDetectableRateMeetsTheTargetANudgeAboveFallsBelow() {
            double detectable = calculator.detectableRate(100, 0.87, 0.95, 0.80);
            assertThat(calculator.powerAt(100, 0.87, detectable, 0.95))
                    .isGreaterThanOrEqualTo(0.80);
            assertThat(calculator.powerAt(100, 0.87, detectable + 1e-6, 0.95))
                    .isLessThan(0.80);
        }

        @Test
        @DisplayName("inverting at the required sample size recovers the declared tolerance")
        void invertingAtTheRequiredSampleSizeRecoversTheDeclaredTolerance() {
            int requiredSamples = calculator.requiredSamples(0.87, 0.84, 0.95, 0.80);
            assertThat(calculator.detectableRate(requiredSamples, 0.87, 0.95, 0.80))
                    .isCloseTo(0.84, within(1e-3));
        }

        @Test
        @DisplayName("finds the collapse a small affordable sample can still detect")
        void findsTheCollapseASmallAffordableSampleCanStillDetect() {
            assertThat(calculator.detectableRate(100, 0.87, 0.95, 0.80))
                    .isCloseTo(0.769353, within(1e-6));
        }
    }

    @Nested
    @DisplayName("Domain")
    class Domain {

        @Test
        @DisplayName("rejects a tolerated minimum equal to the baseline rate")
        void rejectsAToleratedMinimumEqualToTheBaselineRate() {
            assertThatThrownBy(() -> calculator.powerAt(100, 0.90, 0.90, 0.95))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("below the measured baseline rate");
        }

        @Test
        @DisplayName("rejects a tolerated minimum above the baseline rate")
        void rejectsAToleratedMinimumAboveTheBaselineRate() {
            assertThatThrownBy(() -> calculator.requiredSamples(0.90, 0.95, 0.95, 0.80))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("re-measure the baseline");
        }

        @Test
        @DisplayName("rejects a non-positive sample size")
        void rejectsANonPositiveSampleSize() {
            assertThatThrownBy(() -> calculator.powerAt(0, 0.90, 0.85, 0.95))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Sample size must be positive");
        }

        @Test
        @DisplayName("rejects rates and probabilities outside the open unit interval")
        void rejectsRatesAndProbabilitiesOutsideTheOpenUnitInterval() {
            assertThatThrownBy(() -> calculator.powerAt(100, 1.0, 0.85, 0.95))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> calculator.requiredSamples(0.90, 0.85, 0.95, 1.0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> calculator.detectableRate(100, 0.90, 0.0, 0.80))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
