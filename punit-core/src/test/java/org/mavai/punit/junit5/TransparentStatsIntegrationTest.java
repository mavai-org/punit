package org.mavai.punit.junit5;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.mavai.punit.junit5.testsubjects.PUnitSubjects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;

/**
 * Integration test for {@code PUnit.TestBuilder.transparentStats()}:
 * verifies that opting in produces the verbose statistical analysis
 * on stderr and that off-by-default tests do not.
 */
@DisplayName("Transparent stats — verbose verdict reporting via the typed builder")
class TransparentStatsIntegrationTest {

    private static final String JUNIT_ENGINE_ID = "junit-jupiter";

    private PrintStream originalErr;
    private ByteArrayOutputStream capturedErr;

    @BeforeEach
    void redirectStderr() {
        originalErr = System.err;
        capturedErr = new ByteArrayOutputStream();
        System.setErr(new PrintStream(capturedErr, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restoreStderr() {
        System.setErr(originalErr);
    }

    @Test
    @DisplayName("transparentStats() builder method renders the verbose breakdown to stderr")
    void rendersVerboseBreakdown() {
        Events events = run(PUnitSubjects.TransparentStatsTest.class);
        events.assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));

        String stderr = capturedErr.toString(StandardCharsets.UTF_8);
        assertThat(stderr)
                .contains("STATISTICAL ANALYSIS — verdict: PASS")
                .contains("[REQUIRED] bernoulli-pass-rate → PASS")
                .contains("Hypothesis test")
                .contains("H₀ (null):")
                .contains("Observed data")
                .contains("Inference")
                .contains("Test intent: VERIFICATION");
    }

    @Test
    @DisplayName("default (transparentStats off) emits no verbose breakdown")
    void noOutputWhenDisabled() {
        Events events = run(PUnitSubjects.PassingContractualTest.class);
        events.assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));

        String stderr = capturedErr.toString(StandardCharsets.UTF_8);
        assertThat(stderr).doesNotContain("STATISTICAL ANALYSIS");
    }

    private static Events run(Class<?> testClass) {
        return EngineTestKit.engine(JUNIT_ENGINE_ID)
                .selectors(DiscoverySelectors.selectClass(testClass))
                .execute()
                .testEvents();
    }
}
