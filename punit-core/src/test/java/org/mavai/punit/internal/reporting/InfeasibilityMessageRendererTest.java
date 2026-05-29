package org.mavai.punit.internal.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import org.mavai.punit.statistics.VerificationFeasibilityEvaluator;
import org.mavai.punit.statistics.VerificationFeasibilityEvaluator.FeasibilityResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link InfeasibilityMessageRenderer}.
 */
@DisplayName("InfeasibilityMessageRenderer")
class InfeasibilityMessageRendererTest {

    private final FeasibilityResult infeasibleResult =
            VerificationFeasibilityEvaluator.evaluate(50, 0.9999, 0.95);

    @Nested
    @DisplayName("summary (verbose=false)")
    class SummaryMessage {

        @Test
        @DisplayName("includes test name, sample count, target percentage, and minimum N")
        void includesKeyFacts() {
            String message = InfeasibilityMessageRenderer.render(
                    "smokeTestQuick", infeasibleResult, false);

            assertThat(message)
                    .contains("smokeTestQuick")
                    .contains("50")
                    .contains("99.99%")
                    .contains(String.valueOf(infeasibleResult.minimumSamples()));
        }

        @Test
        @DisplayName("includes remediation options")
        void includesRemediation() {
            String message = InfeasibilityMessageRenderer.render(
                    "smokeTestQuick", infeasibleResult, false);

            assertThat(message)
                    .contains("Increase samples")
                    .contains("intent = SMOKE");
        }

        @Test
        @DisplayName("omits statistical jargon")
        void omitsStatisticalJargon() {
            String message = InfeasibilityMessageRenderer.render(
                    "smokeTestQuick", infeasibleResult, false);

            assertThat(message)
                    .doesNotContain("Wilson")
                    .doesNotContain("α")
                    .doesNotContain("p₀")
                    .doesNotContain("Confidence")
                    .doesNotContain("Bernoulli")
                    .doesNotContain("Criterion")
                    .doesNotContain("Assumption");
        }
    }

    @Nested
    @DisplayName("verbose (verbose=true)")
    class VerboseMessage {

        @Test
        @DisplayName("includes full statistical context")
        void includesStatisticalContext() {
            String message = InfeasibilityMessageRenderer.render(
                    "smokeTestQuick", infeasibleResult, true);

            assertThat(message)
                    .contains("Wilson score")
                    .contains("α")
                    .contains("p₀")
                    .contains("Confidence")
                    .contains("Bernoulli")
                    .contains("Criterion")
                    .contains("Assumption");
        }

        @Test
        @DisplayName("includes test name and remediation")
        void includesTestNameAndRemediation() {
            String message = InfeasibilityMessageRenderer.render(
                    "smokeTestQuick", infeasibleResult, true);

            assertThat(message)
                    .contains("smokeTestQuick")
                    .contains("Increase samples")
                    .contains("intent = SMOKE");
        }
    }

    @Test
    @DisplayName("formats whole-number target as integer percentage")
    void formatsWholeNumberTarget() {
        FeasibilityResult result = VerificationFeasibilityEvaluator.evaluate(1, 0.90, 0.95);
        String message = InfeasibilityMessageRenderer.render("test", result, false);

        assertThat(message).contains("90%");
    }

    @Test
    @DisplayName("formats fractional target without trailing zeros")
    void formatsFractionalTarget() {
        FeasibilityResult result = VerificationFeasibilityEvaluator.evaluate(1, 0.999, 0.95);
        String message = InfeasibilityMessageRenderer.render("test", result, false);

        assertThat(message).contains("99.9%");
    }
}
