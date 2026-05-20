package org.javai.punit.internal.engine.criteria;

import static org.javai.punit.api.criterion.Criteria.meeting;
import org.javai.punit.api.criterion.Criteria;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.javai.outcome.Outcome;
import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.api.Contract;
import org.javai.punit.api.FactorBundle;
import org.javai.punit.api.LatencyResult;
import org.javai.punit.api.TokenTracker;
import org.javai.punit.api.ServiceContractOutcome;
import org.javai.punit.api.spec.CriterionResult;
import org.javai.punit.api.spec.EvaluationContext;
import org.javai.punit.api.spec.Experiment;
import org.javai.punit.api.spec.PassRateStatistics;
import org.javai.punit.api.spec.PerCriterionPassRateStatistics;
import org.javai.punit.api.spec.SampleSummary;
import org.javai.punit.api.spec.TerminationReason;
import org.javai.punit.api.spec.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PassRate")
class PassRateTest {

    record Factors(String label) {}

    private static final Contract<Object, String> STUB_CONTRACT = new Contract<>() {
        @Override public Outcome<String> invoke(Object input, TokenTracker tracker) {
            return Outcome.ok("ok");
        }
        @Override public Criteria<String> criteria() {
            return meeting().<String>zeroFailures();
        }

    };

    private static SampleSummary<String> summary(int successes, int failures) {
        int total = successes + failures;
        var outcomes = new java.util.ArrayList<ServiceContractOutcome<?, String>>(total);
        for (int i = 0; i < successes; i++) outcomes.add(stubOutcome(Outcome.ok("ok")));
        for (int i = 0; i < failures; i++) outcomes.add(stubOutcome(Outcome.fail("nope", "msg")));
        return new SampleSummary<>(
                outcomes,
                Duration.ofMillis(1),
                successes, failures, 0L, 0,
                LatencyResult.empty(),
                TerminationReason.COMPLETED,
                List.of(),
                java.util.Map.of(), LatencyResult.empty(), List.of());
    }

    private static ServiceContractOutcome<Object, String> stubOutcome(Outcome<String> result) {
        return new ServiceContractOutcome<>(
                result, STUB_CONTRACT,
                List.of(),
                0L, Duration.ZERO);
    }

    private static final String DEFAULT_IDENTITY = "sha256:test-default-identity";

    private static <OT> EvaluationContext<OT, PerCriterionPassRateStatistics> ctx(
            SampleSummary<OT> summary, Optional<PassRateStatistics> baseline) {
        return ctx(summary, baseline, DEFAULT_IDENTITY,
                baseline.isPresent() ? Optional.of(DEFAULT_IDENTITY) : Optional.empty());
    }

    private static <OT> EvaluationContext<OT, PerCriterionPassRateStatistics> ctx(
            SampleSummary<OT> summary,
            Optional<PassRateStatistics> baseline,
            String testIdentity,
            Optional<String> baselineIdentity) {
        // Wrap the hand-constructed PassRateStatistics into a
        // single-entry PerCriterionPassRateStatistics so the K-aware
        // PassRate evaluator can read its lone criterion's basis.
        Optional<PerCriterionPassRateStatistics> wrapped = baseline.map(
                b -> PerCriterionPassRateStatistics.of("contract", b));
        return new EvaluationContext<OT, PerCriterionPassRateStatistics>() {
            @Override public SampleSummary<OT> summary() { return summary; }
            @Override public Optional<PerCriterionPassRateStatistics> baseline() { return wrapped; }
            @Override public FactorBundle factors() { return FactorBundle.of(new Factors("x")); }
            @Override public String testInputsIdentity() { return testIdentity; }
            @Override public Optional<String> baselineInputsIdentity() { return baselineIdentity; }
        };
    }

    // ── meeting() — contractual ────────────────────────────────────

    @Test
    @DisplayName("meeting() returns PASS when observed meets threshold")
    void contractualPass() {
        PassRate<String> criterion = PassRate.meeting(ThresholdOrigin.SLA, 0.9);

        CriterionResult result = criterion.evaluate(ctx(summary(95, 5), Optional.empty()));

        assertThat(result.verdict()).isEqualTo(Verdict.PASS);
        assertThat(result.criterionName()).isEqualTo("bernoulli-pass-rate");
        assertThat(result.detail()).containsEntry("origin", "SLA");
        assertThat(result.detail()).containsEntry("threshold", 0.9);
        assertThat((double) result.detail().get("observed")).isEqualTo(0.95);
        assertThat(result.detail()).containsEntry("total", 100);
    }

    @Test
    @DisplayName("meeting() returns FAIL when observed below threshold")
    void contractualFail() {
        PassRate<String> criterion = PassRate.meeting(ThresholdOrigin.SLA, 0.9);

        CriterionResult result = criterion.evaluate(ctx(summary(80, 20), Optional.empty()));

        assertThat(result.verdict()).isEqualTo(Verdict.FAIL);
        assertThat((double) result.detail().get("observed")).isEqualTo(0.8);
    }

    @Test
    @DisplayName("meeting() rejects threshold outside [0, 1]")
    void meetingRejectsOutOfRange() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PassRate.meeting(ThresholdOrigin.SLA, -0.1));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PassRate.meeting(ThresholdOrigin.SLA, 1.1));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PassRate.meeting(ThresholdOrigin.SLA, Double.NaN));
    }

    @Test
    @DisplayName("meeting() rejects ThresholdOrigin.EMPIRICAL")
    void meetingRejectsEmpiricalOrigin() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PassRate.meeting(ThresholdOrigin.EMPIRICAL, 0.9))
                .withMessageContaining("empirical");
    }

    // ── empirical() — default resolution ────────────────────────────

    @Test
    @DisplayName("empirical() with no baseline returns INCONCLUSIVE with the "
            + "'no baseline available' discriminant on the detail map")
    void empiricalNoBaselineInconclusive() {
        PassRate<String> criterion = PassRate.empirical();

        CriterionResult result = criterion.evaluate(ctx(summary(90, 10), Optional.empty()));

        assertThat(result.verdict()).isEqualTo(Verdict.INCONCLUSIVE);
        // The diagnostic must explain what happened in domain language
        // (no orchestrator-internal requirement codes leaking into the
        // developer-facing message — see the project family's
        // requirement-ID convention in the orchestrator's CLAUDE.md).
        assertThat(result.explanation())
                .contains("no baseline was resolvable")
                .contains("Run a measure experiment under this configuration first")
                .doesNotContainPattern("\\b[A-Z]{2,3}\\d{2}\\b");
        assertThat(result.detail())
                .as("verdict-builder vocabulary discriminant")
                .containsEntry(
                        org.javai.punit.api.spec.InconclusiveReasons.DETAIL_KEY,
                        org.javai.punit.api.spec.InconclusiveReasons.NO_BASELINE_AVAILABLE);
    }

    @Test
    @DisplayName("empirical() PASS when observed pass rate clears the derived threshold")
    void empiricalWithBaselinePass() {
        PassRate<String> criterion = PassRate.empirical();
        PassRateStatistics baseline = new PassRateStatistics(0.88, 2000);

        // baseline rate 0.88, test n=1000, c=0.95 → derived threshold ≈ 0.862;
        // observed 0.95 ≥ 0.862 → PASS.
        CriterionResult pass = criterion.evaluate(ctx(summary(950, 50), Optional.of(baseline)));

        assertThat(pass.verdict()).isEqualTo(Verdict.PASS);
        assertThat(pass.detail()).containsEntry("origin", "EMPIRICAL");
        assertThat(pass.detail()).containsEntry("baselineSampleCount", 2000);
        assertThat((double) pass.detail().get("threshold")).isLessThan(0.88);
        assertThat((double) pass.detail().get("observed"))
                .isGreaterThanOrEqualTo((double) pass.detail().get("threshold"));
    }

    @Test
    @DisplayName("empirical() FAIL when observed pass rate dips below the derived threshold")
    void empiricalWithBaselineFail() {
        PassRate<String> criterion = PassRate.empirical();
        PassRateStatistics baseline = new PassRateStatistics(0.95, 2000);

        // baseline rate 0.95, test n=1000, c=0.95 → derived threshold ≈ 0.937;
        // observed 0.92 < 0.937 → FAIL.
        CriterionResult fail = criterion.evaluate(ctx(summary(920, 80), Optional.of(baseline)));

        assertThat(fail.verdict()).isEqualTo(Verdict.FAIL);
        assertThat((double) fail.detail().get("observed"))
                .isLessThan((double) fail.detail().get("threshold"));
    }

    @Test
    @DisplayName("empirical() smaller test n widens the interval and lowers the threshold")
    void empiricalSmallerTestSampleLowersThreshold() {
        PassRate<String> criterion = PassRate.empirical();
        PassRateStatistics baseline = new PassRateStatistics(0.88, 1000);

        // Companion §3.5 / §3.4: smaller n_test → wider Wilson interval → lower
        // threshold. The threshold compensates for sampling uncertainty in the
        // test, so a small-n test is permitted a more lenient bar.
        CriterionResult small = criterion.evaluate(ctx(summary(45, 5), Optional.of(baseline)));
        CriterionResult large = criterion.evaluate(ctx(summary(450, 50), Optional.of(baseline)));

        double smallThreshold = (double) small.detail().get("threshold");
        double largeThreshold = (double) large.detail().get("threshold");
        assertThat(smallThreshold).isLessThan(largeThreshold);
    }

    @Test
    @DisplayName("empirical() detail map carries confidence, threshold, and baseline rate")
    void empiricalDetailMapCarriesMethodologyFields() {
        PassRate<String> criterion = PassRate.empirical();
        PassRateStatistics baseline = new PassRateStatistics(0.88, 2000);

        CriterionResult result = criterion.evaluate(ctx(summary(950, 50), Optional.of(baseline)));

        assertThat(result.detail()).containsKeys(
                "confidence", "threshold", "baselineRate", "baselineSampleCount");
        assertThat(result.detail()).doesNotContainKey("wilsonLowerBound");
        assertThat((double) result.detail().get("confidence")).isEqualTo(0.95);
        assertThat((double) result.detail().get("baselineRate")).isEqualTo(0.88);
        // Derived threshold sits below the baseline rate (companion §3.4): we
        // are 95% confident the true rate is at least this low under sampling.
        assertThat((double) result.detail().get("threshold")).isLessThan(0.88);
    }

    @Test
    @DisplayName("empirical() explanation names the threshold and its baseline-rate provenance")
    void empiricalExplanationMentionsDerivedThreshold() {
        PassRate<String> criterion = PassRate.empirical();
        PassRateStatistics baseline = new PassRateStatistics(0.88, 2000);

        CriterionResult result = criterion.evaluate(ctx(summary(950, 50), Optional.of(baseline)));

        assertThat(result.explanation())
                .contains("threshold=")
                .contains("baseline rate")
                .contains("Wilson-95% lower");
    }

    @Test
    @DisplayName("atConfidence(higher) lowers the derived threshold — less willing to claim degradation")
    void higherConfidenceLowersThreshold() {
        PassRateStatistics baseline = new PassRateStatistics(0.88, 2000);
        PassRate<String> at95 = PassRate.<String>empirical().atConfidence(0.95);
        PassRate<String> at999 = PassRate.<String>empirical().atConfidence(0.999);

        CriterionResult r95 = at95.evaluate(ctx(summary(920, 80), Optional.of(baseline)));
        CriterionResult r999 = at999.evaluate(ctx(summary(920, 80), Optional.of(baseline)));

        double threshold95 = (double) r95.detail().get("threshold");
        double threshold999 = (double) r999.detail().get("threshold");
        assertThat(threshold999).isLessThan(threshold95);
    }

    // ── sample-size constraint (test_N ≤ baseline_N) ───────────────

    @Test
    @DisplayName("empirical() with test sample count > baseline returns INCONCLUSIVE")
    void empiricalRejectsTestLargerThanBaseline() {
        PassRate<String> criterion = PassRate.empirical();
        PassRateStatistics baseline = new PassRateStatistics(0.88, 100);

        // 200 test samples > 100 baseline samples
        CriterionResult result = criterion.evaluate(ctx(summary(180, 20), Optional.of(baseline)));

        assertThat(result.verdict()).isEqualTo(Verdict.INCONCLUSIVE);
        assertThat(result.explanation())
                .contains("test sample size (200)")
                .contains("baseline sample size (100)")
                .contains("at least as rigorous");
        assertThat(result.detail()).containsEntry("testSampleCount", 200);
        assertThat(result.detail()).containsEntry("baselineSampleCount", 100);
    }

    @Test
    @DisplayName("empirical() with test sample count == baseline proceeds to verdict")
    void empiricalAcceptsEqualSampleCount() {
        PassRate<String> criterion = PassRate.empirical();
        PassRateStatistics baseline = new PassRateStatistics(0.88, 1000);

        // Equal sample counts (1000 each); observed 0.95 well above baseline 0.88
        // → Wilson lower bound clears the threshold, sample-size rule allows
        // through, so PASS.
        CriterionResult result = criterion.evaluate(ctx(summary(950, 50), Optional.of(baseline)));

        assertThat(result.verdict()).isEqualTo(Verdict.PASS);
    }

    @Test
    @DisplayName("empirical() with test sample count < baseline proceeds to verdict")
    void empiricalAcceptsSmallerTestSampleCount() {
        PassRate<String> criterion = PassRate.empirical();
        PassRateStatistics baseline = new PassRateStatistics(0.88, 5000);

        // Test 1000 ≪ baseline 5000; observed 0.95 → Wilson lower clears threshold.
        CriterionResult result = criterion.evaluate(ctx(summary(950, 50), Optional.of(baseline)));

        assertThat(result.verdict()).isEqualTo(Verdict.PASS);
    }

    // ── inputs-identity check ───────────────────────────────────────

    @Test
    @DisplayName("empirical() with mismatched test/baseline inputs identity returns INCONCLUSIVE")
    void empiricalRejectsIdentityMismatch() {
        PassRate<String> criterion = PassRate.empirical();
        PassRateStatistics baseline = new PassRateStatistics(0.88, 1000);

        CriterionResult result = criterion.evaluate(ctx(
                summary(90, 10), Optional.of(baseline),
                "sha256:test-inputs-A",
                Optional.of("sha256:baseline-inputs-B")));

        assertThat(result.verdict()).isEqualTo(Verdict.INCONCLUSIVE);
        assertThat(result.explanation())
                .contains("inputs identity")
                .contains("re-run the baseline measure");
        assertThat(result.detail())
                .containsEntry("testInputsIdentity", "sha256:test-inputs-A")
                .containsEntry("baselineInputsIdentity", "sha256:baseline-inputs-B");
    }

    @Test
    @DisplayName("empirical() with matching test/baseline identity proceeds to verdict")
    void empiricalAcceptsMatchingIdentity() {
        PassRate<String> criterion = PassRate.empirical();
        PassRateStatistics baseline = new PassRateStatistics(0.88, 2000);

        // Sample size large enough that Wilson lower bound clears 0.88 threshold.
        CriterionResult result = criterion.evaluate(ctx(
                summary(950, 50), Optional.of(baseline),
                "sha256:matching-id",
                Optional.of("sha256:matching-id")));

        assertThat(result.verdict()).isEqualTo(Verdict.PASS);
    }

    @Test
    @DisplayName("empirical() identity-mismatch fires before sample-size — identity is the more fundamental violation")
    void identityMismatchPrecedesSampleSize() {
        PassRate<String> criterion = PassRate.empirical();
        PassRateStatistics baseline = new PassRateStatistics(0.88, 100);

        // Both rules would fire: 200 test samples > 100 baseline AND identities differ.
        CriterionResult result = criterion.evaluate(ctx(
                summary(180, 20), Optional.of(baseline),
                "sha256:test-id", Optional.of("sha256:baseline-id")));

        assertThat(result.explanation()).contains("inputs identity");
        assertThat(result.explanation()).doesNotContain("sample size");
    }

    @Test
    @DisplayName("contractual meeting() does not impose sample-size constraint — no baseline involved")
    void contractualIgnoresSampleSize() {
        PassRate<String> criterion = PassRate.meeting(ThresholdOrigin.SLA, 0.9);

        // Test has 10000 samples; no baseline, so the constraint doesn't apply.
        CriterionResult result = criterion.evaluate(ctx(summary(9500, 500), Optional.empty()));

        assertThat(result.verdict()).isEqualTo(Verdict.PASS);
    }

    // ── atConfidence() ───────────────────────────────────────────────

    @Test
    @DisplayName("atConfidence() returns a new criterion carrying the confidence")
    void atConfidenceRecordedInDetail() {
        PassRate<String> criterion = PassRate.<String>empirical().atConfidence(0.99);
        PassRateStatistics baseline = new PassRateStatistics(0.9, 1000);

        CriterionResult result = criterion.evaluate(ctx(summary(95, 5), Optional.of(baseline)));

        assertThat(result.detail()).containsEntry("confidence", 0.99);
    }

    @Test
    @DisplayName("atConfidence() default is 0.95 on the empirical variant")
    void atConfidenceDefaultEmpirical() {
        PassRate<String> criterion = PassRate.empirical();
        PassRateStatistics baseline = new PassRateStatistics(0.9, 1000);

        CriterionResult result = criterion.evaluate(ctx(summary(95, 5), Optional.of(baseline)));

        assertThat(result.detail()).containsEntry("confidence", 0.95);
    }

    @Test
    @DisplayName("atConfidence() rejects values outside (0, 1)")
    void atConfidenceRejectsOutOfRange() {
        PassRate<String> c = PassRate.empirical();
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> c.atConfidence(0.0));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> c.atConfidence(1.0));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> c.atConfidence(-0.5));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> c.atConfidence(Double.NaN));
    }

    // ── empiricalFrom() — pinned ─────────────────────────────────────

    @Test
    @DisplayName("empiricalFrom() exposes the supplier for framework routing")
    void empiricalFromExposesSupplier() {
        java.util.function.Supplier<Experiment> supplier = () -> null;

        PassRate<String> criterion = PassRate.empiricalFrom(supplier);

        assertThat(criterion.baselineSupplier()).contains(supplier);
    }

    @Test
    @DisplayName("empiricalFrom() rejects null supplier")
    void empiricalFromRejectsNull() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> PassRate.empiricalFrom(null));
    }

    @Test
    @DisplayName("non-empirical variants expose empty baselineSupplier")
    void nonEmpiricalHasEmptySupplier() {
        assertThat(PassRate.meeting(ThresholdOrigin.SLA, 0.9).baselineSupplier()).isEmpty();
        assertThat(PassRate.empirical().baselineSupplier()).isEmpty();
    }

    // ── zero samples ─────────────────────────────────────────────────

    @Test
    @DisplayName("zero samples → INCONCLUSIVE regardless of mode")
    void zeroSamplesInconclusive() {
        PassRate<String> c1 = PassRate.meeting(ThresholdOrigin.SLA, 0.9);
        PassRate<String> c2 = PassRate.empirical();

        var empty = summary(0, 0);
        assertThat(c1.evaluate(ctx(empty, Optional.empty())).verdict()).isEqualTo(Verdict.INCONCLUSIVE);
        assertThat(c2.evaluate(ctx(empty, Optional.of(new PassRateStatistics(0.9, 1000)))).verdict())
                .isEqualTo(Verdict.INCONCLUSIVE);
    }

    // ── plumbing ─────────────────────────────────────────────────────

    @Test
    @DisplayName("criterion exposes PerCriterionPassRateStatistics.class as its statistics type")
    void statisticsTypeIsPerCriterionPassRateStatistics() {
        PassRate<String> criterion = PassRate.empirical();
        assertThat(criterion.statisticsType()).isEqualTo(PerCriterionPassRateStatistics.class);
    }

    @Test
    @DisplayName("name() is 'bernoulli-pass-rate'")
    void name() {
        assertThat(PassRate.empirical().name()).isEqualTo("bernoulli-pass-rate");
    }
}
