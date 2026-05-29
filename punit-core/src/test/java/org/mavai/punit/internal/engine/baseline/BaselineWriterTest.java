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
import org.yaml.snakeyaml.Yaml;

@DisplayName("BaselineWriter — punit-baseline-2 schema serialisation")
class BaselineWriterTest {

    private final BaselineWriter writer = new BaselineWriter();

    private BaselineRecord recordWith(Map<String, BaselineStatistics> stats) {
        return new BaselineRecord(
                "ShoppingBasketServiceContract",
                "measureBaseline",
                "a1b2c3d4",
                "sha256:7d3a8c1e9b2f",
                1000,
                Instant.parse("2026-04-26T15:30:00Z"),
                stats);
    }

    @Test
    @DisplayName("emits the schema discriminator and identity keys")
    void emitsHeader() {
        String yaml = writer.toYaml(recordWith(Map.of(
                "bernoulli-pass-rate", PerCriterionPassRateStatistics.of("contract", 0.94, 1000))));

        Map<String, Object> root = new Yaml().load(yaml);

        assertThat(root)
                .containsEntry("schemaVersion", "punit-baseline-3")
                .containsEntry("useCaseId", "ShoppingBasketServiceContract")
                .containsEntry("methodName", "measureBaseline")
                .containsEntry("factorsFingerprint", "a1b2c3d4")
                .containsEntry("inputsIdentity", "sha256:7d3a8c1e9b2f")
                .containsEntry("sampleCount", 1000)
                .containsEntry("generatedAt", "2026-04-26T15:30:00Z");
    }

    @Test
    @DisplayName("serialises a PerCriterionPassRateStatistics entry under criteria.<id> with observedPassRate + sampleCount")
    void serialisesPassRate() {
        String yaml = writer.toYaml(recordWith(Map.of(
                "bernoulli-pass-rate", PerCriterionPassRateStatistics.of("contract", 0.94, 1000))));

        Map<String, Object> root = new Yaml().load(yaml);
        Map<String, Object> stats = (Map<String, Object>) root.get("statistics");
        Map<String, Object> entry = (Map<String, Object>) stats.get("bernoulli-pass-rate");
        Map<String, Object> criteria = (Map<String, Object>) entry.get("criteria");
        Map<String, Object> contract = (Map<String, Object>) criteria.get("contract");

        assertThat(contract)
                .containsEntry("observedPassRate", 0.94)
                .containsEntry("sampleCount", 1000);
    }

    @Test
    @DisplayName("serialises latency to the top-level latency: block with ms-integer percentiles")
    void serialisesLatency() {
        LatencyResult percentiles = new LatencyResult(
                Duration.ofMillis(250),
                Duration.ofMillis(500),
                Duration.ofMillis(800),
                Duration.ofMillis(1200),
                1000);
        LatencyIndicator indicator = new LatencyIndicator(percentiles, new long[1000], 1000, 1000);

        String yaml = writer.toYaml(recordWithLatency(
                Map.of("bernoulli-pass-rate", PerCriterionPassRateStatistics.of("contract", 0.94, 1000)),
                indicator));

        Map<String, Object> root = new Yaml().load(yaml);
        // Legacy statistics.percentile-latency block is retired —
        // latency lives at the top level only.
        Map<String, Object> stats = (Map<String, Object>) root.get("statistics");
        assertThat(stats).doesNotContainKey("percentile-latency");

        Map<String, Object> latency = (Map<String, Object>) root.get("latency");
        // snakeyaml round-trips small integers as Integer, not Long.
        assertThat(latency)
                .containsEntry("basis", "passing-samples")
                .containsEntry("contributingSamples", 1000)
                .containsEntry("totalSamples", 1000);
        assertThat(((Number) latency.get("p50Ms")).longValue()).isEqualTo(250L);
        assertThat(((Number) latency.get("p90Ms")).longValue()).isEqualTo(500L);
        assertThat(((Number) latency.get("p95Ms")).longValue()).isEqualTo(800L);
        assertThat(((Number) latency.get("p99Ms")).longValue()).isEqualTo(1200L);
    }

    @Test
    @DisplayName("serialises pass-rate under statistics: and latency at the top level alongside")
    void serialisesBothCriteria() {
        Map<String, BaselineStatistics> entries = new LinkedHashMap<>();
        entries.put("bernoulli-pass-rate", PerCriterionPassRateStatistics.of("contract", 0.94, 1000));
        LatencyIndicator indicator = new LatencyIndicator(
                new LatencyResult(Duration.ofMillis(250), Duration.ofMillis(500),
                        Duration.ofMillis(800), Duration.ofMillis(1200), 1000),
                new long[1000], 1000, 1000);

        String yaml = writer.toYaml(recordWithLatency(entries, indicator));
        Map<String, Object> root = new Yaml().load(yaml);
        Map<String, Object> stats = (Map<String, Object>) root.get("statistics");

        assertThat(stats).containsOnlyKeys("bernoulli-pass-rate");
        assertThat(root).containsKey("latency");
    }

    private BaselineRecord recordWithLatency(
            Map<String, BaselineStatistics> stats, LatencyIndicator indicator) {
        return new BaselineRecord(
                "ShoppingBasketServiceContract",
                "measureBaseline",
                "a1b2c3d4",
                "sha256:7d3a8c1e9b2f",
                1000,
                Instant.parse("2026-04-26T15:30:00Z"),
                stats,
                org.mavai.punit.api.covariate.CovariateProfile.empty(),
                indicator);
    }

    @Test
    @DisplayName("write(...) creates baselineDir if missing and returns the file path")
    void writeCreatesDirectory(@TempDir Path tempDir) throws IOException {
        Path baselineDir = tempDir.resolve("baselines");

        Path file = writer.write(
                recordWith(Map.of("bernoulli-pass-rate", PerCriterionPassRateStatistics.of("contract", 0.5, 100))),
                baselineDir);

        assertThat(Files.exists(baselineDir)).isTrue();
        assertThat(file)
                .exists()
                .isEqualTo(baselineDir.resolve(
                        "ShoppingBasketServiceContract.measureBaseline-a1b2c3d4.yaml"));

        String content = Files.readString(file);
        assertThat(content).contains("schemaVersion: punit-baseline-3");
    }

    @Test
    @DisplayName("emits a contentFingerprint: line as the last field — SHA-256 of the body "
            + "preceding it (integrity check)")
    void emitsContentFingerprintAsLastField() {
        String yaml = writer.toYaml(recordWith(Map.of(
                "bernoulli-pass-rate", PerCriterionPassRateStatistics.of("contract", 0.94, 1000))));

        // Last non-empty line carries the fingerprint key.
        java.util.List<String> nonEmpty = yaml.lines()
                .filter(l -> !l.isBlank())
                .toList();
        assertThat(nonEmpty.get(nonEmpty.size() - 1))
                .startsWith("contentFingerprint: ")
                .matches("contentFingerprint: [0-9a-f]{64}");

        // The reader must accept its own writer's output without surfacing
        // an integrity warning.
        Map<String, Object> root = new Yaml().load(yaml);
        assertThat(root).containsKey("contentFingerprint");
    }

    @Test
    @DisplayName("rejects an unsupported BaselineStatistics flavour with a diagnostic")
    void rejectsUnknownStatisticsKind() {
        BaselineStatistics unknown = () -> 1;
        BaselineRecord record = recordWith(Map.of("custom-criterion", unknown));

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> writer.toYaml(record))
                .withMessageContaining("custom-criterion")
                .withMessageContaining("Unsupported baseline statistics kind");
    }

    @Test
    @DisplayName("omits the covariates section when the profile is empty (default constructor)")
    void omitsCovariatesWhenEmpty() {
        String yaml = writer.toYaml(recordWith(Map.of(
                "bernoulli-pass-rate", PerCriterionPassRateStatistics.of("contract", 0.94, 1000))));

        assertThat(yaml).doesNotContain("covariates:");
    }

    @Test
    @DisplayName("emits a covariates block when the profile is non-empty, preserving order")
    void emitsCovariatesBlock() {
        Map<String, String> profile = new LinkedHashMap<>();
        profile.put("day_of_week", "WEEKDAY");
        profile.put("region", "DE_FR");
        profile.put("model_version", "v1");

        BaselineRecord record = new BaselineRecord(
                "ShoppingBasketServiceContract",
                "measureBaseline",
                "a1b2c3d4",
                "sha256:7d3a8c1e9b2f",
                1000,
                Instant.parse("2026-04-26T15:30:00Z"),
                Map.of("bernoulli-pass-rate", PerCriterionPassRateStatistics.of("contract", 0.94, 1000)),
                CovariateProfile.of(profile));

        String yaml = writer.toYaml(record);
        Map<String, Object> root = new Yaml().load(yaml);

        assertThat(root).containsKey("covariates");
        @SuppressWarnings("unchecked")
        Map<String, Object> covariates = (Map<String, Object>) root.get("covariates");
        // SnakeYAML preserves declaration order on the way in.
        assertThat(covariates.keySet())
                .containsExactly("day_of_week", "region", "model_version");
        assertThat(covariates)
                .containsEntry("day_of_week", "WEEKDAY")
                .containsEntry("region", "DE_FR")
                .containsEntry("model_version", "v1");
    }
}
