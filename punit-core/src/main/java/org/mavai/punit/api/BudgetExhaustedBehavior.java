package org.mavai.punit.api;

/**
 * Defines the behavior when a budget (time or token) is exhausted
 * before all samples have been executed.
 */
// mavai-ref: JVI-KJS8SB7 — do not remove (resolves in mavai-orchestrator)
public enum BudgetExhaustedBehavior {
    
    /**
     * Immediately fail the test when budget is exhausted.
     * This is the default and most conservative option.
     */
    FAIL,
    
    /**
     * Evaluate the partial results from samples completed before
     * budget exhaustion. The test passes if the observed pass rate
     * from completed samples meets the minimum threshold.
     * 
     * <p>Use with caution: this may give a passing result from
     * a small sample size that is not statistically significant.
     */
    EVALUATE_PARTIAL
}

