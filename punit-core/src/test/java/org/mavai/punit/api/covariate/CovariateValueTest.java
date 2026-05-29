package org.mavai.punit.api.covariate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CovariateValue}.
 */
@DisplayName("CovariateValue")
class CovariateValueTest {

    @Nested
    @DisplayName("StringValue")
    class StringValueTests {

        @Test
        @DisplayName("toCanonicalString() should return the value")
        void toCanonicalStringShouldReturnValue() {
            var value = new CovariateValue.StringValue("WEEKEND");

            assertThat(value.toCanonicalString()).isEqualTo("WEEKEND");
        }

        @Test
        @DisplayName("should reject null value")
        void shouldRejectNullValue() {
            assertThatThrownBy(() -> new CovariateValue.StringValue(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("equality should work correctly")
        void equalityShouldWorkCorrectly() {
            var value1 = new CovariateValue.StringValue("EU");
            var value2 = new CovariateValue.StringValue("EU");
            var value3 = new CovariateValue.StringValue("US");

            assertThat(value1).isEqualTo(value2);
            assertThat(value1).isNotEqualTo(value3);
            assertThat(value1.hashCode()).isEqualTo(value2.hashCode());
        }

        @Test
        @DisplayName("should support time period labels")
        void shouldSupportTimePeriodLabels() {
            var value = new CovariateValue.StringValue("MORNING");

            assertThat(value.toCanonicalString()).isEqualTo("MORNING");
        }

        @Test
        @DisplayName("should support region group labels")
        void shouldSupportRegionGroupLabels() {
            var value = new CovariateValue.StringValue("OTHER");

            assertThat(value.toCanonicalString()).isEqualTo("OTHER");
        }

        @Test
        @DisplayName("should support timezone identifiers")
        void shouldSupportTimezoneIdentifiers() {
            var value = new CovariateValue.StringValue("Europe/London");

            assertThat(value.toCanonicalString()).isEqualTo("Europe/London");
        }
    }
}
