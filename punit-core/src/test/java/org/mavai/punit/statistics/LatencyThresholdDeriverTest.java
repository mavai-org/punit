package org.mavai.punit.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("LatencyThresholdDeriver")
class LatencyThresholdDeriverTest {

    /** Sorted baseline of 100 ramp values from 10..1000 ms. */
    private static double[] ramp100() {
        double[] v = new double[100];
        for (int i = 0; i < 100; i++) v[i] = (i + 1) * 10.0;
        return v;
    }

    @Nested
    @DisplayName("Binomial order-statistic upper bound")
    class Derivation {

        @Test
        @DisplayName("threshold is the k-th order statistic of the baseline")
        void thresholdIsOrderStatistic() {
            double[] baseline = ramp100();

            LatencyThresholdDeriver.Threshold t =
                    LatencyThresholdDeriver.derive(baseline, 0.95, 0.95);

            assertThat(t.n()).isEqualTo(100);
            assertThat(t.rank()).isBetween(95, 100);
            assertThat(t.threshold()).isEqualTo(t.rank() * 10.0);
            assertThat(t.baselinePercentile()).isEqualTo(950.0);
        }

        @Test
        @DisplayName("rank is >= nearest-rank point rank (ceil(p*n))")
        void rankIsAtLeastPointRank() {
            double[] baseline = ramp100();

            LatencyThresholdDeriver.Threshold t =
                    LatencyThresholdDeriver.derive(baseline, 0.50, 0.95);

            assertThat(t.rank()).isGreaterThanOrEqualTo(50);
        }

        @Test
        @DisplayName("higher confidence gives larger-or-equal threshold")
        void higherConfidenceGivesLargerThreshold() {
            double[] baseline = ramp100();

            LatencyThresholdDeriver.Threshold low =
                    LatencyThresholdDeriver.derive(baseline, 0.90, 0.80);
            LatencyThresholdDeriver.Threshold high =
                    LatencyThresholdDeriver.derive(baseline, 0.90, 0.99);

            assertThat(high.rank()).isGreaterThanOrEqualTo(low.rank());
            assertThat(high.threshold()).isGreaterThanOrEqualTo(low.threshold());
        }

        @Test
        @DisplayName("threshold saturates at the maximum when n is small")
        void saturatesAtMaximumWhenNSmall() {
            double[] baseline = {10, 20, 30, 40, 50};

            LatencyThresholdDeriver.Threshold t =
                    LatencyThresholdDeriver.derive(baseline, 0.99, 0.99);

            assertThat(t.rank()).isEqualTo(5);
            assertThat(t.threshold()).isEqualTo(50.0);
        }

        @Test
        @DisplayName("unsorted input is handled correctly")
        void unsortedInputHandled() {
            double[] baseline = {50, 10, 30, 40, 20};

            LatencyThresholdDeriver.Threshold t =
                    LatencyThresholdDeriver.derive(baseline, 0.60, 0.95);

            // Sorted: [10, 20, 30, 40, 50]; point rank = ceil(0.6*5) = 3 → value 30
            // Binomial bound may push higher but always in-sample.
            assertThat(t.threshold()).isIn(30.0, 40.0, 50.0);
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("should reject empty baseline")
        void shouldRejectEmptyBaseline() {
            assertThatThrownBy(() -> LatencyThresholdDeriver.derive(new double[0], 0.95, 0.95))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("should reject null baseline")
        void shouldRejectNullBaseline() {
            assertThatThrownBy(() -> LatencyThresholdDeriver.derive(null, 0.95, 0.95))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject p <= 0")
        void shouldRejectPZero() {
            assertThatThrownBy(() -> LatencyThresholdDeriver.derive(ramp100(), 0.0, 0.95))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("p");
        }

        @Test
        @DisplayName("should reject p >= 1")
        void shouldRejectPOne() {
            assertThatThrownBy(() -> LatencyThresholdDeriver.derive(ramp100(), 1.0, 0.95))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("p");
        }

        @Test
        @DisplayName("should reject confidence of zero")
        void shouldRejectConfidenceZero() {
            assertThatThrownBy(() -> LatencyThresholdDeriver.derive(ramp100(), 0.95, 0.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("confidence");
        }

        @Test
        @DisplayName("should reject confidence of one")
        void shouldRejectConfidenceOne() {
            assertThatThrownBy(() -> LatencyThresholdDeriver.derive(ramp100(), 0.95, 1.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("confidence");
        }
    }
}
