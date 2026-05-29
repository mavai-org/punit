package org.mavai.punit.internal.engine.budget;

import static org.assertj.core.api.Assertions.assertThat;
import org.mavai.punit.api.BudgetExhaustedBehavior;
import org.mavai.punit.verdict.TerminationReason;
import org.mavai.punit.verdict.TokenMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BudgetMessageFormatter}.
 */
@DisplayName("BudgetMessageFormatter")
class BudgetMessageFormatterTest {

    private BudgetMessageFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new BudgetMessageFormatter();
    }

    private CostBudgetMonitor unlimitedMethodBudget() {
        return new CostBudgetMonitor(0, 0, 0, TokenMode.NONE,
                BudgetExhaustedBehavior.EVALUATE_PARTIAL);
    }

    @Nested
    @DisplayName("buildExhaustionMessage()")
    class BuildExhaustionMessage {

        @Test
        @DisplayName("falls back to description when suite budget is null for suite time exhaustion")
        void fallbackWhenSuiteNullForSuiteTime() {
            String message = formatter.buildExhaustionMessage(
                    TerminationReason.SUITE_TIME_BUDGET_EXHAUSTED,
                    unlimitedMethodBudget(), null, null);

            assertThat(message).isEqualTo(TerminationReason.SUITE_TIME_BUDGET_EXHAUSTED.getDescription());
        }

        @Test
        @DisplayName("falls back to description when suite budget is null for suite token exhaustion")
        void fallbackWhenSuiteNullForSuiteToken() {
            String message = formatter.buildExhaustionMessage(
                    TerminationReason.SUITE_TOKEN_BUDGET_EXHAUSTED,
                    unlimitedMethodBudget(), null, null);

            assertThat(message).isEqualTo(TerminationReason.SUITE_TOKEN_BUDGET_EXHAUSTED.getDescription());
        }

        @Test
        @DisplayName("falls back to description when class budget is null for class time exhaustion")
        void fallbackWhenClassNullForClassTime() {
            String message = formatter.buildExhaustionMessage(
                    TerminationReason.CLASS_TIME_BUDGET_EXHAUSTED,
                    unlimitedMethodBudget(), null, null);

            assertThat(message).isEqualTo(TerminationReason.CLASS_TIME_BUDGET_EXHAUSTED.getDescription());
        }

        @Test
        @DisplayName("falls back to description when class budget is null for class token exhaustion")
        void fallbackWhenClassNullForClassToken() {
            String message = formatter.buildExhaustionMessage(
                    TerminationReason.CLASS_TOKEN_BUDGET_EXHAUSTED,
                    unlimitedMethodBudget(), null, null);

            assertThat(message).isEqualTo(TerminationReason.CLASS_TOKEN_BUDGET_EXHAUSTED.getDescription());
        }

        @Test
        @DisplayName("formats suite time exhaustion with budget details")
        void formatsSuiteTimeWithDetails() {
            SharedBudgetMonitor suiteBudget = new SharedBudgetMonitor(
                    SharedBudgetMonitor.Scope.SUITE, 100, 0, BudgetExhaustedBehavior.FAIL);

            String message = formatter.buildExhaustionMessage(
                    TerminationReason.SUITE_TIME_BUDGET_EXHAUSTED,
                    unlimitedMethodBudget(), null, suiteBudget);

            assertThat(message).contains("Suite time budget exhausted");
            assertThat(message).contains("100ms budget");
        }

        @Test
        @DisplayName("formats class token exhaustion with budget details")
        void formatsClassTokenWithDetails() {
            SharedBudgetMonitor classBudget = new SharedBudgetMonitor(
                    SharedBudgetMonitor.Scope.CLASS, 0, 500, BudgetExhaustedBehavior.FAIL);
            classBudget.addTokens(750);

            String message = formatter.buildExhaustionMessage(
                    TerminationReason.CLASS_TOKEN_BUDGET_EXHAUSTED,
                    unlimitedMethodBudget(), classBudget, null);

            assertThat(message).contains("Class token budget exhausted");
            assertThat(message).contains("750 tokens");
            assertThat(message).contains("500 budget");
        }

        @Test
        @DisplayName("formats method time exhaustion")
        void formatsMethodTime() throws InterruptedException {
            CostBudgetMonitor methodBudget = new CostBudgetMonitor(
                    200, 0, 0, TokenMode.NONE, BudgetExhaustedBehavior.FAIL);
            Thread.sleep(10);

            String message = formatter.buildExhaustionMessage(
                    TerminationReason.METHOD_TIME_BUDGET_EXHAUSTED,
                    methodBudget, null, null);

            assertThat(message).contains("Method time budget exhausted");
            assertThat(message).contains("200ms budget");
        }

        @Test
        @DisplayName("formats method token exhaustion")
        void formatsMethodToken() {
            CostBudgetMonitor methodBudget = new CostBudgetMonitor(
                    0, 300, 0, TokenMode.DYNAMIC, BudgetExhaustedBehavior.FAIL);
            methodBudget.recordDynamicTokens(400);

            String message = formatter.buildExhaustionMessage(
                    TerminationReason.METHOD_TOKEN_BUDGET_EXHAUSTED,
                    methodBudget, null, null);

            assertThat(message).contains("Method token budget exhausted");
            assertThat(message).contains("400 tokens");
            assertThat(message).contains("300 budget");
        }

        @Test
        @DisplayName("returns description for unrecognised reason")
        void returnsDescriptionForUnrecognisedReason() {
            String message = formatter.buildExhaustionMessage(
                    TerminationReason.COMPLETED,
                    unlimitedMethodBudget(), null, null);

            assertThat(message).isEqualTo(TerminationReason.COMPLETED.getDescription());
        }
    }

    @Nested
    @DisplayName("buildExhaustionFailureMessage()")
    class BuildExhaustionFailureMessage {

        @Test
        @DisplayName("includes failure reason and sample statistics")
        void includesFailureReasonAndStats() {
            String message = formatter.buildExhaustionFailureMessage(
                    TerminationReason.METHOD_TIME_BUDGET_EXHAUSTED,
                    "Method time budget exhausted: 600ms >= 500ms",
                    5, 10, 0.8, 4, 0.9, 600);

            assertThat(message).contains("PUnit FAILED:");
            assertThat(message).contains("Samples executed: 5 of 10");
            assertThat(message).contains("4/5");
            assertThat(message).contains("Required pass rate:");
            assertThat(message).contains("Elapsed: 600ms");
        }

        @Test
        @DisplayName("handles null termination reason")
        void handlesNullReason() {
            String message = formatter.buildExhaustionFailureMessage(
                    null, null, 3, 10, 0.5, 2, 0.9, 500);

            assertThat(message).contains("PUnit FAILED: Budget exhausted");
        }

        @Test
        @DisplayName("omits termination details when null or empty")
        void omitsEmptyDetails() {
            String withNull = formatter.buildExhaustionFailureMessage(
                    TerminationReason.COMPLETED, null, 5, 10, 0.8, 4, 0.9, 100);
            String withEmpty = formatter.buildExhaustionFailureMessage(
                    TerminationReason.COMPLETED, "", 5, 10, 0.8, 4, 0.9, 100);

            // Neither should have a stray detail line between "FAILED" and "Samples"
            assertThat(withNull).doesNotContain("  \n  Samples");
            assertThat(withEmpty).doesNotContain("  \n  Samples");
        }
    }
}
