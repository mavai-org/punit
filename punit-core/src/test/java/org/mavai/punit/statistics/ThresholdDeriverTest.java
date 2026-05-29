package org.mavai.punit.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ThresholdDeriver}.
 * 
 * <h2>Statistical Background</h2>
 * <p>Threshold derivation accounts for:
 * <ul>
 *   <li>Uncertainty in the baseline estimate (it's an estimate, not truth)</li>
 *   <li>Increased variance with smaller test samples</li>
 *   <li>Desired false positive rate (α = 1 - confidence)</li>
 * </ul>
 * 
 * <h2>Key Test Cases</h2>
 * <ul>
 *   <li>Sample-Size-First approach (given n and α, derive threshold)</li>
 *   <li>Threshold-First approach (given threshold, derive implied confidence)</li>
 *   <li>Perfect baseline handling (p̂ = 1)</li>
 *   <li>Statistical soundness flags</li>
 * </ul>
 */
@DisplayName("ThresholdDeriver")
class ThresholdDeriverTest {
    
    private ThresholdDeriver deriver;
    
    @BeforeEach
    void setUp() {
        deriver = new ThresholdDeriver();
    }
    
    @Nested
    @DisplayName("Sample-Size-First Approach (Cost-Driven)")
    class SampleSizeFirstApproach {
        
        @Test
        @DisplayName("derives threshold from baseline data")
        void derivesThresholdFromBaselineData() {
            // Baseline: 951 successes out of 1000 trials (95.1%)
            // Test: 100 samples at 95% confidence
            // Companion §3.4: threshold is Wilson lower at n_test from
            // baseline rate ≈ 0.902.
            DerivedThreshold threshold = deriver.deriveSampleSizeFirst(
                1000, 951, 100, 0.95);

            assertThat(threshold.value()).isCloseTo(0.902, within(0.01));
            assertThat(threshold.approach()).isEqualTo(OperationalApproach.SAMPLE_SIZE_FIRST);
            assertThat(threshold.isStatisticallySound()).isTrue();
        }

        @Test
        @DisplayName("threshold is lower than baseline rate")
        void thresholdIsLowerThanBaselineRate() {
            DerivedThreshold threshold = deriver.deriveSampleSizeFirst(
                1000, 951, 100, 0.95);

            // Gap accounts for sampling uncertainty at the test sample size.
            assertThat(threshold.value()).isLessThan(0.951);
            assertThat(threshold.gapFromBaseline()).isGreaterThan(0);
        }

        @Test
        @DisplayName("higher confidence produces lower threshold")
        void higherConfidenceProducesLowerThreshold() {
            DerivedThreshold threshold95 = deriver.deriveSampleSizeFirst(
                1000, 951, 100, 0.95);
            DerivedThreshold threshold99 = deriver.deriveSampleSizeFirst(
                1000, 951, 100, 0.99);

            // 99% confidence → more conservative threshold
            assertThat(threshold99.value()).isLessThan(threshold95.value());
        }

        @Test
        @DisplayName("smaller test sample size produces lower threshold")
        void smallerTestSampleProducesLowerThreshold() {
            // Companion §3.5 / §3.4: the threshold is Wilson lower at the
            // test sample size. A smaller test produces a wider interval
            // and therefore a lower bound, regardless of baseline size.
            DerivedThreshold smallTest = deriver.deriveSampleSizeFirst(
                1000, 950, 50, 0.95);
            DerivedThreshold largeTest = deriver.deriveSampleSizeFirst(
                1000, 950, 200, 0.95);

            assertThat(largeTest.value()).isGreaterThan(smallTest.value());
        }

        @Test
        @DisplayName("handles perfect baseline (100% success) correctly")
        void handlesPerfectBaseline() {
            // All 1000 trials succeeded; test 100 at 95%.
            // Companion §4.3.2: p₀ = Wilson_lower(1000, 1000) ≈ 0.9973,
            // then Wilson_lower_from_rate(p₀, 100, 0.95) ≈ 0.9686.
            DerivedThreshold threshold = deriver.deriveSampleSizeFirst(
                1000, 1000, 100, 0.95);

            assertThat(threshold.value()).isLessThan(1.0);
            assertThat(threshold.value()).isCloseTo(0.9686, within(0.001));
        }
        
        @Test
        @DisplayName("context captures derivation parameters")
        void contextCapturesDerivationParameters() {
            DerivedThreshold threshold = deriver.deriveSampleSizeFirst(
                1000, 951, 100, 0.95);
            
            DerivationContext context = threshold.context();
            assertThat(context.baselineRate()).isCloseTo(0.951, within(0.001));
            assertThat(context.baselineSamples()).isEqualTo(1000);
            assertThat(context.testSamples()).isEqualTo(100);
            assertThat(context.confidence()).isEqualTo(0.95);
        }
    }
    
    @Nested
    @DisplayName("Threshold-First Approach (Baseline-Anchored)")
    class ThresholdFirstApproach {
        
        @Test
        @DisplayName("computes implied confidence for given threshold")
        void computesImpliedConfidenceForGivenThreshold() {
            // Baseline: 951/1000 (95.1%); test=100; explicit threshold 0.85.
            // Companion §6.3: the implied confidence is the conf at which
            // deriveSampleSizeFirst would produce 0.85. With baseline rate
            // 0.951 at n_test=100, that confidence sits comfortably above
            // 90% and the result is statistically sound.
            DerivedThreshold result = deriver.deriveThresholdFirst(
                1000, 951, 100, 0.85);

            assertThat(result.value()).isEqualTo(0.85);
            assertThat(result.approach()).isEqualTo(OperationalApproach.THRESHOLD_FIRST);
            assertThat(result.context().confidence()).isGreaterThan(0.90);
            assertThat(result.isStatisticallySound()).isTrue();
        }
        
        @Test
        @DisplayName("flags threshold at baseline rate as statistically unsound")
        void flagsThresholdAtBaselineAsUnsound() {
            // Setting threshold = baseline rate is statistically unwise
            // Implied confidence ≈ 50% (coin flip)
            DerivedThreshold result = deriver.deriveThresholdFirst(
                1000, 951, 100, 0.951);
            
            // Should be flagged as unsound
            assertThat(result.isStatisticallySound()).isFalse();
            assertThat(result.context().confidence()).isLessThan(0.8);
        }
        
        @Test
        @DisplayName("flags threshold above baseline rate as statistically unsound")
        void flagsThresholdAboveBaselineAsUnsound() {
            // Threshold above baseline: guaranteed to fail often
            DerivedThreshold result = deriver.deriveThresholdFirst(
                1000, 900, 100, 0.95); // baseline = 90%, threshold = 95%
            
            assertThat(result.isStatisticallySound()).isFalse();
        }
    }
    
    @Nested
    @DisplayName("Input Validation")
    class InputValidation {
        
        @Test
        @DisplayName("rejects non-positive baseline samples")
        void rejectsNonPositiveBaselineSamples() {
            assertThatThrownBy(() -> deriver.deriveSampleSizeFirst(0, 0, 100, 0.95))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Baseline samples must be positive");
        }
        
        @Test
        @DisplayName("rejects baseline successes exceeding samples")
        void rejectsBaselineSuccessesExceedingSamples() {
            assertThatThrownBy(() -> deriver.deriveSampleSizeFirst(1000, 1100, 100, 0.95))
                .isInstanceOf(IllegalArgumentException.class);
        }
        
        @Test
        @DisplayName("rejects non-positive test samples")
        void rejectsNonPositiveTestSamples() {
            assertThatThrownBy(() -> deriver.deriveSampleSizeFirst(1000, 951, 0, 0.95))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Test samples must be positive");
        }
        
        @Test
        @DisplayName("rejects confidence outside (0, 1)")
        void rejectsInvalidConfidence() {
            assertThatThrownBy(() -> deriver.deriveSampleSizeFirst(1000, 951, 100, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Confidence");
            
            assertThatThrownBy(() -> deriver.deriveSampleSizeFirst(1000, 951, 100, 1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Confidence");
        }
        
        @Test
        @DisplayName("rejects explicit threshold outside [0, 1]")
        void rejectsInvalidExplicitThreshold() {
            assertThatThrownBy(() -> deriver.deriveThresholdFirst(1000, 951, 100, -0.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Explicit threshold");
            
            assertThatThrownBy(() -> deriver.deriveThresholdFirst(1000, 951, 100, 1.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Explicit threshold");
        }
    }
    
    @Nested
    @DisplayName("Worked Example from STATISTICAL-COMPANION")
    class WorkedExample {
        
        @Test
        @DisplayName("baseline experiment: 951/1000 with 100-sample test")
        void baselineExperimentWorkedExample() {
            // Statistical companion §3.4: threshold is Wilson one-sided
            // lower bound at the test sample size, applied to the baseline
            // rate. For 951/1000 baseline → p̂_baseline = 0.951; at
            // n_test=100 and 95% confidence → threshold ≈ 0.902.

            DerivedThreshold threshold = deriver.deriveSampleSizeFirst(
                1000, 951, 100, 0.95);

            assertThat(threshold.value())
                .as("Threshold for 951/1000 baseline at n_test=100, 95% confidence")
                .isCloseTo(0.902, within(0.01));

            assertThat(threshold.gapFromBaseline())
                .as("Gap from baseline")
                .isGreaterThan(0.01);
        }
    }

    @Nested
    @DisplayName("Scenario Validation — pipeline plan Scenarios A/B")
    class ScenarioValidation {

        @Test
        @DisplayName("derives π₀ ≈ 0.9008 from baseline 950/1000 at n_test=100, 95% confidence")
        void scenarioAB_thresholdMatchesPlanValue() {
            // Scenarios A and B use baseline: 1000 samples, 950 successes,
            // p̂_baseline = 0.95. Companion §3.4: Wilson one-sided lower
            // bound at n_test=100, conf=0.95 → π₀ ≈ 0.9008.
            DerivedThreshold result =
                    deriver.deriveSampleSizeFirst(1000, 950, 100, 0.95);

            assertThat(result.value())
                    .as("Threshold used in Scenarios A and B")
                    .isCloseTo(0.9008, within(0.001));
        }
    }
}

