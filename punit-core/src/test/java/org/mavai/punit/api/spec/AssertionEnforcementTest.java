package org.mavai.punit.api.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.mavai.punit.api.spec.AssertionEnforcement.Dimension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("AssertionEnforcement — the advisory setting")
class AssertionEnforcementTest {

    @AfterEach
    void clearProperty() {
        System.clearProperty(AssertionEnforcement.PROPERTY);
    }

    @Test
    @DisplayName("unset or blank, every assertion is enforced")
    void unsetEnforcesEverything() {
        for (String raw : new String[] {null, "", "   "}) {
            AssertionEnforcement e = AssertionEnforcement.parse(raw);
            assertThat(e).isEqualTo(AssertionEnforcement.ALL_ENFORCED);
            assertThat(e.mode(Dimension.FUNCTIONAL)).isEqualTo(EnforcementMode.ENFORCED);
            assertThat(e.mode(Dimension.LATENCY)).isEqualTo(EnforcementMode.ENFORCED);
        }
    }

    @Test
    @DisplayName("functional makes the functional dimension advisory only")
    void functionalOnly() {
        AssertionEnforcement e = AssertionEnforcement.parse("functional");
        assertThat(e.mode(Dimension.FUNCTIONAL)).isEqualTo(EnforcementMode.ADVISORY);
        assertThat(e.mode(Dimension.LATENCY)).isEqualTo(EnforcementMode.ENFORCED);
    }

    @Test
    @DisplayName("latency makes the latency dimension advisory only")
    void latencyOnly() {
        AssertionEnforcement e = AssertionEnforcement.parse("latency");
        assertThat(e.mode(Dimension.FUNCTIONAL)).isEqualTo(EnforcementMode.ENFORCED);
        assertThat(e.mode(Dimension.LATENCY)).isEqualTo(EnforcementMode.ADVISORY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"functional,latency", "latency,functional", " Functional , LATENCY ",
            "latency,latency,functional"})
    @DisplayName("both, comma-separated, in any order and case")
    void both(String raw) {
        assertThat(AssertionEnforcement.parse(raw))
                .isEqualTo(AssertionEnforcement.advisory(Dimension.FUNCTIONAL, Dimension.LATENCY));
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "all", "both", "correctness", "latency,", ",functional",
            "functional;latency", "functional,,latency"})
    @DisplayName("an unknown or empty item is a configuration error, never ignored")
    void unknownValueRefused(String raw) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> AssertionEnforcement.parse(raw))
                .withMessageContaining(AssertionEnforcement.PROPERTY)
                .withMessageContaining("functional,latency");
    }

    @Test
    @DisplayName("the system property is the run's setting")
    void resolvedFromSystemProperty() {
        System.setProperty(AssertionEnforcement.PROPERTY, "latency");
        assertThat(AssertionEnforcement.fromEnvironment())
                .isEqualTo(AssertionEnforcement.advisory(Dimension.LATENCY));
    }

    @Test
    @DisplayName("the setting is spelled punit.advisory / PUNIT_ADVISORY")
    void names() {
        assertThat(AssertionEnforcement.PROPERTY).isEqualTo("punit.advisory");
        assertThat(AssertionEnforcement.ENV_VAR).isEqualTo("PUNIT_ADVISORY");
    }
}
