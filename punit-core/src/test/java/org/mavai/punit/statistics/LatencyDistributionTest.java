package org.mavai.punit.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("LatencyDistribution")
class LatencyDistributionTest {

    @Nested
    @DisplayName("Factory method validation")
    class FactoryValidation {

        @Test
        @DisplayName("should reject null durations list")
        void shouldRejectNullDurations() {
            assertThatThrownBy(() -> LatencyDistribution.fromDurations(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject empty durations list")
        void shouldRejectEmptyDurations() {
            assertThatThrownBy(() -> LatencyDistribution.fromDurations(List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("should reject null millis array")
        void shouldRejectNullMillis() {
            assertThatThrownBy(() -> LatencyDistribution.fromMillis(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject empty millis array")
        void shouldRejectEmptyMillis() {
            assertThatThrownBy(() -> LatencyDistribution.fromMillis(new long[0]))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("empty");
        }
    }

    @Nested
    @DisplayName("Single element")
    class SingleElement {

        @Test
        @DisplayName("should handle single duration")
        void shouldHandleSingleDuration() {
            LatencyDistribution dist = LatencyDistribution.fromDurations(
                    List.of(Duration.ofMillis(500)));

            assertThat(dist.sampleCount()).isEqualTo(1);
            assertThat(dist.meanMs()).isEqualTo(500);
            assertThat(dist.p50Ms()).isEqualTo(500);
            assertThat(dist.p90Ms()).isEqualTo(500);
            assertThat(dist.p95Ms()).isEqualTo(500);
            assertThat(dist.p99Ms()).isEqualTo(500);
            assertThat(dist.maxMs()).isEqualTo(500);
        }
    }

    @Nested
    @DisplayName("Percentile accuracy")
    class PercentileAccuracy {

        @Test
        @DisplayName("should compute correct percentiles for known data")
        void shouldComputeCorrectPercentilesForKnownData() {
            // 100 values: 1, 2, 3, ..., 100
            long[] values = LongStream.rangeClosed(1, 100).toArray();
            LatencyDistribution dist = LatencyDistribution.fromMillis(values);

            assertThat(dist.sampleCount()).isEqualTo(100);
            assertThat(dist.p50Ms()).isEqualTo(50);   // ceil(0.50 * 100) = 50 → index 49 → value 50
            assertThat(dist.p90Ms()).isEqualTo(90);   // ceil(0.90 * 100) = 90 → index 89 → value 90
            assertThat(dist.p95Ms()).isEqualTo(95);   // ceil(0.95 * 100) = 95 → index 94 → value 95
            assertThat(dist.p99Ms()).isEqualTo(99);   // ceil(0.99 * 100) = 99 → index 98 → value 99
            assertThat(dist.maxMs()).isEqualTo(100);
        }

        @Test
        @DisplayName("should compute correct percentiles for 10 elements")
        void shouldComputeCorrectPercentilesForTenElements() {
            // 10 values: 100, 200, ..., 1000
            long[] values = {100, 200, 300, 400, 500, 600, 700, 800, 900, 1000};
            LatencyDistribution dist = LatencyDistribution.fromMillis(values);

            assertThat(dist.sampleCount()).isEqualTo(10);
            assertThat(dist.p50Ms()).isEqualTo(500);  // ceil(0.50 * 10) = 5 → index 4 → value 500
            assertThat(dist.p90Ms()).isEqualTo(900);  // ceil(0.90 * 10) = 9 → index 8 → value 900
            assertThat(dist.p95Ms()).isEqualTo(1000); // ceil(0.95 * 10) = 10 → index 9 → value 1000
            assertThat(dist.p99Ms()).isEqualTo(1000); // ceil(0.99 * 10) = 10 → index 9 → value 1000
            assertThat(dist.maxMs()).isEqualTo(1000);
        }

        @Test
        @DisplayName("should handle unsorted input")
        void shouldHandleUnsortedInput() {
            long[] values = {900, 100, 500, 300, 700, 200, 800, 400, 600, 1000};
            LatencyDistribution dist = LatencyDistribution.fromMillis(values);

            assertThat(dist.p50Ms()).isEqualTo(500);
            assertThat(dist.p90Ms()).isEqualTo(900);
            assertThat(dist.maxMs()).isEqualTo(1000);
        }

        @Test
        @DisplayName("should handle two elements")
        void shouldHandleTwoElements() {
            long[] values = {100, 200};
            LatencyDistribution dist = LatencyDistribution.fromMillis(values);

            assertThat(dist.sampleCount()).isEqualTo(2);
            assertThat(dist.p50Ms()).isEqualTo(100);  // ceil(0.50 * 2) = 1 → index 0 → value 100
            assertThat(dist.p90Ms()).isEqualTo(200);  // ceil(0.90 * 2) = 2 → index 1 → value 200
            assertThat(dist.p99Ms()).isEqualTo(200);
            assertThat(dist.maxMs()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("Mean and sorted vector")
    class MeanAndSorted {

        @Test
        @DisplayName("should compute correct mean")
        void shouldComputeCorrectMean() {
            long[] values = {100, 200, 300, 400, 500};
            LatencyDistribution dist = LatencyDistribution.fromMillis(values);

            assertThat(dist.meanMs()).isEqualTo(300);
        }

        @Test
        @DisplayName("should expose sorted latency vector")
        void shouldExposeSortedLatencyVector() {
            long[] values = {500, 100, 300, 200, 400};
            LatencyDistribution dist = LatencyDistribution.fromMillis(values);

            assertThat(dist.sortedLatenciesMs()).containsExactly(100, 200, 300, 400, 500);
        }

        @Test
        @DisplayName("should defensively copy the sorted vector on access")
        void shouldDefensivelyCopy() {
            LatencyDistribution dist = LatencyDistribution.fromMillis(new long[]{100, 200, 300});

            long[] first = dist.sortedLatenciesMs();
            first[0] = 999;

            assertThat(dist.sortedLatenciesMs()[0]).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("Duration-based factory")
    class DurationBasedFactory {

        @Test
        @DisplayName("should convert Duration to milliseconds")
        void shouldConvertDurationToMilliseconds() {
            List<Duration> durations = List.of(
                    Duration.ofMillis(100),
                    Duration.ofMillis(200),
                    Duration.ofSeconds(1));

            LatencyDistribution dist = LatencyDistribution.fromDurations(durations);

            assertThat(dist.sampleCount()).isEqualTo(3);
            assertThat(dist.maxMs()).isEqualTo(1000);
        }
    }

    @Nested
    @DisplayName("Large dataset")
    class LargeDataset {

        @Test
        @DisplayName("should handle large dataset efficiently")
        void shouldHandleLargeDataset() {
            long[] values = LongStream.rangeClosed(1, 10_000).toArray();
            LatencyDistribution dist = LatencyDistribution.fromMillis(values);

            assertThat(dist.sampleCount()).isEqualTo(10_000);
            assertThat(dist.meanMs()).isEqualTo(5001); // mean of 1..10000 = 5000.5 → rounds to 5001
            assertThat(dist.p50Ms()).isEqualTo(5000);
            assertThat(dist.p99Ms()).isEqualTo(9900);
            assertThat(dist.maxMs()).isEqualTo(10_000);
        }
    }

    @Nested
    @DisplayName("Equality and toString")
    class EqualityAndToString {

        @Test
        @DisplayName("should be equal for same data")
        void shouldBeEqualForSameData() {
            long[] values = {100, 200, 300};
            LatencyDistribution a = LatencyDistribution.fromMillis(values);
            LatencyDistribution b = LatencyDistribution.fromMillis(values);

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("should have descriptive toString")
        void shouldHaveDescriptiveToString() {
            LatencyDistribution dist = LatencyDistribution.fromMillis(new long[]{100, 200, 300});

            assertThat(dist.toString())
                    .contains("LatencyDistribution")
                    .contains("p50=")
                    .contains("p99=");
        }
    }
}
