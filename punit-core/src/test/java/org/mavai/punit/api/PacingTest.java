package org.mavai.punit.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Pacing")
class PacingTest {

    @Test
    @DisplayName("unlimited() has every knob empty and zero delay")
    void unlimitedIsEmpty() {
        Pacing p = Pacing.unlimited();
        assertThat(p.maxRequestsPerSecond()).isEmpty();
        assertThat(p.maxRequestsPerMinute()).isEmpty();
        assertThat(p.minMillisPerSample()).isEmpty();
        assertThat(p.maxConcurrent()).isEmpty();
        assertThat(p.effectiveMinDelayMillis()).isZero();
    }

    @Test
    @DisplayName("unlimited() returns the same canonical instance")
    void unlimitedIsCanonical() {
        assertThat(Pacing.unlimited()).isSameAs(Pacing.unlimited());
    }

    @Test
    @DisplayName("maxRequestsPerSecond implies 1000/rate floor millis")
    void rpsImpliesDelay() {
        Pacing p = Pacing.builder().maxRequestsPerSecond(10.0).build();
        assertThat(p.effectiveMinDelayMillis()).isEqualTo(100L);
    }

    @Test
    @DisplayName("maxRequestsPerMinute implies 60000/rate floor millis")
    void rpmImpliesDelay() {
        Pacing p = Pacing.builder().maxRequestsPerMinute(60.0).build();
        assertThat(p.effectiveMinDelayMillis()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("minMillisPerSample is taken literally")
    void minMsDelay() {
        Pacing p = Pacing.builder().minMillisPerSample(250L).build();
        assertThat(p.effectiveMinDelayMillis()).isEqualTo(250L);
    }

    @Test
    @DisplayName("most-restrictive-wins: the largest implied delay wins")
    void compositionMostRestrictiveWins() {
        Pacing p = Pacing.builder()
                .maxRequestsPerSecond(10.0)      // implies 100ms
                .maxRequestsPerMinute(60.0)      // implies 1000ms
                .minMillisPerSample(250L)        // literal 250ms
                .build();
        assertThat(p.effectiveMinDelayMillis()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("maxConcurrent does not influence the effective delay")
    void maxConcurrentOrthogonal() {
        Pacing p = Pacing.builder().maxConcurrent(8).build();
        assertThat(p.effectiveMinDelayMillis()).isZero();
        assertThat(p.maxConcurrent()).hasValue(8);
    }

    @Test
    @DisplayName("builder rejects non-positive rates and delays")
    void rejectsNonPositive() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Pacing.builder().maxRequestsPerSecond(0).build());
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Pacing.builder().maxRequestsPerSecond(-1).build());
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Pacing.builder().maxRequestsPerMinute(0).build());
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Pacing.builder().minMillisPerSample(0).build());
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Pacing.builder().minMillisPerSample(-5).build());
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Pacing.builder().maxConcurrent(0).build());
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Pacing.builder().maxConcurrent(-3).build());
    }
}
