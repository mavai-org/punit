package org.javai.punit.internal.engine.baseline;

import static org.javai.punit.api.criterion.Criteria.meeting;
import org.javai.punit.api.criterion.Criteria;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.javai.outcome.Outcome;
import org.javai.punit.api.covariate.CovariateCategory;
import org.javai.punit.api.FactorBundle;
import org.javai.punit.api.Sampling;
import org.javai.punit.api.TokenTracker;
import org.javai.punit.api.ServiceContract;
import org.javai.punit.api.covariate.Covariate;
import org.javai.punit.api.covariate.CovariateProfile;
import org.javai.punit.api.spec.BaselineStatistics;
import org.javai.punit.api.spec.Experiment;
import org.javai.punit.api.spec.PerCriterionPassRateStatistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("PowerAnalysis.sampleSize")
class PowerAnalysisTest {

    record Factors(String label) { }

    private static final Factors FACTORS = new Factors("m");
    private static final String USE_CASE_ID = "echo-use-case";

    private static final ServiceContract<Factors, String, String> ECHO = new ServiceContract<>() {
        @Override public Outcome<String> invoke(String input, TokenTracker tracker) {
            return Outcome.ok(input);
        }
        @Override public Criteria<String> criteria() {
            return meeting().<String>zeroFailures();
        }

        @Override public String id() { return USE_CASE_ID; }
    };

    private static Supplier<Experiment> baseline() {
        return () -> {
            Sampling<Factors, String, String> sampling = Sampling
                    .<Factors, String, String>builder()
                    .serviceContractFactory(f -> ECHO)
                    .inputs("a")
                    .samples(100)
                    .build();
            return Experiment.measuring(sampling, FACTORS).build();
        };
    }

    /**
     * Writes a baseline file matching {@link #baseline()}'s identity
     * to {@code dir} so the resolver can find it; returns {@code dir}
     * for chaining.
     */
    private static Path writeBaselineWithRate(Path dir, double passRate, int sampleCount)
            throws IOException {
        String fingerprint = FactorsFingerprint.of(FactorBundle.of(FACTORS));
        BaselineRecord record = new BaselineRecord(
                USE_CASE_ID, "measureBaseline", fingerprint,
                "sha256:any", sampleCount, Instant.parse("2026-04-26T15:30:00Z"),
                Map.<String, BaselineStatistics>of(
                        "bernoulli-pass-rate",
                        PerCriterionPassRateStatistics.of("contract", passRate, sampleCount)));
        new BaselineWriter().write(record, dir);
        return dir;
    }

    @Test
    @DisplayName("returns a positive integer when a matching baseline exists")
    void returnsPositiveInteger(@TempDir Path dir) throws IOException {
        writeBaselineWithRate(dir, 0.90, 1000);

        int n = PowerAnalysis.sampleSize(dir, baseline(), 0.02, 0.80);

        assertThat(n).isPositive();
    }

    @Test
    @DisplayName("baseline rate drives the sample-size estimate — different rates produce different n")
    void differentBaselineRatesProduceDifferentN(@TempDir Path dirA, @TempDir Path dirB)
            throws IOException {
        // p=0.5 maximises p(1-p), so it requires the largest n. p=0.9 is much
        // less variable and so requires far fewer samples for the same MDE.
        writeBaselineWithRate(dirA, 0.50, 1000);
        writeBaselineWithRate(dirB, 0.90, 1000);

        int nAt50 = PowerAnalysis.sampleSize(dirA, baseline(), 0.05, 0.80);
        int nAt90 = PowerAnalysis.sampleSize(dirB, baseline(), 0.05, 0.80);

        assertThat(nAt50).isGreaterThan(nAt90);
    }

    @Test
    @DisplayName("required n shrinks as MDE grows (easier-to-detect effect needs fewer samples)")
    void nShrinksAsMdeGrows(@TempDir Path dir) throws IOException {
        writeBaselineWithRate(dir, 0.80, 1000);

        int small = PowerAnalysis.sampleSize(dir, baseline(), 0.01, 0.80);
        int medium = PowerAnalysis.sampleSize(dir, baseline(), 0.05, 0.80);
        int large = PowerAnalysis.sampleSize(dir, baseline(), 0.10, 0.80);
        assertThat(small).isGreaterThan(medium).isGreaterThan(large);
    }

    @Test
    @DisplayName("required n grows as power grows (higher confidence in detection needs more samples)")
    void nGrowsAsPowerGrows(@TempDir Path dir) throws IOException {
        writeBaselineWithRate(dir, 0.80, 1000);

        int low = PowerAnalysis.sampleSize(dir, baseline(), 0.05, 0.50);
        int med = PowerAnalysis.sampleSize(dir, baseline(), 0.05, 0.80);
        int high = PowerAnalysis.sampleSize(dir, baseline(), 0.05, 0.95);
        assertThat(low).isLessThan(med).isLessThan(high);
    }

    @Test
    @DisplayName("throws IllegalStateException when no baseline file matches")
    void noBaselineThrows(@TempDir Path emptyDir) {
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> PowerAnalysis.sampleSize(emptyDir, baseline(), 0.05, 0.80))
                .withMessageContaining(USE_CASE_ID)
                .withMessageContaining("run the baseline measure");
    }

    @Test
    @DisplayName("throws IllegalArgumentException when supplier yields a non-MEASURE Experiment")
    void rejectsNonMeasure(@TempDir Path dir) throws IOException {
        writeBaselineWithRate(dir, 0.80, 1000);
        Supplier<Experiment> exploreSupplier = () -> {
            Sampling<Factors, String, String> sampling = Sampling
                    .<Factors, String, String>builder()
                    .serviceContractFactory(f -> ECHO)
                    .inputs("a")
                    .samples(100)
                    .build();
            return Experiment.exploring(sampling).grid(FACTORS).build();
        };

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PowerAnalysis.sampleSize(dir, exploreSupplier, 0.05, 0.80))
                .withMessageContaining("MEASURE")
                .withMessageContaining("EXPLORE");
    }

    @Test
    @DisplayName("rejects null baselineDir")
    void rejectsNullBaselineDir() {
        assertThatNullPointerException()
                .isThrownBy(() -> PowerAnalysis.sampleSize(null, baseline(), 0.05, 0.80));
    }

    @Test
    @DisplayName("rejects null baseline supplier")
    void rejectsNullSupplier(@TempDir Path dir) {
        assertThatNullPointerException()
                .isThrownBy(() -> PowerAnalysis.sampleSize(dir, null, 0.05, 0.80));
    }

    @Test
    @DisplayName("rejects a baseline supplier that returns null")
    void rejectsSupplierReturningNull(@TempDir Path dir) {
        assertThatNullPointerException()
                .isThrownBy(() -> PowerAnalysis.sampleSize(dir, () -> null, 0.05, 0.80));
    }

    @Test
    @DisplayName("rejects mde out of (0, 1)")
    void rejectsMdeOutOfRange(@TempDir Path dir) throws IOException {
        writeBaselineWithRate(dir, 0.80, 1000);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PowerAnalysis.sampleSize(dir, baseline(), 0.0, 0.80))
                .withMessageContaining("mde");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PowerAnalysis.sampleSize(dir, baseline(), 1.0, 0.80))
                .withMessageContaining("mde");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PowerAnalysis.sampleSize(dir, baseline(), -0.1, 0.80));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PowerAnalysis.sampleSize(dir, baseline(), Double.NaN, 0.80));
    }

    @Test
    @DisplayName("rejects power out of (0, 1)")
    void rejectsPowerOutOfRange(@TempDir Path dir) throws IOException {
        writeBaselineWithRate(dir, 0.80, 1000);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PowerAnalysis.sampleSize(dir, baseline(), 0.05, 0.0))
                .withMessageContaining("power");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PowerAnalysis.sampleSize(dir, baseline(), 0.05, 1.0))
                .withMessageContaining("power");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PowerAnalysis.sampleSize(dir, baseline(), 0.05, Double.NaN));
    }

    @Test
    @DisplayName("rejects an MDE that pushes the alternative-hypothesis rate to or below 0")
    void rejectsIncompatibleMde(@TempDir Path dir) throws IOException {
        writeBaselineWithRate(dir, 0.10, 1000);
        // rate=0.10 with mde=0.10 → p1 = 0; rate=0.10 with mde=0.50 → p1 < 0.
        // Both must be rejected; the diagnostic names the one-sided
        // alternative-hypothesis rate invariant rather than the obsolete
        // symmetric [rate-mde, rate+mde] interval.
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PowerAnalysis.sampleSize(dir, baseline(), 0.10, 0.80))
                .withMessageContaining("alternative-hypothesis rate");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PowerAnalysis.sampleSize(dir, baseline(), 0.50, 0.80));
    }

    @Test
    @DisplayName("accepts a perfect baseline (rate = 1.0) — the verdict path is one-sided")
    void acceptsPerfectBaseline(@TempDir Path dir) throws IOException {
        // The previous symmetric precondition rejected rate = 1.0
        // because rate + mde >= 1; the relaxed one-sided check admits
        // it. Downstream, σ0 = 0 collapses the sample-size formula to
        // n = (z_β · σ1)² / δ², which the calculator now accepts.
        writeBaselineWithRate(dir, 1.0, 1000);

        int n = PowerAnalysis.sampleSize(dir, baseline(), 0.05, 0.80);

        assertThat(n).isPositive();
    }

    @Test
    @DisplayName("accepts a baseline whose rate is strictly above 1 − mde (rate = 0.99, mde = 0.05)")
    void acceptsBaselineRateAboveOneMinusMde(@TempDir Path dir) throws IOException {
        // Previously rejected by the symmetric two-sided check; the
        // formula handles 0.99 cleanly (both σ0 and σ1 are positive).
        writeBaselineWithRate(dir, 0.99, 1000);

        int n = PowerAnalysis.sampleSize(dir, baseline(), 0.05, 0.80);

        assertThat(n).isPositive();
    }

    @Test
    @DisplayName("invokes the baseline supplier exactly once")
    void invokesSupplierOnce(@TempDir Path dir) throws IOException {
        writeBaselineWithRate(dir, 0.80, 1000);
        int[] callCount = {0};
        Supplier<Experiment> counting = () -> {
            callCount[0]++;
            return baseline().get();
        };
        PowerAnalysis.sampleSize(dir, counting, 0.05, 0.80);
        assertThat(callCount[0]).isEqualTo(1);
    }

    // ── Covariate-aware resolution ────────────────────────────────────

    private static final String COVARIATE_USE_CASE_ID = "covariate-echo";

    /**
     * Service contract declaring a single CONFIGURATION covariate whose
     * resolver returns a fixed value chosen at construction time.
     * Modelled on the punitexamples ShoppingBasketServiceContract pattern.
     */
    private static ServiceContract<Factors, String, String> covariateServiceContract(String resolvedRegion) {
        return new ServiceContract<>() {
            @Override public Outcome<String> invoke(String input, TokenTracker tracker) {
                return Outcome.ok(input);
            }
            @Override public Criteria<String> criteria() {
                return meeting().<String>zeroFailures();
            }

            @Override public String id() { return COVARIATE_USE_CASE_ID; }
            @Override public List<Covariate> covariates() {
                return List.of(Covariate.custom("region", CovariateCategory.CONFIGURATION));
            }
            @Override public Map<String, Supplier<String>> customCovariateResolvers() {
                return Map.of("region", () -> resolvedRegion);
            }
        };
    }

    private static Supplier<Experiment> covariateBaseline(String resolvedRegion) {
        return () -> {
            ServiceContract<Factors, String, String> serviceContract = covariateServiceContract(resolvedRegion);
            Sampling<Factors, String, String> sampling = Sampling
                    .<Factors, String, String>builder()
                    .serviceContractFactory(f -> serviceContract)
                    .inputs("a")
                    .samples(100)
                    .build();
            return Experiment.measuring(sampling, FACTORS).build();
        };
    }

    /**
     * Writes a covariate-stamped baseline record to {@code dir} with
     * the given covariate profile values. The filename includes the
     * covariate hashes so it byte-matches what the resolver expects
     * for a covariate-aware lookup.
     */
    private static void writeCovariateStampedBaseline(
            Path dir, double passRate, int sampleCount, Map<String, String> profileValues)
            throws IOException {
        String fingerprint = FactorsFingerprint.of(FactorBundle.of(FACTORS));
        BaselineRecord record = new BaselineRecord(
                COVARIATE_USE_CASE_ID, "measureBaseline", fingerprint,
                "sha256:any", sampleCount, Instant.parse("2026-04-26T15:30:00Z"),
                Map.<String, BaselineStatistics>of(
                        "bernoulli-pass-rate",
                        PerCriterionPassRateStatistics.of("contract", passRate, sampleCount)),
                CovariateProfile.of(profileValues));
        new BaselineWriter().write(record, dir);
    }

    @Test
    @DisplayName("resolves a covariate-stamped baseline whose profile aligns with the service contract")
    void resolvesCovariateStampedBaseline(@TempDir Path dir) throws IOException {
        writeCovariateStampedBaseline(dir, 0.80, 1000, Map.of("region", "EU"));

        int n = PowerAnalysis.sampleSize(dir, covariateBaseline("EU"), 0.05, 0.80);

        assertThat(n).isPositive();
    }

    @Test
    @DisplayName("covariate-stamped baseline whose profile does not align → IllegalStateException naming the profile")
    void coVariateMismatchThrowsAndNamesProfile(@TempDir Path dir) throws IOException {
        // Baseline is stamped with region=US; the service contract resolves
        // region=EU. The selector rejects the EU candidate (no
        // matching baseline for the resolved profile) and the
        // diagnostic must surface the resolved profile so the
        // operator knows *which* configuration went unmatched.
        writeCovariateStampedBaseline(dir, 0.80, 1000, Map.of("region", "US"));

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> PowerAnalysis.sampleSize(
                        dir, covariateBaseline("EU"), 0.05, 0.80))
                .withMessageContaining(COVARIATE_USE_CASE_ID)
                .withMessageContaining("region=EU");
    }

    @Test
    @DisplayName("covariate-naïve service contract still resolves an unstamped baseline (no regression)")
    void covariateNaiveServiceContractStillResolvesUnstampedBaseline(@TempDir Path dir) throws IOException {
        // The original ECHO service contract declares no covariates. Its
        // baseline has no covariate stamp. The covariate-aware
        // resolver path still selects an unstamped candidate when
        // declarations are empty — this is the no-regression case
        // for the existing PowerAnalysis call sites.
        writeBaselineWithRate(dir, 0.80, 1000);

        int n = PowerAnalysis.sampleSize(dir, baseline(), 0.05, 0.80);

        assertThat(n).isPositive();
    }

    // ── Single-argument overload: reads MDE / power from the contract's
    //    confidence-first criteria. ─────────────────────────────────────

    private static Supplier<Experiment> confidenceFirstBaseline(
            double mde, double power) {
        return () -> {
            ServiceContract<Factors, String, String> contract = new ServiceContract<>() {
                @Override public String id() { return USE_CASE_ID; }
                @Override public Outcome<String> invoke(String input, TokenTracker tracker) {
                    return Outcome.ok(input);
                }
                @Override public org.javai.punit.api.criterion.Criteria<String> criteria() {
                    return org.javai.punit.api.criterion.Criteria.empirical().<String>passRate()
                            .name("the-criterion")
                            .detectingMde(mde).atPower(power)
                            .satisfies("always", v -> Outcome.ok());
                }
            };
            Sampling<Factors, String, String> sampling = Sampling
                    .<Factors, String, String>builder()
                    .serviceContractFactory(f -> contract)
                    .inputs("a")
                    .samples(100)
                    .build();
            return Experiment.measuring(sampling, FACTORS).build();
        };
    }

    @Test
    @DisplayName("single-arg overload reads MDE / power from the contract's confidence-first criterion")
    void singleArgReadsFromContractCriterion(@TempDir Path dir) throws IOException {
        writeBaselineWithRate(dir, 0.90, 1000);

        int viaContract = PowerAnalysis.sampleSize(dir, confidenceFirstBaseline(0.05, 0.80));
        int viaExplicit = PowerAnalysis.sampleSize(dir, baseline(), 0.05, 0.80);

        assertThat(viaContract).isEqualTo(viaExplicit);
    }

    @Test
    @DisplayName("single-arg overload returns the max sample count across confidence-first criteria")
    void singleArgReturnsMaxAcrossCriteria(@TempDir Path dir) throws IOException {
        writeBaselineWithRate(dir, 0.90, 1000);
        Supplier<Experiment> twoCriteria = () -> {
            ServiceContract<Factors, String, String> contract = new ServiceContract<>() {
                @Override public String id() { return USE_CASE_ID; }
                @Override public Outcome<String> invoke(String input, TokenTracker tracker) {
                    return Outcome.ok(input);
                }
                @Override public org.javai.punit.api.criterion.Criteria<String> criteria() {
                    return org.javai.punit.api.criterion.Criteria.of(
                            // Looser: MDE 0.10 / power 0.50  → smaller N
                            org.javai.punit.api.criterion.Criteria.empirical().<String>passRate()
                                    .name("loose")
                                    .detectingMde(0.10).atPower(0.50)
                                    .satisfies("always", v -> Outcome.ok()),
                            // Tighter: MDE 0.02 / power 0.95 → larger N
                            org.javai.punit.api.criterion.Criteria.empirical().<String>passRate()
                                    .name("tight")
                                    .detectingMde(0.02).atPower(0.95)
                                    .satisfies("always", v -> Outcome.ok()));
                }
            };
            Sampling<Factors, String, String> sampling = Sampling
                    .<Factors, String, String>builder()
                    .serviceContractFactory(f -> contract)
                    .inputs("a")
                    .samples(100)
                    .build();
            return Experiment.measuring(sampling, FACTORS).build();
        };

        int viaContract = PowerAnalysis.sampleSize(dir, twoCriteria);
        int viaTight = PowerAnalysis.sampleSize(dir, baseline(), 0.02, 0.95);

        assertThat(viaContract).isEqualTo(viaTight);
    }

    @Test
    @DisplayName("single-arg overload throws when no criterion declares MDE + power")
    void singleArgThrowsWhenNoConfidenceFirstCriterion(@TempDir Path dir) throws IOException {
        writeBaselineWithRate(dir, 0.90, 1000);

        // The base `baseline()` uses ECHO — a no-criteria contract.
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> PowerAnalysis.sampleSize(dir, baseline()))
                .withMessageContaining("confidence-first")
                .withMessageContaining(".detectingMde");
    }
}
