package org.mavai.punit.sentinel;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EnvironmentMetadataTest {

    @Nested
    @DisplayName("explicit construction")
    class ExplicitConstruction {

        @Test
        @DisplayName("preserves provided values")
        void preservesProvidedValues() {
            var metadata = new EnvironmentMetadata("staging", "pod-42");

            assertThat(metadata.environmentId()).isEqualTo("staging");
            assertThat(metadata.instanceId()).isEqualTo("pod-42");
        }
    }

    @Nested
    @DisplayName("toMap()")
    class ToMap {

        @Test
        @DisplayName("returns map with environment and instance keys")
        void returnsMapWithExpectedKeys() {
            var metadata = new EnvironmentMetadata("prod", "host-1");
            var map = metadata.toMap();

            assertThat(map).containsEntry("environment", "prod");
            assertThat(map).containsEntry("instance", "host-1");
            assertThat(map).hasSize(2);
        }
    }

    @Nested
    @DisplayName("fromEnvironment()")
    class FromEnvironment {

        @Test
        @DisplayName("resolves system property for environmentId when set")
        void resolvesEnvironmentFromSystemProperty() {
            String previous = System.getProperty("punit.environment");
            try {
                System.setProperty("punit.environment", "test-env");
                var metadata = EnvironmentMetadata.fromEnvironment();

                assertThat(metadata.environmentId()).isEqualTo("test-env");
            } finally {
                if (previous != null) {
                    System.setProperty("punit.environment", previous);
                } else {
                    System.clearProperty("punit.environment");
                }
            }
        }

        @Test
        @DisplayName("resolves system property for instanceId when set")
        void resolvesInstanceIdFromSystemProperty() {
            String previous = System.getProperty("punit.instanceId");
            try {
                System.setProperty("punit.instanceId", "my-pod");
                var metadata = EnvironmentMetadata.fromEnvironment();

                assertThat(metadata.instanceId()).isEqualTo("my-pod");
            } finally {
                if (previous != null) {
                    System.setProperty("punit.instanceId", previous);
                } else {
                    System.clearProperty("punit.instanceId");
                }
            }
        }

        @Test
        @DisplayName("falls back to default when neither system property nor env var is set")
        void fallsBackToDefault() {
            String prevEnv = System.getProperty("punit.environment");
            try {
                System.clearProperty("punit.environment");
                var metadata = EnvironmentMetadata.fromEnvironment();

                // Without env var set, falls back to "unknown"
                // (env var PUNIT_ENVIRONMENT is not set in test context)
                assertThat(metadata.environmentId()).isNotNull();
                assertThat(metadata.environmentId()).isNotEmpty();
            } finally {
                if (prevEnv != null) {
                    System.setProperty("punit.environment", prevEnv);
                }
            }
        }

        @Test
        @DisplayName("instanceId defaults to a non-empty value (hostname or 'unknown')")
        void instanceIdDefaultsToNonEmpty() {
            String previous = System.getProperty("punit.instanceId");
            try {
                System.clearProperty("punit.instanceId");
                var metadata = EnvironmentMetadata.fromEnvironment();

                assertThat(metadata.instanceId()).isNotNull();
                assertThat(metadata.instanceId()).isNotEmpty();
            } finally {
                if (previous != null) {
                    System.setProperty("punit.instanceId", previous);
                }
            }
        }
    }
}
