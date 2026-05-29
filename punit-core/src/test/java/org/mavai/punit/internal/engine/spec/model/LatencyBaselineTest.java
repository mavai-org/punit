package org.mavai.punit.internal.engine.spec.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("LatencyBaseline")
class LatencyBaselineTest {

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should create with valid values")
        void shouldCreateWithValidValues() {
            long[] sorted = {100, 200, 300, 400, 500};
            LatencyBaseline baseline = new LatencyBaseline(sorted, 300, 500);

            assertThat(baseline.sampleCount()).isEqualTo(5);
            assertThat(baseline.meanMs()).isEqualTo(300);
            assertThat(baseline.maxMs()).isEqualTo(500);
            assertThat(baseline.sortedLatenciesMs()).containsExactly(100, 200, 300, 400, 500);
        }

        @Test
        @DisplayName("should reject negative mean")
        void shouldRejectNegativeMean() {
            assertThatThrownBy(() -> new LatencyBaseline(new long[]{100}, -1, 100))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("meanMs");
        }

        @Test
        @DisplayName("should reject negative max")
        void shouldRejectNegativeMax() {
            assertThatThrownBy(() -> new LatencyBaseline(new long[]{100}, 100, -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxMs");
        }

        @Test
        @DisplayName("should reject unsorted vector")
        void shouldRejectUnsortedVector() {
            assertThatThrownBy(() -> new LatencyBaseline(new long[]{500, 100, 300}, 300, 500))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sorted");
        }

        @Test
        @DisplayName("should allow empty vector")
        void shouldAllowEmptyVector() {
            LatencyBaseline baseline = new LatencyBaseline(new long[0], 0, 0);

            assertThat(baseline.sampleCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("should defensively copy sorted vector")
        void shouldDefensivelyCopy() {
            long[] input = {100, 200, 300};
            LatencyBaseline baseline = new LatencyBaseline(input, 200, 300);

            input[0] = 999;
            assertThat(baseline.sortedLatenciesMs()[0]).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("Record equality")
    class RecordEquality {

        @Test
        @DisplayName("should be equal for same values")
        void shouldBeEqualForSameValues() {
            LatencyBaseline a = new LatencyBaseline(new long[]{100, 200, 300}, 200, 300);
            LatencyBaseline b = new LatencyBaseline(new long[]{100, 200, 300}, 200, 300);

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("should not be equal for different vectors")
        void shouldNotBeEqualForDifferentVectors() {
            LatencyBaseline a = new LatencyBaseline(new long[]{100, 200, 300}, 200, 300);
            LatencyBaseline b = new LatencyBaseline(new long[]{100, 200, 400}, 200, 400);

            assertThat(a).isNotEqualTo(b);
        }
    }
}
