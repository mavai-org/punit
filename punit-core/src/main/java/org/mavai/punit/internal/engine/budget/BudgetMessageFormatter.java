package org.mavai.punit.internal.engine.budget;

import org.mavai.punit.verdict.TerminationReason;
import org.mavai.punit.internal.reporting.RateFormat;

/**
 * Formats human-readable messages for budget exhaustion scenarios.
 *
 * <p>Extracted from {@link BudgetOrchestrator} to separate message formatting
 * from budget orchestration logic.
 */
public class BudgetMessageFormatter {

    /**
     * Builds a detailed message describing the budget exhaustion.
     *
     * <p>The message includes the scope, budget value, and consumed amount.
     *
     * @param reason the termination reason
     * @param methodBudget the method-level budget monitor
     * @param classBudget the class-level budget monitor (may be null)
     * @param suiteBudget the suite-level budget monitor (may be null)
     * @return the formatted exhaustion message
     */
    public String buildExhaustionMessage(
            TerminationReason reason,
            CostBudgetMonitor methodBudget,
            SharedBudgetMonitor classBudget,
            SharedBudgetMonitor suiteBudget) {

        switch (reason) {
            case SUITE_TIME_BUDGET_EXHAUSTED:
                return suiteBudget != null
                        ? String.format("Suite time budget exhausted: %dms elapsed >= %dms budget",
                                suiteBudget.getElapsedMs(), suiteBudget.getTimeBudgetMs())
                        : reason.getDescription();
            case SUITE_TOKEN_BUDGET_EXHAUSTED:
                return suiteBudget != null
                        ? String.format("Suite token budget exhausted: %d tokens >= %d budget",
                                suiteBudget.getTokensConsumed(), suiteBudget.getTokenBudget())
                        : reason.getDescription();
            case CLASS_TIME_BUDGET_EXHAUSTED:
                return classBudget != null
                        ? String.format("Class time budget exhausted: %dms elapsed >= %dms budget",
                                classBudget.getElapsedMs(), classBudget.getTimeBudgetMs())
                        : reason.getDescription();
            case CLASS_TOKEN_BUDGET_EXHAUSTED:
                return classBudget != null
                        ? String.format("Class token budget exhausted: %d tokens >= %d budget",
                                classBudget.getTokensConsumed(), classBudget.getTokenBudget())
                        : reason.getDescription();
            case METHOD_TIME_BUDGET_EXHAUSTED:
                return String.format("Method time budget exhausted: %dms elapsed >= %dms budget",
                        methodBudget.getElapsedMs(), methodBudget.getTimeBudgetMs());
            case METHOD_TOKEN_BUDGET_EXHAUSTED:
                return String.format("Method token budget exhausted: %d tokens >= %d budget",
                        methodBudget.getTokensConsumed(), methodBudget.getTokenBudget());
            default:
                return reason.getDescription();
        }
    }

    /**
     * Builds a failure message for budget exhaustion scenarios.
     *
     * <p>This is used when the test is forced to fail due to budget limits,
     * not pass rate. The message includes execution statistics at termination.
     *
     * @param terminationReason the termination reason
     * @param terminationDetails the detailed termination message
     * @param samplesExecuted number of samples executed before termination
     * @param plannedSamples total number of planned samples
     * @param observedPassRate the pass rate at termination
     * @param successes number of successful samples
     * @param minPassRate the required pass rate
     * @param elapsedMs elapsed time in milliseconds
     * @return the formatted failure message
     */
    public String buildExhaustionFailureMessage(
            TerminationReason terminationReason,
            String terminationDetails,
            int samplesExecuted,
            int plannedSamples,
            double observedPassRate,
            int successes,
            double minPassRate,
            long elapsedMs) {

        StringBuilder sb = new StringBuilder();

        String failureReason = terminationReason != null
                ? terminationReason.getDescription()
                : "Budget exhausted";
        sb.append(String.format("PUnit FAILED: %s.%n", failureReason));

        if (terminationDetails != null && !terminationDetails.isEmpty()) {
            sb.append(String.format("  %s%n", terminationDetails));
        }

        sb.append(String.format("%n  Samples executed: %d of %d%n",
                samplesExecuted, plannedSamples));
        sb.append(String.format("  Pass rate at termination: %s (%d/%d)%n",
                RateFormat.format(observedPassRate),
                successes,
                samplesExecuted));
        sb.append(String.format("  Required pass rate: %s%n", RateFormat.format(minPassRate)));
        sb.append(String.format("  Elapsed: %dms%n", elapsedMs));

        return sb.toString();
    }
}
