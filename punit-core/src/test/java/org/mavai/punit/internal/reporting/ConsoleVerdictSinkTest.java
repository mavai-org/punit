package org.mavai.punit.internal.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.mavai.punit.api.spec.Verdict;
import org.mavai.punit.verdict.TerminationReason;
import org.mavai.punit.verdict.ProbabilisticTestVerdict;
import org.mavai.punit.verdict.ProbabilisticTestVerdictBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ConsoleVerdictSink")
class ConsoleVerdictSinkTest {

    @Nested
    @DisplayName("passing verdict")
    class PassingVerdict {

        @Test
        @DisplayName("renders pass verdict with observed and required pass rates")
        void rendersPassVerdict() {
            String output = renderVerdict(passingVerdict());

            assertThat(output).contains("VERDICT: PASS");
            assertThat(output).contains("Observed pass rate:");
            assertThat(output).contains(">= required:");
        }

        @Test
        @DisplayName("includes test name in output")
        void includesTestName() {
            String output = renderVerdict(passingVerdict());

            assertThat(output).contains("MySpec.shouldPass");
        }

        @Test
        @DisplayName("includes elapsed time")
        void includesElapsedTime() {
            String output = renderVerdict(passingVerdict());

            assertThat(output).contains("Elapsed:");
            assertThat(output).contains("150ms");
        }
    }

    @Nested
    @DisplayName("failing verdict")
    class FailingVerdict {

        @Test
        @DisplayName("renders fail verdict with less-than comparison")
        void rendersFailVerdict() {
            String output = renderVerdict(failingVerdict());

            assertThat(output).contains("VERDICT: FAIL");
            assertThat(output).contains("< required:");
        }

        @Test
        @DisplayName("renders budget exhaustion")
        void rendersBudgetExhaustion() {
            ProbabilisticTestVerdict verdict = new ProbabilisticTestVerdictBuilder()
                    .identity("MySpec", "shouldPass", null)
                    .execution(100, 42, 30, 12, 0.9, 0.7143, 200)
                    .termination(TerminationReason.METHOD_TIME_BUDGET_EXHAUSTED, "Time budget exceeded")
                    .junitPassed(false)
                    .criterionVerdict(Verdict.FAIL)
                    .build();

            String output = renderVerdict(verdict);

            assertThat(output).contains("budget exhausted");
            assertThat(output).contains("Termination:");
        }
    }

    @Nested
    @DisplayName("dimension breakdown")
    class DimensionBreakdown {

        @Test
        @DisplayName("shows per-dimension breakdown when both dimensions are asserted")
        void showsBothDimensions() {
            ProbabilisticTestVerdict verdict = new ProbabilisticTestVerdictBuilder()
                    .identity("MySpec", "shouldPass", null)
                    .execution(100, 100, 95, 5, 0.9, 0.95, 150)
                    .functionalDimension(95, 5)
                    .latencyDimension(new ProbabilisticTestVerdictBuilder.LatencyInput(
                            90, 100, false, null,
                            10, 20, 30, 50, 100,
                            List.of()))
                    .junitPassed(true)
                    .criterionVerdict(Verdict.PASS)
                    .build();

            String output = renderVerdict(verdict);

            assertThat(output).contains("Contract:");
            assertThat(output).contains("95/100 passed");
        }

        @Test
        @DisplayName("omits breakdown when only one dimension is asserted")
        void omitsForSingleDimension() {
            ProbabilisticTestVerdict verdict = new ProbabilisticTestVerdictBuilder()
                    .identity("MySpec", "shouldPass", null)
                    .execution(100, 100, 95, 5, 0.9, 0.95, 150)
                    .functionalDimension(95, 5)
                    .junitPassed(true)
                    .criterionVerdict(Verdict.PASS)
                    .build();

            String output = renderVerdict(verdict);

            assertThat(output).doesNotContain("Contract:");
            assertThat(output).doesNotContain("Latency:");
        }
    }

    @Nested
    @DisplayName("framing")
    class Framing {

        @Test
        @DisplayName("includes PUnit header and footer dividers")
        void includesFraming() {
            String output = renderVerdict(passingVerdict());

            assertThat(output).contains("PUnit");
            // Footer is a line of ═ characters
            assertThat(output).contains("═".repeat(78));
        }
    }

    // ── Test helpers ──────────────────────────────────────────────────────

    private ProbabilisticTestVerdict passingVerdict() {
        return new ProbabilisticTestVerdictBuilder()
                .identity("MySpec", "shouldPass", null)
                .execution(100, 100, 95, 5, 0.9, 0.95, 150)
                .junitPassed(true)
                .criterionVerdict(Verdict.PASS)
                .build();
    }

    private ProbabilisticTestVerdict failingVerdict() {
        return new ProbabilisticTestVerdictBuilder()
                .identity("MySpec", "shouldPass", null)
                .execution(100, 100, 80, 20, 0.9, 0.80, 200)
                .junitPassed(false)
                .criterionVerdict(Verdict.FAIL)
                .build();
    }

    @Nested
    @DisplayName("zero-failures verdict")
    class ZeroFailuresVerdict {

        @Test
        @DisplayName("states the failure count, never a pass rate against a required one")
        void statesTheFailureCount() {
            String output = renderVerdict(zeroFailuresVerdict());

            assertThat(output)
                    .contains("Failures:")
                    .contains("11 of 3000 samples (zero failures required)")
                    .doesNotContain("required:")
                    .doesNotContain("%")
                    .doesNotContain("NaN");
        }
    }

    /** A test whose one criterion is a zero-failures criterion: 11 of 3000 failed. */
    private static ProbabilisticTestVerdict zeroFailuresVerdict() {
        return new org.mavai.punit.verdict.ProbabilisticTestVerdictBuilder()
                .identity("HarmfulAdviceTest", "harmfulAdviceIsRejected", null)
                .execution(3000, 3000, 2989, 11, Double.NaN, 2989.0 / 3000, 200)
                .provenance(org.mavai.punit.api.ThresholdOrigin.POLICY, "harmful-advice", null)
                .perCriterion(new org.mavai.punit.verdict.PerCriterionStructure(
                        List.of(org.mavai.punit.verdict.CriterionRow.observational(
                                "harmful-advice-is-rejected",
                                org.mavai.punit.api.spec.Verdict.FAIL, 2989, 11, 0)),
                        org.mavai.punit.api.spec.Verdict.FAIL))
                .junitPassed(false)
                .criterionVerdict(org.mavai.punit.api.spec.Verdict.FAIL)
                .build();
    }

    private String renderVerdict(ProbabilisticTestVerdict verdict) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos, true, StandardCharsets.UTF_8);
        ConsoleVerdictSink sink = new ConsoleVerdictSink(ps);

        sink.accept(verdict);
        return baos.toString(StandardCharsets.UTF_8);
    }
}
