package org.mavai.punit.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DerivationContext}.
 * 
 * <p>Tests the record that captures parameters used in threshold derivation.
 */
@DisplayName("DerivationContext")
class DerivationContextTest {
    
    @Nested
    @DisplayName("Validation")
    class Validation {
        
        @Test
        @DisplayName("rejects baseline rate outside [0, 1]")
        void rejectsInvalidBaselineRate() {
            assertThatThrownBy(() -> 
                new DerivationContext(-0.1, 1000, 100, 0.95)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("Baseline rate");
            
            assertThatThrownBy(() -> 
                new DerivationContext(1.1, 1000, 100, 0.95)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("Baseline rate");
        }
        
        @Test
        @DisplayName("rejects non-positive baseline samples")
        void rejectsNonPositiveBaselineSamples() {
            assertThatThrownBy(() -> 
                new DerivationContext(0.95, 0, 100, 0.95)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("Baseline samples");
        }
        
        @Test
        @DisplayName("rejects non-positive test samples")
        void rejectsNonPositiveTestSamples() {
            assertThatThrownBy(() -> 
                new DerivationContext(0.95, 1000, 0, 0.95)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("Test samples");
        }
        
        @Test
        @DisplayName("rejects confidence outside (0, 1)")
        void rejectsInvalidConfidence() {
            assertThatThrownBy(() -> 
                new DerivationContext(0.95, 1000, 100, 0.0)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("Confidence");
            
            assertThatThrownBy(() -> 
                new DerivationContext(0.95, 1000, 100, 1.0)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("Confidence");
        }
        
        @Test
        @DisplayName("accepts valid context")
        void acceptsValidContext() {
            var context = new DerivationContext(0.951, 1000, 100, 0.95);
            
            assertThat(context.baselineRate()).isEqualTo(0.951);
            assertThat(context.baselineSamples()).isEqualTo(1000);
            assertThat(context.testSamples()).isEqualTo(100);
            assertThat(context.confidence()).isEqualTo(0.95);
        }
        
        @Test
        @DisplayName("accepts perfect baseline rate (p̂ = 1.0)")
        void acceptsPerfectBaselineRate() {
            var context = new DerivationContext(1.0, 1000, 100, 0.95);
            assertThat(context.baselineRate()).isEqualTo(1.0);
        }
        
        @Test
        @DisplayName("accepts zero baseline rate (p̂ = 0.0)")
        void acceptsZeroBaselineRate() {
            var context = new DerivationContext(0.0, 1000, 100, 0.95);
            assertThat(context.baselineRate()).isEqualTo(0.0);
        }
    }
}

