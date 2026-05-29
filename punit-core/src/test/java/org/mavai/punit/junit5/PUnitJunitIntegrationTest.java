package org.mavai.punit.junit5;

import java.nio.file.Path;

import org.mavai.punit.junit5.testsubjects.PUnitSubjects;
import org.mavai.punit.internal.engine.baseline.BaselineResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.TestAbortedException;

@DisplayName("@ProbabilisticTest / @Experiment + PUnit factories — JUnit-driven typed-engine outcomes")
class PUnitJunitIntegrationTest {

    private static final String JUNIT_ENGINE_ID = "junit-jupiter";

    /**
     * Scope each test to a fresh, empty tempDir as the baseline
     * directory. Without this, empirical-criterion subjects would
     * accidentally find baselines emitted by earlier subjects (the
     * {@code @Experiment} path writes to whatever directory the
     * resolver returns).
     */
    @TempDir
    Path baselineDir;
    private String savedProperty;

    @BeforeEach
    void isolateBaselineDir() {
        savedProperty = System.getProperty(BaselineResolver.BASELINE_DIR_PROPERTY);
        System.setProperty(BaselineResolver.BASELINE_DIR_PROPERTY, baselineDir.toString());
    }

    @AfterEach
    void restoreBaselineDir() {
        if (savedProperty == null) {
            System.clearProperty(BaselineResolver.BASELINE_DIR_PROPERTY);
        } else {
            System.setProperty(BaselineResolver.BASELINE_DIR_PROPERTY, savedProperty);
        }
    }

    // ── @ProbabilisticTest path ────────────────────────────────────

    @Test
    @DisplayName("contractual PASS surfaces as a JUnit pass")
    void contractualPass() {
        Events events = run(PUnitSubjects.PassingContractualTest.class);
        events.assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));
    }

    @Test
    @DisplayName("contractual FAIL surfaces as AssertionFailedError")
    void contractualFail() {
        Events events = run(PUnitSubjects.FailingContractualTest.class);
        events.assertStatistics(stats -> stats.started(1).failed(1));
        events.failed()
                .assertThatEvents()
                .anySatisfy(event -> {
                    var throwable = event.getRequiredPayload(
                            org.junit.platform.engine.TestExecutionResult.class)
                            .getThrowable().orElseThrow();
                    org.assertj.core.api.Assertions.assertThat(throwable)
                            .isInstanceOf(AssertionFailedError.class);
                    org.assertj.core.api.Assertions.assertThat(throwable.getMessage())
                            .contains("FAIL")
                            .contains("bernoulli-pass-rate");
                });
    }

    @Test
    @DisplayName("empirical INCONCLUSIVE (no baseline resolved) surfaces as TestAbortedException")
    void empiricalInconclusive() {
        Events events = run(PUnitSubjects.InconclusiveEmpiricalTest.class);
        events.assertStatistics(stats -> stats.started(1).aborted(1));
        events.aborted()
                .assertThatEvents()
                .anySatisfy(event -> {
                    var throwable = event.getRequiredPayload(
                            org.junit.platform.engine.TestExecutionResult.class)
                            .getThrowable().orElseThrow();
                    org.assertj.core.api.Assertions.assertThat(throwable)
                            .isInstanceOf(TestAbortedException.class);
                });
    }

    // ── @Experiment path ───────────────────────────────────────────

    @Test
    @DisplayName("@Experiment PUnit.measuring(...).run() returns normally")
    void measureExperimentRuns() {
        Events events = run(PUnitSubjects.PassingMeasureExperiment.class);
        events.assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));
    }

    @Test
    @DisplayName("@Experiment PUnit.exploring(...).grid(...).run() returns normally")
    void exploreExperimentRuns() {
        Events events = run(PUnitSubjects.PassingExploreExperiment.class);
        events.assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));
    }

    // ── Empirical-supplier path ────────────────────────────────────

    @Test
    @DisplayName("PUnit.testing(supplier).samples(N).criterion(...).assertPasses() derives from baseline")
    void empiricalSupplierDerivesFromBaseline() {
        // Without an on-disk baseline the resolver returns EMPTY, so the
        // criterion yields INCONCLUSIVE → aborted. The path itself works
        // (factors and sampling derived from supplier; only sample count
        // specified at test side).
        Events events = run(PUnitSubjects.EmpiricalSupplierTest.class);
        events.assertStatistics(stats -> stats.started(1).aborted(1));
    }

    @Test
    @DisplayName("PUnit.testing(supplier) rejects a non-MEASURE supplier with IllegalArgumentException")
    void empiricalSupplierRejectsNonMeasure() {
        Events events = run(PUnitSubjects.EmpiricalSupplierBadKindTest.class);
        events.assertStatistics(stats -> stats.started(1).failed(1));
        events.failed()
                .assertThatEvents()
                .anySatisfy(event -> {
                    var throwable = event.getRequiredPayload(
                            org.junit.platform.engine.TestExecutionResult.class)
                            .getThrowable().orElseThrow();
                    org.assertj.core.api.Assertions.assertThat(throwable.getMessage())
                            .contains("MEASURE")
                            .contains("EXPLORE");
                });
    }

    private static Events run(Class<?> testClass) {
        return EngineTestKit.engine(JUNIT_ENGINE_ID)
                .selectors(DiscoverySelectors.selectClass(testClass))
                .execute()
                .testEvents();
    }
}
