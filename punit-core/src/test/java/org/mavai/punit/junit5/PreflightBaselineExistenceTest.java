package org.mavai.punit.junit5;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;

import org.mavai.punit.internal.engine.baseline.BaselineResolver;
import org.mavai.punit.junit5.testsubjects.PreflightSubjects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;

/**
 * Pins the baseline-existence preflight: when a required empirical
 * criterion has no resolvable baseline the framework short-circuits
 * before the engine takes any samples, and the operator-visible
 * outcome (JUnit-aborted INCONCLUSIVE) is the same as the post-sampling
 * path used to produce. The asymmetry the framework removes is that
 * the post-sampling path used to charge {@code N} LLM calls' worth of
 * cost first; the preflight charges zero.
 */
@DisplayName("Preflight baseline-existence short-circuit")
class PreflightBaselineExistenceTest {

    private static final String JUNIT_ENGINE_ID = "junit-jupiter";

    @TempDir Path baselineDir;
    private String savedBaselineProperty;

    @BeforeEach
    void setUp() {
        savedBaselineProperty = System.getProperty(BaselineResolver.BASELINE_DIR_PROPERTY);
        System.setProperty(BaselineResolver.BASELINE_DIR_PROPERTY, baselineDir.toString());
        PreflightSubjects.INVOKE_COUNT.set(0);
    }

    @AfterEach
    void tearDown() {
        if (savedBaselineProperty == null) {
            System.clearProperty(BaselineResolver.BASELINE_DIR_PROPERTY);
        } else {
            System.setProperty(BaselineResolver.BASELINE_DIR_PROPERTY, savedBaselineProperty);
        }
    }

    @Test
    @DisplayName("required empirical without baseline aborts before any sample is taken")
    void requiredEmpiricalNoBaselineShortCircuits() {
        Events events = run(PreflightSubjects.EmpiricalNoBaselineTest.class);
        events.assertStatistics(stats -> stats.started(1).aborted(1).failed(0));
        assertThat(PreflightSubjects.INVOKE_COUNT.get())
                .as("engine must not run when the required empirical criterion has no baseline")
                .isZero();
    }

    @Test
    @DisplayName("required empirical WITH a resolvable baseline runs the engine (no short-circuit)")
    void requiredEmpiricalWithBaselineRunsEngine() throws IOException {
        // Phase 1: stamp a baseline so the paired test resolves.
        run(PreflightSubjects.MeasureBaseline.class)
                .assertStatistics(stats -> stats.started(1).succeeded(1));
        int afterMeasure = PreflightSubjects.INVOKE_COUNT.get();
        assertThat(afterMeasure).isPositive();

        // Phase 2: the empirical test runs to a verdict, taking real samples.
        Events events = run(PreflightSubjects.EmpiricalWithBaselineTest.class);
        events.assertStatistics(stats -> stats.started(1));
        assertThat(PreflightSubjects.INVOKE_COUNT.get())
                .as("a resolvable baseline must not trigger the short-circuit; "
                        + "the test phase contributes its own invocations")
                .isGreaterThan(afterMeasure);
    }

    private static Events run(Class<?> testClass) {
        return EngineTestKit.engine(JUNIT_ENGINE_ID)
                .selectors(DiscoverySelectors.selectClass(testClass))
                .execute()
                .testEvents();
    }
}
