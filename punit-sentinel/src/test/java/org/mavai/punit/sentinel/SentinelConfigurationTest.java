package org.mavai.punit.sentinel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.mavai.punit.internal.reporting.CompositeVerdictSink;
import org.mavai.punit.internal.reporting.LogVerdictSink;
import org.mavai.punit.verdict.VerdictSink;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SentinelConfigurationTest {

    @Nested
    @DisplayName("builder validation")
    class BuilderValidation {

        @Test
        @DisplayName("throws when no sentinel classes are registered")
        void throwsWhenNoSentinelClasses() {
            assertThatThrownBy(() -> SentinelConfiguration.builder().build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("At least one registered class");
        }

        @Test
        @DisplayName("rejects null sentinel class")
        void rejectsNullSentinelClass() {
            assertThatThrownBy(() -> SentinelConfiguration.builder().sentinelClass(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects null sentinel classes list")
        void rejectsNullSentinelClassesList() {
            assertThatThrownBy(() -> SentinelConfiguration.builder().sentinelClasses(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects null verdict sink")
        void rejectsNullVerdictSink() {
            assertThatThrownBy(() -> SentinelConfiguration.builder().verdictSink(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("defaults")
    class Defaults {

        @Test
        @DisplayName("defaults to LogVerdictSink when no sinks are added")
        void defaultsToLogVerdictSink() {
            var config = SentinelConfiguration.builder()
                    .sentinelClass(Object.class)
                    .build();

            assertThat(config.verdictSink()).isInstanceOf(LogVerdictSink.class);
        }

        @Test
        @DisplayName("defaults to environment-resolved metadata")
        void defaultsToEnvironmentMetadata() {
            var config = SentinelConfiguration.builder()
                    .sentinelClass(Object.class)
                    .build();

            assertThat(config.environmentMetadata()).isNotNull();
            assertThat(config.environmentMetadata().environmentId()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("explicit configuration")
    class ExplicitConfiguration {

        @Test
        @DisplayName("registers a single sentinel class")
        void registersSingleClass() {
            var config = SentinelConfiguration.builder()
                    .sentinelClass(String.class)
                    .build();

            assertThat(config.sentinelClasses()).containsExactly(String.class);
        }

        @Test
        @DisplayName("registers multiple sentinel classes")
        void registersMultipleClasses() {
            var config = SentinelConfiguration.builder()
                    .sentinelClass(String.class)
                    .sentinelClass(Integer.class)
                    .build();

            assertThat(config.sentinelClasses()).containsExactly(String.class, Integer.class);
        }

        @Test
        @DisplayName("registers sentinel classes via list")
        void registersClassesViaList() {
            var config = SentinelConfiguration.builder()
                    .sentinelClasses(java.util.List.of(String.class, Integer.class))
                    .build();

            assertThat(config.sentinelClasses()).containsExactly(String.class, Integer.class);
        }

        @Test
        @DisplayName("uses single sink directly without wrapping in composite")
        void singleSinkNotWrapped() {
            VerdictSink sink = verdict -> {};
            var config = SentinelConfiguration.builder()
                    .sentinelClass(Object.class)
                    .verdictSink(sink)
                    .build();

            assertThat(config.verdictSink()).isSameAs(sink);
        }

        @Test
        @DisplayName("wraps multiple sinks in CompositeVerdictSink")
        void multipleSinksWrappedInComposite() {
            VerdictSink sink1 = verdict -> {};
            VerdictSink sink2 = verdict -> {};
            var config = SentinelConfiguration.builder()
                    .sentinelClass(Object.class)
                    .verdictSink(sink1)
                    .verdictSink(sink2)
                    .build();

            assertThat(config.verdictSink()).isInstanceOf(CompositeVerdictSink.class);
        }

        @Test
        @DisplayName("uses custom environment metadata when provided")
        void usesCustomEnvironmentMetadata() {
            var metadata = new EnvironmentMetadata("custom-env", "custom-instance");
            var config = SentinelConfiguration.builder()
                    .sentinelClass(Object.class)
                    .environmentMetadata(metadata)
                    .build();

            assertThat(config.environmentMetadata()).isSameAs(metadata);
        }
    }

    @Nested
    @DisplayName("immutability")
    class Immutability {

        @Test
        @DisplayName("sentinel classes list is immutable")
        void sentinelClassesListIsImmutable() {
            var config = SentinelConfiguration.builder()
                    .sentinelClass(Object.class)
                    .build();

            assertThatThrownBy(() -> config.sentinelClasses().add(String.class))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
