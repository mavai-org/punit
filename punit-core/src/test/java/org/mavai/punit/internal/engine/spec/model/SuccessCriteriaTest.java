package org.mavai.punit.internal.engine.spec.model;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SuccessCriteria")
class SuccessCriteriaTest {

    @Nested
    @DisplayName("alwaysTrue")
    class AlwaysTrue {

        @Test
        @DisplayName("returns true for any values")
        void returnsTrueForAnyValues() {
            SuccessCriteria criteria = SuccessCriteria.alwaysTrue();

            assertThat(criteria.isSuccess(Map.of("value", "anything"))).isTrue();
        }

        @Test
        @DisplayName("has descriptive description")
        void hasDescriptiveDescription() {
            SuccessCriteria criteria = SuccessCriteria.alwaysTrue();

            assertThat(criteria.getDescription()).isEqualTo("(always true)");
        }
    }

    @Nested
    @DisplayName("parse")
    class Parse {

        @Test
        @DisplayName("returns alwaysTrue for null expression")
        void returnsAlwaysTrueForNull() {
            SuccessCriteria criteria = SuccessCriteria.parse(null);

            assertThat(criteria.isSuccess(Map.of())).isTrue();
        }

        @Test
        @DisplayName("returns alwaysTrue for empty expression")
        void returnsAlwaysTrueForEmpty() {
            SuccessCriteria criteria = SuccessCriteria.parse("");

            assertThat(criteria.isSuccess(Map.of())).isTrue();
        }

        @Test
        @DisplayName("returns alwaysTrue for whitespace expression")
        void returnsAlwaysTrueForWhitespace() {
            SuccessCriteria criteria = SuccessCriteria.parse("   ");

            assertThat(criteria.isSuccess(Map.of())).isTrue();
        }

        @Test
        @DisplayName("parses valid expression")
        void parsesValidExpression() {
            SuccessCriteria criteria = SuccessCriteria.parse("isValid == true");

            assertThat(criteria.getDescription()).isEqualTo("isValid == true");
        }
    }

    @Nested
    @DisplayName("equality comparisons")
    class EqualityComparisons {

        @Test
        @DisplayName("equals with boolean true")
        void equalsWithBooleanTrue() {
            SuccessCriteria criteria = SuccessCriteria.parse("isValid == true");

            assertThat(criteria.isSuccess(Map.of("isValid", true))).isTrue();
            assertThat(criteria.isSuccess(Map.of("isValid", false))).isFalse();
        }

        @Test
        @DisplayName("equals with boolean false")
        void equalsWithBooleanFalse() {
            SuccessCriteria criteria = SuccessCriteria.parse("hasError == false");

            assertThat(criteria.isSuccess(Map.of("hasError", false))).isTrue();
            assertThat(criteria.isSuccess(Map.of("hasError", true))).isFalse();
        }

        @Test
        @DisplayName("equals with integer")
        void equalsWithInteger() {
            SuccessCriteria criteria = SuccessCriteria.parse("count == 5");

            assertThat(criteria.isSuccess(Map.of("count", 5))).isTrue();
            assertThat(criteria.isSuccess(Map.of("count", 3))).isFalse();
        }

        @Test
        @DisplayName("equals with double")
        void equalsWithDouble() {
            SuccessCriteria criteria = SuccessCriteria.parse("score == 0.5");

            assertThat(criteria.isSuccess(Map.of("score", 0.5))).isTrue();
            assertThat(criteria.isSuccess(Map.of("score", 0.8))).isFalse();
        }

        @Test
        @DisplayName("equals with string")
        void equalsWithString() {
            SuccessCriteria criteria = SuccessCriteria.parse("status == success");

            assertThat(criteria.isSuccess(Map.of("status", "success"))).isTrue();
            assertThat(criteria.isSuccess(Map.of("status", "error"))).isFalse();
        }

        @Test
        @DisplayName("equals with quoted string")
        void equalsWithQuotedString() {
            SuccessCriteria criteria = SuccessCriteria.parse("status == \"success\"");

            assertThat(criteria.isSuccess(Map.of("status", "success"))).isTrue();
        }

        @Test
        @DisplayName("not equals")
        void notEquals() {
            SuccessCriteria criteria = SuccessCriteria.parse("status != error");

            assertThat(criteria.isSuccess(Map.of("status", "success"))).isTrue();
            assertThat(criteria.isSuccess(Map.of("status", "error"))).isFalse();
        }

        @Test
        @DisplayName("not equals with null value")
        void notEqualsWithNullValue() {
            SuccessCriteria criteria = SuccessCriteria.parse("value != null");

            // When actual is null and we're checking != non-null, it should be true
            assertThat(criteria.isSuccess(Map.of())).isTrue();
        }
    }

    @Nested
    @DisplayName("numeric comparisons")
    class NumericComparisons {

        @Test
        @DisplayName("greater than")
        void greaterThan() {
            SuccessCriteria criteria = SuccessCriteria.parse("score > 0.5");

            assertThat(criteria.isSuccess(Map.of("score", 0.8))).isTrue();
            assertThat(criteria.isSuccess(Map.of("score", 0.5))).isFalse();
            assertThat(criteria.isSuccess(Map.of("score", 0.3))).isFalse();
        }

        @Test
        @DisplayName("greater than or equal")
        void greaterThanOrEqual() {
            SuccessCriteria criteria = SuccessCriteria.parse("score >= 0.5");

            assertThat(criteria.isSuccess(Map.of("score", 0.8))).isTrue();
            assertThat(criteria.isSuccess(Map.of("score", 0.5))).isTrue();
            assertThat(criteria.isSuccess(Map.of("score", 0.3))).isFalse();
        }

        @Test
        @DisplayName("less than")
        void lessThan() {
            SuccessCriteria criteria = SuccessCriteria.parse("errors < 3");

            assertThat(criteria.isSuccess(Map.of("errors", 1))).isTrue();
            assertThat(criteria.isSuccess(Map.of("errors", 3))).isFalse();
            assertThat(criteria.isSuccess(Map.of("errors", 5))).isFalse();
        }

        @Test
        @DisplayName("less than or equal")
        void lessThanOrEqual() {
            SuccessCriteria criteria = SuccessCriteria.parse("errors <= 3");

            assertThat(criteria.isSuccess(Map.of("errors", 1))).isTrue();
            assertThat(criteria.isSuccess(Map.of("errors", 3))).isTrue();
            assertThat(criteria.isSuccess(Map.of("errors", 5))).isFalse();
        }

        @Test
        @DisplayName("compares mixed numeric types")
        void comparesMixedNumericTypes() {
            SuccessCriteria criteria = SuccessCriteria.parse("score >= 0.5");

            // Integer value compared to double threshold
            assertThat(criteria.isSuccess(Map.of("score", 1))).isTrue();
        }
    }

    @Nested
    @DisplayName("logical operators")
    class LogicalOperators {

        @Test
        @DisplayName("AND - both true")
        void andBothTrue() {
            SuccessCriteria criteria = SuccessCriteria.parse("valid == true && score > 0.5");

            assertThat(criteria.isSuccess(Map.of(
                "valid", true,
                "score", 0.8
            ))).isTrue();
        }

        @Test
        @DisplayName("AND - first false")
        void andFirstFalse() {
            SuccessCriteria criteria = SuccessCriteria.parse("valid == true && score > 0.5");

            assertThat(criteria.isSuccess(Map.of(
                "valid", false,
                "score", 0.8
            ))).isFalse();
        }

        @Test
        @DisplayName("AND - second false")
        void andSecondFalse() {
            SuccessCriteria criteria = SuccessCriteria.parse("valid == true && score > 0.5");

            assertThat(criteria.isSuccess(Map.of(
                "valid", true,
                "score", 0.3
            ))).isFalse();
        }

        @Test
        @DisplayName("OR - both true")
        void orBothTrue() {
            SuccessCriteria criteria = SuccessCriteria.parse("valid == true || fallback == true");

            assertThat(criteria.isSuccess(Map.of(
                "valid", true,
                "fallback", true
            ))).isTrue();
        }

        @Test
        @DisplayName("OR - first true")
        void orFirstTrue() {
            SuccessCriteria criteria = SuccessCriteria.parse("valid == true || fallback == true");

            assertThat(criteria.isSuccess(Map.of(
                "valid", true,
                "fallback", false
            ))).isTrue();
        }

        @Test
        @DisplayName("OR - second true")
        void orSecondTrue() {
            SuccessCriteria criteria = SuccessCriteria.parse("valid == true || fallback == true");

            assertThat(criteria.isSuccess(Map.of(
                "valid", false,
                "fallback", true
            ))).isTrue();
        }

        @Test
        @DisplayName("OR - both false")
        void orBothFalse() {
            SuccessCriteria criteria = SuccessCriteria.parse("valid == true || fallback == true");

            assertThat(criteria.isSuccess(Map.of(
                "valid", false,
                "fallback", false
            ))).isFalse();
        }
    }

    @Nested
    @DisplayName("parentheses")
    class Parentheses {

        @Test
        @DisplayName("handles parenthesized expression")
        void handlesParenthesizedExpression() {
            SuccessCriteria criteria = SuccessCriteria.parse("(value == true)");

            assertThat(criteria.isSuccess(Map.of("value", true))).isTrue();
        }
    }

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test
        @DisplayName("returns false for unknown expression format")
        void returnsFalseForUnknownFormat() {
            SuccessCriteria criteria = SuccessCriteria.parse("not a valid expression");

            assertThat(criteria.isSuccess(Map.of())).isFalse();
        }

        @Test
        @DisplayName("returns false when key is missing")
        void returnsFalseWhenKeyMissing() {
            SuccessCriteria criteria = SuccessCriteria.parse("missingKey == true");

            assertThat(criteria.isSuccess(Map.of())).isFalse();
        }

        @Test
        @DisplayName("handles single quoted strings")
        void handlesSingleQuotedStrings() {
            SuccessCriteria criteria = SuccessCriteria.parse("status == 'ok'");

            assertThat(criteria.isSuccess(Map.of("status", "ok"))).isTrue();
        }

        @Test
        @DisplayName("compares Comparable non-numeric values")
        void comparesComparableValues() {
            SuccessCriteria criteria = SuccessCriteria.parse("name > aaa");

            assertThat(criteria.isSuccess(Map.of("name", "zzz"))).isTrue();
            assertThat(criteria.isSuccess(Map.of("name", "aaa"))).isFalse();
        }
    }
}
