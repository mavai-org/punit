package org.mavai.punit.api.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.mavai.punit.api.LatencyResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EngineRunSummary")
class EngineRunSummaryTest {

    @Test
    @DisplayName("empty() yields all-zero scalars, default confidence, empty filename")
    void emptyHasDefaults() {
        EngineRunSummary s = EngineRunSummary.empty();

        assertThat(s.plannedSamples()).isZero();
        assertThat(s.samplesExecuted()).isZero();
        assertThat(s.successes()).isZero();
        assertThat(s.failures()).isZero();
        assertThat(s.elapsedMs()).isZero();
        assertThat(s.tokensConsumed()).isZero();
        assertThat(s.failuresDropped()).isZero();
        assertThat(s.latencyResult()).isEqualTo(LatencyResult.empty());
        assertThat(s.terminationReason()).isEqualTo(TerminationReason.COMPLETED);
        assertThat(s.confidence()).isEqualTo(0.95);
        assertThat(s.baselineFilename()).isEmpty();
    }

    @Test
    @DisplayName("canonical constructor preserves all components")
    void canonicalConstructor() {
        EngineRunSummary s = new EngineRunSummary(
                100, 95, 90, 5, 1500L, 12_345L, 2,
                LatencyResult.empty(),
                TerminationReason.TIME_BUDGET,
                0.99,
                Optional.of("baseline-foo.yaml"));

        assertThat(s.plannedSamples()).isEqualTo(100);
        assertThat(s.samplesExecuted()).isEqualTo(95);
        assertThat(s.successes()).isEqualTo(90);
        assertThat(s.failures()).isEqualTo(5);
        assertThat(s.elapsedMs()).isEqualTo(1500L);
        assertThat(s.tokensConsumed()).isEqualTo(12_345L);
        assertThat(s.failuresDropped()).isEqualTo(2);
        assertThat(s.terminationReason()).isEqualTo(TerminationReason.TIME_BUDGET);
        assertThat(s.confidence()).isEqualTo(0.99);
        assertThat(s.baselineFilename()).contains("baseline-foo.yaml");
    }

    @Test
    @DisplayName("rejects negative counts")
    void rejectsNegativeCounts() {
        assertThatThrownBy(() -> new EngineRunSummary(
                -1, 0, 0, 0, 0L, 0L, 0,
                LatencyResult.empty(), TerminationReason.COMPLETED,
                0.95, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-negative");
    }

    @Test
    @DisplayName("rejects negative elapsedMs")
    void rejectsNegativeElapsed() {
        assertThatThrownBy(() -> new EngineRunSummary(
                0, 0, 0, 0, -1L, 0L, 0,
                LatencyResult.empty(), TerminationReason.COMPLETED,
                0.95, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("elapsedMs");
    }

    @Test
    @DisplayName("rejects confidence outside (0, 1)")
    void rejectsConfidenceOutsideBounds() {
        assertThatThrownBy(() -> new EngineRunSummary(
                0, 0, 0, 0, 0L, 0L, 0,
                LatencyResult.empty(), TerminationReason.COMPLETED,
                0.0, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confidence must be in (0, 1)");

        assertThatThrownBy(() -> new EngineRunSummary(
                0, 0, 0, 0, 0L, 0L, 0,
                LatencyResult.empty(), TerminationReason.COMPLETED,
                1.0, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confidence must be in (0, 1)");

        assertThatThrownBy(() -> new EngineRunSummary(
                0, 0, 0, 0, 0L, 0L, 0,
                LatencyResult.empty(), TerminationReason.COMPLETED,
                Double.NaN, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confidence must be in (0, 1)");
    }

    @Test
    @DisplayName("rejects null required components")
    void rejectsNullComponents() {
        assertThatThrownBy(() -> new EngineRunSummary(
                0, 0, 0, 0, 0L, 0L, 0,
                null, TerminationReason.COMPLETED, 0.95, Optional.empty()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("latencyResult");

        assertThatThrownBy(() -> new EngineRunSummary(
                0, 0, 0, 0, 0L, 0L, 0,
                LatencyResult.empty(), null, 0.95, Optional.empty()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("terminationReason");

        assertThatThrownBy(() -> new EngineRunSummary(
                0, 0, 0, 0, 0L, 0L, 0,
                LatencyResult.empty(), TerminationReason.COMPLETED, 0.95, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("baselineFilename");
    }
}
