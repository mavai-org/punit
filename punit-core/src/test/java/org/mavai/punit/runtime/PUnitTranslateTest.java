package org.mavai.punit.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.mavai.punit.api.FactorBundle;
import org.mavai.punit.api.TestIntent;
import org.mavai.punit.api.covariate.CovariateAlignment;
import org.mavai.punit.api.spec.CriterionResult;
import org.mavai.punit.api.spec.CriterionRole;
import org.mavai.punit.api.spec.EngineRunSummary;
import org.mavai.punit.api.spec.EvaluatedCriterion;
import org.mavai.punit.api.spec.InconclusiveReasons;
import org.mavai.punit.api.spec.PerCriterionEvaluation;
import org.mavai.punit.api.spec.ProbabilisticTestResult;
import org.mavai.punit.api.spec.Verdict;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.TestAbortedException;

/**
 * Behavioural contract for {@link PUnit#translate(ProbabilisticTestResult, String)}'s
 * INCONCLUSIVE-side stderr emission.
 *
 * <p>An INCONCLUSIVE verdict carries a fully-formed explanation
 * inside the {@link TestAbortedException}/{@link AssertionFailedError}
 * it throws. JUnit propagates that as the abort reason and IDEs
 * surface it in their per-test detail panes, but Gradle's default
 * text reporter does not. The framework therefore mirrors the
 * PASS-with-warnings stderr emission for INCONCLUSIVE: a
 * {@code [PUNIT-INCONCLUSIVE]} marker line followed by the
 * formatted body, written to {@link System#err} synchronously
 * before the abort exception is thrown — so an operator watching
 * the build console sees the diagnostic regardless of which
 * trichotomy leg fired.
 */
@DisplayName("PUnit.translate — INCONCLUSIVE stderr emission")
class PUnitTranslateTest {

    record Factors(String label) {}

    private static final FactorBundle FACTORS = FactorBundle.of(new Factors("test"));
    private static final String USE_CASE_ID = "shopping-basket";

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

    @Test
    @DisplayName("INCONCLUSIVE no-baseline → marker + criterion explanation on stderr; aborts")
    void inconclusiveNoBaselineEmitsMarkerAndAborts() {
        ProbabilisticTestResult result = inconclusiveResult(
                "no baseline candidate available for shopping-basket",
                List.of(),
                InconclusiveReasons.NO_BASELINE_AVAILABLE);

        assertThatExceptionOfType(TestAbortedException.class)
                .isThrownBy(() -> PUnit.translate(result, USE_CASE_ID));

        String stderr = captured.toString(StandardCharsets.UTF_8);
        assertThat(stderr).contains("[PUNIT-INCONCLUSIVE] " + USE_CASE_ID);
        assertThat(stderr).contains("INCONCLUSIVE");
        assertThat(stderr).contains("no baseline candidate available for shopping-basket");
    }

    @Test
    @DisplayName("INCONCLUSIVE with rejection notes → marker + warnings on stderr; fails (not aborts)")
    void inconclusiveWithRejectionsEmitsMarkerAndFails() {
        // Trichotomy case 2: candidates were considered but rejected.
        // PUnit.translate throws AssertionFailedError (not
        // TestAbortedException), but the stderr emission fires
        // regardless — both legs of the INCONCLUSIVE branch deserve
        // the same console-side surfacing.
        ProbabilisticTestResult result = inconclusiveResult(
                "no covariate-aligned baseline for shopping-basket",
                List.of(
                        "rejected shopping-basket.baseline-v1-b60d8a8b-7a8b.yaml — "
                                + "CONFIGURATION mismatch on llm_model",
                        "rejected shopping-basket.baseline-v1-b60d8a8b-c1d2.yaml — "
                                + "no overlap with the current covariate profile"),
                "covariate-misalignment");

        assertThatExceptionOfType(AssertionFailedError.class)
                .isThrownBy(() -> PUnit.translate(result, USE_CASE_ID));

        String stderr = captured.toString(StandardCharsets.UTF_8);
        assertThat(stderr).contains("[PUNIT-INCONCLUSIVE] " + USE_CASE_ID);
        assertThat(stderr).contains("rejected shopping-basket.baseline-v1-b60d8a8b-7a8b.yaml");
        assertThat(stderr).contains("CONFIGURATION mismatch on llm_model");
    }

    @Test
    @DisplayName("INCONCLUSIVE with blank serviceContractId → marker without identifier suffix")
    void inconclusiveBlankServiceContractIdEmitsBareMarker() {
        ProbabilisticTestResult result = inconclusiveResult(
                "no baseline available", List.of(),
                InconclusiveReasons.NO_BASELINE_AVAILABLE);

        assertThatExceptionOfType(TestAbortedException.class)
                .isThrownBy(() -> PUnit.translate(result, ""));

        String stderr = captured.toString(StandardCharsets.UTF_8);
        // Bare marker line — no trailing whitespace / no identifier
        assertThat(stderr.lines().findFirst())
                .hasValueSatisfying(line -> assertThat(line).isEqualTo("[PUNIT-INCONCLUSIVE]"));
    }

    @Test
    @DisplayName("PASS → no stderr emission (translate returns normally)")
    void passEmitsNothing() {
        ProbabilisticTestResult result = passResult();

        PUnit.translate(result, USE_CASE_ID);

        assertThat(captured.toString(StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    @DisplayName("FAIL → no INCONCLUSIVE marker on stderr; throws AssertionFailedError")
    void failDoesNotEmitInconclusiveMarker() {
        // The FAIL path is intentionally unchanged by this directive —
        // its message reaches the build log via the surefire/Gradle
        // reporter's failure-summary block. Asserting the absence of
        // [PUNIT-INCONCLUSIVE] keeps the two paths from drifting into
        // shared emission.
        ProbabilisticTestResult result = failResult();

        assertThatExceptionOfType(AssertionFailedError.class)
                .isThrownBy(() -> PUnit.translate(result, USE_CASE_ID));

        assertThat(captured.toString(StandardCharsets.UTF_8))
                .doesNotContain("[PUNIT-INCONCLUSIVE]");
    }

    // ── Synthetic results ─────────────────────────────────────────────

    private static ProbabilisticTestResult inconclusiveResult(
            String explanation, List<String> warnings, String reasonDiscriminant) {
        Map<String, Object> detail = Map.of(
                InconclusiveReasons.DETAIL_KEY, reasonDiscriminant);
        EvaluatedCriterion ec = new EvaluatedCriterion(
                new CriterionResult(
                        "bernoulli-pass-rate",
                        Verdict.INCONCLUSIVE,
                        explanation,
                        detail),
                CriterionRole.REQUIRED);
        return new ProbabilisticTestResult(
                Verdict.INCONCLUSIVE,
                FACTORS,
                List.of(ec),
                TestIntent.VERIFICATION,
                warnings,
                CovariateAlignment.none(),
                Optional.empty(),
                Map.of(),
                EngineRunSummary.empty(),
                PerCriterionEvaluation.empty());
    }

    private static ProbabilisticTestResult passResult() {
        EvaluatedCriterion ec = new EvaluatedCriterion(
                new CriterionResult(
                        "bernoulli-pass-rate",
                        Verdict.PASS,
                        "observed=0.9500, threshold=0.9020",
                        Map.of()),
                CriterionRole.REQUIRED);
        return new ProbabilisticTestResult(
                Verdict.PASS, FACTORS, List.of(ec),
                TestIntent.VERIFICATION, List.of(),
                CovariateAlignment.none(), Optional.empty(), Map.of(),
                EngineRunSummary.empty(), PerCriterionEvaluation.empty());
    }

    private static ProbabilisticTestResult failResult() {
        EvaluatedCriterion ec = new EvaluatedCriterion(
                new CriterionResult(
                        "bernoulli-pass-rate",
                        Verdict.FAIL,
                        "observed=0.7000, threshold=0.9020",
                        Map.of()),
                CriterionRole.REQUIRED);
        return new ProbabilisticTestResult(
                Verdict.FAIL, FACTORS, List.of(ec),
                TestIntent.VERIFICATION, List.of(),
                CovariateAlignment.none(), Optional.empty(), Map.of(),
                EngineRunSummary.empty(), PerCriterionEvaluation.empty());
    }
}
