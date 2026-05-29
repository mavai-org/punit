package org.mavai.punit.internal.engine.baseline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.mavai.punit.api.covariate.CovariateProfile;
import org.mavai.punit.api.LatencyResult;
import org.mavai.punit.api.spec.BaselineStatistics;
import org.mavai.punit.api.spec.LatencyStatistics;
import org.mavai.punit.api.spec.PassRateStatistics;
import org.mavai.punit.api.spec.PerCriterionPassRateStatistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("BaselineReader")
class BaselineReaderTest {

    private final BaselineReader reader = new BaselineReader();
    private final BaselineWriter writer = new BaselineWriter();

    private BaselineRecord roundTrip(Map<String, BaselineStatistics> entries) {
        return roundTrip(entries, LatencyIndicator.empty());
    }

    private BaselineRecord roundTrip(Map<String, BaselineStatistics> entries,
                                     LatencyIndicator indicator) {
        BaselineRecord original = new BaselineRecord(
                "ShoppingBasket", "measureBaseline", "a1b2c3d4",
                "sha256:abc123", 1000,
                Instant.parse("2026-04-26T15:30:00Z"),
                entries,
                org.mavai.punit.api.covariate.CovariateProfile.empty(),
                indicator);
        return reader.parse(writer.toYaml(original));
    }

    @Test
    @DisplayName("round-trips a PerCriterionPassRateStatistics record without loss")
    void roundTripPassRate() {
        BaselineRecord parsed = roundTrip(Map.of(
                "bernoulli-pass-rate", PerCriterionPassRateStatistics.of("contract", 0.94, 1000)));

        assertThat(parsed.serviceContractId()).isEqualTo("ShoppingBasket");
        assertThat(parsed.methodName()).isEqualTo("measureBaseline");
        assertThat(parsed.factorsFingerprint()).isEqualTo("a1b2c3d4");
        assertThat(parsed.inputsIdentity()).isEqualTo("sha256:abc123");
        assertThat(parsed.sampleCount()).isEqualTo(1000);
        assertThat(parsed.generatedAt()).isEqualTo(Instant.parse("2026-04-26T15:30:00Z"));

        BaselineStatistics entry = parsed.statisticsByCriterionName().get("bernoulli-pass-rate");
        assertThat(entry).isInstanceOf(PerCriterionPassRateStatistics.class);
        PerCriterionPassRateStatistics perCriterion = (PerCriterionPassRateStatistics) entry;
        PassRateStatistics passRate = perCriterion.byCriterion().get("contract");
        assertThat(passRate).isNotNull();
        assertThat(passRate.observedPassRate()).isEqualTo(0.94);
        assertThat(passRate.sampleCount()).isEqualTo(1000);
    }

    @Test
    @DisplayName("round-trips latency through the top-level latency: block (ms-integer percentiles)")
    void roundTripLatency() {
        LatencyResult percentiles = new LatencyResult(
                Duration.ofMillis(250),
                Duration.ofMillis(500),
                Duration.ofMillis(800),
                Duration.ofMillis(1200),
                1000);
        LatencyIndicator indicator = new LatencyIndicator(percentiles, new long[1000], 1000, 1000);

        // Pass-rate is mandatory (statisticsByCriterionName must be
        // non-empty per BaselineRecord's invariant); latency rides on
        // the indicator.
        BaselineRecord parsed = roundTrip(
                Map.of("bernoulli-pass-rate", PerCriterionPassRateStatistics.of("contract", 0.94, 1000)),
                indicator);

        // Reader synthesises the LatencyStatistics map entry from
        // the top-level latency: block, so the criterion-lookup
        // pattern keeps working without requiring the legacy
        // statistics.percentile-latency YAML emission.
        BaselineStatistics entry = parsed.statisticsByCriterionName().get("percentile-latency");
        assertThat(entry).isInstanceOf(LatencyStatistics.class);
        LatencyStatistics latency = (LatencyStatistics) entry;
        assertThat(latency.percentiles().p50()).isEqualTo(Duration.ofMillis(250));
        assertThat(latency.percentiles().p90()).isEqualTo(Duration.ofMillis(500));
        assertThat(latency.percentiles().p95()).isEqualTo(Duration.ofMillis(800));
        assertThat(latency.percentiles().p99()).isEqualTo(Duration.ofMillis(1200));
        assertThat(latency.sampleCount()).isEqualTo(1000);

        // The typed indicator survives round-trip too.
        assertThat(parsed.latencyIndicator().contributingSamples()).isEqualTo(1000);
        assertThat(parsed.latencyIndicator().totalSamples()).isEqualTo(1000);
    }

    @Test
    @DisplayName("round-trips both criteria — pass-rate via statistics:, latency via top-level block")
    void roundTripBothCriteria() {
        Map<String, BaselineStatistics> entries = new LinkedHashMap<>();
        entries.put("bernoulli-pass-rate", PerCriterionPassRateStatistics.of("contract", 0.94, 1000));
        LatencyIndicator indicator = new LatencyIndicator(
                new LatencyResult(Duration.ofMillis(250), Duration.ofMillis(500),
                        Duration.ofMillis(800), Duration.ofMillis(1200), 1000),
                new long[1000], 1000, 1000);

        BaselineRecord parsed = roundTrip(entries, indicator);

        assertThat(parsed.statisticsByCriterionName()).containsOnlyKeys(
                "bernoulli-pass-rate", "percentile-latency");
    }

    @Test
    @DisplayName("read(Path) wraps parse failure with the file path in the diagnostic")
    void readWrapsErrorWithPath(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("malformed.yaml");
        Files.writeString(file, "schemaVersion: punit-baseline-3\n");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> reader.read(file))
                .withMessageContaining("malformed.yaml")
                .withMessageContaining("Missing required field");
    }

    @Test
    @DisplayName("load(Path) returns the parsed record with no integrity warning when the "
            + "file's contentFingerprint matches its body")
    void loadOkOnFreshlyWrittenFile(@TempDir Path tempDir) throws IOException {
        BaselineRecord original = new BaselineRecord(
                "ShoppingBasket", "measureBaseline", "a1b2c3d4",
                "sha256:abc", 100,
                Instant.parse("2026-04-26T15:30:00Z"),
                Map.of("bernoulli-pass-rate", PerCriterionPassRateStatistics.of("contract", 0.9, 100)),
                CovariateProfile.empty(),
                LatencyIndicator.empty());
        Path file = writer.write(original, tempDir);

        BaselineReader.LoadedBaseline loaded = reader.load(file);

        assertThat(loaded.record().serviceContractId()).isEqualTo("ShoppingBasket");
        assertThat(loaded.integrityWarning())
                .as("a freshly-written baseline must verify cleanly")
                .isEmpty();
    }

    @Test
    @DisplayName("load(Path) surfaces the mismatch warning when the file body has been "
            + "edited after the contentFingerprint was written")
    void loadMismatchOnTamperedFile(@TempDir Path tempDir) throws IOException {
        BaselineRecord original = new BaselineRecord(
                "ShoppingBasket", "measureBaseline", "a1b2c3d4",
                "sha256:abc", 100,
                Instant.parse("2026-04-26T15:30:00Z"),
                Map.of("bernoulli-pass-rate", PerCriterionPassRateStatistics.of("contract", 0.95, 100)),
                CovariateProfile.empty(),
                LatencyIndicator.empty());
        Path file = writer.write(original, tempDir);
        // Tamper: lower the observed pass rate without re-fingerprinting.
        String tampered = Files.readString(file).replace(
                "observedPassRate: 0.95", "observedPassRate: 0.5");
        Files.writeString(file, tampered);

        BaselineReader.LoadedBaseline loaded = reader.load(file);

        assertThat(loaded.integrityWarning()).isPresent();
        assertThat(loaded.integrityWarning().get())
                .contains("integrity check failed")
                .contains(file.toString());
    }

    @Test
    @DisplayName("load(Path) surfaces the softer missing warning when the file predates "
            + "the contentFingerprint convention")
    void loadMissingOnLegacyFile(@TempDir Path tempDir) throws IOException {
        // Synthesise a legacy baseline by writing a valid record and then
        // stripping the trailing contentFingerprint: line.
        BaselineRecord original = new BaselineRecord(
                "Legacy", "measureBaseline", "a1b2c3d4",
                "sha256:abc", 50,
                Instant.parse("2026-04-26T15:30:00Z"),
                Map.of("bernoulli-pass-rate", PerCriterionPassRateStatistics.of("contract", 0.8, 50)),
                CovariateProfile.empty(),
                LatencyIndicator.empty());
        Path file = writer.write(original, tempDir);
        String stripped = Files.readString(file)
                .replaceAll("(?m)^contentFingerprint:.*\\R?", "");
        Files.writeString(file, stripped);

        BaselineReader.LoadedBaseline loaded = reader.load(file);

        assertThat(loaded.integrityWarning()).isPresent();
        assertThat(loaded.integrityWarning().get())
                .contains("predates integrity verification")
                .contains(file.toString());
    }

    @Test
    @DisplayName("rejects unknown schemaVersion")
    void rejectsUnknownSchemaVersion() {
        String yaml = "schemaVersion: punit-baseline-99\n"
                + "useCaseId: x\n"
                + "methodName: m\n"
                + "factorsFingerprint: f\n"
                + "inputsIdentity: sha256:x\n"
                + "sampleCount: 1\n"
                + "generatedAt: 2026-04-26T15:30:00Z\n"
                + "statistics:\n"
                + "  bernoulli-pass-rate:\n"
                + "    criteria:\n"
                + "      contract:\n"
                + "        observedPassRate: 0.5\n"
                + "        sampleCount: 1\n";

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> reader.parse(yaml))
                .withMessageContaining("punit-baseline-99")
                .withMessageContaining("punit-baseline-3");
    }

    @Test
    @DisplayName("rejects an empty file")
    void rejectsEmptyFile() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> reader.parse(""))
                .withMessageContaining("empty");
    }

    @Test
    @DisplayName("rejects a missing required field with the field name in the diagnostic")
    void rejectsMissingField() {
        String yaml = "schemaVersion: punit-baseline-3\n"
                + "useCaseId: x\n";

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> reader.parse(yaml))
                .withMessageContaining("methodName");
    }

    @Test
    @DisplayName("rejects a statistics entry whose shape matches no known statistics kind")
    void rejectsUnknownStatisticsShape() {
        String yaml = "schemaVersion: punit-baseline-3\n"
                + "useCaseId: x\n"
                + "methodName: m\n"
                + "factorsFingerprint: f\n"
                + "inputsIdentity: sha256:x\n"
                + "sampleCount: 1\n"
                + "generatedAt: 2026-04-26T15:30:00Z\n"
                + "statistics:\n"
                + "  weird-criterion:\n"
                + "    foo: 1\n"
                + "    bar: 2\n";

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> reader.parse(yaml))
                .withMessageContaining("weird-criterion")
                .withMessageContaining("Unrecognised statistics entry shape");
    }

    @Test
    @DisplayName("round-trips a baseline with a covariate profile, preserving order")
    void roundTripCovariates() {
        Map<String, String> profile = new LinkedHashMap<>();
        profile.put("day_of_week", "WEEKDAY");
        profile.put("region", "DE_FR");
        profile.put("model_version", "v1");

        BaselineRecord original = new BaselineRecord(
                "ShoppingBasket", "measureBaseline", "a1b2c3d4",
                "sha256:abc123", 1000,
                Instant.parse("2026-04-26T15:30:00Z"),
                Map.of("bernoulli-pass-rate", PerCriterionPassRateStatistics.of("contract", 0.94, 1000)),
                CovariateProfile.of(profile));

        BaselineRecord parsed = reader.parse(writer.toYaml(original));

        assertThat(parsed.covariateProfile().values())
                .containsExactly(
                        Map.entry("day_of_week", "WEEKDAY"),
                        Map.entry("region", "DE_FR"),
                        Map.entry("model_version", "v1"));
    }

    @Test
    @DisplayName("baselines without a covariates block parse to an empty profile")
    void missingCovariates() {
        // Older YAML predates the covariates: block. The reader must
        // accept it and assign the empty profile so old baselines on
        // disk continue to work after the upgrade.
        String yaml = "schemaVersion: punit-baseline-3\n"
                + "useCaseId: x\n"
                + "methodName: m\n"
                + "factorsFingerprint: f\n"
                + "inputsIdentity: sha256:x\n"
                + "sampleCount: 1\n"
                + "generatedAt: 2026-04-26T15:30:00Z\n"
                + "statistics:\n"
                + "  bernoulli-pass-rate:\n"
                + "    criteria:\n"
                + "      contract:\n"
                + "        observedPassRate: 0.5\n"
                + "        sampleCount: 1\n";

        BaselineRecord parsed = reader.parse(yaml);
        assertThat(parsed.covariateProfile().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("rejects a non-string covariate value")
    void rejectsNonStringCovariate() {
        String yaml = "schemaVersion: punit-baseline-3\n"
                + "useCaseId: x\n"
                + "methodName: m\n"
                + "factorsFingerprint: f\n"
                + "inputsIdentity: sha256:x\n"
                + "sampleCount: 1\n"
                + "generatedAt: 2026-04-26T15:30:00Z\n"
                + "statistics:\n"
                + "  bernoulli-pass-rate:\n"
                + "    criteria:\n"
                + "      contract:\n"
                + "        observedPassRate: 0.5\n"
                + "        sampleCount: 1\n"
                + "covariates:\n"
                + "  region: 42\n";

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> reader.parse(yaml))
                .withMessageContaining("region")
                .withMessageContaining("must be a string");
    }
}
