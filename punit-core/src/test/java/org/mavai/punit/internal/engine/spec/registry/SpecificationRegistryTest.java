package org.mavai.punit.internal.engine.spec.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("SpecificationRegistry")
class SpecificationRegistryTest {

    @TempDir
    Path tempDir;

    private SpecificationRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SpecificationRegistry(tempDir);
    }

    private void createSpecFile(String serviceContractId, double minPassRate) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("schemaVersion: punit-spec-1\n");
        sb.append("specId: ").append(serviceContractId).append("\n");
        sb.append("useCaseId: ").append(serviceContractId).append("\n");
        sb.append("generatedAt: 2026-01-09T10:00:00Z\n");
        sb.append("approvedAt: 2026-01-09T12:00:00Z\n");
        sb.append("approvedBy: tester\n");
        sb.append("\n");
        sb.append("execution:\n");
        sb.append("  samplesPlanned: 100\n");
        sb.append("  samplesExecuted: 100\n");
        sb.append("  terminationReason: COMPLETED\n");
        sb.append("\n");
        sb.append("statistics:\n");
        sb.append("  successRate:\n");
        sb.append("    observed: ").append(minPassRate).append("\n");
        sb.append("    standardError: 0.01\n");
        sb.append("    confidenceInterval95: [").append(minPassRate - 0.02).append(", ").append(minPassRate + 0.02).append("]\n");
        sb.append("  successes: ").append((int)(100 * minPassRate)).append("\n");
        sb.append("  failures: ").append((int)(100 * (1 - minPassRate))).append("\n");
        sb.append("\n");
        sb.append("cost:\n");
        sb.append("  totalTimeMs: 1000\n");
        sb.append("  avgTimePerSampleMs: 10\n");
        sb.append("  totalTokens: 10000\n");
        sb.append("  avgTokensPerSample: 100\n");
        sb.append("\n");
        sb.append("requirements:\n");
        sb.append("  minPassRate: ").append(minPassRate).append("\n");
        String content = sb.toString();
        sb.append("contentFingerprint: ").append(computeFingerprint(content)).append("\n");
        
        Files.writeString(tempDir.resolve(serviceContractId + ".yaml"), sb.toString());
    }

    private void createV2SpecFile(String serviceContractId, int samples, int successes) throws IOException {
        double successRate = (double) successes / samples;
        int failures = samples - successes;
        
        StringBuilder sb = new StringBuilder();
        sb.append("schemaVersion: punit-spec-2\n");
        sb.append("specId: ").append(serviceContractId).append("\n");
        sb.append("useCaseId: ").append(serviceContractId).append("\n");
        sb.append("generatedAt: 2026-01-09T10:00:00Z\n");
        sb.append("\n");
        sb.append("execution:\n");
        sb.append("  samplesPlanned: ").append(samples).append("\n");
        sb.append("  samplesExecuted: ").append(samples).append("\n");
        sb.append("  terminationReason: COMPLETED\n");
        sb.append("\n");
        sb.append("statistics:\n");
        sb.append("  successRate:\n");
        sb.append("    observed: ").append(String.format("%.4f", successRate)).append("\n");
        sb.append("    standardError: 0.01\n");
        sb.append("    confidenceInterval95: [").append(String.format("%.4f", successRate - 0.02)).append(", ").append(String.format("%.4f", successRate + 0.02)).append("]\n");
        sb.append("  successes: ").append(successes).append("\n");
        sb.append("  failures: ").append(failures).append("\n");
        sb.append("\n");
        sb.append("cost:\n");
        sb.append("  totalTimeMs: ").append(samples * 10).append("\n");
        sb.append("  avgTimePerSampleMs: 10\n");
        sb.append("  totalTokens: ").append(samples * 100).append("\n");
        sb.append("  avgTokensPerSample: 100\n");
        sb.append("\n");
        sb.append("empiricalBasis:\n");
        sb.append("  samples: ").append(samples).append("\n");
        sb.append("  successes: ").append(successes).append("\n");
        sb.append("\n");
        sb.append("requirements:\n");
        sb.append("  minPassRate: 0.85\n");
        String content = sb.toString();
        sb.append("contentFingerprint: ").append(computeFingerprint(content)).append("\n");

        Files.writeString(tempDir.resolve(serviceContractId + ".yaml"), sb.toString());
    }

    private String computeFingerprint(String content) {
        return SpecificationLoader.computeFingerprint(content);
    }

    @Nested
    @DisplayName("resolve")
    class Resolve {

        @Test
        @DisplayName("loads spec by service contract ID")
        void loadsSpecByServiceContractId() throws IOException {
            createSpecFile("TestServiceContract", 0.85);

            var spec = registry.resolveOrThrow("TestServiceContract");

            assertThat(spec.getServiceContractId()).isEqualTo("TestServiceContract");
            assertThat(spec.getMinPassRate()).isEqualTo(0.85);
        }

        @Test
        @DisplayName("loads v2 spec with empirical basis")
        void loadsV2SpecWithEmpiricalBasis() throws IOException {
            createV2SpecFile("TestServiceContract", 1000, 900);

            var spec = registry.resolveOrThrow("TestServiceContract");

            assertThat(spec.getServiceContractId()).isEqualTo("TestServiceContract");
            assertThat(spec.hasEmpiricalBasis()).isTrue();
            assertThat(spec.getEmpiricalBasis().samples()).isEqualTo(1000);
            assertThat(spec.getEmpiricalBasis().successes()).isEqualTo(900);
            assertThat(spec.getObservedRate()).isEqualTo(0.9);
        }

        @Test
        @DisplayName("strips older version suffix")
        void stripsOlderVersionSuffix() throws IOException {
            createSpecFile("TestServiceContract", 0.9);

            var spec = registry.resolveOrThrow("TestServiceContract:v1");

            assertThat(spec.getServiceContractId()).isEqualTo("TestServiceContract");
        }

        @Test
        @DisplayName("caches loaded specs")
        void cachesLoadedSpecs() throws IOException {
            createSpecFile("TestServiceContract", 0.85);

            var spec1 = registry.resolveOrThrow("TestServiceContract");
            var spec2 = registry.resolveOrThrow("TestServiceContract");

            assertThat(spec1).isSameAs(spec2);
        }

        @Test
        @DisplayName("throws when spec not found via resolveOrThrow")
        void throwsWhenSpecNotFound() {
            assertThatThrownBy(() -> registry.resolveOrThrow("NonExistent"))
                .isInstanceOf(SpecificationNotFoundException.class)
                .hasMessageContaining("NonExistent");
        }

        @Test
        @DisplayName("returns empty when spec not found via resolve")
        void returnsEmptyWhenSpecNotFound() {
            assertThat(registry.resolve("NonExistent")).isEmpty();
        }

        @Test
        @DisplayName("throws on null spec ID")
        void throwsOnNullSpecId() {
            assertThatThrownBy(() -> registry.resolveOrThrow(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("returns empty for null via resolve")
        void returnsEmptyForNull() {
            assertThat(registry.resolve(null)).isEmpty();
        }

        @Test
        @DisplayName("finds .yml files")
        void findsYmlFiles() throws IOException {
            StringBuilder sb = new StringBuilder();
            sb.append("schemaVersion: punit-spec-1\n");
            sb.append("specId: YmlCase\n");
            sb.append("useCaseId: YmlCase\n");
            sb.append("generatedAt: 2026-01-09T10:00:00Z\n");
            sb.append("approvedAt: 2026-01-09T12:00:00Z\n");
            sb.append("approvedBy: tester\n");
            sb.append("\n");
            sb.append("execution:\n");
            sb.append("  samplesPlanned: 100\n");
            sb.append("  samplesExecuted: 100\n");
            sb.append("  terminationReason: COMPLETED\n");
            sb.append("\n");
            sb.append("statistics:\n");
            sb.append("  successRate:\n");
            sb.append("    observed: 0.8\n");
            sb.append("    standardError: 0.04\n");
            sb.append("    confidenceInterval95: [0.72, 0.88]\n");
            sb.append("  successes: 80\n");
            sb.append("  failures: 20\n");
            sb.append("\n");
            sb.append("cost:\n");
            sb.append("  totalTimeMs: 1000\n");
            sb.append("  avgTimePerSampleMs: 10\n");
            sb.append("  totalTokens: 10000\n");
            sb.append("  avgTokensPerSample: 100\n");
            sb.append("\n");
            sb.append("requirements:\n");
            sb.append("  minPassRate: 0.8\n");
            String content = sb.toString();
            sb.append("contentFingerprint: ").append(computeFingerprint(content)).append("\n");
            Files.writeString(tempDir.resolve("YmlCase.yml"), sb.toString());

            var spec = registry.resolveOrThrow("YmlCase");

            assertThat(spec.getServiceContractId()).isEqualTo("YmlCase");
        }

        @Test
        @DisplayName("validates v2 spec without approval metadata")
        void validatesV2SpecWithoutApprovalMetadata() throws IOException {
            createV2SpecFile("TestServiceContract", 500, 450);

            var spec = registry.resolveOrThrow("TestServiceContract");

            // v2 specs without approval should still be valid
            assertThat(spec.hasApprovalMetadata()).isFalse();
            // validate() should not throw
            spec.validate();
        }
    }

    @Nested
    @DisplayName("exists")
    class Exists {

        @Test
        @DisplayName("returns true for existing spec")
        void returnsTrueForExistingSpec() throws IOException {
            createSpecFile("TestServiceContract", 0.9);

            assertThat(registry.exists("TestServiceContract")).isTrue();
        }

        @Test
        @DisplayName("returns true for existing v2 spec")
        void returnsTrueForExistingV2Spec() throws IOException {
            createV2SpecFile("TestServiceContract", 100, 90);

            assertThat(registry.exists("TestServiceContract")).isTrue();
        }

        @Test
        @DisplayName("returns false for non-existing spec")
        void returnsFalseForNonExistingSpec() {
            assertThat(registry.exists("NonExistent")).isFalse();
        }

        @Test
        @DisplayName("returns false for null")
        void returnsFalseForNull() {
            assertThat(registry.exists(null)).isFalse();
        }

        @Test
        @DisplayName("returns true for cached spec")
        void returnsTrueForCachedSpec() throws IOException {
            createSpecFile("TestServiceContract", 0.9);
            registry.resolveOrThrow("TestServiceContract"); // Load into cache

            assertThat(registry.exists("TestServiceContract")).isTrue();
        }

        @Test
        @DisplayName("handles older version suffix")
        void handlesOlderVersionSuffix() throws IOException {
            createSpecFile("TestServiceContract", 0.9);

            assertThat(registry.exists("TestServiceContract:v1")).isTrue();
        }
    }

    @Nested
    @DisplayName("clearCache")
    class ClearCache {

        @Test
        @DisplayName("clears cached specs")
        void clearsCachedSpecs() throws IOException {
            createSpecFile("TestServiceContract", 0.85);
            var spec1 = registry.resolveOrThrow("TestServiceContract");

            registry.clearCache();
            var spec2 = registry.resolveOrThrow("TestServiceContract");

            assertThat(spec1).isNotSameAs(spec2);
        }
    }

    @Nested
    @DisplayName("SpecRepository interface")
    class SpecRepositoryInterface {

        @Test
        @DisplayName("resolves spec via SpecRepository interface")
        void resolvesSpecViaInterface() throws IOException {
            createSpecFile("TestServiceContract", 0.85);

            SpecRepository repo = registry;
            var result = repo.resolve("TestServiceContract");

            assertThat(result).isPresent();
            assertThat(result.get().getServiceContractId()).isEqualTo("TestServiceContract");
        }

        @Test
        @DisplayName("returns empty for non-existing spec via SpecRepository interface")
        void returnsEmptyForNonExistingSpec() {
            SpecRepository repo = registry;
            var result = repo.resolve("NonExistent");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("resolves dimension-qualified latency spec")
        void resolvesDimensionQualifiedLatencySpec() throws IOException {
            createLatencySpecFile("TestServiceContract");

            SpecRepository repo = registry;
            var result = repo.resolve("TestServiceContract.latency");

            assertThat(result).isPresent();
            assertThat(result.get().hasLatencyBaseline()).isTrue();
        }
    }

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("throws on null specs root")
        void throwsOnNullSpecsRoot() {
            assertThatThrownBy(() -> new SpecificationRegistry(null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    private void createLatencySpecFile(String serviceContractId) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("schemaVersion: punit-spec-2\n");
        sb.append("useCaseId: ").append(serviceContractId).append(".latency\n");
        sb.append("generatedAt: 2026-03-07T10:00:00Z\n");
        sb.append("execution:\n");
        sb.append("  samplesPlanned: 1000\n");
        sb.append("  samplesExecuted: 1000\n");
        sb.append("  terminationReason: COMPLETED\n");
        sb.append("statistics:\n");
        sb.append("  successRate:\n");
        sb.append("    observed: 0.9500\n");
        sb.append("    standardError: 0.0069\n");
        sb.append("    confidenceInterval95: [0.9365, 0.9635]\n");
        sb.append("  successes: 950\n");
        sb.append("  failures: 50\n");
        sb.append("empiricalBasis:\n");
        sb.append("  samples: 1000\n");
        sb.append("  successes: 950\n");
        sb.append("latency:\n");
        sb.append("  sampleCount: 3\n");
        sb.append("  meanMs: 450\n");
        sb.append("  maxMs: 1400\n");
        sb.append("  sortedLatenciesMs: [380, 620, 1400]\n");
        sb.append("cost:\n");
        sb.append("  totalTimeMs: 450000\n");
        sb.append("  avgTimePerSampleMs: 450\n");
        sb.append("  totalTokens: 100000\n");
        sb.append("  avgTokensPerSample: 100\n");
        sb.append("requirements:\n");
        sb.append("  minPassRate: 0.9000\n");
        String content = sb.toString();
        sb.append("contentFingerprint: ").append(computeFingerprint(content)).append("\n");

        Files.writeString(tempDir.resolve(serviceContractId + ".latency.yaml"), sb.toString());
    }
}
