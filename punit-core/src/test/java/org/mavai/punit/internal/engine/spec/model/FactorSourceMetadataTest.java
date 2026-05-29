package org.mavai.punit.internal.engine.spec.model;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import org.mavai.punit.internal.engine.spec.model.ExecutionSpecification.FactorSourceMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("FactorSourceMetadata")
class FactorSourceMetadataTest {

    @Nested
    @DisplayName("hasHash()")
    class HasHash {

        @Test
        @DisplayName("should return true when hash is present")
        void shouldReturnTrueWhenHashIsPresent() {
            var metadata = new FactorSourceMetadata("abc123", "source", 100, false);
            assertThat(metadata.hasHash()).isTrue();
        }

        @Test
        @DisplayName("should return false when hash is null")
        void shouldReturnFalseWhenHashIsNull() {
            var metadata = new FactorSourceMetadata(null, "source", 100, false);
            assertThat(metadata.hasHash()).isFalse();
        }

        @Test
        @DisplayName("should return false when hash is empty")
        void shouldReturnFalseWhenHashIsEmpty() {
            var metadata = new FactorSourceMetadata("", "source", 100, false);
            assertThat(metadata.hasHash()).isFalse();
        }
    }

    @Nested
    @DisplayName("ExecutionSpecification integration")
    class SpecificationIntegration {

        @Test
        @DisplayName("should store and retrieve factor source metadata")
        void shouldStoreAndRetrieveMetadata() {
            var spec = ExecutionSpecification.builder()
                    .serviceContractId("test-spec")
                    .serviceContractId("TestServiceContract")
                    .factorSourceMetadata("hash123", "productQueries", 500)
                    .build();

            assertThat(spec.getFactorSourceMetadata()).isNotNull();
            assertThat(spec.getFactorSourceMetadata().sourceHash()).isEqualTo("hash123");
            assertThat(spec.getFactorSourceMetadata().sourceName()).isEqualTo("productQueries");
            assertThat(spec.getFactorSourceMetadata().samplesUsed()).isEqualTo(500);
            assertThat(spec.getFactorSourceMetadata().earlyTermination()).isFalse();
        }

        @Test
        @DisplayName("should store early termination flag")
        void shouldStoreEarlyTerminationFlag() {
            var spec = ExecutionSpecification.builder()
                    .serviceContractId("test-spec")
                    .serviceContractId("TestServiceContract")
                    .factorSourceMetadata("hash123", "source", 50, true)
                    .build();

            assertThat(spec.getFactorSourceMetadata().earlyTermination()).isTrue();
        }

        @Test
        @DisplayName("should return null when no metadata set")
        void shouldReturnNullWhenNoMetadataSet() {
            var spec = ExecutionSpecification.builder()
                    .serviceContractId("test-spec")
                    .serviceContractId("TestServiceContract")
                    .build();

            assertThat(spec.getFactorSourceMetadata()).isNull();
            assertThat(spec.hasFactorSourceMetadata()).isFalse();
        }

        @Test
        @DisplayName("hasFactorSourceMetadata() should check for valid hash")
        void hasFactorSourceMetadataShouldCheckForValidHash() {
            var specWithHash = ExecutionSpecification.builder()
                    .serviceContractId("test-spec")
                    .serviceContractId("TestServiceContract")
                    .factorSourceMetadata("hash123", "source", 100)
                    .build();

            var specWithoutHash = ExecutionSpecification.builder()
                    .serviceContractId("test-spec")
                    .serviceContractId("TestServiceContract")
                    .factorSourceMetadata(new FactorSourceMetadata(null, "source", 100, false))
                    .build();

            assertThat(specWithHash.hasFactorSourceMetadata()).isTrue();
            assertThat(specWithoutHash.hasFactorSourceMetadata()).isFalse();
        }

        @Test
        @DisplayName("should work alongside other spec fields")
        void shouldWorkAlongsideOtherSpecFields() {
            var spec = ExecutionSpecification.builder()
                    .serviceContractId("ProductSearch")
                    .version(2)
                    .generatedAt(Instant.now())
                    .empiricalBasis(1000, 940)
                    .factorSourceMetadata("abc123def456", "standardQueries", 1000)
                    .build();

            assertThat(spec.getServiceContractId()).isEqualTo("ProductSearch");
            assertThat(spec.getEmpiricalBasis().samples()).isEqualTo(1000);
            assertThat(spec.getFactorSourceMetadata().sourceHash()).isEqualTo("abc123def456");
        }
    }
}

