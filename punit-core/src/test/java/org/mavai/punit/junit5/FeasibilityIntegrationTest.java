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

@DisplayName("Configuration refusal — invalid designs are refused before any sample, SMOKE proceeds")
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
        // n=50 against a baseline of 100 samples: not larger than its baseline.
        writeBaselineAt(0.50, 100);

        Events events = run(FeasibilitySubjects.VerificationFeasible.class);
        events.assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));
    }

    @Test
    @DisplayName("a test larger than its baseline is refused before any sample (TEST_LARGER_THAN_BASELINE)")
    void verificationInfeasibleFailsFast() throws IOException {
        // n=10 against a baseline of 5 samples: the test is larger than
        // the baseline it consumes, which mavai's design policy refuses.
        writeBaselineAt(0.95, 5);

        Events events = run(FeasibilitySubjects.VerificationInfeasible.class);
        events.assertStatistics(stats -> stats.started(1).failed(1));
        events.failed()
                .assertThatEvents()
                .anySatisfy(event -> {
                    var throwable = event.getRequiredPayload(
                            org.junit.platform.engine.TestExecutionResult.class)
                            .getThrowable().orElseThrow();
                    assertThat(throwable).isInstanceOf(
                            org.mavai.punit.api.spec.ConfigurationRefusedException.class);
                    assertThat(throwable.getMessage())
                            .contains("CONFIGURATION REFUSED")
                            .contains(FeasibilitySubjects.USE_CASE_ID)
                            .contains("TEST_LARGER_THAN_BASELINE")
                            .contains("(10 samples)")
                            .contains("(5 samples)");
                });
    }

    @Test
    @DisplayName("SMOKE + undersized sample — engine runs silently; verdict produced")
    void smokeInfeasibleAllowed() throws IOException {
        // An empirical test of 10 against a baseline of 1000 is a valid
        // design under either intent: the regression rule has no
        // feasibility minimum. The run proceeds to a verdict.
        writeBaselineAt(0.95, 1000);

        Events events = run(FeasibilitySubjects.SmokeInfeasible.class);
        // The point of this test is the run wasn't *aborted* — it
        // executed and produced a real verdict.
        events.assertStatistics(stats -> stats.started(1));
        // It's either succeeded or failed; not aborted (which would mean
        // INCONCLUSIVE), and not skipped (which would mean discovery filter).
        long failedOrSucceeded = events.failed().count() + events.succeeded().count();
        assertThat(failedOrSucceeded)
                .as("SMOKE-intent test runs to verdict; not aborted/skipped")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("VERIFICATION + contractual SLA threshold + undersized sample — refused (COMPLIANCE_INFEASIBLE)")
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
                    assertThat(throwable).isInstanceOf(
                            org.mavai.punit.api.spec.ConfigurationRefusedException.class);
                    assertThat(throwable.getMessage())
                            .contains("CONFIGURATION REFUSED")
                            .contains(FeasibilitySubjects.USE_CASE_ID)
                            .contains("COMPLIANCE_INFEASIBLE")
                            .contains("no count of 50 samples")
                            .contains("feasibility minimum 29956")
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
