package org.mavai.punit.internal.engine.baseline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import org.mavai.outcome.Outcome;
import org.mavai.punit.api.FactorBundle;
import org.mavai.punit.api.Sampling;
import org.mavai.punit.api.TokenTracker;
import org.mavai.punit.api.ServiceContract;
import org.mavai.punit.api.ThresholdOrigin;
import org.mavai.punit.api.criterion.Criteria;
import static org.mavai.punit.api.criterion.Criteria.empirical;
import static org.mavai.punit.api.criterion.Criteria.meeting;
import org.mavai.punit.api.spec.BaselineStatistics;
import org.mavai.punit.api.spec.PerCriterionPassRateStatistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("SampleSizeResolver — silent uplift from contract postures")
class SampleSizeResolverTest {

    record Factors(String label) { }

    private static final Factors FACTORS = new Factors("m");
    private static final String CONTRACT_ID = "echo-contract";

    private static Path writeBaseline(Path dir, double rate, int n) throws IOException {
        BaselineRecord record = new BaselineRecord(
                CONTRACT_ID, "measure", FactorsFingerprint.of(FactorBundle.of(FACTORS)),
                "sha256:any", n, Instant.parse("2026-05-16T00:00:00Z"),
                Map.<String, BaselineStatistics>of(
                        "bernoulli-pass-rate",
                        PerCriterionPassRateStatistics.of("the-criterion", rate, n)));
        new BaselineWriter().write(record, dir);
        return dir;
    }

    private static ServiceContract<Factors, String, String> contractWithConfidenceFirst(
            double mde, double power) {
        return new ServiceContract<>() {
            @Override public String id() { return CONTRACT_ID; }
            @Override public Outcome<String> invoke(String input, TokenTracker t) {
                return Outcome.ok(input);
            }
            @Override public Criteria<String> criteria() {
                return empirical().<String>passRate()
                        .name("the-criterion")
                        .detectingMde(mde)
                        .atPower(power)
                        .satisfies("always", v -> Outcome.ok());
            }
        };
    }

    private static ServiceContract<Factors, String, String> contractNoConfidenceFirst() {
        return new ServiceContract<>() {
            @Override public String id() { return CONTRACT_ID; }
            @Override public Outcome<String> invoke(String input, TokenTracker t) {
                return Outcome.ok(input);
            }
            @Override public Criteria<String> criteria() {
                return meeting().<String>passRate(0.90)
                        .name("threshold-criterion")
                        .satisfies("always", v -> Outcome.ok());
            }
        };
    }

    @Test
    @DisplayName("uplifts the declared count when a confidence-first criterion demands more")
    void upliftsWhenConfidenceFirstDemandsMore(@TempDir Path dir) throws IOException {
        writeBaseline(dir, 0.90, 1000);
        var provider = new org.mavai.punit.internal.engine.baseline.YamlBaselineProvider(dir);

        // MDE 0.05, power 0.80, rate 0.90 → ~470 samples per the
        // SampleSizeCalculator; declare only 100.
        var resolution = SampleSizeResolver.resolve(
                contractWithConfidenceFirst(0.05, 0.80),
                FactorBundle.of(FACTORS),
                provider,
                100);

        assertThat(resolution.declared()).isEqualTo(100);
        assertThat(resolution.effective()).isGreaterThan(100);
        assertThat(resolution.drivenBy()).contains("the-criterion");
        assertThat(resolution.wasUplifted()).isTrue();
    }

    @Test
    @DisplayName("keeps the declared count when it already meets the criterion's demand")
    void noUpliftWhenDeclaredAlreadyMeetsDemand(@TempDir Path dir) throws IOException {
        writeBaseline(dir, 0.90, 1000);
        var provider = new org.mavai.punit.internal.engine.baseline.YamlBaselineProvider(dir);

        var resolution = SampleSizeResolver.resolve(
                contractWithConfidenceFirst(0.05, 0.80),
                FactorBundle.of(FACTORS),
                provider,
                10000);

        assertThat(resolution.declared()).isEqualTo(10000);
        assertThat(resolution.effective()).isEqualTo(10000);
        assertThat(resolution.drivenBy()).isEmpty();
        assertThat(resolution.wasUplifted()).isFalse();
    }

    @Test
    @DisplayName("no uplift when the contract has no confidence-first criterion")
    void noUpliftForNonConfidenceFirstContract(@TempDir Path dir) throws IOException {
        writeBaseline(dir, 0.90, 1000);
        var provider = new org.mavai.punit.internal.engine.baseline.YamlBaselineProvider(dir);

        var resolution = SampleSizeResolver.resolve(
                contractNoConfidenceFirst(),
                FactorBundle.of(FACTORS),
                provider,
                100);

        assertThat(resolution.declared()).isEqualTo(100);
        assertThat(resolution.effective()).isEqualTo(100);
        assertThat(resolution.drivenBy()).isEmpty();
    }

    @Test
    @DisplayName("no uplift when the baseline is missing — verdict-path INCONCLUSIVE will handle it")
    void noUpliftWhenBaselineAbsent(@TempDir Path emptyDir) {
        var provider = new org.mavai.punit.internal.engine.baseline.YamlBaselineProvider(emptyDir);

        var resolution = SampleSizeResolver.resolve(
                contractWithConfidenceFirst(0.05, 0.80),
                FactorBundle.of(FACTORS),
                provider,
                100);

        assertThat(resolution.declared()).isEqualTo(100);
        assertThat(resolution.effective()).isEqualTo(100);
        assertThat(resolution.drivenBy()).isEmpty();
    }

    private static ServiceContract<Factors, String, String> contractWithTolerance(
            double tolerated) {
        return new ServiceContract<>() {
            @Override public String id() { return CONTRACT_ID; }
            @Override public Outcome<String> invoke(String input, TokenTracker t) {
                return Outcome.ok(input);
            }
            @Override public Criteria<String> criteria() {
                return empirical().<String>passRate()
                        .name("the-criterion")
                        .tolerating(tolerated)
                        .satisfies("always", v -> Outcome.ok());
            }
        };
    }

    @Test
    @DisplayName("risk-driven: a declared tolerance prices the count self-consistently and uplifts")
    void riskDrivenTolerancePricesTheCount(@TempDir Path dir) throws IOException {
        writeBaseline(dir, 0.96, 1000);
        var provider = new org.mavai.punit.internal.engine.baseline.YamlBaselineProvider(dir);

        var resolution = SampleSizeResolver.resolve(
                contractWithTolerance(0.93),
                FactorBundle.of(FACTORS),
                provider,
                100);

        // The oracle-locked pricing for (0.96, 0.93, 0.95, 0.80).
        int expected = new org.mavai.punit.statistics.RiskDrivenSizingCalculator()
                .requiredSamples(0.96, 0.93, 0.95, 0.80);
        assertThat(resolution.effective()).isEqualTo(expected);
        assertThat(resolution.drivenBy()).contains("the-criterion");
        assertThat(resolution.wasUplifted()).isTrue();
    }

    @Test
    @DisplayName("risk-driven: over-reach is refused naming the criterion and the re-measure remedy")
    void riskDrivenOverReachIsRefused(@TempDir Path dir) throws IOException {
        writeBaseline(dir, 0.96, 1000);
        var provider = new org.mavai.punit.internal.engine.baseline.YamlBaselineProvider(dir);

        assertThatThrownBy(() -> SampleSizeResolver.resolve(
                contractWithTolerance(0.97),
                FactorBundle.of(FACTORS),
                provider,
                100))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("criterion 'the-criterion'")
                .hasMessageContaining("re-measure the baseline");
    }

    @Test
    @DisplayName("risk-driven: a requirement the baseline cannot ground is refused in sizing terms")
    void riskDrivenRequirementBeyondBaselineIsRefused(@TempDir Path dir) throws IOException {
        // (0.96, 0.93) prices to ~hundreds of samples; a 200-sample
        // baseline cannot ground that test.
        writeBaseline(dir, 0.96, 200);
        var provider = new org.mavai.punit.internal.engine.baseline.YamlBaselineProvider(dir);

        assertThatThrownBy(() -> SampleSizeResolver.resolve(
                contractWithTolerance(0.93),
                FactorBundle.of(FACTORS),
                provider,
                100))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requires")
                .hasMessageContaining("measured over only 200")
                .hasMessageContaining("Re-measure the baseline with at least");
    }

    @Test
    @DisplayName("risk-driven: the declared count stays a floor when it exceeds the requirement")
    void riskDrivenDeclaredCountRemainsAFloor(@TempDir Path dir) throws IOException {
        writeBaseline(dir, 0.96, 1000);
        var provider = new org.mavai.punit.internal.engine.baseline.YamlBaselineProvider(dir);

        var resolution = SampleSizeResolver.resolve(
                contractWithTolerance(0.85),
                FactorBundle.of(FACTORS),
                provider,
                900);

        assertThat(resolution.effective()).isEqualTo(900);
        assertThat(resolution.wasUplifted()).isFalse();
    }

    @Test
    @DisplayName("multi-criterion: max across confidence-first criteria wins; drivenBy names that criterion")
    void multiCriterionMaxWins(@TempDir Path dir) throws IOException {
        // Two-criterion baseline: 'loose' at 0.95 (easy), 'tight' at 0.95.
        BaselineRecord record = new BaselineRecord(
                CONTRACT_ID, "measure", FactorsFingerprint.of(FactorBundle.of(FACTORS)),
                "sha256:any", 1000, Instant.parse("2026-05-16T00:00:00Z"),
                Map.<String, BaselineStatistics>of(
                        "bernoulli-pass-rate",
                        new PerCriterionPassRateStatistics(Map.of(
                                "loose", new org.mavai.punit.api.spec.PassRateStatistics(0.95, 1000),
                                "tight", new org.mavai.punit.api.spec.PassRateStatistics(0.95, 1000)))));
        new BaselineWriter().write(record, dir);
        var provider = new org.mavai.punit.internal.engine.baseline.YamlBaselineProvider(dir);

        ServiceContract<Factors, String, String> contract = new ServiceContract<>() {
            @Override public String id() { return CONTRACT_ID; }
            @Override public Outcome<String> invoke(String input, TokenTracker t) {
                return Outcome.ok(input);
            }
            @Override public Criteria<String> criteria() {
                return Criteria.of(
                        empirical().<String>passRate()
                                .name("loose")
                                .detectingMde(0.10).atPower(0.50)
                                .satisfies("always", v -> Outcome.ok()),
                        empirical().<String>passRate()
                                .name("tight")
                                .detectingMde(0.02).atPower(0.95)
                                .satisfies("always", v -> Outcome.ok()));
            }
        };

        var resolution = SampleSizeResolver.resolve(
                contract,
                FactorBundle.of(FACTORS),
                provider,
                10);

        // 'tight' (small MDE + high power) needs many more samples.
        assertThat(resolution.drivenBy()).contains("tight");
        assertThat(resolution.effective()).isGreaterThan(100);
    }
}
