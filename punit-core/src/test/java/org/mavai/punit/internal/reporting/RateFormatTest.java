package org.mavai.punit.internal.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("RateFormat")
class RateFormatTest {

    @Nested
    @DisplayName("format()")
    class Format {

        @Test
        @DisplayName("formats typical pass rate with 4 decimal places")
        void formatsTypicalRate() {
            assertThat(RateFormat.format(0.95)).isEqualTo("0.9500");
        }

        @Test
        @DisplayName("preserves precision for high-reliability thresholds")
        void preservesPrecisionForHighReliability() {
            assertThat(RateFormat.format(0.9999)).isEqualTo("0.9999");
        }

        @Test
        @DisplayName("formats zero")
        void formatsZero() {
            assertThat(RateFormat.format(0.0)).isEqualTo("0.0000");
        }

        @Test
        @DisplayName("formats one")
        void formatsOne() {
            assertThat(RateFormat.format(1.0)).isEqualTo("1.0000");
        }

        @Test
        @DisplayName("rounds to 4 decimal places")
        void roundsToFourDecimalPlaces() {
            assertThat(RateFormat.format(0.91649)).isEqualTo("0.9165");
        }
    }
}
