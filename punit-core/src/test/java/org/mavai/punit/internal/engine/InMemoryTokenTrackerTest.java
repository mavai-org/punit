package org.mavai.punit.internal.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InMemoryTokenTracker — accumulates per-run cost")
class InMemoryTokenTrackerTest {

    @Test
    @DisplayName("a fresh tracker reports zero")
    void freshIsZero() {
        assertThat(new InMemoryTokenTracker().totalTokens()).isZero();
    }

    @Test
    @DisplayName("recordTokens accumulates")
    void accumulates() {
        InMemoryTokenTracker t = new InMemoryTokenTracker();

        t.recordTokens(7);
        t.recordTokens(13);
        t.recordTokens(0);

        assertThat(t.totalTokens()).isEqualTo(20);
    }

    @Test
    @DisplayName("recordTokens(0) is allowed")
    void zeroAllowed() {
        InMemoryTokenTracker t = new InMemoryTokenTracker();
        t.recordTokens(0);
        assertThat(t.totalTokens()).isZero();
    }

    @Test
    @DisplayName("negative values are rejected")
    void negativeRejected() {
        InMemoryTokenTracker t = new InMemoryTokenTracker();

        assertThatThrownBy(() -> t.recordTokens(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-negative");
    }

    @Test
    @DisplayName("per-sample cost can be derived by diffing totalTokens()")
    void diffYieldsPerSample() {
        InMemoryTokenTracker t = new InMemoryTokenTracker();
        t.recordTokens(50);   // earlier sample's cost

        long start = t.totalTokens();
        t.recordTokens(12);   // current sample
        t.recordTokens(3);
        long perSample = t.totalTokens() - start;

        assertThat(perSample).isEqualTo(15);
        assertThat(t.totalTokens()).isEqualTo(65);
    }
}
