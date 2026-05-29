package org.mavai.punit.internal.engine.spec.registry;

import static org.assertj.core.api.Assertions.assertThat;

import org.mavai.punit.internal.engine.spec.model.ExecutionSpecification;
import org.mavai.punit.internal.engine.spec.model.LatencyBaseline;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SpecificationLoader — Latency parsing")
class SpecificationLoaderLatencyTest {

    @Nested
    @DisplayName("Top-level latency section")
    class TopLevelLatency {

        @Test
        @DisplayName("should parse latency section with sorted vector")
        void shouldParseLatencySection() {
            long[] sorted = {100, 200, 300, 400, 500};
            String yaml = createYamlWithLatency(5, 300, 500, sorted);

            ExecutionSpecification spec = SpecificationLoader.parseYaml(yaml);

            assertThat(spec.hasLatencyBaseline()).isTrue();
            LatencyBaseline lb = spec.getLatencyBaseline();
            assertThat(lb.sampleCount()).isEqualTo(5);
            assertThat(lb.meanMs()).isEqualTo(300);
            assertThat(lb.maxMs()).isEqualTo(500);
            assertThat(lb.sortedLatenciesMs()).containsExactly(100, 200, 300, 400, 500);
        }

        @Test
        @DisplayName("should return null latency baseline when no latency section")
        void shouldReturnNullWhenNoLatencySection() {
            String yaml = createYamlWithoutLatency();

            ExecutionSpecification spec = SpecificationLoader.parseYaml(yaml);

            assertThat(spec.hasLatencyBaseline()).isFalse();
            assertThat(spec.getLatencyBaseline()).isNull();
        }
    }

    @Nested
    @DisplayName("Backward compatibility")
    class BackwardCompatibility {

        @Test
        @DisplayName("existing spec without latency should parse correctly")
        void existingSpecWithoutLatencyShouldParseCorrectly() {
            String yaml = createYamlWithoutLatency();

            ExecutionSpecification spec = SpecificationLoader.parseYaml(yaml);

            assertThat(spec.getServiceContractId()).isEqualTo("TestCase");
            assertThat(spec.getMinPassRate()).isEqualTo(0.85);
            assertThat(spec.hasLatencyBaseline()).isFalse();
        }

        @Test
        @DisplayName("spec with latency should still parse other fields correctly")
        void specWithLatencyShouldStillParseOtherFields() {
            String yaml = createYamlWithLatency(3, 200, 300, new long[]{100, 200, 300});

            ExecutionSpecification spec = SpecificationLoader.parseYaml(yaml);

            assertThat(spec.getServiceContractId()).isEqualTo("TestCase");
            assertThat(spec.getMinPassRate()).isEqualTo(0.85);
            assertThat(spec.hasLatencyBaseline()).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private String createYamlWithLatency(int sampleCount, long mean, long max, long[] sorted) {
        StringBuilder sb = new StringBuilder();
        sb.append("schemaVersion: punit-spec-1\n");
        sb.append("useCaseId: TestCase\n");
        sb.append("generatedAt: 2026-02-15T10:00:00Z\n");
        sb.append("approvedAt: 2026-02-15T12:00:00Z\n");
        sb.append("approvedBy: tester\n");
        sb.append("\n");
        sb.append("execution:\n");
        sb.append("  samplesPlanned: 1000\n");
        sb.append("  samplesExecuted: 1000\n");
        sb.append("  terminationReason: COMPLETED\n");
        sb.append("\n");
        sb.append("statistics:\n");
        sb.append("  successRate:\n");
        sb.append("    observed: 0.9000\n");
        sb.append("    standardError: 0.0095\n");
        sb.append("    confidenceInterval95: [0.8814, 0.9186]\n");
        sb.append("  successes: 900\n");
        sb.append("  failures: 100\n");
        sb.append("\n");
        sb.append("latency:\n");
        sb.append("  sampleCount: ").append(sampleCount).append("\n");
        sb.append("  meanMs: ").append(mean).append("\n");
        sb.append("  maxMs: ").append(max).append("\n");
        sb.append("  sortedLatenciesMs: [");
        for (int i = 0; i < sorted.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(sorted[i]);
        }
        sb.append("]\n");
        sb.append("\n");
        sb.append("cost:\n");
        sb.append("  totalTimeMs: 5000\n");
        sb.append("  avgTimePerSampleMs: 5\n");
        sb.append("  totalTokens: 100000\n");
        sb.append("  avgTokensPerSample: 100\n");
        sb.append("\n");
        sb.append("requirements:\n");
        sb.append("  minPassRate: 0.85\n");

        String content = sb.toString();
        String fingerprint = SpecificationLoader.computeFingerprint(content);
        sb.append("contentFingerprint: ").append(fingerprint).append("\n");

        return sb.toString();
    }

    private String createYamlWithoutLatency() {
        StringBuilder sb = new StringBuilder();
        sb.append("schemaVersion: punit-spec-1\n");
        sb.append("useCaseId: TestCase\n");
        sb.append("generatedAt: 2026-02-15T10:00:00Z\n");
        sb.append("approvedAt: 2026-02-15T12:00:00Z\n");
        sb.append("approvedBy: tester\n");
        sb.append("\n");
        sb.append("execution:\n");
        sb.append("  samplesPlanned: 1000\n");
        sb.append("  samplesExecuted: 1000\n");
        sb.append("  terminationReason: COMPLETED\n");
        sb.append("\n");
        sb.append("statistics:\n");
        sb.append("  successRate:\n");
        sb.append("    observed: 0.9000\n");
        sb.append("    standardError: 0.0095\n");
        sb.append("    confidenceInterval95: [0.8814, 0.9186]\n");
        sb.append("  successes: 900\n");
        sb.append("  failures: 100\n");
        sb.append("\n");
        sb.append("cost:\n");
        sb.append("  totalTimeMs: 5000\n");
        sb.append("  avgTimePerSampleMs: 5\n");
        sb.append("  totalTokens: 100000\n");
        sb.append("  avgTokensPerSample: 100\n");
        sb.append("\n");
        sb.append("requirements:\n");
        sb.append("  minPassRate: 0.85\n");

        String content = sb.toString();
        String fingerprint = SpecificationLoader.computeFingerprint(content);
        sb.append("contentFingerprint: ").append(fingerprint).append("\n");

        return sb.toString();
    }
}
