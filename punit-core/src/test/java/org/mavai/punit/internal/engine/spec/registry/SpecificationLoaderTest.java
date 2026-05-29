package org.mavai.punit.internal.engine.spec.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.mavai.punit.internal.engine.spec.model.ExecutionSpecification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SpecificationLoader")
class SpecificationLoaderTest {

    // Helper to create valid spec YAML with fingerprint (v1 schema with approval)
    // Now includes all required fields for schema validation
    private String createValidYaml(String specId, double minPassRate) {
        StringBuilder sb = new StringBuilder();
        sb.append("schemaVersion: punit-spec-1\n");
        sb.append("specId: ").append(specId).append("\n");
        sb.append("useCaseId: ").append(specId).append("\n");
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
        
        // Compute fingerprint
        String contentForHashing = sb.toString();
        String fingerprint = computeFingerprint(contentForHashing);
        sb.append("contentFingerprint: ").append(fingerprint).append("\n");
        
        return sb.toString();
    }

    // Helper to create valid spec YAML with v2 schema (no approval required)
    // Now includes all required fields for schema validation
    private String createV2Yaml(String specId, int samples, int successes) {
        double successRate = (double) successes / samples;
        int failures = samples - successes;
        
        StringBuilder sb = new StringBuilder();
        sb.append("schemaVersion: punit-spec-2\n");
        sb.append("specId: ").append(specId).append("\n");
        sb.append("useCaseId: ").append(specId).append("\n");
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
        sb.append("  generatedAt: 2026-01-09T10:00:00Z\n");
        sb.append("\n");
        sb.append("requirements:\n");
        sb.append("  minPassRate: 0.85\n");

        String contentForHashing = sb.toString();
        String fingerprint = computeFingerprint(contentForHashing);
        sb.append("contentFingerprint: ").append(fingerprint).append("\n");

        return sb.toString();
    }
    
    private String computeFingerprint(String content) {
        return SpecificationLoader.computeFingerprint(content);
    }

    @Nested
    @DisplayName("parseYaml - v1 schema")
    class ParseYamlV1 {

        @Test
        @DisplayName("parses valid YAML with all required fields")
        void parsesValidYaml() {
            String yaml = createValidYaml("TestServiceContract", 0.85);
            
            ExecutionSpecification spec = SpecificationLoader.parseYaml(yaml);
            
            assertThat(spec.getServiceContractId()).isEqualTo("TestServiceContract");
            assertThat(spec.getServiceContractId()).isEqualTo("TestServiceContract");
            assertThat(spec.getMinPassRate()).isEqualTo(0.85);
        }

        @Test
        @DisplayName("parses approval metadata")
        void parsesApprovalMetadata() {
            String yaml = createValidYaml("TestCase", 0.9);
            
            ExecutionSpecification spec = SpecificationLoader.parseYaml(yaml);
            
            assertThat(spec.getApprovedBy()).isEqualTo("tester");
            assertThat(spec.getApprovedAt()).isNotNull();
            assertThat(spec.hasApprovalMetadata()).isTrue();
        }

        @Test
        @DisplayName("parses execution context")
        void parsesExecutionContext() {
            StringBuilder sb = new StringBuilder();
            sb.append("specId: TestCase\n");
            sb.append("useCaseId: TestCase\n");
            sb.append("approvedAt: 2026-01-09T12:00:00Z\n");
            sb.append("approvedBy: tester\n");
            sb.append("executionContext:\n");
            sb.append("  model: gpt-4\n");
            sb.append("  temperature: 0.7\n");
            sb.append("requirements:\n");
            sb.append("  minPassRate: 0.9\n");
            sb.append("schemaVersion: punit-spec-1\n");
            String content = sb.toString();
            sb.append("contentFingerprint: ").append(computeFingerprint(content)).append("\n");
            
            ExecutionSpecification spec = SpecificationLoader.parseYaml(sb.toString());
            
            assertThat(spec.getExecutionContext()).containsEntry("model", "gpt-4");
            assertThat(spec.getExecutionContext()).containsEntry("temperature", 0.7);
        }

        @Test
        @DisplayName("parses source baselines list")
        void parsesSourceBaselines() {
            StringBuilder sb = new StringBuilder();
            sb.append("specId: TestCase\n");
            sb.append("useCaseId: TestCase\n");
            sb.append("sourceBaselines:\n");
            sb.append("  - baseline-1\n");
            sb.append("  - baseline-2\n");
            sb.append("requirements:\n");
            sb.append("  minPassRate: 0.9\n");
            sb.append("schemaVersion: punit-spec-1\n");
            String content = sb.toString();
            sb.append("contentFingerprint: ").append(computeFingerprint(content)).append("\n");
            
            ExecutionSpecification spec = SpecificationLoader.parseYaml(sb.toString());
            
            assertThat(spec.getSourceBaselines()).containsExactly("baseline-1", "baseline-2");
        }

        @Test
        @DisplayName("parses cost envelope")
        void parsesCostEnvelope() {
            StringBuilder sb = new StringBuilder();
            sb.append("specId: TestCase\n");
            sb.append("useCaseId: TestCase\n");
            sb.append("requirements:\n");
            sb.append("  minPassRate: 0.9\n");
            sb.append("costEnvelope:\n");
            sb.append("  maxTimePerSampleMs: 100\n");
            sb.append("  maxTokensPerSample: 500\n");
            sb.append("  totalTokenBudget: 10000\n");
            sb.append("schemaVersion: punit-spec-1\n");
            String content = sb.toString();
            sb.append("contentFingerprint: ").append(computeFingerprint(content)).append("\n");
            
            ExecutionSpecification spec = SpecificationLoader.parseYaml(sb.toString());
            
            assertThat(spec.getCostEnvelope()).isNotNull();
            assertThat(spec.getCostEnvelope().totalTokenBudget()).isEqualTo(10000);
        }

        @Test
        @DisplayName("skips comment lines")
        void skipsCommentLines() {
            StringBuilder sb = new StringBuilder();
            sb.append("# This is a comment\n");
            sb.append("specId: TestCase\n");
            sb.append("# Another comment\n");
            sb.append("useCaseId: TestCase\n");
            sb.append("requirements:\n");
            sb.append("  minPassRate: 0.9\n");
            sb.append("schemaVersion: punit-spec-1\n");
            String content = sb.toString();
            sb.append("contentFingerprint: ").append(computeFingerprint(content)).append("\n");
            
            ExecutionSpecification spec = SpecificationLoader.parseYaml(sb.toString());
            
            assertThat(spec.getServiceContractId()).isEqualTo("TestCase");
        }

        @Test
        @DisplayName("handles quoted string values")
        void handlesQuotedStringValues() {
            StringBuilder sb = new StringBuilder();
            sb.append("specId: \"TestCase\"\n");
            sb.append("useCaseId: 'TestCase'\n");
            sb.append("approvalNotes: \"Some notes here\"\n");
            sb.append("requirements:\n");
            sb.append("  minPassRate: 0.9\n");
            sb.append("schemaVersion: punit-spec-1\n");
            String content = sb.toString();
            sb.append("contentFingerprint: ").append(computeFingerprint(content)).append("\n");
            
            ExecutionSpecification spec = SpecificationLoader.parseYaml(sb.toString());
            
            assertThat(spec.getServiceContractId()).isEqualTo("TestCase");
            assertThat(spec.getApprovalNotes()).isEqualTo("Some notes here");
        }

        @Test
        @DisplayName("parses version field for backwards compatibility")
        void parsesVersionField() {
            StringBuilder sb = new StringBuilder();
            sb.append("specId: TestCase\n");
            sb.append("useCaseId: TestCase\n");
            sb.append("version: 2\n");
            sb.append("requirements:\n");
            sb.append("  minPassRate: 0.9\n");
            sb.append("schemaVersion: punit-spec-1\n");
            String content = sb.toString();
            sb.append("contentFingerprint: ").append(computeFingerprint(content)).append("\n");
            
            ExecutionSpecification spec = SpecificationLoader.parseYaml(sb.toString());
            
            assertThat(spec.getVersion()).isEqualTo(2);
        }

        @Test
        @DisplayName("parses boolean values in context")
        void parsesBooleanValuesInContext() {
            StringBuilder sb = new StringBuilder();
            sb.append("specId: TestCase\n");
            sb.append("useCaseId: TestCase\n");
            sb.append("executionContext:\n");
            sb.append("  enabled: true\n");
            sb.append("  disabled: false\n");
            sb.append("requirements:\n");
            sb.append("  minPassRate: 0.9\n");
            sb.append("schemaVersion: punit-spec-1\n");
            String content = sb.toString();
            sb.append("contentFingerprint: ").append(computeFingerprint(content)).append("\n");
            
            ExecutionSpecification spec = SpecificationLoader.parseYaml(sb.toString());
            
            assertThat(spec.getExecutionContext()).containsEntry("enabled", true);
            assertThat(spec.getExecutionContext()).containsEntry("disabled", false);
        }

        @Test
        @DisplayName("parses integer values in context")
        void parsesIntegerValuesInContext() {
            StringBuilder sb = new StringBuilder();
            sb.append("specId: TestCase\n");
            sb.append("useCaseId: TestCase\n");
            sb.append("executionContext:\n");
            sb.append("  count: 42\n");
            sb.append("requirements:\n");
            sb.append("  minPassRate: 0.9\n");
            sb.append("schemaVersion: punit-spec-1\n");
            String content = sb.toString();
            sb.append("contentFingerprint: ").append(computeFingerprint(content)).append("\n");
            
            ExecutionSpecification spec = SpecificationLoader.parseYaml(sb.toString());
            
            assertThat(spec.getExecutionContext()).containsEntry("count", 42L);
        }

        @Test
        @DisplayName("parses baselineData section (v1 format)")
        void parsesBaselineData() {
            StringBuilder sb = new StringBuilder();
            sb.append("specId: TestCase\n");
            sb.append("useCaseId: TestCase\n");
            sb.append("baselineData:\n");
            sb.append("  samples: 1000\n");
            sb.append("  successes: 850\n");
            sb.append("  generatedAt: 2026-01-09T10:00:00Z\n");
            sb.append("requirements:\n");
            sb.append("  minPassRate: 0.85\n");
            sb.append("schemaVersion: punit-spec-1\n");
            String content = sb.toString();
            sb.append("contentFingerprint: ").append(computeFingerprint(content)).append("\n");

            ExecutionSpecification spec = SpecificationLoader.parseYaml(sb.toString());

            assertThat(spec.hasEmpiricalBasis()).isTrue();
            assertThat(spec.getEmpiricalBasis().samples()).isEqualTo(1000);
            assertThat(spec.getEmpiricalBasis().successes()).isEqualTo(850);
            assertThat(spec.getObservedRate()).isEqualTo(0.85);
        }
    }

    @Nested
    @DisplayName("parseYaml - v2 schema")
    class ParseYamlV2 {

        @Test
        @DisplayName("parses v2 spec without approval metadata")
        void parsesV2WithoutApproval() {
            String yaml = createV2Yaml("TestServiceContract", 1000, 900);

            ExecutionSpecification spec = SpecificationLoader.parseYaml(yaml);

            assertThat(spec.getServiceContractId()).isEqualTo("TestServiceContract");
            assertThat(spec.hasApprovalMetadata()).isFalse();
            assertThat(spec.hasEmpiricalBasis()).isTrue();
        }

        @Test
        @DisplayName("parses empiricalBasis section")
        void parsesEmpiricalBasis() {
            String yaml = createV2Yaml("TestServiceContract", 500, 475);

            ExecutionSpecification spec = SpecificationLoader.parseYaml(yaml);

            assertThat(spec.getEmpiricalBasis().samples()).isEqualTo(500);
            assertThat(spec.getEmpiricalBasis().successes()).isEqualTo(475);
            assertThat(spec.getObservedRate()).isEqualTo(0.95);
        }

        @Test
        @DisplayName("parses generatedAt at top level")
        void parsesGeneratedAt() {
            String yaml = createV2Yaml("TestServiceContract", 100, 90);

            ExecutionSpecification spec = SpecificationLoader.parseYaml(yaml);

            assertThat(spec.getGeneratedAt()).isNotNull();
        }

        @Test
        @DisplayName("parses extendedStatistics section")
        void parsesExtendedStatistics() {
            StringBuilder sb = new StringBuilder();
            sb.append("specId: TestCase\n");
            sb.append("useCaseId: TestCase\n");
            sb.append("empiricalBasis:\n");
            sb.append("  samples: 1000\n");
            sb.append("  successes: 950\n");
            sb.append("extendedStatistics:\n");
            sb.append("  standardError: 0.0069\n");
            sb.append("  confidenceIntervalLower: 0.936\n");
            sb.append("  confidenceIntervalUpper: 0.964\n");
            sb.append("  totalTimeMs: 60000\n");
            sb.append("  avgTimePerSampleMs: 60\n");
            sb.append("  totalTokens: 500000\n");
            sb.append("  avgTokensPerSample: 500\n");
            sb.append("requirements:\n");
            sb.append("  minPassRate: 0.93\n");
            sb.append("schemaVersion: punit-spec-2\n");
            String content = sb.toString();
            sb.append("contentFingerprint: ").append(computeFingerprint(content)).append("\n");

            ExecutionSpecification spec = SpecificationLoader.parseYaml(sb.toString());

            assertThat(spec.getExtendedStatistics()).isNotNull();
            assertThat(spec.getExtendedStatistics().standardError()).isEqualTo(0.0069);
            assertThat(spec.getExtendedStatistics().confidenceIntervalLower()).isEqualTo(0.936);
            assertThat(spec.getExtendedStatistics().confidenceIntervalUpper()).isEqualTo(0.964);
            assertThat(spec.getExtendedStatistics().totalTimeMs()).isEqualTo(60000);
            assertThat(spec.getExtendedStatistics().avgTokensPerSample()).isEqualTo(500);
        }

        @Test
        @DisplayName("parses failureDistribution in extendedStatistics")
        void parsesFailureDistribution() {
            StringBuilder sb = new StringBuilder();
            sb.append("specId: TestCase\n");
            sb.append("useCaseId: TestCase\n");
            sb.append("empiricalBasis:\n");
            sb.append("  samples: 1000\n");
            sb.append("  successes: 900\n");
            sb.append("extendedStatistics:\n");
            sb.append("  standardError: 0.01\n");
            sb.append("  failureDistribution:\n");
            sb.append("    invalid_json: 50\n");
            sb.append("    missing_fields: 30\n");
            sb.append("    timeout: 20\n");
            sb.append("requirements:\n");
            sb.append("  minPassRate: 0.9\n");
            sb.append("schemaVersion: punit-spec-2\n");
            String content = sb.toString();
            sb.append("contentFingerprint: ").append(computeFingerprint(content)).append("\n");

            ExecutionSpecification spec = SpecificationLoader.parseYaml(sb.toString());

            assertThat(spec.getExtendedStatistics().failureDistribution())
                    .containsEntry("invalid_json", 50)
                    .containsEntry("missing_fields", 30)
                    .containsEntry("timeout", 20);
        }

        @Test
        @DisplayName("parses configuration section (alias for executionContext)")
        void parsesConfigurationSection() {
            StringBuilder sb = new StringBuilder();
            sb.append("specId: TestCase\n");
            sb.append("useCaseId: TestCase\n");
            sb.append("configuration:\n");
            sb.append("  model: gpt-4\n");
            sb.append("  temperature: 0.2\n");
            sb.append("empiricalBasis:\n");
            sb.append("  samples: 100\n");
            sb.append("  successes: 95\n");
            sb.append("requirements:\n");
            sb.append("  minPassRate: 0.9\n");
            sb.append("schemaVersion: punit-spec-2\n");
            String content = sb.toString();
            sb.append("contentFingerprint: ").append(computeFingerprint(content)).append("\n");

            ExecutionSpecification spec = SpecificationLoader.parseYaml(sb.toString());

            assertThat(spec.getExecutionContext())
                    .containsEntry("model", "gpt-4")
                    .containsEntry("temperature", 0.2);
        }
    }

    @Nested
    @DisplayName("parseYaml - raw MEASURE output sections")
    class ParseYamlMeasureOutput {

        @Test
        @DisplayName("populates empirical basis from execution and statistics sections")
        void populatesEmpiricalBasisFromExecutionAndStatistics() {
            StringBuilder sb = new StringBuilder();
            sb.append("schemaVersion: punit-spec-2\n");
            sb.append("specId: TestCase\n");
            sb.append("useCaseId: TestCase\n");
            sb.append("generatedAt: 2026-01-09T10:00:00Z\n");
            sb.append("execution:\n");
            sb.append("  samplesPlanned: 1000\n");
            sb.append("  samplesExecuted: 1000\n");
            sb.append("  terminationReason: COMPLETED\n");
            sb.append("statistics:\n");
            sb.append("  successRate:\n");
            sb.append("    observed: 0.85\n");
            sb.append("    standardError: 0.0113\n");
            sb.append("  successes: 850\n");
            sb.append("  failures: 150\n");
            sb.append("requirements:\n");
            sb.append("  minPassRate: 0.80\n");
            String content = sb.toString();
            sb.append("contentFingerprint: ").append(computeFingerprint(content)).append("\n");

            ExecutionSpecification spec = SpecificationLoader.parseYaml(sb.toString());

            assertThat(spec.hasEmpiricalBasis()).isTrue();
            assertThat(spec.getEmpiricalBasis().samples()).isEqualTo(1000);
            assertThat(spec.getEmpiricalBasis().successes()).isEqualTo(850);
            assertThat(spec.getObservedRate()).isEqualTo(0.85);
        }

        @Test
        @DisplayName("does not override empiricalBasis when already provided")
        void doesNotOverrideExplicitEmpiricalBasis() {
            StringBuilder sb = new StringBuilder();
            sb.append("schemaVersion: punit-spec-2\n");
            sb.append("specId: TestCase\n");
            sb.append("useCaseId: TestCase\n");
            sb.append("empiricalBasis:\n");
            sb.append("  samples: 500\n");
            sb.append("  successes: 475\n");
            sb.append("  generatedAt: 2026-01-09T10:00:00Z\n");
            sb.append("execution:\n");
            sb.append("  samplesExecuted: 1000\n");
            sb.append("statistics:\n");
            sb.append("  successes: 850\n");
            sb.append("requirements:\n");
            sb.append("  minPassRate: 0.80\n");
            String content = sb.toString();
            sb.append("contentFingerprint: ").append(computeFingerprint(content)).append("\n");

            ExecutionSpecification spec = SpecificationLoader.parseYaml(sb.toString());

            assertThat(spec.getEmpiricalBasis().samples()).isEqualTo(500);
            assertThat(spec.getEmpiricalBasis().successes()).isEqualTo(475);
        }

        @Test
        @DisplayName("parses cost section as alias for costEnvelope")
        void parsesCostSectionAlias() {
            StringBuilder sb = new StringBuilder();
            sb.append("schemaVersion: punit-spec-2\n");
            sb.append("specId: TestCase\n");
            sb.append("useCaseId: TestCase\n");
            sb.append("cost:\n");
            sb.append("  totalTokenBudget: 50000\n");
            sb.append("  maxTimePerSampleMs: 200\n");
            sb.append("  maxTokensPerSample: 800\n");
            sb.append("requirements:\n");
            sb.append("  minPassRate: 0.90\n");
            String content = sb.toString();
            sb.append("contentFingerprint: ").append(computeFingerprint(content)).append("\n");

            ExecutionSpecification spec = SpecificationLoader.parseYaml(sb.toString());

            assertThat(spec.getCostEnvelope()).isNotNull();
            assertThat(spec.getCostEnvelope().totalTokenBudget()).isEqualTo(50000);
            assertThat(spec.getCostEnvelope().maxTimePerSampleMs()).isEqualTo(200);
            assertThat(spec.getCostEnvelope().maxTokensPerSample()).isEqualTo(800);
        }

        @Test
        @DisplayName("parses failure distribution from statistics section")
        void parsesFailureDistributionFromStatistics() {
            StringBuilder sb = new StringBuilder();
            sb.append("schemaVersion: punit-spec-2\n");
            sb.append("specId: TestCase\n");
            sb.append("useCaseId: TestCase\n");
            sb.append("execution:\n");
            sb.append("  samplesExecuted: 100\n");
            sb.append("statistics:\n");
            sb.append("  successes: 90\n");
            sb.append("  standardError: 0.03\n");
            sb.append("  failureDistribution:\n");
            sb.append("    invalid_format: 6\n");
            sb.append("    timeout: 4\n");
            sb.append("requirements:\n");
            sb.append("  minPassRate: 0.85\n");
            String content = sb.toString();
            sb.append("contentFingerprint: ").append(computeFingerprint(content)).append("\n");

            ExecutionSpecification spec = SpecificationLoader.parseYaml(sb.toString());

            assertThat(spec.getExtendedStatistics()).isNotNull();
            assertThat(spec.getExtendedStatistics().standardError()).isEqualTo(0.03);
            assertThat(spec.getExtendedStatistics().failureDistribution())
                    .containsEntry("invalid_format", 6)
                    .containsEntry("timeout", 4);
        }

        @Test
        @DisplayName("parses standard error from statistics section")
        void parsesStandardErrorFromStatistics() {
            StringBuilder sb = new StringBuilder();
            sb.append("schemaVersion: punit-spec-2\n");
            sb.append("specId: TestCase\n");
            sb.append("useCaseId: TestCase\n");
            sb.append("execution:\n");
            sb.append("  samplesExecuted: 200\n");
            sb.append("statistics:\n");
            sb.append("  successes: 180\n");
            sb.append("  standardError: 0.0283\n");
            sb.append("requirements:\n");
            sb.append("  minPassRate: 0.85\n");
            String content = sb.toString();
            sb.append("contentFingerprint: ").append(computeFingerprint(content)).append("\n");

            ExecutionSpecification spec = SpecificationLoader.parseYaml(sb.toString());

            assertThat(spec.getExtendedStatistics()).isNotNull();
            assertThat(spec.getExtendedStatistics().standardError()).isEqualTo(0.0283);
        }
    }

    @Nested
    @DisplayName("integrity validation")
    class IntegrityValidation {

        @Test
        @DisplayName("throws when schemaVersion is missing")
        void throwsWhenSchemaVersionMissing() {
            String yaml = """
                specId: TestCase
                useCaseId: TestCase
                requirements:
                  minPassRate: 0.9
                contentFingerprint: abc123
                """;
            
            assertThatThrownBy(() -> SpecificationLoader.parseYaml(yaml))
                .isInstanceOf(SpecificationIntegrityException.class)
                .hasMessageContaining("Missing schemaVersion");
        }

        @Test
        @DisplayName("throws when schemaVersion is unsupported")
        void throwsWhenSchemaVersionUnsupported() {
            String yaml = """
                specId: TestCase
                useCaseId: TestCase
                requirements:
                  minPassRate: 0.9
                schemaVersion: punit-spec-99
                contentFingerprint: abc123
                """;
            
            assertThatThrownBy(() -> SpecificationLoader.parseYaml(yaml))
                .isInstanceOf(SpecificationIntegrityException.class)
                .hasMessageContaining("Unsupported schema version");
        }

        @Test
        @DisplayName("throws when contentFingerprint is missing")
        void throwsWhenFingerprintMissing() {
            String yaml = """
                specId: TestCase
                useCaseId: TestCase
                requirements:
                  minPassRate: 0.9
                schemaVersion: punit-spec-1
                """;
            
            assertThatThrownBy(() -> SpecificationLoader.parseYaml(yaml))
                .isInstanceOf(SpecificationIntegrityException.class)
                .hasMessageContaining("Missing contentFingerprint");
        }

        @Test
        @DisplayName("throws when content has been tampered with")
        void throwsWhenContentTampered() {
            // Create valid YAML then modify it
            String validYaml = createValidYaml("TestCase", 0.85);
            String tamperedYaml = validYaml.replace("minPassRate: 0.85", "minPassRate: 0.5");
            
            assertThatThrownBy(() -> SpecificationLoader.parseYaml(tamperedYaml))
                .isInstanceOf(SpecificationIntegrityException.class)
                .hasMessageContaining("fingerprint mismatch");
        }

        @Test
        @DisplayName("accepts valid fingerprint for v1 schema")
        void acceptsValidFingerprintV1() {
            String yaml = createValidYaml("TestCase", 0.9);
            
            // Should not throw
            ExecutionSpecification spec = SpecificationLoader.parseYaml(yaml);
            assertThat(spec.getMinPassRate()).isEqualTo(0.9);
        }

        @Test
        @DisplayName("accepts valid fingerprint for v2 schema")
        void acceptsValidFingerprintV2() {
            String yaml = createV2Yaml("TestCase", 1000, 900);

            // Should not throw
            ExecutionSpecification spec = SpecificationLoader.parseYaml(yaml);
            assertThat(spec.hasEmpiricalBasis()).isTrue();
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("v2 spec without approval passes validation")
        void v2SpecWithoutApprovalPassesValidation() {
            String yaml = createV2Yaml("TestCase", 1000, 900);

            ExecutionSpecification spec = SpecificationLoader.parseYaml(yaml);

            // Should not throw
            spec.validate();
        }

        @Test
        @DisplayName("spec with invalid minPassRate fails validation")
        void invalidMinPassRateFailsValidation() {
            StringBuilder sb = new StringBuilder();
            sb.append("specId: TestCase\n");
            sb.append("useCaseId: TestCase\n");
            sb.append("requirements:\n");
            sb.append("  minPassRate: 1.5\n");
            sb.append("schemaVersion: punit-spec-2\n");
            String content = sb.toString();
            sb.append("contentFingerprint: ").append(computeFingerprint(content)).append("\n");

            ExecutionSpecification spec = SpecificationLoader.parseYaml(sb.toString());

            assertThatThrownBy(spec::validate)
                    .isInstanceOf(org.mavai.punit.internal.engine.spec.model.SpecificationValidationException.class)
                    .hasMessageContaining("invalid minPassRate");
        }
    }
}
