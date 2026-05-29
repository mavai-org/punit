package org.mavai.punit.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ServiceContractAttributes")
class ServiceContractAttributesTest {

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        void defaultAttributes() {
            assertThat(ServiceContractAttributes.DEFAULT.warmup()).isEqualTo(0);
            assertThat(ServiceContractAttributes.DEFAULT.maxConcurrent()).isEqualTo(0);
            assertThat(ServiceContractAttributes.DEFAULT.hasWarmup()).isFalse();
            assertThat(ServiceContractAttributes.DEFAULT.hasMaxConcurrent()).isFalse();
        }

        @Test
        void warmupOnly() {
            var attrs = new ServiceContractAttributes(5);
            assertThat(attrs.warmup()).isEqualTo(5);
            assertThat(attrs.maxConcurrent()).isEqualTo(0);
            assertThat(attrs.hasWarmup()).isTrue();
            assertThat(attrs.hasMaxConcurrent()).isFalse();
        }

        @Test
        void bothAttributes() {
            var attrs = new ServiceContractAttributes(3, 4);
            assertThat(attrs.warmup()).isEqualTo(3);
            assertThat(attrs.maxConcurrent()).isEqualTo(4);
            assertThat(attrs.hasWarmup()).isTrue();
            assertThat(attrs.hasMaxConcurrent()).isTrue();
        }

        @Test
        void negativeWarmupThrows() {
            assertThatThrownBy(() -> new ServiceContractAttributes(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("warmup");
        }

        @Test
        void negativeMaxConcurrentThrows() {
            assertThatThrownBy(() -> new ServiceContractAttributes(0, -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxConcurrent");
        }
    }

    @Nested
    @DisplayName("equality")
    class Equality {

        @Test
        void sameValuesAreEqual() {
            var a = new ServiceContractAttributes(5, 2);
            var b = new ServiceContractAttributes(5, 2);
            assertThat(a).isEqualTo(b);
        }

        @Test
        void differentValuesAreNotEqual() {
            var a = new ServiceContractAttributes(5, 2);
            var b = new ServiceContractAttributes(5, 3);
            assertThat(a).isNotEqualTo(b);
        }
    }
}
