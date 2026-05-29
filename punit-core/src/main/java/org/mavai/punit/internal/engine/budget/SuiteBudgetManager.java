package org.mavai.punit.internal.engine.budget;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.mavai.punit.api.BudgetExhaustedBehavior;

/**
 * Singleton manager for suite-level budget tracking.
 * 
 * <p>Suite-level budgets are configured via system properties:
 * <ul>
 *   <li>{@code punit.suite.timeBudgetMs} - Max time for all probabilistic tests in JVM</li>
 *   <li>{@code punit.suite.tokenBudget} - Max tokens for all probabilistic tests in JVM</li>
 *   <li>{@code punit.suite.onBudgetExhausted} - Behavior when suite budget exhausted (FAIL or EVALUATE_PARTIAL)</li>
 * </ul>
 * 
 * <p>The suite budget monitor is lazily initialized on first access and shared
 * across all test classes in the JVM.
 * 
 * <h2>Thread Safety</h2>
 * <p>This class uses lazy initialization with double-checked locking and
 * delegates to {@link SharedBudgetMonitor} which uses atomic operations.
 */
public final class SuiteBudgetManager {

    // System property names
    public static final String PROP_SUITE_TIME_BUDGET_MS = "punit.suite.timeBudgetMs";
    public static final String PROP_SUITE_TOKEN_BUDGET = "punit.suite.tokenBudget";
    public static final String PROP_SUITE_ON_BUDGET_EXHAUSTED = "punit.suite.onBudgetExhausted";

    // Environment variable names
    public static final String ENV_SUITE_TIME_BUDGET_MS = "PUNIT_SUITE_TIME_BUDGET_MS";
    public static final String ENV_SUITE_TOKEN_BUDGET = "PUNIT_SUITE_TOKEN_BUDGET";
    public static final String ENV_SUITE_ON_BUDGET_EXHAUSTED = "PUNIT_SUITE_ON_BUDGET_EXHAUSTED";

    private static final AtomicReference<SharedBudgetMonitor> INSTANCE = new AtomicReference<>();

    private SuiteBudgetManager() {
        // Prevent instantiation
    }

    /**
     * Gets the suite-level budget monitor, creating it if necessary.
     * 
     * <p>The monitor is initialized from system properties/environment variables
     * on first access. If no suite budget is configured, returns empty.
     *
     * @return an Optional containing the suite budget monitor, or empty if not configured
     */
    public static Optional<SharedBudgetMonitor> getMonitor() {
        SharedBudgetMonitor monitor = INSTANCE.get();
        if (monitor != null) {
            return Optional.of(monitor);
        }

        // Lazy initialization
        Optional<SharedBudgetMonitor> newMonitorOpt = createMonitorFromProperties();
        if (newMonitorOpt.isEmpty()) {
            return Optional.empty();
        }

        SharedBudgetMonitor newMonitor = newMonitorOpt.get();
        // CAS to ensure only one instance is created
        if (INSTANCE.compareAndSet(null, newMonitor)) {
            return Optional.of(newMonitor);
        } else {
            // Another thread created the monitor first
            return Optional.ofNullable(INSTANCE.get());
        }
    }

    /**
     * Checks if a suite-level budget is configured.
     *
     * @return true if suite budget is configured
     */
    public static boolean hasSuiteBudget() {
        return getMonitor().isPresent();
    }

    /**
     * Resets the suite budget manager. Used for testing.
     * 
     * <p><strong>Warning:</strong> This method is intended for testing only.
     * Calling it during normal test execution may cause unpredictable behavior.
     */
    public static void reset() {
        INSTANCE.set(null);
    }

    private static Optional<SharedBudgetMonitor> createMonitorFromProperties() {
        long timeBudgetMs = resolveLong(PROP_SUITE_TIME_BUDGET_MS, ENV_SUITE_TIME_BUDGET_MS, 0);
        long tokenBudget = resolveLong(PROP_SUITE_TOKEN_BUDGET, ENV_SUITE_TOKEN_BUDGET, 0);

        // If no budget is configured, return empty
        if (timeBudgetMs <= 0 && tokenBudget <= 0) {
            return Optional.empty();
        }

        BudgetExhaustedBehavior behavior = resolveBehavior(
                PROP_SUITE_ON_BUDGET_EXHAUSTED,
                ENV_SUITE_ON_BUDGET_EXHAUSTED,
                BudgetExhaustedBehavior.FAIL
        );

        return Optional.of(new SharedBudgetMonitor(
                SharedBudgetMonitor.Scope.SUITE,
                timeBudgetMs,
                tokenBudget,
                behavior
        ));
    }

    private static long resolveLong(String sysProp, String envVar, long defaultValue) {
        String value = System.getProperty(sysProp);
        if (value != null && !value.isEmpty()) {
            try {
                return Long.parseLong(value.trim());
            } catch (NumberFormatException e) {
                // Log warning and use default
                return defaultValue;
            }
        }

        value = System.getenv(envVar);
        if (value != null && !value.isEmpty()) {
            try {
                return Long.parseLong(value.trim());
            } catch (NumberFormatException e) {
                // Log warning and use default
                return defaultValue;
            }
        }

        return defaultValue;
    }

    private static BudgetExhaustedBehavior resolveBehavior(String sysProp, String envVar,
                                                            BudgetExhaustedBehavior defaultValue) {
        String value = System.getProperty(sysProp);
        if (value != null && !value.isEmpty()) {
            try {
                return BudgetExhaustedBehavior.valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                // Log warning and use default
                return defaultValue;
            }
        }

        value = System.getenv(envVar);
        if (value != null && !value.isEmpty()) {
            try {
                return BudgetExhaustedBehavior.valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                // Log warning and use default
                return defaultValue;
            }
        }

        return defaultValue;
    }
}

