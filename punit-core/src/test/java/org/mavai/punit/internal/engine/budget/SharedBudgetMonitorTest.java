package org.mavai.punit.internal.engine.budget;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Optional;
import org.mavai.punit.api.BudgetExhaustedBehavior;
import org.mavai.punit.verdict.TerminationReason;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SharedBudgetMonitor}.
 */
class SharedBudgetMonitorTest {

    @Test
    void classScopeReturnsClassTerminationReasons() {
        SharedBudgetMonitor monitor = new SharedBudgetMonitor(
                SharedBudgetMonitor.Scope.CLASS,
                1, // 1ms time budget - will be exhausted immediately
                100,
                BudgetExhaustedBehavior.FAIL
        );
        
        // Wait for time budget to exhaust
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        Optional<TerminationReason> reason = monitor.checkTimeBudget();
        assertThat(reason).isPresent();
        assertThat(reason.get()).isEqualTo(TerminationReason.CLASS_TIME_BUDGET_EXHAUSTED);
    }

    @Test
    void suiteScopeReturnsSuiteTerminationReasons() {
        SharedBudgetMonitor monitor = new SharedBudgetMonitor(
                SharedBudgetMonitor.Scope.SUITE,
                1, // 1ms time budget - will be exhausted immediately
                100,
                BudgetExhaustedBehavior.FAIL
        );
        
        // Wait for time budget to exhaust
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        Optional<TerminationReason> reason = monitor.checkTimeBudget();
        assertThat(reason).isPresent();
        assertThat(reason.get()).isEqualTo(TerminationReason.SUITE_TIME_BUDGET_EXHAUSTED);
    }

    @Test
    void tokenBudgetExhaustionReturnsCorrectReason() {
        SharedBudgetMonitor monitor = new SharedBudgetMonitor(
                SharedBudgetMonitor.Scope.CLASS,
                0, // No time budget
                100, // 100 token budget
                BudgetExhaustedBehavior.FAIL
        );
        
        // Add tokens to exceed budget
        monitor.addTokens(150);
        
        Optional<TerminationReason> reason = monitor.checkTokenBudget();
        assertThat(reason).isPresent();
        assertThat(reason.get()).isEqualTo(TerminationReason.CLASS_TOKEN_BUDGET_EXHAUSTED);
    }

    @Test
    void addTokensIsThreadSafe() throws InterruptedException {
        SharedBudgetMonitor monitor = new SharedBudgetMonitor(
                SharedBudgetMonitor.Scope.CLASS,
                0,
                Long.MAX_VALUE, // No budget limit
                BudgetExhaustedBehavior.FAIL
        );
        
        int numThreads = 10;
        int tokensPerThread = 1000;
        Thread[] threads = new Thread[numThreads];
        
        for (int i = 0; i < numThreads; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < tokensPerThread; j++) {
                    monitor.addTokens(1);
                }
            });
        }
        
        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }
        
        assertThat(monitor.getTokensConsumed()).isEqualTo(numThreads * tokensPerThread);
    }

    @Test
    void unlimitedBudgetNeverExhausts() {
        SharedBudgetMonitor monitor = new SharedBudgetMonitor(
                SharedBudgetMonitor.Scope.SUITE,
                0, // Unlimited time
                0, // Unlimited tokens
                BudgetExhaustedBehavior.FAIL
        );
        
        assertThat(monitor.hasBudget()).isFalse();
        assertThat(monitor.checkTimeBudget()).isEmpty();
        assertThat(monitor.checkTokenBudget()).isEmpty();
        assertThat(monitor.getRemainingTokenBudget()).isEqualTo(Long.MAX_VALUE);
        assertThat(monitor.getRemainingTimeMs()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void remainingBudgetCalculatedCorrectly() {
        SharedBudgetMonitor monitor = new SharedBudgetMonitor(
                SharedBudgetMonitor.Scope.CLASS,
                10000, // 10 second time budget
                1000,  // 1000 token budget
                BudgetExhaustedBehavior.FAIL
        );
        
        assertThat(monitor.getRemainingTokenBudget()).isEqualTo(1000);
        
        monitor.addTokens(300);
        assertThat(monitor.getRemainingTokenBudget()).isEqualTo(700);
        
        monitor.addTokens(700);
        assertThat(monitor.getRemainingTokenBudget()).isEqualTo(0);
    }
}

