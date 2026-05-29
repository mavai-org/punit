package org.mavai.punit.junit5;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.mavai.punit.api.FactorBundle;
import org.mavai.punit.api.InputSupplier;
import org.mavai.punit.api.NoFactors;
import org.mavai.punit.api.spec.BaselineStatistics;
import org.mavai.punit.api.spec.PassRateStatistics;
import org.mavai.punit.api.spec.PerCriterionPassRateStatistics;
import org.mavai.punit.internal.engine.baseline.BaselineRecord;
import org.mavai.punit.internal.engine.baseline.BaselineResolver;
import org.mavai.punit.internal.engine.baseline.BaselineWriter;
import org.mavai.punit.internal.engine.baseline.FactorsFingerprint;
import org.mavai.punit.junit5.testsubjects.PreflightInvariantSubjects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;

/**
 * Audits each pre-flight invariant the framework upholds, end-to-end
 * through the typed authoring surface — declared-threshold
 * feasibility, declared-sample-size feasibility, power-analysis-derived
 * feasibility, parameter validation, and configuration coherence. Each
 * test constructs a configuration that should trip the invariant (or
 * be feasible by construction, in the power-analysis case) and asserts
 * the framework's response — including, where the gate aborts, that no
 * samples ran.
 *
 * <p>The original feasibility regression slipped past existing unit
 * tests because the gate was wired only at the evaluator level, not
 * exercised end-to-end against the
 * {@code PUnit.testing(...).criterion(...).assertPasses()} surface.
 * One test per audited invariant guards against the same shape of
 * regression in the future.
 *
 * <p>The soundness floor (≥ 80% confidence regardless of intent) is
 * covered by its own end-to-end audit, {@code SoundnessFloorTest}.
 * The cross-intent enforcement lives in {@code Feasibility.check},
 * which aborts on a below-floor configuration before the
 * intent-specific branch — so both VERIFICATION and SMOKE trip the
 * same abort. Keeping the soundness-floor coverage in a separate
 * test file mirrors the catalog's split between the broader
 * infeasibility family (here) and the floor-as-its-own-invariant
 * (there).
 */
@DisplayName("Pre-flight invariants — end-to-end audit")
class PreflightInvariantsTest {

    private static final String JUNIT_ENGINE_ID = "junit-jupiter";

    @TempDir Path baselineDir;
    private String savedProperty;

    @BeforeEach
    void setUp() {
        savedProperty = System.getProperty(BaselineResolver.BASELINE_DIR_PROPERTY);
        System.setProperty(BaselineResolver.BASELINE_DIR_PROPERTY, baselineDir.toString());
        PreflightInvariantSubjects.INVOKE_COUNT.set(0);
    }

    @AfterEach
    void tearDown() {
        if (savedProperty == null) {
            System.clearProperty(BaselineResolver.BASELINE_DIR_PROPERTY);
        } else {
            System.setProperty(BaselineResolver.BASELINE_DIR_PROPERTY, savedProperty);
        }
    }

    @Test
    @DisplayName("declared (samples, threshold) infeasible under VERIFICATION → abort, no samples")
    void declaredThresholdInfeasibleAbortsBeforeSampling() {
        Events events = run(
                PreflightInvariantSubjects.DeclaredThresholdInfeasibleTest.class);
        events.assertStatistics(stats -> stats.started(1).failed(1));
        assertInfeasibilityException(events);
        assertThat(PreflightInvariantSubjects.INVOKE_COUNT.get())
                .as("declared-threshold abort must precede sampling — engine never runs")
                .isZero();
    }

    @Test
    @DisplayName("declared (samples, confidence) + empirical baseline rate too high → abort, no samples")
    void declaredSampleSizeInfeasibleAbortsBeforeSampling() throws IOException {
        // Hand-write a baseline at rate 0.95: the always-passing use
        // case would have produced rate 1.0, which Feasibility skips
        // as degenerate. The invariant fires when an empirical-derived
        // threshold sits above what the configured sample size can
        // underwrite.
        writeBaselineAt(0.95, 1000);

        Events events = run(
                PreflightInvariantSubjects.DeclaredSampleSizeInfeasibleTest.class);
        events.assertStatistics(stats -> stats.started(1).failed(1));
        assertInfeasibilityException(events);
        assertThat(PreflightInvariantSubjects.INVOKE_COUNT.get())
                .as("declared-sample-size abort must precede sampling — engine never runs")
                .isZero();
    }

    @Test
    @DisplayName("PowerAnalysis-derived sample count is feasible by construction → engine runs")
    void powerAnalysisDerivedSampleSizeIsFeasible() {
        // Phase 1: seed the baseline PowerAnalysis will read from.
        run(PreflightInvariantSubjects.PowerAnalysisBaselineMeasure.class)
                .assertStatistics(stats -> stats.started(1).succeeded(1));
        int afterMeasure = PreflightInvariantSubjects.INVOKE_COUNT.get();

        // Phase 2: the empirical test asks PowerAnalysis for a sized
        // sample count and configures itself with that. The
        // configuration is feasible by construction; the engine runs.
        Events events = run(
                PreflightInvariantSubjects.PowerAnalysisDerivedFeasibleTest.class);
        events.assertStatistics(stats -> stats.started(1));
        long terminal = events.failed().count() + events.succeeded().count();
        assertThat(terminal)
                .as("feasible-by-construction config runs to a verdict, not aborted")
                .isEqualTo(1);
        assertThat(PreflightInvariantSubjects.INVOKE_COUNT.get())
                .as("engine runs the configured number of samples")
                .isGreaterThan(afterMeasure);
    }

    private void writeBaselineAt(double rate, int sampleCount) throws IOException {
        BaselineRecord record = new BaselineRecord(
                PreflightInvariantSubjects.USE_CASE_ID,
                "hand-written",
                FactorsFingerprint.of(FactorBundle.of(new NoFactors())),
                InputSupplier.from(() -> List.of(1, 2, 3)).identity(),
                sampleCount,
                Instant.parse("2026-05-10T00:00:00Z"),
                Map.<String, BaselineStatistics>of(
                        "bernoulli-pass-rate",
                        PerCriterionPassRateStatistics.of("contract", rate, sampleCount)));
        new BaselineWriter().write(record, baselineDir);
    }

    private static void assertInfeasibilityException(Events events) {
        events.failed()
                .assertThatEvents()
                .anySatisfy(event -> {
                    Throwable t = event.getRequiredPayload(TestExecutionResult.class)
                            .getThrowable().orElseThrow();
                    assertThat(t).isInstanceOf(IllegalStateException.class);
                    assertThat(t.getMessage())
                            .contains("INFEASIBLE VERIFICATION")
                            .contains(PreflightInvariantSubjects.USE_CASE_ID)
                            .contains("Increase samples")
                            .contains("intent = SMOKE");
                });
    }

    private static Events run(Class<?> testClass) {
        return EngineTestKit.engine(JUNIT_ENGINE_ID)
                .selectors(DiscoverySelectors.selectClass(testClass))
                .execute()
                .testEvents();
    }
}
