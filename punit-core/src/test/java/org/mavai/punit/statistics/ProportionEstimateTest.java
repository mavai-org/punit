package org.mavai.punit.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ProportionEstimate}.
 * 
 * <p>Tests the record that captures a point estimate (p̂) and confidence interval
 * for a binomial proportion.
 */
@DisplayName("ProportionEstimate")
class ProportionEstimateTest {
    
    @Nested
    @DisplayName("Validation")
    class Validation {
        
        @Test
        @DisplayName("rejects point estimate outside [0, 1]")
        void rejectsInvalidPointEstimate() {
            assertThatThrownBy(() -> 
                new ProportionEstimate(-0.1, 100, 0.5, 0.6, 0.95)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("Point estimate");
            
            assertThatThrownBy(() -> 
                new ProportionEstimate(1.1, 100, 0.5, 0.6, 0.95)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("Point estimate");
        }
        
        @Test
        @DisplayName("rejects non-positive sample size")
        void rejectsNonPositiveSampleSize() {
            assertThatThrownBy(() -> 
                new ProportionEstimate(0.5, 0, 0.4, 0.6, 0.95)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("Sample size");
            
            assertThatThrownBy(() -> 
                new ProportionEstimate(0.5, -10, 0.4, 0.6, 0.95)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("Sample size");
        }
        
        @Test
        @DisplayName("rejects bounds outside [0, 1]")
        void rejectsInvalidBounds() {
            assertThatThrownBy(() -> 
                new ProportionEstimate(0.5, 100, -0.1, 0.6, 0.95)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("Lower bound");
            
            assertThatThrownBy(() -> 
                new ProportionEstimate(0.5, 100, 0.4, 1.1, 0.95)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("Upper bound");
        }
        
        @Test
        @DisplayName("rejects lower bound exceeding upper bound")
        void rejectsInvertedBounds() {
            assertThatThrownBy(() -> 
                new ProportionEstimate(0.5, 100, 0.7, 0.5, 0.95)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("Lower bound must not exceed upper bound");
        }
        
        @Test
        @DisplayName("rejects confidence level outside (0, 1)")
        void rejectsInvalidConfidenceLevel() {
            assertThatThrownBy(() -> 
                new ProportionEstimate(0.5, 100, 0.4, 0.6, 0.0)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("Confidence level");
            
            assertThatThrownBy(() -> 
                new ProportionEstimate(0.5, 100, 0.4, 0.6, 1.0)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("Confidence level");
        }
        
        @Test
        @DisplayName("accepts valid estimates")
        void acceptsValidEstimates() {
            // Typical case: p̂ = 0.9 with 95% CI
            var estimate = new ProportionEstimate(0.9, 100, 0.83, 0.95, 0.95);
            
            assertThat(estimate.pointEstimate()).isEqualTo(0.9);
            assertThat(estimate.sampleSize()).isEqualTo(100);
            assertThat(estimate.lowerBound()).isEqualTo(0.83);
            assertThat(estimate.upperBound()).isEqualTo(0.95);
            assertThat(estimate.confidenceLevel()).isEqualTo(0.95);
        }
        
        @Test
        @DisplayName("accepts edge cases: p̂ = 0 and p̂ = 1")
        void acceptsEdgeCases() {
            // Perfect failure rate
            var zeroEstimate = new ProportionEstimate(0.0, 100, 0.0, 0.04, 0.95);
            assertThat(zeroEstimate.pointEstimate()).isEqualTo(0.0);
            
            // Perfect success rate
            var perfectEstimate = new ProportionEstimate(1.0, 100, 0.96, 1.0, 0.95);
            assertThat(perfectEstimate.pointEstimate()).isEqualTo(1.0);
        }
    }
    
    @Nested
    @DisplayName("Derived Values")
    class DerivedValues {
        
        @Test
        @DisplayName("intervalWidth returns upper - lower")
        void intervalWidthCalculation() {
            var estimate = new ProportionEstimate(0.5, 100, 0.40, 0.60, 0.95);
            
            assertThat(estimate.intervalWidth()).isCloseTo(0.20, within(0.001));
        }
        
        @Test
        @DisplayName("marginOfError returns half interval width")
        void marginOfErrorCalculation() {
            var estimate = new ProportionEstimate(0.5, 100, 0.40, 0.60, 0.95);
            
            assertThat(estimate.marginOfError()).isCloseTo(0.10, within(0.001));
        }
        
        @Test
        @DisplayName("narrow interval indicates precise estimate")
        void narrowIntervalIndicatesPrecision() {
            // Large sample → narrow interval
            var largeN = new ProportionEstimate(0.5, 10000, 0.49, 0.51, 0.95);
            
            // Small sample → wide interval
            var smallN = new ProportionEstimate(0.5, 25, 0.30, 0.70, 0.95);
            
            assertThat(largeN.intervalWidth()).isLessThan(smallN.intervalWidth());
        }
    }
}

