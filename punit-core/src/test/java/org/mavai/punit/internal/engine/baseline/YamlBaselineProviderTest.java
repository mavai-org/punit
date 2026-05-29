package org.mavai.punit.internal.engine.baseline;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import org.mavai.punit.api.FactorBundle;
import org.mavai.punit.api.spec.BaselineLookup;
import org.mavai.punit.api.spec.BaselineStatistics;
import org.mavai.punit.api.spec.PassRateStatistics;
import org.mavai.punit.api.spec.PerCriterionPassRateStatistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("YamlBaselineProvider — bridges BaselineProvider contract to file-system resolver")
class YamlBaselineProviderTest {

    record Factors(String model, double temperature) { }

    private final BaselineWriter writer = new BaselineWriter();

    @Test
    @DisplayName("returns the recorded statistics when service contract + factors fingerprint match")
    void resolvesPresent(@TempDir Path dir) throws IOException {
        FactorBundle bundle = FactorBundle.of(new Factors("gpt-4o", 0.0));
        BaselineRecord record = baseline("ShoppingBasket", FactorsFingerprint.of(bundle),
                Map.of("bernoulli-pass-rate", PerCriterionPassRateStatistics.of("contract", 0.92, 1000)));
        writer.write(record, dir);

        var provider = new YamlBaselineProvider(dir);

        var resolved = provider.baselineFor(
                "ShoppingBasket", bundle, "bernoulli-pass-rate", PassRateStatistics.class);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().observedPassRate()).isEqualTo(0.92);
    }

    @Test
    @DisplayName("returns empty for an unmatched factor bundle (different fingerprint)")
    void emptyForDifferentFactors(@TempDir Path dir) throws IOException {
        FactorBundle measured = FactorBundle.of(new Factors("gpt-4o", 0.0));
        FactorBundle queried = FactorBundle.of(new Factors("gpt-4o", 0.7));
        BaselineRecord record = baseline("ShoppingBasket", FactorsFingerprint.of(measured),
                Map.of("bernoulli-pass-rate", PerCriterionPassRateStatistics.of("contract", 0.92, 1000)));
        writer.write(record, dir);

        var provider = new YamlBaselineProvider(dir);

        var resolved = provider.baselineFor(
                "ShoppingBasket", queried, "bernoulli-pass-rate", PassRateStatistics.class);

        assertThat(resolved).isEmpty();
    }

    @Test
    @DisplayName("returns empty for an empty baseline directory")
    void emptyForNoBaselines(@TempDir Path dir) {
        var provider = new YamlBaselineProvider(dir);

        var resolved = provider.baselineFor(
                "ShoppingBasket",
                FactorBundle.of(new Factors("gpt-4o", 0.0)),
                "bernoulli-pass-rate",
                PassRateStatistics.class);

        assertThat(resolved).isEmpty();
    }

    @Test
    @DisplayName("baselineLookup returns no integrity warning for a freshly-written, "
            + "fingerprint-matching baseline")
    void lookupCleanWhenFingerprintMatches(@TempDir Path dir) throws IOException {
        FactorBundle bundle = FactorBundle.of(new Factors("gpt-4o", 0.0));
        BaselineRecord record = baseline("ShoppingBasket", FactorsFingerprint.of(bundle),
                Map.of("bernoulli-pass-rate", PerCriterionPassRateStatistics.of("contract", 0.92, 1000)));
        writer.write(record, dir);

        var provider = new YamlBaselineProvider(dir);
        BaselineLookup<PassRateStatistics> lookup = provider.baselineLookup(
                "ShoppingBasket", bundle, "bernoulli-pass-rate", PassRateStatistics.class);

        assertThat(lookup.selected()).isPresent();
        assertThat(lookup.notes())
                .as("clean integrity check produces no notes")
                .isEmpty();
    }

    @Test
    @DisplayName("baselineLookup propagates the integrity-mismatch warning into notes "
            + "when the selected baseline's body has been edited after fingerprint emission")
    void lookupSurfacesMismatchWarning(@TempDir Path dir) throws IOException {
        FactorBundle bundle = FactorBundle.of(new Factors("gpt-4o", 0.0));
        BaselineRecord record = baseline("ShoppingBasket", FactorsFingerprint.of(bundle),
                Map.of("bernoulli-pass-rate", PerCriterionPassRateStatistics.of("contract", 0.92, 1000)));
        Path file = writer.write(record, dir);
        // Tamper: lower observedPassRate without re-fingerprinting.
        String tampered = Files.readString(file).replace(
                "observedPassRate: 0.92", "observedPassRate: 0.50");
        Files.writeString(file, tampered);

        var provider = new YamlBaselineProvider(dir);
        BaselineLookup<PassRateStatistics> lookup = provider.baselineLookup(
                "ShoppingBasket", bundle, "bernoulli-pass-rate", PassRateStatistics.class);

        assertThat(lookup.selected())
                .as("test runs to completion — verdict is not dismissed out of hand on integrity warning")
                .isPresent();
        assertThat(lookup.notes())
                .as("integrity warning lands in notes → ProbabilisticTestResult.warnings()")
                .anyMatch(n -> n.contains("integrity check failed"));
    }

    @Test
    @DisplayName("baselineLookup propagates the softer missing-fingerprint warning when "
            + "the selected baseline predates the integrity-verification feature")
    void lookupSurfacesMissingWarning(@TempDir Path dir) throws IOException {
        FactorBundle bundle = FactorBundle.of(new Factors("gpt-4o", 0.0));
        BaselineRecord record = baseline("ShoppingBasket", FactorsFingerprint.of(bundle),
                Map.of("bernoulli-pass-rate", PerCriterionPassRateStatistics.of("contract", 0.88, 500)));
        Path file = writer.write(record, dir);
        // Strip the trailing fingerprint line — synthesises a pre-integrity baseline.
        String stripped = Files.readString(file)
                .replaceAll("(?m)^contentFingerprint:.*\\R?", "");
        Files.writeString(file, stripped);

        var provider = new YamlBaselineProvider(dir);
        BaselineLookup<PassRateStatistics> lookup = provider.baselineLookup(
                "ShoppingBasket", bundle, "bernoulli-pass-rate", PassRateStatistics.class);

        assertThat(lookup.selected()).isPresent();
        assertThat(lookup.notes())
                .anyMatch(n -> n.contains("predates integrity verification"));
    }

    private BaselineRecord baseline(String serviceContractId, String fingerprint,
                                     Map<String, BaselineStatistics> entries) {
        return new BaselineRecord(
                serviceContractId, "measureBaseline", fingerprint,
                "sha256:irrelevant", 1000, Instant.parse("2026-04-26T15:30:00Z"),
                entries);
    }
}
