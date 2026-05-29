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
import org.mavai.punit.internal.engine.baseline.BaselineWriter;
import org.mavai.punit.internal.engine.baseline.FactorsFingerprint;
import org.mavai.punit.junit5.testsubjects.FeasibilitySubjects;
import org.mavai.punit.internal.engine.baseline.BaselineResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;

@DisplayName("Feasibility — VERIFICATION fails fast, SMOKE proceeds silently")
class FeasibilityIntegrationTest {

    private static final String JUNIT_ENGINE_ID = "junit-jupiter";

    @TempDir Path baselineDir;
    private String savedProperty;

    @BeforeEach
    void setUp() {
        savedProperty = System.getProperty(BaselineResolver.BASELINE_DIR_PROPERTY);
        System.setProperty(BaselineResolver.BASELINE_DIR_PROPERTY, baselineDir.toString());
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
    @DisplayName("VERIFICATION + adequate sample size — feasibility passes; engine runs; verdict PASS")
    void verificationFeasible() throws IOException {
        // n=50 against rate 0.50 is feasible (Wilson at observed=1.0, n=50 ≈ 0.949 > 0.50).
        writeBaselineAt(0.50, 100);

        Events events = run(FeasibilitySubjects.VerificationFeasible.class);
        events.assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));
    }

    @Test
    @DisplayName("VERIFICATION + undersized sample — feasibility fails fast with IllegalStateException")
    void verificationInfeasibleFailsFast() throws IOException {
        // n=10 against rate 0.95 is infeasible (Wilson at observed=1.0, n=10 ≈ 0.787 < 0.95).
        writeBaselineAt(0.95, 1000);

        Events events = run(FeasibilitySubjects.VerificationInfeasible.class);
        events.assertStatistics(stats -> stats.started(1).failed(1));
        events.failed()
                .assertThatEvents()
                .anySatisfy(event -> {
                    var throwable = event.getRequiredPayload(
                            org.junit.platform.engine.TestExecutionResult.class)
                            .getThrowable().orElseThrow();
                    assertThat(throwable).isInstanceOf(IllegalStateException.class);
                    assertThat(throwable.getMessage())
                            .contains("INFEASIBLE VERIFICATION")
                            .contains(FeasibilitySubjects.USE_CASE_ID)
                            .contains("(10)")
                            .contains("At least")
                            .contains("Increase samples")
                            .contains("intent = SMOKE");
                });
    }

    @Test
    @DisplayName("SMOKE + undersized sample — engine runs silently; verdict produced")
    void smokeInfeasibleAllowed() throws IOException {
        // Same config as VerificationInfeasible but intent=SMOKE. The
        // developer has declared "I know this is undersized; treat as
        // a sentinel" — the gate produces no warning and the run
        // proceeds.
        writeBaselineAt(0.95, 1000);

        Events events = run(FeasibilitySubjects.SmokeInfeasible.class);
        // The verdict at observed=1.0, n=10 is FAIL (Wilson lower bound 0.787
        // < baseline 0.95). The point of this test is the run wasn't
        // *aborted* — it executed and produced a real verdict (here: FAIL).
        events.assertStatistics(stats -> stats.started(1));
        // It's either succeeded or failed; not aborted (which would mean
        // INCONCLUSIVE), and not skipped (which would mean discovery filter).
        long failedOrSucceeded = events.failed().count() + events.succeeded().count();
        assertThat(failedOrSucceeded)
                .as("SMOKE-intent test runs to verdict; not aborted/skipped")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("VERIFICATION + contractual SLA threshold + undersized sample — feasibility fails fast")
    void contractualVerificationInfeasibleFailsFast() {
        // No baseline written — contractual targets do not consult one.
        // n=50 against a contractual 99.99% target at default 95%
        // confidence is infeasible (Wilson at observed=1.0, n=50 ≈
        // 0.949 < 0.9999). The pre-flight gate must abort before any
        // samples execute.
        Events events = run(FeasibilitySubjects.ContractualVerificationInfeasible.class);
        events.assertStatistics(stats -> stats.started(1).failed(1));
        events.failed()
                .assertThatEvents()
                .anySatisfy(event -> {
                    var throwable = event.getRequiredPayload(
                            org.junit.platform.engine.TestExecutionResult.class)
                            .getThrowable().orElseThrow();
                    assertThat(throwable).isInstanceOf(IllegalStateException.class);
                    assertThat(throwable.getMessage())
                            .contains("INFEASIBLE VERIFICATION")
                            .contains(FeasibilitySubjects.USE_CASE_ID)
                            .contains("(50)")
                            .contains("99.99%")
                            .contains("At least")
                            .contains("Increase samples")
                            .contains("intent = SMOKE");
                });
    }

    @Test
    @DisplayName("SMOKE + contractual SLA threshold + undersized sample — engine runs silently")
    void contractualSmokeInfeasibleAllowed() {
        Events events = run(FeasibilitySubjects.ContractualSmokeInfeasible.class);
        // Subject's service contract always passes; contractual evaluator does
        // observed >= threshold, so observed=1.0 >= 0.9999 → PASS.
        // The point of this test is the run wasn't *aborted*.
        events.assertStatistics(stats -> stats.started(1));
        long failedOrSucceeded = events.failed().count() + events.succeeded().count();
        assertThat(failedOrSucceeded)
                .as("contractual SMOKE-intent test runs to verdict; not aborted/skipped")
                .isEqualTo(1);
    }

    private void writeBaselineAt(double rate, int sampleCount) throws IOException {
        BaselineRecord record = new BaselineRecord(
                FeasibilitySubjects.USE_CASE_ID,
                "hand-written",
                FactorsFingerprint.of(FactorBundle.of(new NoFactors())),
                InputSupplier.from(() -> List.of(1, 2, 3)).identity(),
                sampleCount,
                Instant.parse("2026-04-28T15:00:00Z"),
                Map.<String, BaselineStatistics>of(
                        "bernoulli-pass-rate",
                        PerCriterionPassRateStatistics.of("contract", rate, sampleCount)));
        new BaselineWriter().write(record, baselineDir);
    }

    private static Events run(Class<?> testClass) {
        return EngineTestKit.engine(JUNIT_ENGINE_ID)
                .selectors(DiscoverySelectors.selectClass(testClass))
                .execute()
                .testEvents();
    }
}
