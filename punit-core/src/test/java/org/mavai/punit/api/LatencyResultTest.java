package org.mavai.punit.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LatencyResult")
class LatencyResultTest {

    @Test
    @DisplayName("empty() yields zero duration and zero sample count")
    void emptyIsZero() {
        LatencyResult r = LatencyResult.empty();
        assertThat(r.p50()).isEqualTo(Duration.ZERO);
        assertThat(r.p90()).isEqualTo(Duration.ZERO);
        assertThat(r.p95()).isEqualTo(Duration.ZERO);
        assertThat(r.p99()).isEqualTo(Duration.ZERO);
        assertThat(r.sampleCount()).isZero();
    }

    @Test
    @DisplayName("accessors return the values supplied at construction")
    void accessorsReturnValues() {
        LatencyResult r = new LatencyResult(
                Duration.ofMillis(10),
                Duration.ofMillis(20),
                Duration.ofMillis(30),
                Duration.ofMillis(40),
                500);
        assertThat(r.p50()).isEqualTo(Duration.ofMillis(10));
        assertThat(r.p90()).isEqualTo(Duration.ofMillis(20));
        assertThat(r.p95()).isEqualTo(Duration.ofMillis(30));
        assertThat(r.p99()).isEqualTo(Duration.ofMillis(40));
        assertThat(r.sampleCount()).isEqualTo(500);
    }

    @Test
    @DisplayName("negative sample count is rejected")
    void rejectsNegativeSampleCount() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new LatencyResult(
                        Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO, -1));
    }

    @Test
    @DisplayName("null percentiles are rejected")
    void rejectsNullPercentiles() {
        assertThatNullPointerException()
                .isThrownBy(() -> new LatencyResult(null, Duration.ZERO, Duration.ZERO, Duration.ZERO, 0));
        assertThatNullPointerException()
                .isThrownBy(() -> new LatencyResult(Duration.ZERO, null, Duration.ZERO, Duration.ZERO, 0));
    }
}
