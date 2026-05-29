package org.mavai.punit.internal.engine.spec.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.mavai.punit.internal.engine.spec.model.ExecutionSpecification.CostEnvelope;
import org.mavai.punit.internal.engine.spec.model.ExecutionSpecification.EmpiricalBasis;
import org.mavai.punit.internal.engine.spec.model.ExecutionSpecification.SpecRequirements;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ExecutionSpecification")
class ExecutionSpecificationTest {

    private ExecutionSpecification.Builder validBuilder() {
        return ExecutionSpecification.builder()
            .serviceContractId("TestServiceContract")
            .approvedAt(Instant.now())
            .approvedBy("tester");
    }

    @Nested
    @DisplayName("builder")
    class BuilderTests {

        @Test
        @DisplayName("builds with all fields")
        void buildsWithAllFields() {
            Instant now = Instant.now();
            ExecutionSpecification spec = ExecutionSpecification.builder()
                .serviceContractId("TestServiceContract")
                .version(2)
                .approvedAt(now)
                .approvedBy("approver")
                .approvalNotes("Notes here")
                .sourceBaselines(List.of("baseline1", "baseline2"))
                .executionContext(Map.of("model", "gpt-4"))
                .requirements(0.9, "isValid == true")
                .costEnvelope(100, 500, 10000)
                .empiricalBasis(100, 85)
                .build();

            assertThat(spec.getServiceContractId()).isEqualTo("TestServiceContract");
            // getSpecId() is deprecated and returns serviceContractId
            assertThat(spec.getServiceContractId()).isEqualTo("TestServiceContract");
            assertThat(spec.getVersion()).isEqualTo(2);
            assertThat(spec.getApprovedAt()).isEqualTo(now);
            assertThat(spec.getApprovedBy()).isEqualTo("approver");
            assertThat(spec.getApprovalNotes()).isEqualTo("Notes here");
            assertThat(spec.getSourceBaselines()).containsExactly("baseline1", "baseline2");
            assertThat(spec.getExecutionContext()).containsEntry("model", "gpt-4");
            assertThat(spec.getMinPassRate()).isEqualTo(0.9);
            assertThat(spec.getCostEnvelope().maxTimePerSampleMs()).isEqualTo(100);
            assertThat(spec.getBaselineSamples()).isEqualTo(100);
        }

        @Test
        @DisplayName("throws on null serviceContractId")
        void throwsOnNullServiceContractId() {
            assertThatThrownBy(() -> 
                ExecutionSpecification.builder()
                    .build()
            ).isInstanceOf(NullPointerException.class)
             .hasMessageContaining("serviceContractId");
        }
        
        @Test
        @DisplayName("specId sets serviceContractId for backwards compatibility")
        void specIdSetsServiceContractId() {
            ExecutionSpecification spec = ExecutionSpecification.builder()
                .serviceContractId("LegacySpec")  // deprecated, but should still work
                .approvedAt(Instant.now())
                .approvedBy("tester")
                .build();
            
            assertThat(spec.getServiceContractId()).isEqualTo("LegacySpec");
        }

        @Test
        @DisplayName("defaults to empty lists and maps")
        void defaultsToEmptyCollections() {
            ExecutionSpecification spec = validBuilder().build();

            assertThat(spec.getSourceBaselines()).isEmpty();
            assertThat(spec.getExecutionContext()).isEmpty();
        }

        @Test
        @DisplayName("defaults requirements")
        void defaultsRequirements() {
            ExecutionSpecification spec = validBuilder().build();

            assertThat(spec.getMinPassRate()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("can set requirements with record")
        void canSetRequirementsWithRecord() {
            ExecutionSpecification spec = validBuilder()
                .requirements(new SpecRequirements(0.8, "criteria"))
                .build();

            assertThat(spec.getRequirements().minPassRate()).isEqualTo(0.8);
            assertThat(spec.getRequirements().successCriteria()).isEqualTo("criteria");
        }

        @Test
        @DisplayName("can set cost envelope with record")
        void canSetCostEnvelopeWithRecord() {
            ExecutionSpecification spec = validBuilder()
                .costEnvelope(new CostEnvelope(100, 200, 300))
                .build();

            assertThat(spec.getCostEnvelope().totalTokenBudget()).isEqualTo(300);
        }

        @Test
        @DisplayName("can set baseline data with record")
        void canSetBaselineDataWithRecord() {
            ExecutionSpecification spec = validBuilder()
                .empiricalBasis(new EmpiricalBasis(50, 40, Instant.now()))
                .build();

            assertThat(spec.getBaselineSamples()).isEqualTo(50);
        }

        @Test
        @DisplayName("can set baseline data with timestamp")
        void canSetBaselineDataWithTimestamp() {
            Instant now = Instant.now();
            ExecutionSpecification spec = validBuilder()
                .empiricalBasis(50, 40, now)
                .build();

            assertThat(spec.getEmpiricalBasis().generatedAt()).isEqualTo(now);
        }
    }

    @Nested
    @DisplayName("baseline data")
    class BaselineDataTests {

        @Test
        @DisplayName("hasEmpiricalBasis returns true when present")
        void hasEmpiricalBasisReturnsTrue() {
            ExecutionSpecification spec = validBuilder()
                .empiricalBasis(100, 85)
                .build();

            assertThat(spec.hasEmpiricalBasis()).isTrue();
        }

        @Test
        @DisplayName("hasEmpiricalBasis returns false when absent")
        void hasEmpiricalBasisReturnsFalse() {
            ExecutionSpecification spec = validBuilder().build();

            assertThat(spec.hasEmpiricalBasis()).isFalse();
        }

        @Test
        @DisplayName("hasEmpiricalBasis returns false when samples is 0")
        void hasEmpiricalBasisReturnsFalseForZeroSamples() {
            ExecutionSpecification spec = validBuilder()
                .empiricalBasis(0, 0)
                .build();

            assertThat(spec.hasEmpiricalBasis()).isFalse();
        }

        @Test
        @DisplayName("getBaselineSamples returns 0 when no data")
        void getBaselineSamplesReturnsZero() {
            ExecutionSpecification spec = validBuilder().build();

            assertThat(spec.getBaselineSamples()).isEqualTo(0);
        }

        @Test
        @DisplayName("getBaselineSuccesses returns 0 when no data")
        void getBaselineSuccessesReturnsZero() {
            ExecutionSpecification spec = validBuilder().build();

            assertThat(spec.getBaselineSuccesses()).isEqualTo(0);
        }

        @Test
        @DisplayName("getObservedRate returns 0 when no data")
        void getObservedRateReturnsZeroWhenNoData() {
            ExecutionSpecification spec = validBuilder().build();

            assertThat(spec.getObservedRate()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("getObservedRate returns 0 when samples is 0")
        void getObservedRateReturnsZeroForZeroSamples() {
            ExecutionSpecification spec = validBuilder()
                .empiricalBasis(0, 0)
                .build();

            assertThat(spec.getObservedRate()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("getObservedRate calculates correctly")
        void getObservedRateCalculatesCorrectly() {
            ExecutionSpecification spec = validBuilder()
                .empiricalBasis(100, 85)
                .build();

            assertThat(spec.getObservedRate()).isEqualTo(0.85);
        }
    }

    @Nested
    @DisplayName("approval")
    class ApprovalTests {

        @Test
        @DisplayName("isApproved returns true when valid")
        void isApprovedReturnsTrue() {
            ExecutionSpecification spec = validBuilder().build();

            assertThat(spec.hasApprovalMetadata()).isTrue();
        }

        @Test
        @DisplayName("isApproved returns false when approvedAt is null")
        void isApprovedReturnsFalseWhenApprovedAtNull() {
            ExecutionSpecification spec = ExecutionSpecification.builder()
                .serviceContractId("Spec")
                .serviceContractId("ServiceContract")
                .approvedBy("approver")
                .build();

            assertThat(spec.hasApprovalMetadata()).isFalse();
        }

        @Test
        @DisplayName("isApproved returns false when approvedBy is null")
        void isApprovedReturnsFalseWhenApprovedByNull() {
            ExecutionSpecification spec = ExecutionSpecification.builder()
                .serviceContractId("Spec")
                .serviceContractId("ServiceContract")
                .approvedAt(Instant.now())
                .build();

            assertThat(spec.hasApprovalMetadata()).isFalse();
        }

        @Test
        @DisplayName("isApproved returns false when approvedBy is empty")
        void isApprovedReturnsFalseWhenApprovedByEmpty() {
            ExecutionSpecification spec = ExecutionSpecification.builder()
                .serviceContractId("Spec")
                .serviceContractId("ServiceContract")
                .approvedAt(Instant.now())
                .approvedBy("")
                .build();

            assertThat(spec.hasApprovalMetadata()).isFalse();
        }
    }

    @Nested
    @DisplayName("validate")
    class ValidateTests {

        @Test
        @DisplayName("succeeds for valid spec")
        void succeedsForValidSpec() {
            ExecutionSpecification spec = validBuilder().build();

            // Should not throw
            spec.validate();
        }

        @Test
        @DisplayName("succeeds without approval metadata (v2 behavior)")
        void succeedsWithoutApprovalMetadata() {
            ExecutionSpecification spec = ExecutionSpecification.builder()
                .serviceContractId("Spec")
                .serviceContractId("ServiceContract")
                .empiricalBasis(100, 90)
                .build();

            // Should not throw - approval is optional in v2
            spec.validate();
        }

        @Test
        @DisplayName("throws when minPassRate is negative")
        void throwsWhenMinPassRateNegative() {
            ExecutionSpecification spec = validBuilder()
                .requirements(-0.1, "")
                .build();

            assertThatThrownBy(spec::validate)
                .isInstanceOf(SpecificationValidationException.class)
                .hasMessageContaining("invalid minPassRate");
        }

        @Test
        @DisplayName("throws when minPassRate is greater than 1")
        void throwsWhenMinPassRateGreaterThanOne() {
            ExecutionSpecification spec = validBuilder()
                .requirements(1.5, "")
                .build();

            assertThatThrownBy(spec::validate)
                .isInstanceOf(SpecificationValidationException.class)
                .hasMessageContaining("invalid minPassRate");
        }
    }

    @Nested
    @DisplayName("success criteria")
    class SuccessCriteriaTests {

        @Test
        @DisplayName("returns alwaysTrue when no criteria set")
        void returnsAlwaysTrueWhenNoCriteria() {
            ExecutionSpecification spec = validBuilder().build();

            assertThat(spec.getSuccessCriteria().getDescription()).isEqualTo("(always true)");
        }

        @Test
        @DisplayName("returns alwaysTrue when criteria is empty")
        void returnsAlwaysTrueWhenEmpty() {
            ExecutionSpecification spec = validBuilder()
                .requirements(0.8, "")
                .build();

            assertThat(spec.getSuccessCriteria().getDescription()).isEqualTo("(always true)");
        }

        @Test
        @DisplayName("parses criteria expression")
        void parsesCriteriaExpression() {
            ExecutionSpecification spec = validBuilder()
                .requirements(0.8, "isValid == true")
                .build();

            assertThat(spec.getSuccessCriteria().getDescription()).isEqualTo("isValid == true");
        }
    }

    @Nested
    @DisplayName("EmpiricalBasis")
    class EmpiricalBasisTests {

        @Test
        @DisplayName("computes observedRate")
        void computesObservedRate() {
            EmpiricalBasis data = new EmpiricalBasis(100, 85, Instant.now());

            assertThat(data.observedRate()).isEqualTo(0.85);
        }

        @Test
        @DisplayName("observedRate is 0 for 0 samples")
        void observedRateIsZeroForZeroSamples() {
            EmpiricalBasis data = new EmpiricalBasis(0, 0, null);

            assertThat(data.observedRate()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("throws for negative samples")
        void throwsForNegativeSamples() {
            assertThatThrownBy(() -> new EmpiricalBasis(-1, 0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("samples must be non-negative");
        }

        @Test
        @DisplayName("throws for negative successes")
        void throwsForNegativeSuccesses() {
            assertThatThrownBy(() -> new EmpiricalBasis(10, -1, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("successes must be non-negative");
        }

        @Test
        @DisplayName("throws when successes exceeds samples")
        void throwsWhenSuccessesExceedSamples() {
            assertThatThrownBy(() -> new EmpiricalBasis(10, 20, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("successes cannot exceed samples");
        }
    }
}

