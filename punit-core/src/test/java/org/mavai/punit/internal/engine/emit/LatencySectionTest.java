package org.mavai.punit.internal.engine.emit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.mavai.punit.api.LatencyResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@code latency:} block builder.
 *
 * <p>Three cases:
 * <ul>
 *   <li>Zero passing samples → empty Optional (no block to emit).</li>
 *   <li>Some passing samples → block carries the indicator triple
 *       ({@code basis} / {@code contributingSamples} / {@code totalSamples})
 *       plus the four percentiles in milliseconds.</li>
 *   <li>All samples passing → contributingSamples == totalSamples.</li>
 * </ul>
 */
@DisplayName("LatencySection — block shape, indicator triple, zero-passing edge case")
class LatencySectionTest {

    @Test
    @DisplayName("Returns empty Optional when no samples passed")
    void emptyWhenNoPassingSamples() {
        assertThat(LatencySection.blockFor(LatencyResult.empty(), 0, 20)).isEmpty();
    }

    @Test
    @DisplayName("With ≥ 100 passing samples, the block carries all four percentiles")
    void blockShapeAtFullThresholds() {
        LatencyResult passing = new LatencyResult(
                Duration.ofMillis(42),
                Duration.ofMillis(78),
                Duration.ofMillis(95),
                Duration.ofMillis(150),
                100);

        Optional<Map<String, Object>> block = LatencySection.blockFor(passing, 100, 110);
        assertThat(block).isPresent();
        Map<String, Object> map = block.get();
        assertThat(map).containsExactly(
                Map.entry("basis", "passing-samples"),
                Map.entry("contributingSamples", 100),
                Map.entry("totalSamples", 110),
                Map.entry("p50Ms", 42L),
                Map.entry("p90Ms", 78L),
                Map.entry("p95Ms", 95L),
                Map.entry("p99Ms", 150L));
    }

    @Test
    @DisplayName("minimum-sample rule: 18 passing → only p50 / p90 keys emitted")
    void omitsPercentilesBelowThreshold() {
        LatencyResult passing = new LatencyResult(
                Duration.ofMillis(42),
                Duration.ofMillis(78),
                Duration.ofMillis(95),
                Duration.ofMillis(150),
                18);

        Map<String, Object> block = LatencySection.blockFor(passing, 18, 20).orElseThrow();
        // p50 needs ≥ 1, p90 needs ≥ 10 — both met at 18.
        assertThat(block).containsKeys("p50Ms", "p90Ms");
        // p95 needs ≥ 20, p99 needs ≥ 100 — both unmet at 18.
        assertThat(block).doesNotContainKeys("p95Ms", "p99Ms");
    }

    @Test
    @DisplayName("contributingSamples == totalSamples when every sample passed (above all thresholds)")
    void allSamplesPassing() {
        LatencyResult passing = new LatencyResult(
                Duration.ofMillis(10),
                Duration.ofMillis(20),
                Duration.ofMillis(25),
                Duration.ofMillis(30),
                100);

        Map<String, Object> block = LatencySection.blockFor(passing, 100, 100).orElseThrow();
        assertThat(block).containsEntry("contributingSamples", 100);
        assertThat(block).containsEntry("totalSamples", 100);
        // All four percentiles emit at n=100.
        assertThat(block).containsKeys("p50Ms", "p90Ms", "p95Ms", "p99Ms");
    }

    @Test
    @DisplayName("isPercentileEmittable encodes the thresholds (1 / 10 / 20 / 100)")
    void thresholdRule() {
        assertThat(LatencySection.isPercentileEmittable("p50", 1)).isTrue();
        assertThat(LatencySection.isPercentileEmittable("p90", 9)).isFalse();
        assertThat(LatencySection.isPercentileEmittable("p90", 10)).isTrue();
        assertThat(LatencySection.isPercentileEmittable("p95", 19)).isFalse();
        assertThat(LatencySection.isPercentileEmittable("p95", 20)).isTrue();
        assertThat(LatencySection.isPercentileEmittable("p99", 99)).isFalse();
        assertThat(LatencySection.isPercentileEmittable("p99", 100)).isTrue();
    }
}
