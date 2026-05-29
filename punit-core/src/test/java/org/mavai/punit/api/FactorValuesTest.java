package org.mavai.punit.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("FactorValues")
class FactorValuesTest {

    @Nested
    @DisplayName("get by name")
    class GetByName {

        @Test
        @DisplayName("returns value for existing name")
        void returnsValueForExistingName() {
            FactorValues factors = new FactorValues(
                new Object[]{"value1", 42},
                List.of("name1", "name2")
            );

            assertThat(factors.get("name1")).isEqualTo("value1");
            assertThat(factors.get("name2")).isEqualTo(42);
        }

        @Test
        @DisplayName("throws for unknown name")
        void throwsForUnknownName() {
            FactorValues factors = new FactorValues(
                new Object[]{"value1"},
                List.of("name1")
            );

            assertThatThrownBy(() -> factors.get("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown factor")
                .hasMessageContaining("unknown");
        }
    }

    @Nested
    @DisplayName("getString")
    class GetString {

        @Test
        @DisplayName("returns string value")
        void returnsStringValue() {
            FactorValues factors = new FactorValues(
                new Object[]{"test"},
                List.of("name")
            );

            assertThat(factors.getString("name")).isEqualTo("test");
        }

        @Test
        @DisplayName("converts non-string to string")
        void convertsNonStringToString() {
            FactorValues factors = new FactorValues(
                new Object[]{42},
                List.of("number")
            );

            assertThat(factors.getString("number")).isEqualTo("42");
        }

        @Test
        @DisplayName("returns null for null value")
        void returnsNullForNullValue() {
            FactorValues factors = new FactorValues(
                new Object[]{null},
                List.of("name")
            );

            assertThat(factors.getString("name")).isNull();
        }
    }

    @Nested
    @DisplayName("getDouble")
    class GetDouble {

        @Test
        @DisplayName("returns double value")
        void returnsDoubleValue() {
            FactorValues factors = new FactorValues(
                new Object[]{3.14},
                List.of("pi")
            );

            assertThat(factors.getDouble("pi")).isEqualTo(3.14);
        }

        @Test
        @DisplayName("converts Number to double")
        void convertsNumberToDouble() {
            FactorValues factors = new FactorValues(
                new Object[]{42},
                List.of("int")
            );

            assertThat(factors.getDouble("int")).isEqualTo(42.0);
        }

        @Test
        @DisplayName("parses string as double")
        void parsesStringAsDouble() {
            FactorValues factors = new FactorValues(
                new Object[]{"3.14"},
                List.of("pi")
            );

            assertThat(factors.getDouble("pi")).isEqualTo(3.14);
        }

        @Test
        @DisplayName("throws for null value")
        void throwsForNullValue() {
            FactorValues factors = new FactorValues(
                new Object[]{null},
                List.of("name")
            );

            assertThatThrownBy(() -> factors.getDouble("name"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
        }
    }

    @Nested
    @DisplayName("getInt")
    class GetInt {

        @Test
        @DisplayName("returns int value")
        void returnsIntValue() {
            FactorValues factors = new FactorValues(
                new Object[]{42},
                List.of("number")
            );

            assertThat(factors.getInt("number")).isEqualTo(42);
        }

        @Test
        @DisplayName("converts Number to int")
        void convertsNumberToInt() {
            FactorValues factors = new FactorValues(
                new Object[]{42.9},
                List.of("double")
            );

            assertThat(factors.getInt("double")).isEqualTo(42);
        }

        @Test
        @DisplayName("parses string as int")
        void parsesStringAsInt() {
            FactorValues factors = new FactorValues(
                new Object[]{"42"},
                List.of("number")
            );

            assertThat(factors.getInt("number")).isEqualTo(42);
        }

        @Test
        @DisplayName("throws for null value")
        void throwsForNullValue() {
            FactorValues factors = new FactorValues(
                new Object[]{null},
                List.of("name")
            );

            assertThatThrownBy(() -> factors.getInt("name"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
        }
    }

    @Nested
    @DisplayName("get by index")
    class GetByIndex {

        @Test
        @DisplayName("returns value at index")
        void returnsValueAtIndex() {
            FactorValues factors = new FactorValues(
                new Object[]{"a", "b", "c"},
                List.of("x", "y", "z")
            );

            assertThat(factors.get(0)).isEqualTo("a");
            assertThat(factors.get(1)).isEqualTo("b");
            assertThat(factors.get(2)).isEqualTo("c");
        }

        @Test
        @DisplayName("throws for invalid index")
        void throwsForInvalidIndex() {
            FactorValues factors = new FactorValues(
                new Object[]{"a"},
                List.of("x")
            );

            assertThatThrownBy(() -> factors.get(5))
                .isInstanceOf(ArrayIndexOutOfBoundsException.class);
        }
    }

    @Nested
    @DisplayName("has")
    class Has {

        @Test
        @DisplayName("returns true for existing name")
        void returnsTrueForExistingName() {
            FactorValues factors = new FactorValues(
                new Object[]{"value"},
                List.of("name")
            );

            assertThat(factors.has("name")).isTrue();
        }

        @Test
        @DisplayName("returns false for non-existing name")
        void returnsFalseForNonExistingName() {
            FactorValues factors = new FactorValues(
                new Object[]{"value"},
                List.of("name")
            );

            assertThat(factors.has("other")).isFalse();
        }
    }

    @Nested
    @DisplayName("size")
    class Size {

        @Test
        @DisplayName("returns number of factors")
        void returnsNumberOfFactors() {
            FactorValues factors = new FactorValues(
                new Object[]{"a", "b", "c"},
                List.of("x", "y", "z")
            );

            assertThat(factors.size()).isEqualTo(3);
        }

        @Test
        @DisplayName("returns zero for empty factors")
        void returnsZeroForEmpty() {
            FactorValues factors = new FactorValues(
                new Object[]{},
                List.of()
            );

            assertThat(factors.size()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("names and values")
    class NamesAndValues {

        @Test
        @DisplayName("names() returns all names")
        void namesReturnsAllNames() {
            FactorValues factors = new FactorValues(
                new Object[]{"a", "b"},
                List.of("x", "y")
            );

            assertThat(factors.names()).containsExactly("x", "y");
        }

        @Test
        @DisplayName("values() returns clone of values")
        void valuesReturnsClone() {
            Object[] original = new Object[]{"a", "b"};
            FactorValues factors = new FactorValues(original, List.of("x", "y"));

            Object[] values = factors.values();
            values[0] = "modified";

            // Original should be unchanged
            assertThat(factors.get(0)).isEqualTo("a");
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTest {

        @Test
        @DisplayName("formats single factor")
        void formatsSingleFactor() {
            FactorValues factors = new FactorValues(
                new Object[]{"value"},
                List.of("name")
            );

            assertThat(factors.toString()).isEqualTo("FactorValues{name=value}");
        }

        @Test
        @DisplayName("formats multiple factors")
        void formatsMultipleFactors() {
            FactorValues factors = new FactorValues(
                new Object[]{"a", 42},
                List.of("str", "num")
            );

            assertThat(factors.toString()).isEqualTo("FactorValues{str=a, num=42}");
        }

        @Test
        @DisplayName("formats empty factors")
        void formatsEmptyFactors() {
            FactorValues factors = new FactorValues(
                new Object[]{},
                List.of()
            );

            assertThat(factors.toString()).isEqualTo("FactorValues{}");
        }
    }
}

