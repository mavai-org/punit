package org.mavai.punit.verdict;

/**
 * Reasons why a probabilistic test or optimization terminated.
 *
 * <p>Termination reasons fall into several categories:
 * <ul>
 *   <li>COMPLETED: Normal completion (all samples ran)</li>
 *   <li>IMPOSSIBILITY: Mathematical impossibility to reach pass rate</li>
 *   <li>Budget exhaustion: Time or token budget exceeded at method, class, or suite level</li>
 *   <li>Optimization-specific: Max iterations, no improvement, failures</li>
 * </ul>
 */
public enum TerminationReason {

    /**
     * All planned samples were executed.
     */
    COMPLETED("All samples completed"),

    /**
     * Test terminated early because it became mathematically impossible
     * to reach the required minimum pass rate.
     */
    IMPOSSIBILITY("Cannot reach required pass rate (insufficient remaining samples)"),

    /**
     * Test terminated early because the required pass rate has already been
     * achieved and remaining samples cannot change the outcome.
     */
    SUCCESS_GUARANTEED("Required pass rate already achieved"),

    /**
     * Method-level time budget was exhausted.
     */
    METHOD_TIME_BUDGET_EXHAUSTED("Method time budget exhausted"),

    /**
     * Method-level token budget was exhausted.
     */
    METHOD_TOKEN_BUDGET_EXHAUSTED("Method token budget exhausted"),

    /**
     * Class-level time budget was exhausted.
     */
    CLASS_TIME_BUDGET_EXHAUSTED("Class time budget exhausted"),

    /**
     * Class-level token budget was exhausted.
     */
    CLASS_TOKEN_BUDGET_EXHAUSTED("Class token budget exhausted"),

    /**
     * Suite-level time budget was exhausted.
     */
    SUITE_TIME_BUDGET_EXHAUSTED("Suite time budget exhausted"),

    /**
     * Suite-level token budget was exhausted.
     */
    SUITE_TOKEN_BUDGET_EXHAUSTED("Suite token budget exhausted"),

    // === Optimization-specific termination reasons ===

    /**
     * Maximum iteration count reached during optimization.
     */
    MAX_ITERATIONS("Maximum iterations reached"),

    /**
     * No improvement detected within the configured window during optimization.
     */
    NO_IMPROVEMENT("No improvement in recent iterations"),

    /**
     * Target score threshold was reached during optimization (early success).
     */
    SCORE_THRESHOLD_REACHED("Target score threshold reached"),

    /**
     * Optimization time budget was exhausted.
     */
    OPTIMIZATION_TIME_BUDGET_EXHAUSTED("Optimization time budget exhausted"),

    /**
     * Optimization token budget was exhausted.
     */
    OPTIMIZATION_TOKEN_BUDGET_EXHAUSTED("Optimization token budget exhausted"),

    /**
     * Mutator failed to produce a valid new factor value during optimization.
     */
    MUTATION_FAILURE("Mutation failed"),

    /**
     * Scorer failed to evaluate an iteration during optimization.
     */
    SCORING_FAILURE("Scoring failed");

    private final String description;

    TerminationReason(String description) {
        this.description = description;
    }

    /**
     * Returns a human-readable description of the termination reason.
     *
     * @return description of the termination reason
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns true if this termination reason represents an early termination
     * (before all samples were executed).
     *
     * @return true if early termination
     */
    public boolean isEarlyTermination() {
        return this != COMPLETED;
    }

    /**
     * Returns true if this is a budget-related termination.
     *
     * @return true if budget exhaustion caused termination
     */
    public boolean isBudgetExhaustion() {
        return this == METHOD_TIME_BUDGET_EXHAUSTED ||
               this == METHOD_TOKEN_BUDGET_EXHAUSTED ||
               this == CLASS_TIME_BUDGET_EXHAUSTED ||
               this == CLASS_TOKEN_BUDGET_EXHAUSTED ||
               this == SUITE_TIME_BUDGET_EXHAUSTED ||
               this == SUITE_TOKEN_BUDGET_EXHAUSTED;
    }

    /**
     * Returns true if this is a time budget exhaustion.
     *
     * @return true if time budget exhausted
     */
    public boolean isTimeBudgetExhaustion() {
        return this == METHOD_TIME_BUDGET_EXHAUSTED ||
               this == CLASS_TIME_BUDGET_EXHAUSTED ||
               this == SUITE_TIME_BUDGET_EXHAUSTED ||
               this == OPTIMIZATION_TIME_BUDGET_EXHAUSTED;
    }

    /**
     * Returns true if this is a token budget exhaustion.
     *
     * @return true if token budget exhausted
     */
    public boolean isTokenBudgetExhaustion() {
        return this == METHOD_TOKEN_BUDGET_EXHAUSTED ||
               this == CLASS_TOKEN_BUDGET_EXHAUSTED ||
               this == SUITE_TOKEN_BUDGET_EXHAUSTED ||
               this == OPTIMIZATION_TOKEN_BUDGET_EXHAUSTED;
    }

    /**
     * Returns true if this is an optimization-specific termination reason.
     *
     * @return true if optimization-related
     */
    public boolean isOptimizationTermination() {
        return this == MAX_ITERATIONS ||
               this == NO_IMPROVEMENT ||
               this == SCORE_THRESHOLD_REACHED ||
               this == OPTIMIZATION_TIME_BUDGET_EXHAUSTED ||
               this == OPTIMIZATION_TOKEN_BUDGET_EXHAUSTED ||
               this == MUTATION_FAILURE ||
               this == SCORING_FAILURE;
    }

    /**
     * Returns true if this termination represents a failure condition.
     *
     * @return true if termination due to failure
     */
    public boolean isFailure() {
        return this == MUTATION_FAILURE ||
               this == SCORING_FAILURE;
    }
}
