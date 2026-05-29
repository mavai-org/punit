package org.mavai.punit.internal.engine.budget;

import java.util.Optional;
import org.mavai.punit.api.BudgetExhaustedBehavior;
import org.mavai.punit.verdict.TerminationReason;
import org.mavai.punit.verdict.TokenMode;

/**
 * Monitors time and token budgets for a probabilistic test method.
 * 
 * <p>This class tracks:
 * <ul>
 *   <li>Wall-clock time elapsed since test start</li>
 *   <li>Token consumption (static or dynamic mode)</li>
 *   <li>Budget exhaustion conditions</li>
 * </ul>
 * 
 * <p>Budget checks can be performed before or after sample execution depending
 * on the charging mode:
 * <ul>
 *   <li><strong>Static mode</strong>: Pre-sample check if next sample would exceed budget</li>
 *   <li><strong>Dynamic mode</strong>: Post-sample check after tokens are recorded</li>
 * </ul>
 */
public class CostBudgetMonitor {

    private final long timeBudgetMs;
    private final long tokenBudget;
    private final int staticTokenCharge;
    private final TokenMode tokenMode;
    private final BudgetExhaustedBehavior onBudgetExhausted;

    private final long startTimeMs;
    private long tokensConsumed = 0;

    /**
     * Creates a new budget monitor.
     *
     * @param timeBudgetMs wall-clock time budget in ms (0 = unlimited)
     * @param tokenBudget maximum tokens allowed (0 = unlimited)
     * @param staticTokenCharge tokens charged per sample in static mode
     * @param tokenMode the token charging mode
     * @param onBudgetExhausted behavior when budget exhausted
     */
    public CostBudgetMonitor(long timeBudgetMs, long tokenBudget, int staticTokenCharge,
                             TokenMode tokenMode, BudgetExhaustedBehavior onBudgetExhausted) {
        this.timeBudgetMs = timeBudgetMs;
        this.tokenBudget = tokenBudget;
        this.staticTokenCharge = staticTokenCharge;
        this.tokenMode = tokenMode;
        this.onBudgetExhausted = onBudgetExhausted;
        this.startTimeMs = System.currentTimeMillis();
    }

    /**
     * Checks if the time budget is exhausted (pre-sample check).
     *
     * @return termination reason if time budget exceeded, empty otherwise
     */
    public Optional<TerminationReason> checkTimeBudget() {
        if (timeBudgetMs > 0 && getElapsedMs() >= timeBudgetMs) {
            return Optional.of(TerminationReason.METHOD_TIME_BUDGET_EXHAUSTED);
        }
        return Optional.empty();
    }

    /**
     * Checks if executing the next sample would exceed the token budget (static mode only).
     * This is a pre-sample check.
     *
     * @return termination reason if next sample would exceed budget, empty otherwise
     */
    public Optional<TerminationReason> checkTokenBudgetBeforeSample() {
        if (tokenMode != TokenMode.STATIC || tokenBudget <= 0) {
            return Optional.empty();
        }
        
        // Would the next sample exceed the budget?
        if (tokensConsumed + staticTokenCharge > tokenBudget) {
            return Optional.of(TerminationReason.METHOD_TOKEN_BUDGET_EXHAUSTED);
        }
        return Optional.empty();
    }

    /**
     * Records static token consumption after a sample completes.
     * Only applicable in static mode.
     */
    public void recordStaticTokenCharge() {
        if (tokenMode == TokenMode.STATIC) {
            tokensConsumed += staticTokenCharge;
        }
    }

    /**
     * Records dynamic token consumption after a sample completes.
     * Only applicable in dynamic mode.
     *
     * @param tokens the tokens consumed by this sample
     */
    public void recordDynamicTokens(long tokens) {
        if (tokenMode == TokenMode.DYNAMIC) {
            tokensConsumed += tokens;
        }
    }

    /**
     * Checks if the token budget is exhausted after recording tokens (dynamic mode).
     * This is a post-sample check.
     *
     * @return termination reason if budget exceeded, empty otherwise
     */
    public Optional<TerminationReason> checkTokenBudgetAfterSample() {
        if (tokenMode != TokenMode.DYNAMIC || tokenBudget <= 0) {
            return Optional.empty();
        }
        
        if (tokensConsumed > tokenBudget) {
            return Optional.of(TerminationReason.METHOD_TOKEN_BUDGET_EXHAUSTED);
        }
        return Optional.empty();
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
        return tokensConsumed;
    }

    /**
     * @return remaining token budget, or Long.MAX_VALUE if unlimited
     */
    public long getRemainingTokenBudget() {
        if (tokenBudget <= 0) {
            return Long.MAX_VALUE;
        }
        return Math.max(0, tokenBudget - tokensConsumed);
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
     * @return the token charging mode
     */
    public TokenMode getTokenMode() {
        return tokenMode;
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
     * @return the static token charge per sample
     */
    public int getStaticTokenCharge() {
        return staticTokenCharge;
    }
}

