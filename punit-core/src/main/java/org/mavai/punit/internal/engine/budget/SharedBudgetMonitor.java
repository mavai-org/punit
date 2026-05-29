package org.mavai.punit.internal.engine.budget;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.mavai.punit.api.BudgetExhaustedBehavior;
import org.mavai.punit.verdict.TerminationReason;

/**
 * Thread-safe budget monitor for shared budgets at class or suite level.
 * 
 * <p>Unlike {@link CostBudgetMonitor} which is per-method and not thread-safe,
 * this class uses atomic operations to safely track consumption from multiple
 * concurrent test methods.
 * 
 * <h2>Usage</h2>
 * <ul>
 *   <li>Class-level: Created by {@code ProbabilisticTestBudgetExtension} in BeforeAll</li>
 *   <li>Suite-level: Managed by {@link SuiteBudgetManager} singleton</li>
 * </ul>
 */
public class SharedBudgetMonitor {

    /**
     * The scope of this shared budget.
     */
    public enum Scope {
        CLASS,
        SUITE
    }

    private final Scope scope;
    private final long timeBudgetMs;
    private final long tokenBudget;
    private final BudgetExhaustedBehavior onBudgetExhausted;
    private final long startTimeMs;
    private final AtomicLong tokensConsumed = new AtomicLong(0);

    /**
     * Creates a new shared budget monitor.
     *
     * @param scope the scope of this budget (CLASS or SUITE)
     * @param timeBudgetMs wall-clock time budget in ms (0 = unlimited)
     * @param tokenBudget maximum tokens allowed (0 = unlimited)
     * @param onBudgetExhausted behavior when budget exhausted
     */
    public SharedBudgetMonitor(Scope scope, long timeBudgetMs, long tokenBudget,
                                BudgetExhaustedBehavior onBudgetExhausted) {
        this.scope = scope;
        this.timeBudgetMs = timeBudgetMs;
        this.tokenBudget = tokenBudget;
        this.onBudgetExhausted = onBudgetExhausted;
        this.startTimeMs = System.currentTimeMillis();
    }

    /**
     * Checks if the time budget is exhausted.
     *
     * @return termination reason if exhausted, empty otherwise
     */
    public Optional<TerminationReason> checkTimeBudget() {
        if (timeBudgetMs > 0 && getElapsedMs() >= timeBudgetMs) {
            return Optional.of(getTimeBudgetExhaustedReason());
        }
        return Optional.empty();
    }

    /**
     * Checks if the token budget is exhausted.
     *
     * @return termination reason if exhausted, empty otherwise
     */
    public Optional<TerminationReason> checkTokenBudget() {
        if (tokenBudget > 0 && tokensConsumed.get() > tokenBudget) {
            return Optional.of(getTokenBudgetExhaustedReason());
        }
        return Optional.empty();
    }

    /**
     * Atomically adds tokens to the consumed total.
     *
     * @param tokens the tokens to add
     * @return the new total tokens consumed
     */
    public long addTokens(long tokens) {
        return tokensConsumed.addAndGet(tokens);
    }

    /**
     * @return elapsed time in milliseconds since monitor creation
     */
    public long getElapsedMs() {
        return System.currentTimeMillis() - startTimeMs;
    }

    /**
     * @return total tokens consumed so far
     */
    public long getTokensConsumed() {
        return tokensConsumed.get();
    }

    /**
     * @return remaining token budget, or Long.MAX_VALUE if unlimited
     */
    public long getRemainingTokenBudget() {
        if (tokenBudget <= 0) {
            return Long.MAX_VALUE;
        }
        return Math.max(0, tokenBudget - tokensConsumed.get());
    }

    /**
     * @return remaining time budget in ms, or Long.MAX_VALUE if unlimited
     */
    public long getRemainingTimeMs() {
        if (timeBudgetMs <= 0) {
            return Long.MAX_VALUE;
        }
        return Math.max(0, timeBudgetMs - getElapsedMs());
    }

    /**
     * @return the configured time budget in ms (0 = unlimited)
     */
    public long getTimeBudgetMs() {
        return timeBudgetMs;
    }

    /**
     * @return the configured token budget (0 = unlimited)
     */
    public long getTokenBudget() {
        return tokenBudget;
    }

    /**
     * @return the scope of this budget
     */
    public Scope getScope() {
        return scope;
    }

    /**
     * @return the budget exhaustion behavior
     */
    public BudgetExhaustedBehavior getOnBudgetExhausted() {
        return onBudgetExhausted;
    }

    /**
     * @return true if time budget is enabled (> 0)
     */
    public boolean hasTimeBudget() {
        return timeBudgetMs > 0;
    }

    /**
     * @return true if token budget is enabled (> 0)
     */
    public boolean hasTokenBudget() {
        return tokenBudget > 0;
    }

    /**
     * @return true if any budget is configured
     */
    public boolean hasBudget() {
        return hasTimeBudget() || hasTokenBudget();
    }

    private TerminationReason getTimeBudgetExhaustedReason() {
        return scope == Scope.CLASS
                ? TerminationReason.CLASS_TIME_BUDGET_EXHAUSTED
                : TerminationReason.SUITE_TIME_BUDGET_EXHAUSTED;
    }

    private TerminationReason getTokenBudgetExhaustedReason() {
        return scope == Scope.CLASS
                ? TerminationReason.CLASS_TOKEN_BUDGET_EXHAUSTED
                : TerminationReason.SUITE_TOKEN_BUDGET_EXHAUSTED;
    }
}

