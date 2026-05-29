package org.mavai.punit.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SampleSizeRequirement}.
 * 
 * <p>Tests the record that captures the result of a power analysis calculation.
 */
@DisplayName("SampleSizeRequirement")
class SampleSizeRequirementTest {
    
    @Nested
    @DisplayName("Validation")
    class Validation {
        
        @Test
        @DisplayName("rejects non-positive required samples")
        void rejectsNonPositiveRequiredSamples() {
            assertThatThrownBy(() -> 
                new SampleSizeRequirement(0, 0.95, 0.80, 0.05, 0.95, 0.90)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("Required samples");
        }
        
        @Test
        @DisplayName("rejects confidence outside (0, 1)")
        void rejectsInvalidConfidence() {
            assertThatThrownBy(() -> 
                new SampleSizeRequirement(100, 0.0, 0.80, 0.05, 0.95, 0.90)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("Confidence");
            
            assertThatThrownBy(() -> 
                new SampleSizeRequirement(100, 1.0, 0.80, 0.05, 0.95, 0.90)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("Confidence");
        }
        
        @Test
        @DisplayName("rejects power outside (0, 1)")
        void rejectsInvalidPower() {
            assertThatThrownBy(() -> 
                new SampleSizeRequirement(100, 0.95, 0.0, 0.05, 0.95, 0.90)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("Power");
        }
        
        @Test
        @DisplayName("rejects effect size outside (0, 1)")
        void rejectsInvalidEffectSize() {
            assertThatThrownBy(() -> 
                new SampleSizeRequirement(100, 0.95, 0.80, 0.0, 0.95, 0.90)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("Minimum detectable effect");
        }
        
        @Test
        @DisplayName("rejects null rate outside [0, 1]")
        void rejectsInvalidNullRate() {
            assertThatThrownBy(() -> 
                new SampleSizeRequirement(100, 0.95, 0.80, 0.05, -0.1, 0.90)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("Null rate");
        }
        
        @Test
        @DisplayName("rejects alternative rate outside [0, 1]")
        void rejectsInvalidAlternativeRate() {
            assertThatThrownBy(() -> 
                new SampleSizeRequirement(100, 0.95, 0.80, 0.05, 0.95, -0.1)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("Alternative rate");
        }
        
        @Test
        @DisplayName("accepts valid requirement")
        void acceptsValidRequirement() {
            var req = new SampleSizeRequirement(150, 0.95, 0.80, 0.05, 0.95, 0.90);
            
            assertThat(req.requiredSamples()).isEqualTo(150);
            assertThat(req.confidence()).isEqualTo(0.95);
            assertThat(req.power()).isEqualTo(0.80);
            assertThat(req.minDetectableEffect()).isEqualTo(0.05);
            assertThat(req.nullRate()).isEqualTo(0.95);
            assertThat(req.alternativeRate()).isEqualTo(0.90);
        }
    }
}

