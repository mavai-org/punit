/**
 * Budget controls for limiting resource consumption during test and experiment execution.
 *
 * <p>This package provides infrastructure for enforcing time and token budgets at
 * multiple scopes:
 * <ul>
 *   <li><b>Method-level:</b> {@link CostBudgetMonitor} tracks budgets for individual test methods</li>
 *   <li><b>Class-level:</b> {@link SharedBudgetMonitor} with {@code ProbabilisticTestBudgetExtension}
 *       enforces shared budgets across all tests in a class</li>
 *   <li><b>Suite-level:</b> {@link SuiteBudgetManager} manages JVM-wide budget limits</li>
 * </ul>
 *
 * <p>The {@link BudgetOrchestrator} coordinates budget checking across all scopes with
 * precedence order: suite → class → method. The first exhausted budget triggers termination.
 *
 * <p>Token tracking supports two modes:
 * <ul>
 *   <li><b>Static:</b> Fixed token charge per sample, configured via annotation</li>
 *   <li><b>Dynamic:</b> Actual tokens recorded during execution via {@link DefaultTokenChargeRecorder}</li>
 * </ul>
 *
 * @see org.mavai.punit.api.ProbabilisticTestBudget
 * @see org.mavai.punit.api.TokenChargeRecorder
 */
package org.mavai.punit.internal.engine.budget;
