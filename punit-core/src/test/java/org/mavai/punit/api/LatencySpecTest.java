package org.mavai.punit.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LatencySpec")
class LatencySpecTest {

    @Test
    @DisplayName("disabled() is the no-threshold sentinel")
    void disabledIsSentinel() {
        LatencySpec d = LatencySpec.disabled();
        assertThat(d.isDisabled()).isTrue();
        assertThat(d.hasAnyThreshold()).isFalse();
        assertThat(d.p50Millis()).isEmpty();
        assertThat(d.p90Millis()).isEmpty();
        assertThat(d.p95Millis()).isEmpty();
        assertThat(d.p99Millis()).isEmpty();
    }

    @Test
    @DisplayName("disabled() returns the same canonical instance")
    void disabledIsCanonical() {
        assertThat(LatencySpec.disabled()).isSameAs(LatencySpec.disabled());
    }

    @Test
    @DisplayName("builder with one threshold is not disabled")
    void builderWithThresholdIsActive() {
        LatencySpec s = LatencySpec.builder().p95Millis(300L).build();
        assertThat(s.isDisabled()).isFalse();
        assertThat(s.hasAnyThreshold()).isTrue();
        assertThat(s.p95Millis()).hasValue(300L);
    }

    @Test
    @DisplayName("builder with no thresholds builds the disabled shape")
    void builderWithNoThresholdsIsDisabled() {
        LatencySpec s = LatencySpec.builder().build();
        assertThat(s.isDisabled()).isTrue();
        assertThat(s.hasAnyThreshold()).isFalse();
    }

    @Test
    @DisplayName("builder accepts multiple percentiles")
    void builderAcceptsMultiple() {
        LatencySpec s = LatencySpec.builder()
                .p50Millis(100L)
                .p90Millis(200L)
                .p95Millis(300L)
                .p99Millis(500L)
                .build();
        assertThat(s.p50Millis()).hasValue(100L);
        assertThat(s.p90Millis()).hasValue(200L);
        assertThat(s.p95Millis()).hasValue(300L);
        assertThat(s.p99Millis()).hasValue(500L);
    }

    @Test
    @DisplayName("non-positive percentiles are rejected at build time")
    void rejectsNonPositivePercentiles() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> LatencySpec.builder().p50Millis(0L).build());
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> LatencySpec.builder().p90Millis(-1L).build());
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> LatencySpec.builder().p95Millis(-100L).build());
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> LatencySpec.builder().p99Millis(0L).build());
    }
}
