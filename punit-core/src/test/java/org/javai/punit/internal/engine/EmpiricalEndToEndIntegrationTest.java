package org.javai.punit.internal.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.javai.outcome.Outcome;
import org.javai.punit.api.FactorBundle;
import org.javai.punit.api.Sampling;
import org.javai.punit.api.TokenTracker;
import org.javai.punit.api.ServiceContract;
import org.javai.punit.api.covariate.CovariateProfile;
import static org.javai.punit.api.criterion.Criteria.empirical;

import org.javai.punit.api.criterion.Criteria;
import org.javai.punit.api.spec.BaselineProvider;
import org.javai.punit.api.spec.BaselineStatistics;
import org.javai.punit.api.spec.PassRateStatistics;
import org.javai.punit.api.spec.PerCriterionPassRateStatistics;
import org.javai.punit.api.spec.ProbabilisticTest;
import org.javai.punit.api.spec.ProbabilisticTestResult;
import org.javai.punit.api.spec.Verdict;
import org.javai.punit.internal.engine.baseline.BaselineRecord;
import org.javai.punit.internal.engine.baseline.BaselineWriter;
import org.javai.punit.internal.engine.baseline.FactorsFingerprint;
import org.javai.punit.internal.engine.baseline.LatencyIndicator;
import org.javai.punit.internal.engine.baseline.YamlBaselineProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Empirical criterion end-to-end — Engine + YamlBaselineProvider + PassRate.empirical()")
class EmpiricalEndToEndIntegrationTest {

    record Factors(String model, double temperature) { }

    private static final Factors FACTORS = new Factors("gpt-4o", 0.0);
    private static final String USE_CASE_ID = "always-passes-use-case";

    static class AlwaysPassesServiceContract implements ServiceContract<Factors, Integer, Boolean> {
        @Override public Criteria<Boolean> criteria() {
            return empirical().passRate();
        }
        @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
            return Outcome.ok(true);
        }
        @Override public String id() { return USE_CASE_ID; }
    }

    private static Sampling<Factors, Integer, Boolean> sampling(int samples) {
        return Sampling.<Factors, Integer, Boolean>builder()
                .serviceContractFactory(f -> new AlwaysPassesServiceContract())
                .inputs(1, 2, 3)
                .samples(samples)
                .build();
    }

    private static ProbabilisticTest empiricalTest(Sampling<Factors, Integer, Boolean> sampling) {
        return ProbabilisticTest
                .testing(sampling, FACTORS)
                .build();
    }

    /**
     * Writes a baseline whose recorded inputs identity matches what
     * {@link #sampling(int)} produces — the in-process integrity
     * guarantee, restated cross-process. Tests that want to exercise
     * an identity mismatch use {@link #writeBaselineWithMismatchedIdentity}.
     */
    private static void writeBaselineWithPassRate(
            Path baselineDir, double passRate, int sampleCount) throws IOException {
        writeBaseline(baselineDir, passRate, sampleCount, sampling(1).inputsIdentity());
    }

    private static void writeBaselineWithMismatchedIdentity(
            Path baselineDir, double passRate, int sampleCount) throws IOException {
        writeBaseline(baselineDir, passRate, sampleCount, "sha256:other-input-population");
    }

    private static void writeBaseline(
            Path baselineDir, double passRate, int sampleCount,
            String inputsIdentity) throws IOException {
        String fingerprint = FactorsFingerprint.of(FactorBundle.of(FACTORS));
        BaselineRecord record = new BaselineRecord(
                USE_CASE_ID, "measureBaseline", fingerprint,
                inputsIdentity, sampleCount,
                Instant.parse("2026-04-26T15:30:00Z"),
                Map.<String, BaselineStatistics>of(
                        "bernoulli-pass-rate",
                        PerCriterionPassRateStatistics.of("contract", passRate, sampleCount)));
        new BaselineWriter().write(record, baselineDir);
    }

    @Test
    @DisplayName("with an on-disk baseline at p = 0.80, an always-passing test produces PASS")
    void empiricalProducesPassWhenObservedExceedsBaseline(@TempDir Path baselineDir)
            throws IOException {
        writeBaselineWithPassRate(baselineDir, 0.80, 1000);

        var engine = new Engine(new YamlBaselineProvider(baselineDir));
        var result = (ProbabilisticTestResult) engine.run(empiricalTest(sampling(20)));

        assertThat(result.verdict()).isEqualTo(Verdict.PASS);
        var detail = result.criterionResults().get(0).result().detail();
        assertThat(detail).containsEntry("origin", "EMPIRICAL");
        assertThat(detail).containsEntry("baselineSampleCount", 1000);
        assertThat(detail).containsEntry("baselineRate", 0.80);
        // Companion §3.4: derived threshold sits below the baseline rate.
        assertThat((double) detail.get("threshold")).isLessThan(0.80);
    }

    @Test
    @DisplayName("with no on-disk baseline, the empirical criterion still yields INCONCLUSIVE — same as the EMPTY provider")
    void empiricalYieldsInconclusiveWhenNoBaselineFile(@TempDir Path emptyDir) {
        var engine = new Engine(new YamlBaselineProvider(emptyDir));
        var result = (ProbabilisticTestResult) engine.run(empiricalTest(sampling(20)));

        assertThat(result.verdict()).isEqualTo(Verdict.INCONCLUSIVE);
        assertThat(result.criterionResults().get(0).result().explanation())
                .contains("baseline");
    }

    @Test
    @DisplayName("without an explicit provider, Engine uses BaselineProvider.EMPTY — empirical criteria yield INCONCLUSIVE")
    void emptyProviderIsTheDefault() {
        // No baselineDir at all — default Engine() falls back to BaselineProvider.EMPTY.
        var result = (ProbabilisticTestResult) new Engine().run(empiricalTest(sampling(20)));

        assertThat(result.verdict()).isEqualTo(Verdict.INCONCLUSIVE);
    }

    @Test
    @DisplayName("with a baseline whose sample count is below the test's, EmpiricalChecks rejects → INCONCLUSIVE")
    void empiricalRejectsWhenTestOutRiguresBaseline(@TempDir Path baselineDir) throws IOException {
        // Test asks for 1000 samples; baseline only has 100.
        writeBaselineWithPassRate(baselineDir, 0.50, 100);

        var engine = new Engine(new YamlBaselineProvider(baselineDir));
        var result = (ProbabilisticTestResult) engine.run(empiricalTest(sampling(1000)));

        assertThat(result.verdict()).isEqualTo(Verdict.INCONCLUSIVE);
        var detail = result.criterionResults().get(0).result().detail();
        assertThat(detail).containsEntry("testSampleCount", 1000);
        assertThat(detail).containsEntry("baselineSampleCount", 100);
    }

    @Test
    @DisplayName("with a baseline whose recorded inputs identity differs, EmpiricalChecks rejects → INCONCLUSIVE")
    void empiricalRejectsIdentityMismatch(@TempDir Path baselineDir) throws IOException {
        writeBaselineWithMismatchedIdentity(baselineDir, 0.80, 1000);

        var engine = new Engine(new YamlBaselineProvider(baselineDir));
        var result = (ProbabilisticTestResult) engine.run(empiricalTest(sampling(20)));

        assertThat(result.verdict()).isEqualTo(Verdict.INCONCLUSIVE);
        var detail = result.criterionResults().get(0).result().detail();
        assertThat(detail).containsKey("testInputsIdentity");
        assertThat(detail).containsEntry("baselineInputsIdentity", "sha256:other-input-population");
        assertThat(result.criterionResults().get(0).result().explanation())
                .contains("inputs identity")
                .contains("re-run the baseline measure");
    }

    /**
     * Writes a baseline with a validity window, measured at
     * {@code generatedAt}. Recorded inputs identity matches
     * {@link #sampling(int)} so the empirical comparison proceeds.
     */
    private static void writeBaselineWithWindow(
            Path baselineDir, double passRate, int sampleCount,
            int expiresInDays, Instant generatedAt) throws IOException {
        String fingerprint = FactorsFingerprint.of(FactorBundle.of(FACTORS));
        BaselineRecord record = new BaselineRecord(
                USE_CASE_ID, "measureBaseline", fingerprint,
                sampling(1).inputsIdentity(), sampleCount, generatedAt,
                Map.<String, BaselineStatistics>of(
                        "bernoulli-pass-rate",
                        PerCriterionPassRateStatistics.of("contract", passRate, sampleCount)),
                CovariateProfile.empty(), LatencyIndicator.empty(), expiresInDays);
        new BaselineWriter().write(record, baselineDir);
    }

    @Test
    @DisplayName("an expired baseline surfaces an expiration caveat on the verdict's warnings "
            + "without dismissing the PASS — the reader is told to pay attention, not to "
            + "discard the result")
    void expiredBaselineSurfacesCaveatButKeepsVerdict(@TempDir Path baselineDir)
            throws IOException {
        // Measured 400 days ago with a 30-day validity window → long expired.
        writeBaselineWithWindow(baselineDir, 0.80, 1000, 30,
                Instant.now().minus(Duration.ofDays(400)));

        var engine = new Engine(new YamlBaselineProvider(baselineDir));
        var result = (ProbabilisticTestResult) engine.run(empiricalTest(sampling(20)));

        // The expiration is a CAVEAT, not a dismissal: an always-passing test
        // against a baseline at p = 0.80 still PASSES.
        assertThat(result.verdict()).isEqualTo(Verdict.PASS);

        // …and the verdict carries the expiration as a warning the reader must heed,
        // naming the baseline and that it has expired.
        assertThat(result.warnings())
                .as("an expired baseline must surface as a verdict caveat the reader can act on")
                .anyMatch(w -> w.contains("expired") && w.contains(USE_CASE_ID));
    }

    @Test
    @DisplayName("a baseline comfortably within its validity window surfaces no expiration caveat")
    void freshBaselineSurfacesNoCaveat(@TempDir Path baselineDir) throws IOException {
        // Measured just now with a 30-day window → far from expiry.
        writeBaselineWithWindow(baselineDir, 0.80, 1000, 30, Instant.now());

        var engine = new Engine(new YamlBaselineProvider(baselineDir));
        var result = (ProbabilisticTestResult) engine.run(empiricalTest(sampling(20)));

        assertThat(result.verdict()).isEqualTo(Verdict.PASS);
        assertThat(result.warnings())
                .as("a baseline well within its window must not raise an expiration caveat")
                .noneMatch(w -> w.contains("expired") || w.contains("expires"));
    }

    @Test
    @DisplayName("under the fail-on-expired policy, an expired baseline fails the verdict "
            + "even when the statistics would pass — a stale baseline can't underwrite a PASS")
    void expiredBaselineFailsVerdictUnderFailPolicy(@TempDir Path baselineDir)
            throws IOException {
        writeBaselineWithWindow(baselineDir, 0.80, 1000, 30,
                Instant.now().minus(Duration.ofDays(400)));

        String previous = System.getProperty("punit.expiration.policy");
        System.setProperty("punit.expiration.policy", "FAIL");
        try {
            var engine = new Engine(new YamlBaselineProvider(baselineDir));
            var result = (ProbabilisticTestResult) engine.run(empiricalTest(sampling(20)));

            assertThat(result.verdict())
                    .as("FAIL policy must force an expired baseline's verdict to FAIL")
                    .isEqualTo(Verdict.FAIL);
            assertThat(result.warnings())
                    .anyMatch(w -> w.contains("expired") && w.contains(USE_CASE_ID));
            assertThat(result.warnings())
                    .anyMatch(w -> w.contains("policy=FAIL"));
        } finally {
            restoreProperty("punit.expiration.policy", previous);
        }
    }

    @Test
    @DisplayName("under the fail-on-expired policy, a baseline within its window is unaffected — "
            + "still PASS, no expiration caveat")
    void freshBaselineUnaffectedUnderFailPolicy(@TempDir Path baselineDir) throws IOException {
        writeBaselineWithWindow(baselineDir, 0.80, 1000, 30, Instant.now());

        String previous = System.getProperty("punit.expiration.policy");
        System.setProperty("punit.expiration.policy", "FAIL");
        try {
            var engine = new Engine(new YamlBaselineProvider(baselineDir));
            var result = (ProbabilisticTestResult) engine.run(empiricalTest(sampling(20)));

            assertThat(result.verdict()).isEqualTo(Verdict.PASS);
            assertThat(result.warnings())
                    .noneMatch(w -> w.contains("expired") || w.contains("policy=FAIL"));
        } finally {
            restoreProperty("punit.expiration.policy", previous);
        }
    }

    private static void restoreProperty(String key, String previous) {
        if (previous == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previous);
        }
    }

    @Test
    @DisplayName("the empty provider returns Optional.empty for any query")
    void baselineProviderEmptyContract() {
        var resolved = BaselineProvider.EMPTY.baselineFor(
                "any-id", FactorBundle.of(FACTORS),
                "any-criterion", PassRateStatistics.class);

        assertThat(resolved).isEmpty();
    }
}
