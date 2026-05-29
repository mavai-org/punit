package org.mavai.punit.internal.engine.budget;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Optional;
import org.mavai.punit.api.BudgetExhaustedBehavior;
import org.mavai.punit.verdict.TerminationReason;
import org.mavai.punit.verdict.TokenMode;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CostBudgetMonitor}.
 */
class CostBudgetMonitorTest {

    @Test
    void timeBudgetExhaustionDetected() throws InterruptedException {
        CostBudgetMonitor monitor = new CostBudgetMonitor(
                1, // 1ms time budget
                0,
                0,
                TokenMode.NONE,
                BudgetExhaustedBehavior.FAIL
        );
        
        Thread.sleep(10); // Wait for budget to exhaust
        
        Optional<TerminationReason> reason = monitor.checkTimeBudget();
        assertThat(reason).isPresent();
        assertThat(reason.get()).isEqualTo(TerminationReason.METHOD_TIME_BUDGET_EXHAUSTED);
    }

    @Test
    void staticTokenBudgetPreCheckWorks() {
        CostBudgetMonitor monitor = new CostBudgetMonitor(
                0, // No time budget
                100, // 100 token budget
                50, // 50 tokens per sample
                TokenMode.STATIC,
                BudgetExhaustedBehavior.FAIL
        );
        
        // First sample: 0 consumed, 50 would be added = 50 <= 100, OK
        assertThat(monitor.checkTokenBudgetBeforeSample()).isEmpty();
        monitor.recordStaticTokenCharge();
        assertThat(monitor.getTokensConsumed()).isEqualTo(50);
        
        // Second sample: 50 consumed, 50 would be added = 100 <= 100, OK
        assertThat(monitor.checkTokenBudgetBeforeSample()).isEmpty();
        monitor.recordStaticTokenCharge();
        assertThat(monitor.getTokensConsumed()).isEqualTo(100);
        
        // Third sample: 100 consumed, 50 would be added = 150 > 100, FAIL
        Optional<TerminationReason> reason = monitor.checkTokenBudgetBeforeSample();
        assertThat(reason).isPresent();
        assertThat(reason.get()).isEqualTo(TerminationReason.METHOD_TOKEN_BUDGET_EXHAUSTED);
    }

    @Test
    void dynamicTokenBudgetPostCheckWorks() {
        CostBudgetMonitor monitor = new CostBudgetMonitor(
                0, // No time budget
                100, // 100 token budget
                0,
                TokenMode.DYNAMIC,
                BudgetExhaustedBehavior.FAIL
        );
        
        // Record 80 tokens
        monitor.recordDynamicTokens(80);
        assertThat(monitor.checkTokenBudgetAfterSample()).isEmpty();
        
        // Record 30 more tokens (total 110, exceeds 100)
        monitor.recordDynamicTokens(30);
        Optional<TerminationReason> reason = monitor.checkTokenBudgetAfterSample();
        assertThat(reason).isPresent();
        assertThat(reason.get()).isEqualTo(TerminationReason.METHOD_TOKEN_BUDGET_EXHAUSTED);
    }

    @Test
    void noneTokenModeDoesNotCheckBudget() {
        CostBudgetMonitor monitor = new CostBudgetMonitor(
                0,
                100, // Budget set but mode is NONE
                50,
                TokenMode.NONE,
                BudgetExhaustedBehavior.FAIL
        );
        
        // Pre-check should not trigger
        assertThat(monitor.checkTokenBudgetBeforeSample()).isEmpty();
        
        // Post-check should not trigger
        assertThat(monitor.checkTokenBudgetAfterSample()).isEmpty();
    }

    @Test
    void unlimitedBudgetsNeverExhaust() {
        CostBudgetMonitor monitor = new CostBudgetMonitor(
                0, // Unlimited time
                0, // Unlimited tokens
                100,
                TokenMode.STATIC,
                BudgetExhaustedBehavior.FAIL
        );
        
        assertThat(monitor.hasTimeBudget()).isFalse();
        assertThat(monitor.hasTokenBudget()).isFalse();
        assertThat(monitor.checkTimeBudget()).isEmpty();
        assertThat(monitor.checkTokenBudgetBeforeSample()).isEmpty();
        assertThat(monitor.getRemainingTokenBudget()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void gettersReturnCorrectValues() {
        CostBudgetMonitor monitor = new CostBudgetMonitor(
                5000,
                1000,
                50,
                TokenMode.STATIC,
                BudgetExhaustedBehavior.EVALUATE_PARTIAL
        );
        
        assertThat(monitor.getTimeBudgetMs()).isEqualTo(5000);
        assertThat(monitor.getTokenBudget()).isEqualTo(1000);
        assertThat(monitor.getStaticTokenCharge()).isEqualTo(50);
        assertThat(monitor.getTokenMode()).isEqualTo(TokenMode.STATIC);
        assertThat(monitor.getOnBudgetExhausted()).isEqualTo(BudgetExhaustedBehavior.EVALUATE_PARTIAL);
        assertThat(monitor.hasTimeBudget()).isTrue();
        assertThat(monitor.hasTokenBudget()).isTrue();
    }
}

