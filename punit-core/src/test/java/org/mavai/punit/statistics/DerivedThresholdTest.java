package org.mavai.punit.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DerivedThreshold}.
 * 
 * <p>Tests the record representing a statistically-derived threshold for
 * probabilistic testing.
 */
@DisplayName("DerivedThreshold")
class DerivedThresholdTest {
    
    private DerivationContext sampleContext() {
        // Baseline: 95.1% observed rate from 1000 samples
        // Test: 100 samples at 95% confidence
        return new DerivationContext(0.951, 1000, 100, 0.95);
    }
    
    @Nested
    @DisplayName("Validation")
    class Validation {
        
        @Test
        @DisplayName("rejects threshold value outside [0, 1]")
        void rejectsInvalidThresholdValue() {
            assertThatThrownBy(() -> 
                new DerivedThreshold(-0.1, OperationalApproach.SAMPLE_SIZE_FIRST, sampleContext())
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("Threshold value");
            
            assertThatThrownBy(() -> 
                new DerivedThreshold(1.1, OperationalApproach.SAMPLE_SIZE_FIRST, sampleContext())
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("Threshold value");
        }
        
        @Test
        @DisplayName("rejects null approach")
        void rejectsNullApproach() {
            assertThatThrownBy(() -> 
                new DerivedThreshold(0.9, null, sampleContext())
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("Approach");
        }
        
        @Test
        @DisplayName("rejects null context")
        void rejectsNullContext() {
            assertThatThrownBy(() -> 
                new DerivedThreshold(0.9, OperationalApproach.SAMPLE_SIZE_FIRST, null)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("Context");
        }
        
        @Test
        @DisplayName("accepts valid threshold")
        void acceptsValidThreshold() {
            var threshold = new DerivedThreshold(
                0.916, OperationalApproach.SAMPLE_SIZE_FIRST, sampleContext());
            
            assertThat(threshold.value()).isEqualTo(0.916);
            assertThat(threshold.approach()).isEqualTo(OperationalApproach.SAMPLE_SIZE_FIRST);
            assertThat(threshold.isStatisticallySound()).isTrue();
        }
    }
    
    @Nested
    @DisplayName("Gap Calculation")
    class GapCalculation {
        
        @Test
        @DisplayName("calculates gap from baseline correctly")
        void calculatesGapFromBaseline() {
            // Baseline rate: 95.1%, Threshold: 91.6%
            // Gap should be 3.5%
            var context = new DerivationContext(0.951, 1000, 100, 0.95);
            var threshold = new DerivedThreshold(
                0.916, OperationalApproach.SAMPLE_SIZE_FIRST, context);
            
            assertThat(threshold.gapFromBaseline()).isCloseTo(0.035, within(0.001));
        }
        
        @Test
        @DisplayName("gap is negative when threshold exceeds baseline")
        void negativeGapWhenThresholdExceedsBaseline() {
            var context = new DerivationContext(0.90, 1000, 100, 0.95);
            var threshold = new DerivedThreshold(
                0.95, OperationalApproach.THRESHOLD_FIRST, context);
            
            // Gap = 0.90 - 0.95 = -0.05
            assertThat(threshold.gapFromBaseline()).isCloseTo(-0.05, within(0.001));
        }
    }
    
    @Nested
    @DisplayName("Soundness Flag")
    class SoundnessFlag {
        
        @Test
        @DisplayName("convenience constructor marks as statistically sound")
        void convenienceConstructorIsSoundByDefault() {
            var threshold = new DerivedThreshold(
                0.916, OperationalApproach.SAMPLE_SIZE_FIRST, sampleContext());
            
            assertThat(threshold.isStatisticallySound()).isTrue();
        }
        
        @Test
        @DisplayName("explicit constructor can mark as unsound")
        void explicitConstructorCanMarkAsUnsound() {
            var threshold = new DerivedThreshold(
                0.916, OperationalApproach.THRESHOLD_FIRST, sampleContext(), false);
            
            assertThat(threshold.isStatisticallySound()).isFalse();
        }
    }
}

