package org.mavai.punit.api.spec;

import static org.mavai.punit.api.criterion.Criteria.meeting;
import org.mavai.punit.api.criterion.Criteria;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.mavai.outcome.Outcome;
import org.mavai.punit.api.TestIntent;
import org.mavai.punit.api.ThresholdOrigin;
import org.mavai.punit.api.Contract;
import org.mavai.punit.api.FactorBundle;
import org.mavai.punit.api.LatencyResult;
import org.mavai.punit.api.LatencySpec;
import org.mavai.punit.api.PercentileKey;
import org.mavai.punit.api.TokenTracker;
import org.mavai.punit.api.ServiceContractOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PercentileLatency")
class PercentileLatencyTest {

    record Factors(String label) {}

    private static final Contract<Object, String> STUB_CONTRACT = new Contract<>() {
        @Override public Outcome<String> invoke(Object input, TokenTracker tracker) {
            return Outcome.ok("ok");
        }
        @Override public Criteria<String> criteria() {
            return meeting().<String>zeroFailures();
        }

    };

    private static SampleSummary<String> summary(LatencyResult latency, int successes, int failures) {
        int total = successes + failures;
        var outcomes = new java.util.ArrayList<ServiceContractOutcome<?, String>>(total);
        for (int i = 0; i < successes; i++) outcomes.add(stubOutcome(Outcome.ok("ok")));
        for (int i = 0; i < failures; i++) outcomes.add(stubOutcome(Outcome.fail("nope", "msg")));
        return new SampleSummary<>(
                outcomes,
                Duration.ofMillis(1),
                successes, failures, 0L, 0,
                latency,
                TerminationReason.COMPLETED,
                List.of(),
                java.util.Map.of(), LatencyResult.empty(), List.of());
    }

    /** A run whose passing samples took the given latencies, in milliseconds. */
    private static SampleSummary<String> latencies(long... ms) {
        var outcomes = new java.util.ArrayList<ServiceContractOutcome<?, String>>(ms.length);
        for (long m : ms) {
            outcomes.add(new ServiceContractOutcome<>(
                    Outcome.ok("ok"), STUB_CONTRACT, List.of(), 0L, Duration.ofMillis(m)));
        }
        return new SampleSummary<>(
                outcomes, Duration.ofMillis(1), ms.length, 0, 0L, 0,
                LatencyResult.empty(), TerminationReason.COMPLETED, List.of(),
                java.util.Map.of(), LatencyResult.empty(), List.of());
    }

    /** {@code count} latencies of {@code ms} each. */
    private static long[] repeat(int count, long ms) {
        long[] out = new long[count];
        java.util.Arrays.fill(out, ms);
        return out;
    }

    /** The latencies 1, 2, ..., n milliseconds. */
    private static long[] ascending(int n) {
        long[] out = new long[n];
        for (int i = 0; i < n; i++) {
            out[i] = i + 1;
        }
        return out;
    }

    private static long[] concat(long[] a, long[] b) {
        long[] out = java.util.Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static LatencyStatistics baselineOf(long[] ms) {
        return LatencyStatistics.of(LatencyResult.empty(), ms, ms.length);
    }

    private static ServiceContractOutcome<Object, String> stubOutcome(Outcome<String> result) {
        return new ServiceContractOutcome<>(
                result, STUB_CONTRACT,
                List.of(),
                0L, Duration.ZERO);
    }

    private static LatencyResult observed(long p50, long p90, long p95, long p99, int n) {
        return new LatencyResult(
                Duration.ofMillis(p50),
                Duration.ofMillis(p90),
                Duration.ofMillis(p95),
                Duration.ofMillis(p99),
                n);
    }

    private static final String DEFAULT_IDENTITY = "sha256:test-default-identity";

    private static <OT> EvaluationContext<OT, LatencyStatistics> ctx(
            SampleSummary<OT> summary, Optional<LatencyStatistics> baseline) {
        return ctx(summary, baseline, DEFAULT_IDENTITY,
                baseline.isPresent() ? Optional.of(DEFAULT_IDENTITY) : Optional.empty());
    }

    private static <OT> EvaluationContext<OT, LatencyStatistics> ctx(
            SampleSummary<OT> summary,
            Optional<LatencyStatistics> baseline,
            String testIdentity,
            Optional<String> baselineIdentity) {
        return ctx(summary, baseline, testIdentity, baselineIdentity, TestIntent.VERIFICATION);
    }

    private static <OT> EvaluationContext<OT, LatencyStatistics> ctx(
            SampleSummary<OT> summary,
            Optional<LatencyStatistics> baseline,
            String testIdentity,
            Optional<String> baselineIdentity,
            TestIntent intent) {
        return new EvaluationContext<OT, LatencyStatistics>() {
            @Override public SampleSummary<OT> summary() { return summary; }
            @Override public Optional<LatencyStatistics> baseline() { return baseline; }
            @Override public FactorBundle factors() { return FactorBundle.of(new Factors("x")); }
            @Override public String testInputsIdentity() { return testIdentity; }
            @Override public Optional<String> baselineInputsIdentity() { return baselineIdentity; }
            @Override public TestIntent intent() { return intent; }
        };
    }

    // ── meeting() — contractual ────────────────────────────────────

    @Test
    @DisplayName("meeting() passes when enough latencies are within each ceiling (latency/compliance-exact-binomial)")
    void contractualPass() {
        LatencySpec spec = LatencySpec.builder().p95Millis(500).p99Millis(1000).build();
        PercentileLatency<String> criterion = PercentileLatency.meeting(spec, ThresholdOrigin.SLA);

        CriterionResult result = criterion.evaluate(
                ctx(latencies(repeat(1000, 100)), Optional.empty()));

        assertThat(result.verdict()).isEqualTo(Verdict.PASS);
        assertThat(result.detail()).containsEntry("assertedPercentiles", "p95,p99");
        assertThat(result.detail()).containsEntry("origin", "SLA");
        assertThat(result.detail()).containsEntry("source", "explicit");
        assertThat(result.detail()).containsEntry("threshold.p95", 500L);
        assertThat(result.detail()).containsEntry("threshold.p99", 1000L);
        assertThat(result.detail()).containsEntry("withinThreshold.p95", 1000);
        assertThat(result.detail()).containsEntry("decisionRule.p95", "latency/compliance-exact-binomial");
        assertThat(result.detail()).containsEntry("verdict.p95", "PASS");
        assertThat(result.detail()).containsEntry("observed.p95", 100L);
    }

    @Test
    @DisplayName("meeting() fails a ceiling whose within-threshold count falls short of y_min, naming it")
    void contractualFailWithBreaches() {
        LatencySpec spec = LatencySpec.builder().p95Millis(500).p99Millis(1000).build();
        PercentileLatency<String> criterion = PercentileLatency.meeting(spec, ThresholdOrigin.SLA);

        // 900 of 1000 within 500 ms: far below y_min at p95; all within 1000 ms.
        CriterionResult result = criterion.evaluate(
                ctx(latencies(concat(repeat(900, 100), repeat(100, 600))), Optional.empty()));

        assertThat(result.verdict()).isEqualTo(Verdict.FAIL);
        assertThat(result.detail()).containsEntry("verdict.p95", "FAIL");
        assertThat(result.detail()).containsEntry("verdict.p99", "PASS");
        assertThat(result.detail()).containsEntry("breach.p95", 600L);
        assertThat(result.detail()).doesNotContainKey("breach.p99");
        assertThat(result.explanation()).contains("p95");
    }

    @Test
    @DisplayName("meeting() passes the raw comparison but fails the rule when compliance is not demonstrated")
    void contractualRawComparisonDecidesNothing() {
        LatencySpec spec = LatencySpec.builder().p95Millis(500).build();
        PercentileLatency<String> criterion = PercentileLatency.meeting(spec, ThresholdOrigin.SLA);

        // 96 of 100 within 500 ms: the observed p95 is within, y_min is 99.
        CriterionResult result = criterion.evaluate(
                ctx(latencies(concat(repeat(96, 400), repeat(4, 700))), Optional.empty()));

        assertThat(result.verdict()).isEqualTo(Verdict.FAIL);
        assertThat(result.detail()).containsEntry("rawPercentilePass.p95", true);
        assertThat(result.detail()).containsEntry("requiredWithin.p95", 99);
    }

    @Test
    @DisplayName("meeting() rejects a LatencySpec with no asserted percentiles")
    void meetingRejectsEmptyLatencySpec() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PercentileLatency.meeting(LatencySpec.disabled(), ThresholdOrigin.SLA))
                .withMessageContaining("at least one percentile");
    }

    @Test
    @DisplayName("meeting() rejects ThresholdOrigin.EMPIRICAL")
    void meetingRejectsEmpiricalOrigin() {
        LatencySpec spec = LatencySpec.builder().p95Millis(500).build();
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PercentileLatency.meeting(spec, ThresholdOrigin.EMPIRICAL))
                .withMessageContaining("empirical");
    }

    // ── empirical() ────────────────────────────────────────────────

    @Test
    @DisplayName("empirical() with no baseline returns INCONCLUSIVE")
    void empiricalNoBaselineInconclusive() {
        PercentileLatency<String> criterion = PercentileLatency.empirical(PercentileKey.P95, PercentileKey.P99);

        CriterionResult result = criterion.evaluate(
                ctx(summary(observed(100, 200, 400, 900, 1000), 1000, 0), Optional.empty()));

        assertThat(result.verdict()).isEqualTo(Verdict.INCONCLUSIVE);
        assertThat(result.detail()).containsEntry("assertedPercentiles", "p95,p99");
        // The diagnostic must explain what happened in domain language
        // (no orchestrator-internal requirement codes leaking into the
        // developer-facing message).
        assertThat(result.explanation())
                .contains("no baseline was resolvable")
                .contains("Run a measure experiment under this configuration first")
                .doesNotContainPattern("\\b[A-Z]{2,3}\\d{2}\\b");
    }

    @Test
    @DisplayName("empirical() decides each percentile by latency/precedence against the baseline")
    void empiricalWithBaseline() {
        PercentileLatency<String> criterion = PercentileLatency.empirical(PercentileKey.P95, PercentileKey.P99);
        LatencyStatistics baseline = baselineOf(ascending(2000));

        CriterionResult pass = criterion.evaluate(
                ctx(latencies(ascending(1000)), Optional.of(baseline)));
        CriterionResult fail = criterion.evaluate(
                ctx(latencies(repeat(1000, 3000)), Optional.of(baseline)));

        assertThat(pass.verdict()).isEqualTo(Verdict.PASS);
        assertThat(fail.verdict()).isEqualTo(Verdict.FAIL);
        assertThat(pass.detail()).containsEntry("origin", "EMPIRICAL");
        assertThat(pass.detail()).containsEntry("source", "baseline-derived");
        assertThat(pass.detail()).containsEntry("baselineSampleCount", 2000);
        assertThat(pass.detail()).containsEntry("decisionRule.p95", "latency/precedence");
        assertThat(pass.detail()).containsKey("threshold.p95.rank");
        assertThat(fail.detail()).containsEntry("breach.p95", 3000L);
        assertThat(fail.detail()).containsEntry("breach.p99", 3000L);
    }

    // ── saturation (companion §12.4.2 / §12.5.2.1) ─────────────────

    @Test
    @DisplayName("no baseline rank achieves alpha → INCONCLUSIVE with saturated.<p>=true and no threshold")
    void verificationOnSaturationIsInconclusive() {
        // p95 against a baseline of 100 latencies for a test of 15: the
        // test's p95 is its maximum, and breach(100) = 15/115 > 0.05.
        PercentileLatency<String> criterion = PercentileLatency.empirical(PercentileKey.P95);

        CriterionResult result = criterion.evaluate(
                ctx(latencies(ascending(15)), Optional.of(baselineOf(ascending(100))),
                        DEFAULT_IDENTITY, Optional.of(DEFAULT_IDENTITY), TestIntent.VERIFICATION));

        assertThat(result.verdict()).isEqualTo(Verdict.INCONCLUSIVE);
        assertThat(result.detail()).containsEntry("saturated.p95", true);
        assertThat(result.detail()).doesNotContainKey("threshold.p95");
        assertThat(result.explanation()).contains("p95").contains("undecided");
    }

    @Test
    @DisplayName("saturation is INCONCLUSIVE under SMOKE too — no rank is clamped to manufacture a threshold")
    void smokeOnSaturationIsInconclusive() {
        PercentileLatency<String> criterion = PercentileLatency.empirical(PercentileKey.P95);

        CriterionResult result = criterion.evaluate(
                ctx(latencies(ascending(15)), Optional.of(baselineOf(ascending(100))),
                        DEFAULT_IDENTITY, Optional.of(DEFAULT_IDENTITY), TestIntent.SMOKE));

        assertThat(result.verdict()).isEqualTo(Verdict.INCONCLUSIVE);
        assertThat(result.detail()).containsEntry("saturated.p95", true);
        assertThat(result.detail()).containsEntry("indicative.p95", true);
    }

    @Test
    @DisplayName("below the non-degeneracy minimum under VERIFICATION → INCONCLUSIVE; the latencies are those passing")
    void belowNonDegeneracyMinimumIsInconclusive() {
        PercentileLatency<String> criterion = PercentileLatency.empirical(PercentileKey.P99);

        CriterionResult result = criterion.evaluate(
                ctx(latencies(ascending(99)), Optional.of(baselineOf(ascending(2000)))));

        assertThat(result.verdict()).isEqualTo(Verdict.INCONCLUSIVE);
        assertThat(result.detail()).containsEntry("successfulSamples", 99);
    }

    // ── sample-size constraint (test_N ≤ baseline_N) ───────────────

    @Test
    @DisplayName("a test planned larger than its latency baseline run is refused before the run")
    void empiricalRefusesTestLargerThanBaseline() {
        PercentileLatency<String> criterion = PercentileLatency.empirical(PercentileKey.P95);
        LatencyStatistics baseline = new LatencyStatistics(
                LatencyResult.empty(), ascending(90), 90, 100);

        assertThat(criterion.configurationRefusals(check(101, TestIntent.SMOKE, Optional.of(baseline))))
                .extracting(ConfigurationRefusal::code)
                .containsExactly(org.mavai.punit.statistics.ConfigurationError.TEST_LARGER_THAN_BASELINE);
        // The successful-latency counts are never compared: 100 ≤ N_b.
        assertThat(criterion.configurationRefusals(check(100, TestIntent.VERIFICATION, Optional.of(baseline))))
                .isEmpty();
    }

    @Test
    @DisplayName("an explicit ceiling no planned sample count can demonstrate is refused under VERIFICATION only")
    void explicitCeilingInfeasibleUnderVerification() {
        PercentileLatency<String> criterion = PercentileLatency.meeting(
                LatencySpec.builder().p95Millis(500).build(), ThresholdOrigin.SLA);

        assertThat(criterion.configurationRefusals(check(58, TestIntent.VERIFICATION, Optional.empty())))
                .extracting(ConfigurationRefusal::code)
                .containsExactly(org.mavai.punit.statistics.ConfigurationError.COMPLIANCE_INFEASIBLE);
        assertThat(criterion.configurationRefusals(check(59, TestIntent.VERIFICATION, Optional.empty())))
                .isEmpty();
        assertThat(criterion.configurationRefusals(check(58, TestIntent.SMOKE, Optional.empty())))
                .isEmpty();
    }

    private static ConfigurationCheck<LatencyStatistics> check(
            int planned, TestIntent intent, Optional<LatencyStatistics> baseline) {
        return new ConfigurationCheck<>() {
            @Override public int plannedSamples() { return planned; }
            @Override public TestIntent intent() { return intent; }
            @Override public java.util.Map<String, org.mavai.punit.api.criterion.CriterionPosture>
                    criterionPostures() { return java.util.Map.of(); }
            @Override public Optional<LatencyStatistics> baseline() { return baseline; }
        };
    }

    @Test
    @DisplayName("empirical() with a test as large as the baseline proceeds to a verdict")
    void empiricalAcceptsSmallerOrEqualTestSampleCount() {
        PercentileLatency<String> criterion = PercentileLatency.empirical(PercentileKey.P95);
        LatencyStatistics baseline = baselineOf(ascending(1000));

        CriterionResult equal = criterion.evaluate(
                ctx(latencies(ascending(1000)), Optional.of(baseline)));
        CriterionResult smaller = criterion.evaluate(
                ctx(latencies(ascending(500)), Optional.of(baseline)));

        assertThat(equal.verdict()).isEqualTo(Verdict.PASS);
        assertThat(smaller.verdict()).isEqualTo(Verdict.PASS);
    }

    // ── inputs-identity check ───────────────────────────────────────

    @Test
    @DisplayName("empirical() with mismatched test/baseline inputs identity returns INCONCLUSIVE")
    void empiricalRejectsIdentityMismatch() {
        PercentileLatency<String> criterion = PercentileLatency.empirical(PercentileKey.P95);
        LatencyStatistics baseline = LatencyStatistics.fromPercentiles(observed(100, 200, 500, 1000, 1000), 1000);

        CriterionResult result = criterion.evaluate(ctx(
                summary(observed(80, 180, 480, 950, 200), 200, 0), Optional.of(baseline),
                "sha256:test-id", Optional.of("sha256:baseline-id")));

        assertThat(result.verdict()).isEqualTo(Verdict.INCONCLUSIVE);
        assertThat(result.explanation()).contains("inputs identity");
        assertThat(result.detail())
                .containsEntry("testInputsIdentity", "sha256:test-id")
                .containsEntry("baselineInputsIdentity", "sha256:baseline-id")
                .containsEntry("assertedPercentiles", "p95");
    }

    @Test
    @DisplayName("empirical() with matching test/baseline identity proceeds to verdict")
    void empiricalAcceptsMatchingIdentity() {
        PercentileLatency<String> criterion = PercentileLatency.empirical(PercentileKey.P95);

        CriterionResult result = criterion.evaluate(ctx(
                latencies(ascending(200)), Optional.of(baselineOf(ascending(1000))),
                "sha256:matching", Optional.of("sha256:matching")));

        assertThat(result.verdict()).isEqualTo(Verdict.PASS);
    }

    @Test
    @DisplayName("contractual meeting() has no baseline and no upper test size")
    void contractualLatencyIgnoresSampleSize() {
        PercentileLatency<String> criterion = PercentileLatency.meeting(
                LatencySpec.builder().p95Millis(500).build(), ThresholdOrigin.SLA);

        CriterionResult result = criterion.evaluate(
                ctx(latencies(repeat(10000, 100)), Optional.empty()));

        assertThat(result.verdict()).isEqualTo(Verdict.PASS);
    }

    @Test
    @DisplayName("empirical() deduplicates repeated keys")
    void empiricalDeduplicates() {
        PercentileLatency<String> criterion = PercentileLatency.empirical(
                PercentileKey.P95, PercentileKey.P95, PercentileKey.P99);
        LatencyStatistics baseline = LatencyStatistics.fromPercentiles(observed(100, 200, 500, 1000, 2000), 2000);

        CriterionResult result = criterion.evaluate(
                ctx(summary(observed(80, 180, 480, 950, 1000), 1000, 0), Optional.of(baseline)));

        assertThat(result.detail()).containsEntry("assertedPercentiles", "p95,p99");
    }

    @Test
    @DisplayName("empirical() rejects null first key")
    void empiricalRejectsNullFirst() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> PercentileLatency.empirical(null));
    }

    // ── empiricalFrom() — pinned ───────────────────────────────────

    @Test
    @DisplayName("empiricalFrom() exposes the supplier for framework routing")
    void empiricalFromExposesSupplier() {
        java.util.function.Supplier<Experiment> supplier = () -> null;

        PercentileLatency<String> criterion = PercentileLatency.empiricalFrom(
                supplier, PercentileKey.P95);

        assertThat(criterion.baselineSupplier()).contains(supplier);
    }

    @Test
    @DisplayName("empiricalFrom() rejects null supplier")
    void empiricalFromRejectsNullSupplier() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> PercentileLatency.empiricalFrom(null, PercentileKey.P95));
    }

    @Test
    @DisplayName("non-pinned variants expose empty baselineSupplier")
    void nonPinnedHasEmptySupplier() {
        LatencySpec spec = LatencySpec.builder().p95Millis(500).build();
        assertThat(PercentileLatency.meeting(spec, ThresholdOrigin.SLA).baselineSupplier()).isEmpty();
        assertThat(PercentileLatency.empirical(PercentileKey.P95).baselineSupplier()).isEmpty();
    }

    // ── zero samples ───────────────────────────────────────────────

    @Test
    @DisplayName("zero samples → INCONCLUSIVE regardless of mode")
    void zeroSamplesInconclusive() {
        LatencySpec spec = LatencySpec.builder().p95Millis(500).build();
        PercentileLatency<String> c1 = PercentileLatency.meeting(spec, ThresholdOrigin.SLA);
        PercentileLatency<String> c2 = PercentileLatency.empirical(PercentileKey.P95);

        assertThat(c1.evaluate(ctx(summary(LatencyResult.empty(), 0, 0), Optional.empty())).verdict())
                .isEqualTo(Verdict.INCONCLUSIVE);
        assertThat(c2.evaluate(ctx(summary(LatencyResult.empty(), 0, 0),
                Optional.of(LatencyStatistics.fromPercentiles(observed(100, 200, 500, 1000, 2000), 2000)))).verdict())
                .isEqualTo(Verdict.INCONCLUSIVE);
    }

    // ── plumbing ─────────────────────────────────────────────────────

    @Test
    @DisplayName("criterion exposes LatencyStatistics.class as its statistics type")
    void statisticsTypeIsLatencyStatistics() {
        PercentileLatency<String> criterion = PercentileLatency.empirical(PercentileKey.P95);
        assertThat(criterion.statisticsType()).isEqualTo(LatencyStatistics.class);
    }

    @Test
    @DisplayName("name() is 'percentile-latency'")
    void name() {
        assertThat(PercentileLatency.empirical(PercentileKey.P95).name()).isEqualTo("percentile-latency");
    }
}
