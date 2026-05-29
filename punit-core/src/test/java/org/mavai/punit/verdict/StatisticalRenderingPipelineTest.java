package org.mavai.punit.verdict;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import org.mavai.punit.internal.reporting.PUnitReporter;
import org.mavai.punit.internal.reporting.VerdictTextRenderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * End-to-end rendering contract tests for the statistical pipeline.
 *
 * <p>Each scenario validates that hand-computed statistics flow correctly through
 * the verdict model into the rendered output. Scenarios A–G cover all conditional
 * branches: passed/failed, budget-exhausted, with/without baseline, with/without
 * dimensions, with/without termination, with/without covariate misalignment.
 */
@DisplayName("Statistical Rendering Pipeline")
class StatisticalRenderingPipelineTest {

    @Nested
    @DisplayName("Scenario A: Golden path PASS, baseline-driven")
    class ScenarioA {
        private final ProbabilisticTestVerdict verdict = ScenarioFixtures.scenarioA();

        @Test
        @DisplayName("renderSummary matches expected output")
        void renderSummary_matchesExpected() {
            String summary = VerdictTextRenderer.renderSummary(verdict);

            assertThat(summary).contains("0.9600 (96/100) >= required: 0.9374");
            assertThat(summary).contains(lv("Elapsed:", "150ms"));
            assertThat(summary).contains("Baseline:");
            assertThat(summary).contains("PaymentGateway.yaml (bundled)");
            assertThat(summary).doesNotContain("Termination:");
        }

        @Test
        @DisplayName("renderStatisticalAnalysis matches expected output")
        void renderStatisticalAnalysis_matchesExpected() {
            String analysis = VerdictTextRenderer.renderStatisticalAnalysis(verdict);

            assertThat(analysis).contains(lv("Baseline spec:", "PaymentGateway.yaml"));
            assertThat(analysis).contains("1000 (950 successes, rate: 0.9500)");
            assertThat(analysis).contains("0.9374 (Wilson)");
            assertThat(analysis).contains(lv("Confidence level:", "95.0%"));
            assertThat(analysis).contains("0.0196");
            assertThat(analysis).contains(lv("Wilson lower bound:", "0.9016"));
            assertThat(analysis).contains(lv("Z:", "0.9331"));
            assertThat(analysis).contains(lv("p-value:", "0.8246"));
            assertThat(analysis).doesNotContain("Covariate");
        }

        @Test
        @DisplayName("renderStatisticalAnalysisHtml wraps labels in tooltip spans")
        void renderStatisticalAnalysisHtml_wrapsLabelsInTooltips() {
            String html = VerdictTextRenderer.renderStatisticalAnalysisHtml(verdict);

            assertThat(html).contains("<span class=\"tip\" data-tip=");
            assertThat(html).contains("SE(p\u0302):");
            assertThat(html).contains("Wilson lower bound:");
            assertThat(html).contains("Z:");
            assertThat(html).contains("p-value:");
            // Values should not be inside spans
            assertThat(html).contains("</span>");
        }

        @Test
        @DisplayName("SE is derived from observed rate p̂, not threshold π₀")
        void seIsDerivedFromObservedRate() {
            // SE(p̂) = √(0.96 × 0.04 / 100) = 0.0196, NOT √(0.9374 × 0.0626 / 100) = 0.0242
            assertThat(verdict.statistics().standardError())
                    .isCloseTo(0.0196, within(0.0005));
            assertThat(verdict.statistics().standardError())
                    .isNotCloseTo(0.0242, within(0.001));
        }

        @Test
        @DisplayName("Z is derived from threshold π₀, not observed rate p̂")
        void zIsDerivedFromThreshold() {
            // Z = (0.96 - 0.9374) / SE₀ where SE₀ = √(π₀(1-π₀)/n)
            assertThat(verdict.statistics().testStatistic()).isPresent();
            assertThat(verdict.statistics().testStatistic().get())
                    .isCloseTo(0.9331, within(0.0005));
        }
    }

    @Nested
    @DisplayName("Scenario B: Golden path FAIL, baseline-driven")
    class ScenarioB {
        private final ProbabilisticTestVerdict verdict = ScenarioFixtures.scenarioB();

        @Test
        @DisplayName("renderSummary shows failure with < operator")
        void renderSummary_matchesExpected() {
            String summary = VerdictTextRenderer.renderSummary(verdict);

            assertThat(summary).contains("0.8500 (85/100) < required: 0.9374");
            assertThat(summary).contains(lv("Elapsed:", "150ms"));
            assertThat(summary).contains("PaymentGateway.yaml (bundled)");
        }

        @Test
        @DisplayName("renderStatisticalAnalysis matches expected output")
        void renderStatisticalAnalysis_matchesExpected() {
            String analysis = VerdictTextRenderer.renderStatisticalAnalysis(verdict);

            assertThat(analysis).contains("1000 (950 successes, rate: 0.9500)");
            assertThat(analysis).contains("0.9374 (Wilson)");
            assertThat(analysis).contains("0.0357");
            assertThat(analysis).contains(lv("Wilson lower bound:", "0.7672"));
            assertThat(analysis).contains(lv("Z:", "-3.6080"));
            assertThat(analysis).contains(lv("p-value:", "0.0002"));
        }
    }

    @Nested
    @DisplayName("Scenario C: Budget exhausted FAIL")
    class ScenarioC {
        private final ProbabilisticTestVerdict verdict = ScenarioFixtures.scenarioC();

        @Test
        @DisplayName("renderSummary shows budget exhaustion format")
        void renderSummary_matchesExpected() {
            String summary = VerdictTextRenderer.renderSummary(verdict);

            assertThat(summary).contains("50 of 100 (budget exhausted)");
            assertThat(summary).contains("0.8000 (40/50), required: 0.9000");
            assertThat(summary).contains("Method time budget exhausted (50/100 samples executed)");
            assertThat(summary).contains(lv("Elapsed:", "5000ms"));
            assertThat(summary).doesNotContain("Baseline:");
        }

        @Test
        @DisplayName("renderStatisticalAnalysis has no baseline section")
        void renderStatisticalAnalysis_matchesExpected() {
            String analysis = VerdictTextRenderer.renderStatisticalAnalysis(verdict);

            assertThat(analysis).doesNotContain("Baseline spec:");
            assertThat(analysis).contains(lv("Confidence level:", "95.0%"));
            assertThat(analysis).contains("0.0566");
            assertThat(analysis).contains(lv("Wilson lower bound:", "0.6696"));
            assertThat(analysis).contains(lv("Z:", "-2.3570"));
            assertThat(analysis).contains(lv("p-value:", "0.0092"));
        }
    }

    @Nested
    @DisplayName("Scenario D: Inline threshold PASS (no baseline)")
    class ScenarioD {
        private final ProbabilisticTestVerdict verdict = ScenarioFixtures.scenarioD();

        @Test
        @DisplayName("renderSummary shows pass without baseline")
        void renderSummary_matchesExpected() {
            String summary = VerdictTextRenderer.renderSummary(verdict);

            assertThat(summary).contains("0.9000 (180/200) >= required: 0.8500");
            assertThat(summary).contains(lv("Elapsed:", "300ms"));
            assertThat(summary).doesNotContain("Baseline:");
        }

        @Test
        @DisplayName("renderStatisticalAnalysis has no baseline section")
        void renderStatisticalAnalysis_matchesExpected() {
            String analysis = VerdictTextRenderer.renderStatisticalAnalysis(verdict);

            assertThat(analysis).doesNotContain("Baseline spec:");
            assertThat(analysis).doesNotContain("Derived threshold:");
            assertThat(analysis).contains(lv("Confidence level:", "95.0%"));
            assertThat(analysis).contains("0.0212");
            assertThat(analysis).contains(lv("Wilson lower bound:", "0.8506"));
            assertThat(analysis).contains(lv("Z:", "1.9802"));
            assertThat(analysis).contains(lv("p-value:", "0.9762"));
        }
    }

    @Nested
    @DisplayName("Scenario E: PASS with descriptive latency dimension")
    class ScenarioE {
        private final ProbabilisticTestVerdict verdict = ScenarioFixtures.scenarioE();

        @Test
        @DisplayName("renderSummary shows functional pass-rate + descriptive latency")
        void renderSummary_matchesExpected() {
            String summary = VerdictTextRenderer.renderSummary(verdict);

            assertThat(summary).contains("0.9500 (95/100) >= required: 0.9000");
            assertThat(summary).contains("95/100 passed");
            assertThat(summary).contains("Latency:");
            assertThat(summary).contains("p95=420ms");
            assertThat(summary).contains(lv("Elapsed:", "200ms"));
            assertThat(summary).contains("ShoppingBasket.yaml");
        }

        @Test
        @DisplayName("renderStatisticalAnalysis shows core statistics")
        void renderStatisticalAnalysis_matchesExpected() {
            String analysis = VerdictTextRenderer.renderStatisticalAnalysis(verdict);

            assertThat(analysis).contains(lv("Confidence level:", "95.0%"));
            assertThat(analysis).contains("0.0218");
            assertThat(analysis).contains(lv("Wilson lower bound:", "0.8883"));
            assertThat(analysis).contains(lv("Z:", "1.6667"));
            assertThat(analysis).contains(lv("p-value:", "0.9522"));
        }
    }

    @Nested
    @DisplayName("Scenario F: Inconclusive (covariate misalignment)")
    class ScenarioF {
        private final ProbabilisticTestVerdict verdict = ScenarioFixtures.scenarioF();

        @Test
        @DisplayName("renderSummary shows truthful comparator and inconclusive verdict")
        void renderSummary_matchesExpected() {
            String summary = VerdictTextRenderer.renderSummary(verdict);

            // Comparator reflects actual rate vs threshold, not junitPassed
            assertThat(summary).contains("0.9300 (93/100) >= required: 0.9000");
            assertThat(summary).contains("Verdict:");
            assertThat(summary).contains("Inconclusive");
            assertThat(summary).contains("covariate misalignment");
            assertThat(summary).contains(lv("Elapsed:", "150ms"));
        }

        @Test
        @DisplayName("renderStatisticalAnalysis shows covariate misalignments")
        void renderStatisticalAnalysis_matchesExpected() {
            String analysis = VerdictTextRenderer.renderStatisticalAnalysis(verdict);

            assertThat(analysis).contains(lv("Confidence level:", "95.0%"));
            assertThat(analysis).contains("0.0255");
            assertThat(analysis).contains(lv("Wilson lower bound:", "0.8625"));
            assertThat(analysis).contains(lv("Z:", "1.0000"));
            assertThat(analysis).contains(lv("p-value:", "0.8413"));

            assertThat(analysis).contains("Covariate misalignments:");
            assertThat(analysis).contains("model: baseline=gpt-4, test=gpt-4o");
        }
    }

    @Nested
    @DisplayName("Scenario G: Early termination (impossibility)")
    class ScenarioG {
        private final ProbabilisticTestVerdict verdict = ScenarioFixtures.scenarioG();

        @Test
        @DisplayName("renderSummary shows impossibility termination")
        void renderSummary_matchesExpected() {
            String summary = VerdictTextRenderer.renderSummary(verdict);

            assertThat(summary).contains("0.6667 (20/30) < required: 0.9000");
            assertThat(summary).contains("Cannot reach required pass rate (insufficient remaining samples)");
            assertThat(summary).contains(lv("Elapsed:", "45ms"));
            assertThat(summary).doesNotContain("Baseline:");
        }

        @Test
        @DisplayName("renderStatisticalAnalysis matches expected output")
        void renderStatisticalAnalysis_matchesExpected() {
            String analysis = VerdictTextRenderer.renderStatisticalAnalysis(verdict);

            assertThat(analysis).doesNotContain("Baseline spec:");
            assertThat(analysis).contains(lv("Confidence level:", "95.0%"));
            assertThat(analysis).contains("0.0861");
            assertThat(analysis).contains(lv("Wilson lower bound:", "0.4880"));
            assertThat(analysis).contains(lv("Z:", "-4.2597"));
            assertThat(analysis).contains(lv("p-value:", "0.0000"));
        }
    }

    @Nested
    @DisplayName("Scenario H: Token budget exhausted")
    class ScenarioH {
        private final ProbabilisticTestVerdict verdict = ScenarioFixtures.scenarioH();

        @Test
        @DisplayName("renderSummary shows token budget exhaustion with sample count")
        void renderSummary_matchesExpected() {
            String summary = VerdictTextRenderer.renderSummary(verdict);

            assertThat(summary).contains("66 of 100 (budget exhausted)");
            assertThat(summary).contains("0.8333 (55/66), required: 0.9000");
            assertThat(summary).contains("Method token budget exhausted (66/100 samples executed)");
            assertThat(summary).contains(lv("Elapsed:", "3200ms"));
        }
    }

    @Nested
    @DisplayName("Termination message distinctness regression")
    class TerminationMessageDistinctness {

        @Test
        @DisplayName("impossibility, time budget, and token budget produce distinct termination messages")
        void terminationMessages_areDistinct() {
            String impossibilitySummary = VerdictTextRenderer.renderSummary(ScenarioFixtures.scenarioG());
            String timeBudgetSummary = VerdictTextRenderer.renderSummary(ScenarioFixtures.scenarioC());
            String tokenBudgetSummary = VerdictTextRenderer.renderSummary(ScenarioFixtures.scenarioH());

            // Each identifies its specific cause
            assertThat(impossibilitySummary).contains("insufficient remaining samples");
            assertThat(timeBudgetSummary).contains("time budget exhausted");
            assertThat(tokenBudgetSummary).contains("token budget exhausted");

            // Budget messages include sample counts
            assertThat(timeBudgetSummary).contains("50/100 samples executed");
            assertThat(tokenBudgetSummary).contains("66/100 samples executed");

            // Impossibility does NOT mention budget
            assertThat(impossibilitySummary).doesNotContainIgnoringCase("budget");
        }

        @Test
        @DisplayName("formatTerminationMessage produces distinct text for every termination reason")
        void formatTerminationMessage_allReasonsDistinct() {
            var reasons = java.util.EnumSet.allOf(
                    org.mavai.punit.verdict.TerminationReason.class);
            var messages = new java.util.HashSet<String>();

            for (var reason : reasons) {
                String message = VerdictTextRenderer.formatTerminationMessage(reason, 50, 100);
                assertThat(messages)
                        .as("Duplicate termination message for %s: '%s'", reason, message)
                        .doesNotContain(message);
                messages.add(message);
            }

            assertThat(messages).hasSize(reasons.size());
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Formats a label-value pair using the standard LABEL_WIDTH (21).
     * Both renderSummary and renderStatisticalAnalysis use LABEL_WIDTH.
     */
    private static String lv(String label, String value) {
        return PUnitReporter.labelValue(label, value);
    }
}
