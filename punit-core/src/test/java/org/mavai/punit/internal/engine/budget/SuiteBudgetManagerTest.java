package org.mavai.punit.internal.engine.budget;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SuiteBudgetManager")
class SuiteBudgetManagerTest {

    @BeforeEach
    void setUp() {
        // Reset manager and clear any system properties
        SuiteBudgetManager.reset();
        System.clearProperty(SuiteBudgetManager.PROP_SUITE_TIME_BUDGET_MS);
        System.clearProperty(SuiteBudgetManager.PROP_SUITE_TOKEN_BUDGET);
        System.clearProperty(SuiteBudgetManager.PROP_SUITE_ON_BUDGET_EXHAUSTED);
    }

    @AfterEach
    void tearDown() {
        // Clean up
        SuiteBudgetManager.reset();
        System.clearProperty(SuiteBudgetManager.PROP_SUITE_TIME_BUDGET_MS);
        System.clearProperty(SuiteBudgetManager.PROP_SUITE_TOKEN_BUDGET);
        System.clearProperty(SuiteBudgetManager.PROP_SUITE_ON_BUDGET_EXHAUSTED);
    }

    @Nested
    @DisplayName("getMonitor")
    class GetMonitor {

        @Test
        @DisplayName("returns null when no budget configured")
        void returnsNullWhenNoBudgetConfigured() {
            SharedBudgetMonitor monitor = SuiteBudgetManager.getMonitor().orElse(null);

            assertThat(monitor).isNull();
        }

        @Test
        @DisplayName("creates monitor when time budget configured")
        void createsMonitorWhenTimeBudgetConfigured() {
            System.setProperty(SuiteBudgetManager.PROP_SUITE_TIME_BUDGET_MS, "10000");

            SharedBudgetMonitor monitor = SuiteBudgetManager.getMonitor().orElse(null);

            assertThat(monitor).isNotNull();
            assertThat(monitor.hasTimeBudget()).isTrue();
            assertThat(monitor.getTimeBudgetMs()).isEqualTo(10000);
        }

        @Test
        @DisplayName("creates monitor when token budget configured")
        void createsMonitorWhenTokenBudgetConfigured() {
            System.setProperty(SuiteBudgetManager.PROP_SUITE_TOKEN_BUDGET, "50000");

            SharedBudgetMonitor monitor = SuiteBudgetManager.getMonitor().orElse(null);

            assertThat(monitor).isNotNull();
            assertThat(monitor.hasTokenBudget()).isTrue();
            assertThat(monitor.getTokenBudget()).isEqualTo(50000);
        }

        @Test
        @DisplayName("returns same instance on repeated calls")
        void returnsSameInstanceOnRepeatedCalls() {
            System.setProperty(SuiteBudgetManager.PROP_SUITE_TIME_BUDGET_MS, "10000");

            SharedBudgetMonitor first = SuiteBudgetManager.getMonitor().orElse(null);
            SharedBudgetMonitor second = SuiteBudgetManager.getMonitor().orElse(null);

            assertThat(first).isSameAs(second);
        }

        @Test
        @DisplayName("handles invalid time budget gracefully")
        void handlesInvalidTimeBudgetGracefully() {
            System.setProperty(SuiteBudgetManager.PROP_SUITE_TIME_BUDGET_MS, "not-a-number");

            SharedBudgetMonitor monitor = SuiteBudgetManager.getMonitor().orElse(null);

            // Should return null since invalid value defaults to 0
            assertThat(monitor).isNull();
        }

        @Test
        @DisplayName("handles invalid token budget gracefully")
        void handlesInvalidTokenBudgetGracefully() {
            System.setProperty(SuiteBudgetManager.PROP_SUITE_TOKEN_BUDGET, "invalid");

            SharedBudgetMonitor monitor = SuiteBudgetManager.getMonitor().orElse(null);

            assertThat(monitor).isNull();
        }

        @Test
        @DisplayName("handles invalid behavior gracefully")
        void handlesInvalidBehaviorGracefully() {
            System.setProperty(SuiteBudgetManager.PROP_SUITE_TIME_BUDGET_MS, "10000");
            System.setProperty(SuiteBudgetManager.PROP_SUITE_ON_BUDGET_EXHAUSTED, "INVALID_BEHAVIOR");

            SharedBudgetMonitor monitor = SuiteBudgetManager.getMonitor().orElse(null);

            // Should create monitor with default FAIL behavior
            assertThat(monitor).isNotNull();
        }

        @Test
        @DisplayName("parses EVALUATE_PARTIAL behavior")
        void parsesEvaluatePartialBehavior() {
            System.setProperty(SuiteBudgetManager.PROP_SUITE_TIME_BUDGET_MS, "10000");
            System.setProperty(SuiteBudgetManager.PROP_SUITE_ON_BUDGET_EXHAUSTED, "EVALUATE_PARTIAL");

            SharedBudgetMonitor monitor = SuiteBudgetManager.getMonitor().orElse(null);

            assertThat(monitor).isNotNull();
        }
    }

    @Nested
    @DisplayName("hasSuiteBudget")
    class HasSuiteBudget {

        @Test
        @DisplayName("returns false when no budget configured")
        void returnsFalseWhenNoBudgetConfigured() {
            assertThat(SuiteBudgetManager.hasSuiteBudget()).isFalse();
        }

        @Test
        @DisplayName("returns true when budget configured")
        void returnsTrueWhenBudgetConfigured() {
            System.setProperty(SuiteBudgetManager.PROP_SUITE_TIME_BUDGET_MS, "10000");

            assertThat(SuiteBudgetManager.hasSuiteBudget()).isTrue();
        }
    }

    @Nested
    @DisplayName("reset")
    class Reset {

        @Test
        @DisplayName("clears cached monitor")
        void clearsCachedMonitor() {
            System.setProperty(SuiteBudgetManager.PROP_SUITE_TIME_BUDGET_MS, "10000");
            SharedBudgetMonitor first = SuiteBudgetManager.getMonitor().orElse(null);
            
            SuiteBudgetManager.reset();
            SharedBudgetMonitor second = SuiteBudgetManager.getMonitor().orElse(null);

            assertThat(first).isNotSameAs(second);
        }
    }
}

