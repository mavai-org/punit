package org.mavai.punit.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mavai.punit.api.FactorBundle;
import org.mavai.punit.api.TestIntent;
import org.mavai.punit.api.covariate.CovariateAlignment;
import org.mavai.punit.api.spec.AssertionEnforcement;
import org.mavai.punit.api.spec.CriterionResult;
import org.mavai.punit.api.spec.CriterionRole;
import org.mavai.punit.api.spec.EngineRunSummary;
import org.mavai.punit.api.spec.EvaluatedCriterion;
import org.mavai.punit.api.spec.PerCriterionEvaluation;
import org.mavai.punit.api.spec.PercentileLatency;
import org.mavai.punit.api.spec.ProbabilisticTestResult;
import org.mavai.punit.api.spec.Verdict;
import org.mavai.punit.api.spec.VerdictComposition;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.TestAbortedException;

/**
 * How {@link PUnit#translate(ProbabilisticTestResult, String, PUnit.Assertion)}
 * turns a per-dimension verdict into the test's outcome: the mapping
 * {@code assertPasses()} uses, applied to the asserted dimension alone.
 */
@DisplayName("per-dimension assertions — verdict to outcome")
class PerDimensionTranslateTest {

    private static final String ID = "shopping-basket";
    private static final String RATE = "bernoulli-pass-rate";

    private PrintStream originalErr;
    private ByteArrayOutputStream captured;

    @BeforeEach
    void redirectStderr() {
        originalErr = System.err;
        captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restoreStderr() {
        System.setErr(originalErr);
    }

    private String stderr() {
        return captured.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("an INCONCLUSIVE functional verdict aborts assertContract and leaves assertLatency passing")
    void inconclusiveFunctionalAborts() {
        ProbabilisticTestResult result = result(
                Verdict.INCONCLUSIVE, Verdict.PASS, AssertionEnforcement.ALL_ENFORCED, List.of());

        assertThatExceptionOfType(TestAbortedException.class)
                .isThrownBy(() -> PUnit.translate(result, ID, PUnit.Assertion.CONTRACT))
                .withMessageStartingWith("INCONCLUSIVE (functional dimension, assertContract())");
        assertThat(stderr()).contains("[PUNIT-INCONCLUSIVE] " + ID);
        assertThatCode(() -> PUnit.translate(result, ID, PUnit.Assertion.LATENCY))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an INCONCLUSIVE latency verdict aborts assertLatency and leaves assertContract passing")
    void inconclusiveLatencyAborts() {
        ProbabilisticTestResult result = result(
                Verdict.PASS, Verdict.INCONCLUSIVE, AssertionEnforcement.ALL_ENFORCED, List.of());

        assertThatExceptionOfType(TestAbortedException.class)
                .isThrownBy(() -> PUnit.translate(result, ID, PUnit.Assertion.LATENCY))
                .withMessageStartingWith("INCONCLUSIVE (latency dimension, assertLatency())");
        assertThatCode(() -> PUnit.translate(result, ID, PUnit.Assertion.CONTRACT))
                .doesNotThrowAnyException();
        assertThat(stderr()).contains("[PUNIT-UNASSERTED] " + ID + ": latency INCONCLUSIVE "
                + "(not asserted by assertContract())");
    }

    @Test
    @DisplayName("an INCONCLUSIVE whose baseline candidates were all rejected fails, as assertPasses does")
    void inconclusiveWithRejectedCandidatesFails() {
        ProbabilisticTestResult result = result(
                Verdict.INCONCLUSIVE, Verdict.PASS, AssertionEnforcement.ALL_ENFORCED,
                List.of("rejected shopping-basket.baseline.yaml — CONFIGURATION mismatch on model"));

        assertThatExceptionOfType(AssertionFailedError.class)
                .isThrownBy(() -> PUnit.translate(result, ID, PUnit.Assertion.CONTRACT));
    }

    @Test
    @DisplayName("an advisory dimension never fails its method; its verdict is reported as advisory")
    void advisoryDimensionReported() {
        ProbabilisticTestResult result = result(
                Verdict.FAIL, Verdict.FAIL,
                AssertionEnforcement.advisory(AssertionEnforcement.Dimension.LATENCY), List.of());

        assertThatCode(() -> PUnit.translate(result, ID, PUnit.Assertion.LATENCY))
                .doesNotThrowAnyException();
        assertThat(stderr()).contains("[PUNIT-ADVISORY] " + ID + ": latency FAIL");

        assertThatExceptionOfType(AssertionFailedError.class)
                .isThrownBy(() -> PUnit.translate(result, ID, PUnit.Assertion.CONTRACT))
                .withMessageContaining(PercentileLatency.NAME + " → FAIL (advisory)")
                .withMessageContaining(RATE + " → FAIL: ");
    }

    @Test
    @DisplayName("assertPasses keeps its message and outcome")
    void assertPassesUnchanged() {
        ProbabilisticTestResult result = result(
                Verdict.PASS, Verdict.FAIL, AssertionEnforcement.ALL_ENFORCED, List.of());

        assertThatExceptionOfType(AssertionFailedError.class)
                .isThrownBy(() -> PUnit.translate(result, ID))
                .withMessage(PUnit.formatMessage(result))
                .withMessageStartingWith("FAIL\n");
    }

    @Test
    @DisplayName("a run-level FAIL override binds every enforced dimension")
    void runLevelOverrideBinds() {
        ProbabilisticTestResult decided = result(
                Verdict.PASS, Verdict.PASS, AssertionEnforcement.ALL_ENFORCED, List.of());
        ProbabilisticTestResult overridden = withVerdict(decided, Verdict.FAIL);

        assertThatExceptionOfType(AssertionFailedError.class)
                .isThrownBy(() -> PUnit.translate(overridden, ID, PUnit.Assertion.CONTRACT));
        assertThatExceptionOfType(AssertionFailedError.class)
                .isThrownBy(() -> PUnit.translate(overridden, ID, PUnit.Assertion.LATENCY));
    }

    @Test
    @DisplayName("a result that composed no dimension asserts its own verdict from every terminal")
    void uncomposedResultAssertsItsVerdict() {
        EvaluatedCriterion noBaseline = new EvaluatedCriterion(
                new CriterionResult(RATE, Verdict.INCONCLUSIVE, "no baseline", Map.of()),
                CriterionRole.REQUIRED);
        ProbabilisticTestResult result = new ProbabilisticTestResult(
                Verdict.INCONCLUSIVE, FactorBundle.empty(), List.of(noBaseline),
                TestIntent.VERIFICATION, List.of(), CovariateAlignment.none(), Optional.empty(),
                Map.of(), EngineRunSummary.empty(), PerCriterionEvaluation.empty());

        for (PUnit.Assertion assertion : PUnit.Assertion.values()) {
            assertThatExceptionOfType(TestAbortedException.class)
                    .isThrownBy(() -> PUnit.translate(result, ID, assertion));
        }
    }

    // ── Synthetic results ─────────────────────────────────────────────

    private static ProbabilisticTestResult result(
            Verdict rate, Verdict latency, AssertionEnforcement enforcement, List<String> warnings) {
        VerdictComposition composition = VerdictComposition.compose(
                List.of(new VerdictComposition.Decided(RATE, rate)),
                List.of(new VerdictComposition.Decided("p95", latency)),
                enforcement);
        List<EvaluatedCriterion> evaluated = List.of(
                new EvaluatedCriterion(new CriterionResult(
                        RATE, rate, "observed=0.9000, threshold=0.9500", Map.of()),
                        CriterionRole.REQUIRED),
                new EvaluatedCriterion(new CriterionResult(
                        PercentileLatency.NAME, latency, "latency constraint(s) p95", Map.of()),
                        CriterionRole.REQUIRED));
        return new ProbabilisticTestResult(
                composition.testVerdict(), FactorBundle.empty(), evaluated,
                TestIntent.VERIFICATION, warnings, CovariateAlignment.none(), Optional.empty(),
                Map.of(), EngineRunSummary.empty(), PerCriterionEvaluation.empty(),
                Optional.empty(), Optional.of(composition), List.of());
    }

    private static ProbabilisticTestResult withVerdict(ProbabilisticTestResult r, Verdict verdict) {
        return new ProbabilisticTestResult(
                verdict, r.factors(), r.criterionResults(), r.intent(), r.warnings(),
                r.covariates(), r.contractRef(), r.failuresByPostcondition(), r.engineSummary(),
                r.perCriterionEvaluation(), r.postconditionStandings(), r.composition(),
                r.refusals());
    }
}
