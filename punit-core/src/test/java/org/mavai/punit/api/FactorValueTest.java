package org.mavai.punit.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FactorValue")
class FactorValueTest {

    enum Colour { RED, BLUE }

    @Test
    @DisplayName("lifts booleans")
    void lifts_booleans() {
        assertThat(FactorValue.of(true)).isEqualTo(new BooleanValue(true));
        assertThat(FactorValue.of(true).canonical()).isEqualTo("true");
        assertThat(FactorValue.of(false).canonical()).isEqualTo("false");
        assertThat(FactorValue.of(true).yamlValue()).isEqualTo(true);
    }

    @Test
    @DisplayName("lifts all integral widths to a long-backed value")
    void lifts_integrals() {
        assertThat(FactorValue.of((byte) 7)).isEqualTo(new IntegralValue(7));
        assertThat(FactorValue.of((short) 7)).isEqualTo(new IntegralValue(7));
        assertThat(FactorValue.of(7)).isEqualTo(new IntegralValue(7));
        assertThat(FactorValue.of(7L)).isEqualTo(new IntegralValue(7));
        assertThat(FactorValue.of(2048).canonical()).isEqualTo("2048");
        assertThat(FactorValue.of(-3).canonical()).isEqualTo("-3");
        assertThat(FactorValue.of(0).canonical()).isEqualTo("0");
    }

    @Test
    @DisplayName("lifts floats and doubles to a double-backed value with minimal canonical form")
    void lifts_decimals() {
        assertThat(FactorValue.of(0.3).canonical()).isEqualTo("0.3");
        assertThat(FactorValue.of(1.5).canonical()).isEqualTo("1.5");
        assertThat(FactorValue.of(2.0).canonical()).isEqualTo("2.0");
        assertThat(FactorValue.of(2.0f).canonical()).isEqualTo("2.0");
        assertThat(FactorValue.of(-0.25).canonical()).isEqualTo("-0.25");
    }

    @Test
    @DisplayName("lifts strings and JSON-quotes them canonically")
    void lifts_strings() {
        assertThat(FactorValue.of("hello").canonical()).isEqualTo("\"hello\"");
        assertThat(FactorValue.of("say \"hi\"").canonical())
                .isEqualTo("\"say \\\"hi\\\"\"");
        assertThat(FactorValue.of("a\\b").canonical()).isEqualTo("\"a\\\\b\"");
        assertThat(FactorValue.of("line\nbreak").canonical())
                .isEqualTo("\"line\\nbreak\"");
    }

    @Test
    @DisplayName("lifts enums preserving declaring class and name")
    void lifts_enums() {
        FactorValue red = FactorValue.of(Colour.RED);
        assertThat(red).isInstanceOf(EnumValue.class);
        EnumValue e = (EnumValue) red;
        assertThat(e.name()).isEqualTo("RED");
        assertThat(e.declaringClass()).contains("FactorValueTest");
        assertThat(e.canonical()).isEqualTo("\"RED\"");
        assertThat(e.yamlValue()).isEqualTo("RED");
    }

    @Test
    @DisplayName("lifts Duration to ISO-8601 canonical form")
    void lifts_durations() {
        FactorValue v = FactorValue.of(Duration.ofSeconds(90));
        assertThat(v).isInstanceOf(DurationValue.class);
        assertThat(v.canonical()).isEqualTo("\"PT1M30S\"");
        assertThat(v.yamlValue()).isEqualTo("PT1M30S");
    }

    @Test
    @DisplayName("lifts Instant to ISO-8601 canonical form")
    void lifts_instants() {
        Instant i = Instant.parse("2026-04-23T10:00:00Z");
        FactorValue v = FactorValue.of(i);
        assertThat(v).isInstanceOf(InstantValue.class);
        assertThat(v.canonical()).isEqualTo("\"2026-04-23T10:00:00Z\"");
    }

    @Test
    @DisplayName("lifts URI to canonical string form")
    void lifts_uris() {
        FactorValue v = FactorValue.of(URI.create("https://mavai.org/x"));
        assertThat(v).isInstanceOf(UriValue.class);
        assertThat(v.canonical()).isEqualTo("\"https://mavai.org/x\"");
    }

    @Test
    @DisplayName("rejects null raw values")
    void rejects_null() {
        assertThatNullPointerException()
                .isThrownBy(() -> FactorValue.of(null));
    }

    @Test
    @DisplayName("coerces unsupported types to a string via toString()")
    void coerces_unsupported_types_to_string() {
        FactorValue v = FactorValue.of(List.of(1, 2));
        assertThat(v).isInstanceOf(StringValue.class);
        assertThat(v.yamlValue()).isEqualTo("[1, 2]");
    }
}
