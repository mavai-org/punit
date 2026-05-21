package org.javai.punit.api.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ExpiredBaselinePolicy — config parsing")
class ExpiredBaselinePolicyTest {

    @Test
    @DisplayName("null or blank defaults to WARN")
    void defaultsToWarn() {
        assertThat(ExpiredBaselinePolicy.parse(null)).isEqualTo(ExpiredBaselinePolicy.WARN);
        assertThat(ExpiredBaselinePolicy.parse("")).isEqualTo(ExpiredBaselinePolicy.WARN);
        assertThat(ExpiredBaselinePolicy.parse("   ")).isEqualTo(ExpiredBaselinePolicy.WARN);
    }

    @Test
    @DisplayName("WARN / FAIL parse case- and whitespace-insensitively")
    void parsesKnownValues() {
        assertThat(ExpiredBaselinePolicy.parse("WARN")).isEqualTo(ExpiredBaselinePolicy.WARN);
        assertThat(ExpiredBaselinePolicy.parse("fail")).isEqualTo(ExpiredBaselinePolicy.FAIL);
        assertThat(ExpiredBaselinePolicy.parse("  Fail  ")).isEqualTo(ExpiredBaselinePolicy.FAIL);
    }

    @Test
    @DisplayName("an unknown value fails fast, naming the bad value and the accepted set")
    void rejectsUnknownValue() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ExpiredBaselinePolicy.parse("STRICT"))
                .withMessageContaining("STRICT")
                .withMessageContaining("WARN")
                .withMessageContaining("FAIL");
    }
}
