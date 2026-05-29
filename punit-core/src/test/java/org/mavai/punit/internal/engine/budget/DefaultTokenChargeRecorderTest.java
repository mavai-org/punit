package org.mavai.punit.internal.engine.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DefaultTokenChargeRecorder")
class DefaultTokenChargeRecorderTest {

    @Nested
    @DisplayName("recordTokens")
    class RecordTokens {

        @Test
        @DisplayName("accumulates tokens for current sample")
        void accumulatesTokensForCurrentSample() {
            DefaultTokenChargeRecorder recorder = new DefaultTokenChargeRecorder(1000);

            recorder.recordTokens(50);
            recorder.recordTokens(30);

            assertThat(recorder.getTokensForCurrentSample()).isEqualTo(80);
        }

        @Test
        @DisplayName("accepts int tokens")
        void acceptsIntTokens() {
            DefaultTokenChargeRecorder recorder = new DefaultTokenChargeRecorder(1000);

            recorder.recordTokens((int) 100);

            assertThat(recorder.getTokensForCurrentSample()).isEqualTo(100);
        }

        @Test
        @DisplayName("accepts long tokens")
        void acceptsLongTokens() {
            DefaultTokenChargeRecorder recorder = new DefaultTokenChargeRecorder(Long.MAX_VALUE);

            recorder.recordTokens(1_000_000_000L);

            assertThat(recorder.getTokensForCurrentSample()).isEqualTo(1_000_000_000L);
        }

        @Test
        @DisplayName("throws for negative tokens")
        void throwsForNegativeTokens() {
            DefaultTokenChargeRecorder recorder = new DefaultTokenChargeRecorder(1000);

            assertThatThrownBy(() -> recorder.recordTokens(-10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be >= 0");
        }
    }

    @Nested
    @DisplayName("finalizeSample")
    class FinalizeSample {

        @Test
        @DisplayName("adds to total and resets current")
        void addsToTotalAndResetsCurrent() {
            DefaultTokenChargeRecorder recorder = new DefaultTokenChargeRecorder(1000);
            recorder.recordTokens(50);

            long result = recorder.finalizeSample();

            assertThat(result).isEqualTo(50);
            assertThat(recorder.getTokensForCurrentSample()).isEqualTo(0);
            assertThat(recorder.getTotalTokensConsumed()).isEqualTo(50);
        }

        @Test
        @DisplayName("accumulates across multiple samples")
        void accumulatesAcrossMultipleSamples() {
            DefaultTokenChargeRecorder recorder = new DefaultTokenChargeRecorder(1000);
            
            recorder.recordTokens(30);
            recorder.finalizeSample();
            recorder.recordTokens(50);
            recorder.finalizeSample();

            assertThat(recorder.getTotalTokensConsumed()).isEqualTo(80);
        }
    }

    @Nested
    @DisplayName("resetForNextSample")
    class ResetForNextSample {

        @Test
        @DisplayName("resets current sample tokens without adding to total")
        void resetsWithoutAddingToTotal() {
            DefaultTokenChargeRecorder recorder = new DefaultTokenChargeRecorder(1000);
            recorder.recordTokens(50);

            recorder.resetForNextSample();

            assertThat(recorder.getTokensForCurrentSample()).isEqualTo(0);
            assertThat(recorder.getTotalTokensConsumed()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("getRemainingBudget")
    class GetRemainingBudget {

        @Test
        @DisplayName("returns remaining when budget is set")
        void returnsRemainingWhenBudgetSet() {
            DefaultTokenChargeRecorder recorder = new DefaultTokenChargeRecorder(1000);
            recorder.recordTokens(300);
            recorder.finalizeSample();

            assertThat(recorder.getRemainingBudget()).isEqualTo(700);
        }

        @Test
        @DisplayName("returns MAX_VALUE when unlimited (0 budget)")
        void returnsMaxValueWhenUnlimited() {
            DefaultTokenChargeRecorder recorder = new DefaultTokenChargeRecorder(0);

            assertThat(recorder.getRemainingBudget()).isEqualTo(Long.MAX_VALUE);
        }

        @Test
        @DisplayName("returns 0 when budget exhausted")
        void returnsZeroWhenExhausted() {
            DefaultTokenChargeRecorder recorder = new DefaultTokenChargeRecorder(100);
            recorder.recordTokens(150);
            recorder.finalizeSample();

            assertThat(recorder.getRemainingBudget()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("getTokenBudget")
    class GetTokenBudget {

        @Test
        @DisplayName("returns configured budget")
        void returnsConfiguredBudget() {
            DefaultTokenChargeRecorder recorder = new DefaultTokenChargeRecorder(5000);

            assertThat(recorder.getTokenBudget()).isEqualTo(5000);
        }
    }
}

