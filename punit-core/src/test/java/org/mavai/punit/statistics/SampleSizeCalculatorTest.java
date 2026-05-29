package org.mavai.punit.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SampleSizeCalculator}.
 * 
 * <h2>Statistical Background</h2>
 * <p>Power analysis for one-sided binomial proportion tests:
 * <ul>
 *   <li>H₀: p = p₀ (null hypothesis: no degradation)</li>
 *   <li>H₁: p = p₁ = p₀ - δ (alternative: degradation by effect size δ)</li>
 * </ul>
 * 
 * <h2>Formula</h2>
 * <pre>
 *   n = ((z_α × σ₀ + z_β × σ₁) / δ)²
 *   
 *   where:
 *     σ₀ = √(p₀(1-p₀))
 *     σ₁ = √(p₁(1-p₁))
 *     z_α = Φ⁻¹(1-α)
 *     z_β = Φ⁻¹(1-β)
 * </pre>
 */
@DisplayName("SampleSizeCalculator")
class SampleSizeCalculatorTest {
    
    private SampleSizeCalculator calculator;
    
    @BeforeEach
    void setUp() {
        calculator = new SampleSizeCalculator();
    }
    
    @Nested
    @DisplayName("Sample Size for Power (Confidence-First Approach)")
    class SampleSizeForPower {
        
        @Test
        @DisplayName("calculates sample size for detecting 5% degradation")
        void calculatesSampleSizeForFivePercentDegradation() {
            // Baseline: 95% success rate
            // Detect: 5% degradation (to 90%)
            // Confidence: 95% (α = 0.05)
            // Power: 80% (β = 0.20)
            
            SampleSizeRequirement req = calculator.calculateForPower(
                0.95,  // baseline rate p₀
                0.05,  // minimum detectable effect δ
                0.95,  // confidence (1-α)
                0.80   // power (1-β)
            );
            
            // For these parameters, required n ≈ 150-200
            assertThat(req.requiredSamples()).isBetween(100, 250);
            assertThat(req.nullRate()).isEqualTo(0.95);
            assertThat(req.alternativeRate()).isCloseTo(0.90, within(0.001));
        }
        
        @Test
        @DisplayName("larger effect size requires fewer samples")
        void largerEffectSizeRequiresFewerSamples() {
            SampleSizeRequirement smallEffect = calculator.calculateForPower(
                0.95, 0.02, 0.95, 0.80); // detect 2% drop
            SampleSizeRequirement largeEffect = calculator.calculateForPower(
                0.95, 0.10, 0.95, 0.80); // detect 10% drop
            
            assertThat(largeEffect.requiredSamples())
                .as("Larger effect (10%) needs fewer samples than smaller effect (2%)")
                .isLessThan(smallEffect.requiredSamples());
        }
        
        @Test
        @DisplayName("higher power requires more samples")
        void higherPowerRequiresMoreSamples() {
            SampleSizeRequirement lowPower = calculator.calculateForPower(
                0.95, 0.05, 0.95, 0.70); // 70% power
            SampleSizeRequirement highPower = calculator.calculateForPower(
                0.95, 0.05, 0.95, 0.95); // 95% power
            
            assertThat(highPower.requiredSamples())
                .as("Higher power (95%) requires more samples than lower power (70%)")
                .isGreaterThan(lowPower.requiredSamples());
        }
        
        @Test
        @DisplayName("higher confidence requires more samples")
        void higherConfidenceRequiresMoreSamples() {
            SampleSizeRequirement lowConf = calculator.calculateForPower(
                0.95, 0.05, 0.90, 0.80); // 90% confidence
            SampleSizeRequirement highConf = calculator.calculateForPower(
                0.95, 0.05, 0.99, 0.80); // 99% confidence
            
            assertThat(highConf.requiredSamples())
                .as("Higher confidence (99%) requires more samples than lower confidence (90%)")
                .isGreaterThan(lowConf.requiredSamples());
        }
        
        @Test
        @DisplayName("requirement captures all input parameters")
        void requirementCapturesAllInputParameters() {
            SampleSizeRequirement req = calculator.calculateForPower(
                0.95, 0.05, 0.95, 0.80);
            
            assertThat(req.confidence()).isEqualTo(0.95);
            assertThat(req.power()).isEqualTo(0.80);
            assertThat(req.minDetectableEffect()).isEqualTo(0.05);
            assertThat(req.nullRate()).isEqualTo(0.95);
            assertThat(req.alternativeRate()).isCloseTo(0.90, within(0.001));
        }
    }
    
    @Nested
    @DisplayName("Achieved Power Calculation")
    class AchievedPowerCalculation {
        
        @Test
        @DisplayName("calculates achieved power for given sample size")
        void calculatesAchievedPowerForGivenSampleSize() {
            // If we can only afford 100 samples, what power do we get?
            double achievedPower = calculator.calculateAchievedPower(
                100,   // sample size
                0.95,  // baseline rate
                0.05,  // effect to detect
                0.95   // confidence
            );
            
            // With 100 samples, power should be modest
            assertThat(achievedPower).isBetween(0.4, 0.7);
        }
        
        @Test
        @DisplayName("larger sample size yields higher power")
        void largerSampleSizeYieldsHigherPower() {
            double powerN50 = calculator.calculateAchievedPower(50, 0.95, 0.05, 0.95);
            double powerN200 = calculator.calculateAchievedPower(200, 0.95, 0.05, 0.95);
            
            assertThat(powerN200).isGreaterThan(powerN50);
        }
        
        @Test
        @DisplayName("power approaches 1.0 for large samples")
        void powerApproachesOneForLargeSamples() {
            double power = calculator.calculateAchievedPower(
                1000, 0.95, 0.05, 0.95);
            
            assertThat(power).isGreaterThan(0.99);
        }
        
        @Test
        @DisplayName("round-trip: calculated sample size achieves target power")
        void roundTripConsistency() {
            // Calculate required sample size for 80% power
            SampleSizeRequirement req = calculator.calculateForPower(
                0.95, 0.05, 0.95, 0.80);
            
            // Verify that calculated sample size achieves approximately 80% power
            double achievedPower = calculator.calculateAchievedPower(
                req.requiredSamples(), 0.95, 0.05, 0.95);
            
            // Should be at least 80% (might be slightly higher due to ceiling)
            assertThat(achievedPower)
                .as("Calculated sample size should achieve at least target power")
                .isGreaterThanOrEqualTo(0.79);
        }
    }
    
    @Nested
    @DisplayName("Input Validation")
    class InputValidation {
        
        @Test
        @DisplayName("rejects baseline rate outside (0, 1] — perfect baselines (rate = 1.0) are admissible")
        void rejectsInvalidBaselineRate() {
            // rate = 0 has no signal to plan against — keep it rejected.
            assertThatThrownBy(() -> calculator.calculateForPower(0.0, 0.05, 0.95, 0.80))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Baseline rate");

            // rate > 1 is structurally invalid.
            assertThatThrownBy(() -> calculator.calculateForPower(1.01, 0.05, 0.95, 0.80))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Baseline rate");

            // rate = 1.0 is admissible — see acceptsPerfectBaseline below.
        }

        @Test
        @DisplayName("accepts perfect baseline (rate = 1.0) — σ0 = 0 collapses formula to n = (z_β · σ1)² / δ²")
        void acceptsPerfectBaseline() {
            // For rate = 1.0, σ0 = √(1·0) = 0; the first term of the
            // sample-size formula vanishes. The expected n is
            // ⌈(z_β · √(p1(1−p1)))² / δ²⌉ where p1 = 1 − mde.
            SampleSizeRequirement req = calculator.calculateForPower(1.0, 0.05, 0.95, 0.80);
            assertThat(req.requiredSamples()).isPositive();
            // Closed-form for σ0 = 0: n = (z_β · σ1 / δ)²
            double zBeta = 0.8416212335729143; // qnorm(0.80)
            double p1 = 0.95;
            double sigma1 = Math.sqrt(p1 * (1.0 - p1));
            int expected = (int) Math.ceil(Math.pow(zBeta * sigma1 / 0.05, 2));
            assertThat(req.requiredSamples()).isEqualTo(expected);
        }

        @Test
        @DisplayName("accepts rate = 0.99 with mde = 0.05 — formerly rejected by the symmetric two-sided check")
        void acceptsRateAboveOneMinusMde() {
            // Under the previous symmetric precondition this combination
            // failed at PowerAnalysis. SampleSizeCalculator itself never
            // rejected rate = 0.99, so this test pins that the relaxed
            // upstream precondition reaches the formula and produces a
            // sensible n. The full formula (σ0, σ1 both nonzero) governs.
            SampleSizeRequirement req = calculator.calculateForPower(0.99, 0.05, 0.95, 0.80);
            assertThat(req.requiredSamples()).isPositive();
        }
        
        @Test
        @DisplayName("rejects effect size outside (0, 1)")
        void rejectsInvalidEffectSize() {
            assertThatThrownBy(() -> calculator.calculateForPower(0.95, 0.0, 0.95, 0.80))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Minimum detectable effect");
        }
        
        @Test
        @DisplayName("rejects effect size exceeding baseline rate")
        void rejectsEffectSizeExceedingBaselineRate() {
            // Baseline = 90%, trying to detect 95% drop (impossible)
            assertThatThrownBy(() -> calculator.calculateForPower(0.90, 0.95, 0.95, 0.80))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds baseline rate");
        }
        
        @Test
        @DisplayName("rejects confidence outside (0, 1)")
        void rejectsInvalidConfidence() {
            assertThatThrownBy(() -> calculator.calculateForPower(0.95, 0.05, 0.0, 0.80))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Confidence");
        }
        
        @Test
        @DisplayName("rejects power outside (0, 1)")
        void rejectsInvalidPower() {
            assertThatThrownBy(() -> calculator.calculateForPower(0.95, 0.05, 0.95, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Power");
        }
        
        @Test
        @DisplayName("rejects non-positive sample size in power calculation")
        void rejectsNonPositiveSampleSize() {
            assertThatThrownBy(() -> calculator.calculateAchievedPower(0, 0.95, 0.05, 0.95))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Sample size must be positive");
        }
    }
    
    @Nested
    @DisplayName("Worked Example from STATISTICAL-COMPANION")
    class WorkedExample {
        
        @Test
        @DisplayName("confidence-first: how many samples to detect 5% degradation at 80% power?")
        void confidenceFirstWorkedExample() {
            // From STATISTICAL-COMPANION:
            // Organization wants to detect a 5% degradation from 95% baseline
            // with 95% confidence and 80% power
            
            SampleSizeRequirement req = calculator.calculateForPower(
                0.95,  // baseline p₀ = 95%
                0.05,  // effect δ = 5%
                0.95,  // confidence 1-α = 95%
                0.80   // power 1-β = 80%
            );
            
            // Formula: n = ((z_α × σ₀ + z_β × σ₁) / δ)²
            // z_{0.95} ≈ 1.645, z_{0.80} ≈ 0.842
            // σ₀ = √(0.95 × 0.05) ≈ 0.218
            // σ₁ = √(0.90 × 0.10) ≈ 0.300
            // n ≈ ((1.645 × 0.218 + 0.842 × 0.300) / 0.05)² ≈ 150
            
            assertThat(req.requiredSamples())
                .as("Required samples for 5% effect at 95% conf, 80% power")
                .isBetween(100, 200);
            
            // Verify hypothesis rates
            assertThat(req.nullRate()).isEqualTo(0.95);
            assertThat(req.alternativeRate()).isCloseTo(0.90, within(0.001));
        }
    }
}

