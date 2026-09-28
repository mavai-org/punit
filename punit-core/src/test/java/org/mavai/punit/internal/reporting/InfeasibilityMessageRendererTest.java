package org.mavai.punit.internal.reporting;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mavai.punit.api.spec.ConfigurationRefusal;
import org.mavai.punit.statistics.ConfigurationError;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link InfeasibilityMessageRenderer}.
 */
@DisplayName("InfeasibilityMessageRenderer")
class InfeasibilityMessageRendererTest {

    private static final ConfigurationRefusal LARGER = new ConfigurationRefusal(
            ConfigurationError.TEST_LARGER_THAN_BASELINE,
            "the test (200 samples) is larger than its baseline (100 samples)");
    private static final ConfigurationRefusal INFEASIBLE = new ConfigurationRefusal(
            ConfigurationError.COMPLIANCE_INFEASIBLE,
            "criterion 'c': no count of 200 samples can demonstrate 0.999 at alpha 0.05 "
                    + "(feasibility minimum 2995)");

    @Test
    @DisplayName("names every configuration error, with its reason, in the order given")
    void namesEveryCode() {
        String message = InfeasibilityMessageRenderer.renderRefusal(
                "offerExtraction", List.of(LARGER, INFEASIBLE));

        assertThat(message)
                .contains("CONFIGURATION REFUSED")
                .contains("offerExtraction")
                .contains("TEST_LARGER_THAN_BASELINE — the test (200 samples)")
                .contains("COMPLIANCE_INFEASIBLE — criterion 'c'");
        assertThat(message.indexOf("TEST_LARGER_THAN_BASELINE"))
                .isLessThan(message.indexOf("COMPLIANCE_INFEASIBLE"));
    }

    @Test
    @DisplayName("offers the remedy of each code present")
    void offersRemedies() {
        assertThat(InfeasibilityMessageRenderer.renderRefusal("t", List.of(LARGER)))
                .contains("Measure a baseline at least as large as the test")
                .doesNotContain("intent = SMOKE");
        assertThat(InfeasibilityMessageRenderer.renderRefusal("t", List.of(INFEASIBLE)))
                .contains("feasibility minimum")
                .contains("intent = SMOKE")
                .doesNotContain("Measure a baseline");
    }

    @Test
    @DisplayName("names the soundness floor and the configured confidence")
    void soundnessFloorBreach() {
        String message = InfeasibilityMessageRenderer.renderSoundnessFloorBreach("t", 0.7, 0.8);
        assertThat(message).contains("70%").contains("80%");
    }

    @Test
    @DisplayName("formats whole-number and fractional percentages without trailing zeros")
    void formatsPercentages() {
        assertThat(InfeasibilityMessageRenderer.formatTargetAsPercentage(0.90)).isEqualTo("90%");
        assertThat(InfeasibilityMessageRenderer.formatTargetAsPercentage(0.999)).isEqualTo("99.9%");
    }
}
