package org.mavai.punit.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link VerdictWithConfidence}.
 * 
 * <p>Tests the record that captures a probabilistic test verdict with
 * full statistical context.
 */
@DisplayName("VerdictWithConfidence")
class VerdictWithConfidenceTest {
    
    private DerivedThreshold sampleThreshold() {
        var context = new DerivationContext(0.951, 1000, 100, 0.95);
        return new DerivedThreshold(0.936, OperationalApproach.SAMPLE_SIZE_FIRST, context);
    }
    
    @Nested
    @DisplayName("Validation")
    class Validation {
        
        @Test
        @DisplayName("rejects observed rate outside [0, 1]")
        void rejectsInvalidObservedRate() {
            assertThatThrownBy(() -> 
                new VerdictWithConfidence(true, -0.1, sampleThreshold(), 0.0, "test")
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("Observed rate");
            
            assertThatThrownBy(() -> 
                new VerdictWithConfidence(true, 1.1, sampleThreshold(), 0.0, "test")
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("Observed rate");
        }
        
        @Test
        @DisplayName("rejects null threshold")
        void rejectsNullThreshold() {
            assertThatThrownBy(() -> 
                new VerdictWithConfidence(true, 0.95, null, 0.0, "test")
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("Threshold");
        }
        
        @Test
        @DisplayName("rejects false positive probability outside [0, 1]")
        void rejectsInvalidFalsePositiveProbability() {
            assertThatThrownBy(() -> 
                new VerdictWithConfidence(false, 0.90, sampleThreshold(), -0.1, "test")
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("False positive probability");
            
            assertThatThrownBy(() -> 
                new VerdictWithConfidence(false, 0.90, sampleThreshold(), 1.1, "test")
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("False positive probability");
        }
        
        @Test
        @DisplayName("accepts valid verdict")
        void acceptsValidVerdict() {
            var verdict = new VerdictWithConfidence(
                false, 0.90, sampleThreshold(), 0.05, "Test interpretation"
            );
            
            assertThat(verdict.passed()).isFalse();
            assertThat(verdict.observedRate()).isEqualTo(0.90);
            assertThat(verdict.falsePositiveProbability()).isEqualTo(0.05);
            assertThat(verdict.interpretation()).isEqualTo("Test interpretation");
        }
    }
    
    @Nested
    @DisplayName("Derived Values")
    class DerivedValues {
        
        @Test
        @DisplayName("shortfall is threshold - observed when failed")
        void shortfallWhenFailed() {
            var verdict = new VerdictWithConfidence(
                false, 0.90, sampleThreshold(), 0.05, "Interpretation"
            );
            
            // Threshold ≈ 0.936, observed = 0.90
            assertThat(verdict.shortfall()).isCloseTo(0.036, within(0.001));
        }
        
        @Test
        @DisplayName("shortfall is 0 when passed")
        void shortfallIsZeroWhenPassed() {
            var verdict = new VerdictWithConfidence(
                true, 0.95, sampleThreshold(), 0.0, "Interpretation"
            );
            
            assertThat(verdict.shortfall()).isEqualTo(0.0);
        }
        
        @Test
        @DisplayName("confidence is complement of false positive probability")
        void confidenceIsComplement() {
            var verdict = new VerdictWithConfidence(
                false, 0.90, sampleThreshold(), 0.05, "Interpretation"
            );
            
            assertThat(verdict.confidence()).isCloseTo(0.95, within(0.001));
        }
    }
}

