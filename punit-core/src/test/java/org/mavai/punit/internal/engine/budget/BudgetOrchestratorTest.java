package org.mavai.punit.internal.engine.budget;

import static org.assertj.core.api.Assertions.assertThat;
import org.mavai.punit.api.BudgetExhaustedBehavior;
import org.mavai.punit.internal.engine.budget.BudgetOrchestrator.BudgetCheckResult;
import org.mavai.punit.verdict.TerminationReason;
import org.mavai.punit.verdict.TokenMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link BudgetOrchestrator}.
 */
class BudgetOrchestratorTest {

    private BudgetOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new BudgetOrchestrator();
    }

    // Helper to create a method budget with no limits
    private CostBudgetMonitor unlimitedMethodBudget() {
        return new CostBudgetMonitor(0, 0, 0, TokenMode.NONE, 
                BudgetExhaustedBehavior.EVALUATE_PARTIAL);
    }

    // Helper to create a method budget with time limit (already expired)
    private CostBudgetMonitor expiredTimeBudget() throws InterruptedException {
        CostBudgetMonitor monitor = new CostBudgetMonitor(1, 0, 0, 
                TokenMode.NONE, BudgetExhaustedBehavior.FAIL);
        Thread.sleep(10); // Let it expire
        return monitor;
    }

    // Helper to create a method budget with token limit
    private CostBudgetMonitor tokenLimitedMethodBudget(long budget, int staticCharge) {
        return new CostBudgetMonitor(0, budget, staticCharge,
                TokenMode.STATIC, BudgetExhaustedBehavior.FAIL);
    }

    // Helper to create a shared budget with no limits
    private SharedBudgetMonitor unlimitedSharedBudget(SharedBudgetMonitor.Scope scope) {
        return new SharedBudgetMonitor(scope, 0, 0, BudgetExhaustedBehavior.EVALUATE_PARTIAL);
    }

    // Helper to create a shared budget that's expired
    private SharedBudgetMonitor expiredTimeSharedBudget(SharedBudgetMonitor.Scope scope) 
            throws InterruptedException {
        SharedBudgetMonitor monitor = new SharedBudgetMonitor(scope, 1, 0, BudgetExhaustedBehavior.FAIL);
        Thread.sleep(10); // Let it expire
        return monitor;
    }

    // Helper to create a shared budget with token limit
    private SharedBudgetMonitor tokenLimitedSharedBudget(SharedBudgetMonitor.Scope scope, long budget) {
        return new SharedBudgetMonitor(scope, 0, budget, BudgetExhaustedBehavior.FAIL);
    }

    @Nested
    @DisplayName("checkBeforeSample")
    class CheckBeforeSample {

        @Test
        @DisplayName("returns ok when all budgets are within limits")
        void returnsOkWhenAllBudgetsWithinLimits() {
            CostBudgetMonitor methodBudget = unlimitedMethodBudget();

            BudgetCheckResult result = orchestrator.checkBeforeSample(null, null, methodBudget);

            assertThat(result.shouldTerminate()).isFalse();
        }

        @Test
        @DisplayName("suite time budget triggers termination first")
        void suiteTimeBudgetTriggersFirst() throws InterruptedException {
            SharedBudgetMonitor suiteBudget = expiredTimeSharedBudget(SharedBudgetMonitor.Scope.SUITE);
            SharedBudgetMonitor classBudget = unlimitedSharedBudget(SharedBudgetMonitor.Scope.CLASS);
            CostBudgetMonitor methodBudget = unlimitedMethodBudget();

            BudgetCheckResult result = orchestrator.checkBeforeSample(suiteBudget, classBudget, methodBudget);

            assertThat(result.shouldTerminate()).isTrue();
            assertThat(result.terminationReason()).contains(TerminationReason.SUITE_TIME_BUDGET_EXHAUSTED);
        }

        @Test
        @DisplayName("suite token budget triggers termination")
        void suiteTokenBudgetTriggers() {
            SharedBudgetMonitor suiteBudget = tokenLimitedSharedBudget(SharedBudgetMonitor.Scope.SUITE, 10);
            suiteBudget.addTokens(20); // Exceed budget
            CostBudgetMonitor methodBudget = unlimitedMethodBudget();

            BudgetCheckResult result = orchestrator.checkBeforeSample(suiteBudget, null, methodBudget);

            assertThat(result.shouldTerminate()).isTrue();
            assertThat(result.terminationReason()).contains(TerminationReason.SUITE_TOKEN_BUDGET_EXHAUSTED);
        }

        @Test
        @DisplayName("class budget checked after suite passes")
        void classBudgetCheckedAfterSuite() throws InterruptedException {
            SharedBudgetMonitor suiteBudget = unlimitedSharedBudget(SharedBudgetMonitor.Scope.SUITE);
            SharedBudgetMonitor classBudget = expiredTimeSharedBudget(SharedBudgetMonitor.Scope.CLASS);
            CostBudgetMonitor methodBudget = unlimitedMethodBudget();

            BudgetCheckResult result = orchestrator.checkBeforeSample(suiteBudget, classBudget, methodBudget);

            assertThat(result.shouldTerminate()).isTrue();
            assertThat(result.terminationReason()).contains(TerminationReason.CLASS_TIME_BUDGET_EXHAUSTED);
        }

        @Test
        @DisplayName("method budget checked when suite and class pass")
        void methodBudgetCheckedLast() throws InterruptedException {
            CostBudgetMonitor methodBudget = expiredTimeBudget();

            BudgetCheckResult result = orchestrator.checkBeforeSample(null, null, methodBudget);

            assertThat(result.shouldTerminate()).isTrue();
            assertThat(result.terminationReason()).contains(TerminationReason.METHOD_TIME_BUDGET_EXHAUSTED);
        }

        @Test
        @DisplayName("method token budget uses pre-sample check")
        void methodTokenBudgetUsesPreSampleCheck() {
            // Create a budget that will be exceeded on next sample
            CostBudgetMonitor methodBudget = tokenLimitedMethodBudget(50, 30);
            methodBudget.recordStaticTokenCharge(); // 30 consumed
            methodBudget.recordStaticTokenCharge(); // 60 consumed - exceeds 50

            BudgetCheckResult result = orchestrator.checkBeforeSample(null, null, methodBudget);

            assertThat(result.shouldTerminate()).isTrue();
            assertThat(result.terminationReason()).contains(TerminationReason.METHOD_TOKEN_BUDGET_EXHAUSTED);
        }
    }

    @Nested
    @DisplayName("checkAfterSample")
    class CheckAfterSample {

        @Test
        @DisplayName("returns ok when all budgets within limits")
        void returnsOkWhenAllBudgetsWithinLimits() {
            CostBudgetMonitor methodBudget = unlimitedMethodBudget();

            BudgetCheckResult result = orchestrator.checkAfterSample(null, null, methodBudget);

            assertThat(result.shouldTerminate()).isFalse();
        }

        @Test
        @DisplayName("suite token budget checked first")
        void suiteTokenBudgetCheckedFirst() {
            SharedBudgetMonitor suiteBudget = tokenLimitedSharedBudget(SharedBudgetMonitor.Scope.SUITE, 10);
            suiteBudget.addTokens(20); // Exceed budget
            CostBudgetMonitor methodBudget = unlimitedMethodBudget();

            BudgetCheckResult result = orchestrator.checkAfterSample(suiteBudget, null, methodBudget);

            assertThat(result.shouldTerminate()).isTrue();
            assertThat(result.terminationReason()).contains(TerminationReason.SUITE_TOKEN_BUDGET_EXHAUSTED);
        }

        @Test
        @DisplayName("class token budget checked after suite")
        void classTokenBudgetCheckedAfterSuite() {
            SharedBudgetMonitor suiteBudget = unlimitedSharedBudget(SharedBudgetMonitor.Scope.SUITE);
            SharedBudgetMonitor classBudget = tokenLimitedSharedBudget(SharedBudgetMonitor.Scope.CLASS, 5);
            classBudget.addTokens(10); // Exceed budget
            CostBudgetMonitor methodBudget = unlimitedMethodBudget();

            BudgetCheckResult result = orchestrator.checkAfterSample(suiteBudget, classBudget, methodBudget);

            assertThat(result.shouldTerminate()).isTrue();
            assertThat(result.terminationReason()).contains(TerminationReason.CLASS_TOKEN_BUDGET_EXHAUSTED);
        }
    }

    @Nested
    @DisplayName("recordAndPropagateTokens")
    class RecordAndPropagateTokens {

        @Test
        @DisplayName("records dynamic tokens from recorder")
        void recordsDynamicTokensFromRecorder() {
            DefaultTokenChargeRecorder tokenRecorder = new DefaultTokenChargeRecorder(1000);
            tokenRecorder.recordTokens(100);
            CostBudgetMonitor methodBudget = new CostBudgetMonitor(0, 1000, 0,
                    TokenMode.DYNAMIC, BudgetExhaustedBehavior.FAIL);

            long tokens = orchestrator.recordAndPropagateTokens(
                    tokenRecorder, methodBudget, TokenMode.DYNAMIC,
                    0, null, null);

            assertThat(tokens).isEqualTo(100L);
            assertThat(methodBudget.getTokensConsumed()).isEqualTo(100L);
        }

        @Test
        @DisplayName("records static token charge when no recorder")
        void recordsStaticTokenChargeWhenNoRecorder() {
            CostBudgetMonitor methodBudget = new CostBudgetMonitor(0, 1000, 50,
                    TokenMode.STATIC, BudgetExhaustedBehavior.FAIL);

            long tokens = orchestrator.recordAndPropagateTokens(
                    null, methodBudget, TokenMode.STATIC,
                    50, null, null);

            assertThat(tokens).isEqualTo(50L);
            assertThat(methodBudget.getTokensConsumed()).isEqualTo(50L);
        }

        @Test
        @DisplayName("propagates tokens to class budget")
        void propagatesToClassBudget() {
            DefaultTokenChargeRecorder tokenRecorder = new DefaultTokenChargeRecorder(1000);
            tokenRecorder.recordTokens(75);
            CostBudgetMonitor methodBudget = new CostBudgetMonitor(0, 1000, 0,
                    TokenMode.DYNAMIC, BudgetExhaustedBehavior.FAIL);
            SharedBudgetMonitor classBudget = unlimitedSharedBudget(SharedBudgetMonitor.Scope.CLASS);

            orchestrator.recordAndPropagateTokens(
                    tokenRecorder, methodBudget, TokenMode.DYNAMIC,
                    0, classBudget, null);

            assertThat(classBudget.getTokensConsumed()).isEqualTo(75L);
        }

        @Test
        @DisplayName("propagates tokens to suite budget")
        void propagatesToSuiteBudget() {
            DefaultTokenChargeRecorder tokenRecorder = new DefaultTokenChargeRecorder(1000);
            tokenRecorder.recordTokens(200);
            CostBudgetMonitor methodBudget = new CostBudgetMonitor(0, 1000, 0,
                    TokenMode.DYNAMIC, BudgetExhaustedBehavior.FAIL);
            SharedBudgetMonitor suiteBudget = unlimitedSharedBudget(SharedBudgetMonitor.Scope.SUITE);

            orchestrator.recordAndPropagateTokens(
                    tokenRecorder, methodBudget, TokenMode.DYNAMIC,
                    0, null, suiteBudget);

            assertThat(suiteBudget.getTokensConsumed()).isEqualTo(200L);
        }

        @Test
        @DisplayName("does not propagate zero tokens")
        void doesNotPropagateZeroTokens() {
            CostBudgetMonitor methodBudget = unlimitedMethodBudget();
            SharedBudgetMonitor classBudget = unlimitedSharedBudget(SharedBudgetMonitor.Scope.CLASS);

            long tokens = orchestrator.recordAndPropagateTokens(
                    null, methodBudget, TokenMode.NONE,
                    0, classBudget, null);

            assertThat(tokens).isEqualTo(0L);
            assertThat(classBudget.getTokensConsumed()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("determineBehavior")
    class DetermineBehavior {

        @Test
        @DisplayName("returns suite behavior for suite exhaustion")
        void returnsSuiteBehaviorForSuiteExhaustion() {
            SharedBudgetMonitor suiteBudget = new SharedBudgetMonitor(
                    SharedBudgetMonitor.Scope.SUITE, 0, 0, BudgetExhaustedBehavior.FAIL);

            BudgetExhaustedBehavior behavior = orchestrator.determineBehavior(
                    TerminationReason.SUITE_TIME_BUDGET_EXHAUSTED,
                    suiteBudget, null, BudgetExhaustedBehavior.EVALUATE_PARTIAL);

            assertThat(behavior).isEqualTo(BudgetExhaustedBehavior.FAIL);
        }

        @Test
        @DisplayName("returns class behavior for class exhaustion")
        void returnsClassBehaviorForClassExhaustion() {
            SharedBudgetMonitor classBudget = new SharedBudgetMonitor(
                    SharedBudgetMonitor.Scope.CLASS, 0, 0, BudgetExhaustedBehavior.FAIL);

            BudgetExhaustedBehavior behavior = orchestrator.determineBehavior(
                    TerminationReason.CLASS_TOKEN_BUDGET_EXHAUSTED,
                    null, classBudget, BudgetExhaustedBehavior.EVALUATE_PARTIAL);

            assertThat(behavior).isEqualTo(BudgetExhaustedBehavior.FAIL);
        }

        @Test
        @DisplayName("returns method behavior for method exhaustion")
        void returnsMethodBehaviorForMethodExhaustion() {
            BudgetExhaustedBehavior behavior = orchestrator.determineBehavior(
                    TerminationReason.METHOD_TIME_BUDGET_EXHAUSTED,
                    null, null, BudgetExhaustedBehavior.EVALUATE_PARTIAL);

            assertThat(behavior).isEqualTo(BudgetExhaustedBehavior.EVALUATE_PARTIAL);
        }

        @Test
        @DisplayName("falls back to method behavior when suite budget is null")
        void fallsBackToMethodBehaviorWhenSuiteBudgetNull() {
            BudgetExhaustedBehavior behavior = orchestrator.determineBehavior(
                    TerminationReason.SUITE_TIME_BUDGET_EXHAUSTED,
                    null, null, BudgetExhaustedBehavior.FAIL);

            assertThat(behavior).isEqualTo(BudgetExhaustedBehavior.FAIL);
        }
    }

    @Nested
    @DisplayName("buildExhaustionMessage")
    class BuildExhaustionMessage {

        @Test
        @DisplayName("formats suite time exhaustion message")
        void formatsSuiteTimeExhaustionMessage() throws InterruptedException {
            SharedBudgetMonitor suiteBudget = new SharedBudgetMonitor(
                    SharedBudgetMonitor.Scope.SUITE, 100, 0, BudgetExhaustedBehavior.FAIL);
            Thread.sleep(10); // Let some time pass
            CostBudgetMonitor methodBudget = unlimitedMethodBudget();

            String message = orchestrator.buildExhaustionMessage(
                    TerminationReason.SUITE_TIME_BUDGET_EXHAUSTED,
                    methodBudget, null, suiteBudget);

            assertThat(message).contains("Suite time budget exhausted");
            assertThat(message).contains("100ms budget");
        }

        @Test
        @DisplayName("formats suite token exhaustion message")
        void formatsSuiteTokenExhaustionMessage() {
            SharedBudgetMonitor suiteBudget = tokenLimitedSharedBudget(SharedBudgetMonitor.Scope.SUITE, 1000);
            suiteBudget.addTokens(1500);
            CostBudgetMonitor methodBudget = unlimitedMethodBudget();

            String message = orchestrator.buildExhaustionMessage(
                    TerminationReason.SUITE_TOKEN_BUDGET_EXHAUSTED,
                    methodBudget, null, suiteBudget);

            assertThat(message).contains("Suite token budget exhausted");
            assertThat(message).contains("1500 tokens");
            assertThat(message).contains("1000 budget");
        }

        @Test
        @DisplayName("formats method time exhaustion message")
        void formatsMethodTimeExhaustionMessage() throws InterruptedException {
            CostBudgetMonitor methodBudget = new CostBudgetMonitor(
                    500, 0, 0, TokenMode.NONE, BudgetExhaustedBehavior.FAIL);
            Thread.sleep(10); // Let some time pass

            String message = orchestrator.buildExhaustionMessage(
                    TerminationReason.METHOD_TIME_BUDGET_EXHAUSTED,
                    methodBudget, null, null);

            assertThat(message).contains("Method time budget exhausted");
            assertThat(message).contains("500ms budget");
        }

        @Test
        @DisplayName("formats method token exhaustion message")
        void formatsMethodTokenExhaustionMessage() {
            CostBudgetMonitor methodBudget = new CostBudgetMonitor(
                    0, 400, 0, TokenMode.DYNAMIC, BudgetExhaustedBehavior.FAIL);
            methodBudget.recordDynamicTokens(500);

            String message = orchestrator.buildExhaustionMessage(
                    TerminationReason.METHOD_TOKEN_BUDGET_EXHAUSTED,
                    methodBudget, null, null);

            assertThat(message).contains("Method token budget exhausted");
            assertThat(message).contains("500 tokens");
            assertThat(message).contains("400 budget");
        }

        @Test
        @DisplayName("returns description for unknown reason")
        void returnsDescriptionForUnknownReason() {
            CostBudgetMonitor methodBudget = unlimitedMethodBudget();

            String message = orchestrator.buildExhaustionMessage(
                    TerminationReason.COMPLETED,
                    methodBudget, null, null);

            assertThat(message).isEqualTo(TerminationReason.COMPLETED.getDescription());
        }
    }

    @Nested
    @DisplayName("buildExhaustionFailureMessage")
    class BuildExhaustionFailureMessage {

        @Test
        @DisplayName("includes failure reason")
        void includesFailureReason() {
            String message = orchestrator.buildExhaustionFailureMessage(
                    TerminationReason.METHOD_TIME_BUDGET_EXHAUSTED,
                    "Method time budget exhausted: 1000ms elapsed >= 500ms budget",
                    5, 10, 0.8, 4, 0.9, 1000);

            assertThat(message).contains("PUnit FAILED:");
            assertThat(message).contains(TerminationReason.METHOD_TIME_BUDGET_EXHAUSTED.getDescription());
        }

        @Test
        @DisplayName("includes termination details")
        void includesTerminationDetails() {
            String message = orchestrator.buildExhaustionFailureMessage(
                    TerminationReason.METHOD_TOKEN_BUDGET_EXHAUSTED,
                    "Method token budget exhausted: 500 tokens >= 400 budget",
                    5, 10, 0.8, 4, 0.9, 1000);

            assertThat(message).contains("Method token budget exhausted: 500 tokens >= 400 budget");
        }

        @Test
        @DisplayName("includes sample statistics")
        void includesSampleStatistics() {
            String message = orchestrator.buildExhaustionFailureMessage(
                    TerminationReason.CLASS_TIME_BUDGET_EXHAUSTED,
                    null,
                    7, 20, 0.571, 4, 0.9, 2500);

            assertThat(message).contains("Samples executed: 7 of 20");
            assertThat(message).contains("Pass rate at termination: 0.5710");
            assertThat(message).contains("4/7");
            assertThat(message).contains("Required pass rate: 0.9000");
            assertThat(message).contains("Elapsed: 2500ms");
        }

        @Test
        @DisplayName("handles null termination reason")
        void handlesNullTerminationReason() {
            String message = orchestrator.buildExhaustionFailureMessage(
                    null, null, 5, 10, 0.6, 3, 0.8, 1000);

            assertThat(message).contains("PUnit FAILED: Budget exhausted");
        }

        @Test
        @DisplayName("handles empty termination details")
        void handlesEmptyTerminationDetails() {
            String message = orchestrator.buildExhaustionFailureMessage(
                    TerminationReason.METHOD_TIME_BUDGET_EXHAUSTED,
                    "",
                    5, 10, 0.6, 3, 0.8, 1000);

            // Empty details should not produce extra blank lines
            assertThat(message).doesNotContain("  \n  \n");
        }
    }

    @Nested
    @DisplayName("BudgetCheckResult")
    class BudgetCheckResultTests {

        @Test
        @DisplayName("ok() creates non-terminating result")
        void okCreatesNonTerminatingResult() {
            BudgetCheckResult result = BudgetCheckResult.ok();

            assertThat(result.shouldTerminate()).isFalse();
            assertThat(result.terminationReason()).isEmpty();
        }

        @Test
        @DisplayName("exhausted() creates terminating result")
        void exhaustedCreatesTerminatingResult() {
            BudgetCheckResult result = BudgetCheckResult.exhausted(
                    TerminationReason.METHOD_TIME_BUDGET_EXHAUSTED);

            assertThat(result.shouldTerminate()).isTrue();
            assertThat(result.terminationReason()).contains(TerminationReason.METHOD_TIME_BUDGET_EXHAUSTED);
        }
    }
}
