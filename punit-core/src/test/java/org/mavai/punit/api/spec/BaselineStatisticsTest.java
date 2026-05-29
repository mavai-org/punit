package org.mavai.punit.api.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.mavai.punit.api.LatencyResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BaselineStatistics family")
class BaselineStatisticsTest {

    @Test
    @DisplayName("PassRateStatistics accepts valid values")
    void passRateAcceptsValidValues() {
        PassRateStatistics s = new PassRateStatistics(0.87, 1500);
        assertThat(s.observedPassRate()).isEqualTo(0.87);
        assertThat(s.sampleCount()).isEqualTo(1500);
    }

    @Test
    @DisplayName("PassRateStatistics rejects observedPassRate outside [0, 1]")
    void passRateRejectsOutOfRange() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new PassRateStatistics(-0.1, 100));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new PassRateStatistics(1.5, 100));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new PassRateStatistics(Double.NaN, 100));
    }

    @Test
    @DisplayName("PassRateStatistics rejects negative sampleCount")
    void passRateRejectsNegativeSampleCount() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new PassRateStatistics(0.5, -1));
    }

    @Test
    @DisplayName("LatencyStatistics accepts valid values")
    void latencyAcceptsValidValues() {
        LatencyStatistics s = LatencyStatistics.fromPercentiles(LatencyResult.empty(), 500);
        assertThat(s.percentiles()).isEqualTo(LatencyResult.empty());
        assertThat(s.sampleCount()).isEqualTo(500);
    }

    @Test
    @DisplayName("LatencyStatistics rejects null percentiles")
    void latencyRejectsNullPercentiles() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> LatencyStatistics.fromPercentiles(null, 100));
    }

    @Test
    @DisplayName("NoStatistics has a single INSTANCE")
    void noStatisticsSingleton() {
        assertThat(NoStatistics.INSTANCE).isNotNull();
        assertThat(NoStatistics.values()).hasSize(1).containsExactly(NoStatistics.INSTANCE);
    }

    @Test
    @DisplayName("All three concrete kinds implement BaselineStatistics")
    void allImplementMarker() {
        assertThat(new PassRateStatistics(0.5, 100)).isInstanceOf(BaselineStatistics.class);
        assertThat(LatencyStatistics.fromPercentiles(LatencyResult.empty(), 100)).isInstanceOf(BaselineStatistics.class);
        assertThat(NoStatistics.INSTANCE).isInstanceOf(BaselineStatistics.class);
    }

    @Test
    @DisplayName("BaselineStatistics is unsealed — a third-party type can implement it")
    void baselineStatisticsIsUnsealed() {
        assertThat(BaselineStatistics.class.isSealed())
                .as("BaselineStatistics must remain unsealed for extensibility per DES-CRITERION-EXTENSIBILITY.md")
                .isFalse();
    }
}
