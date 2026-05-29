package org.mavai.punit.sentinel;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Duration;
import java.util.List;
import org.mavai.punit.api.spec.Verdict;
import org.mavai.punit.verdict.ProbabilisticTestVerdict;
import org.mavai.punit.verdict.ProbabilisticTestVerdictBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SentinelResultTest {

    @Nested
    @DisplayName("allPassed()")
    class AllPassed {

        @Test
        @DisplayName("returns true when no failures and no skipped")
        void trueWhenAllPass() {
            var result = new SentinelResult(5, 5, 0, 0, List.of(), Duration.ofSeconds(10));

            assertThat(result.allPassed()).isTrue();
        }

        @Test
        @DisplayName("returns false when there are failures")
        void falseWhenFailures() {
            var result = new SentinelResult(5, 3, 2, 0, List.of(), Duration.ofSeconds(10));

            assertThat(result.allPassed()).isFalse();
        }

        @Test
        @DisplayName("returns false when there are skipped tests")
        void falseWhenSkipped() {
            var result = new SentinelResult(5, 4, 0, 1, List.of(), Duration.ofSeconds(10));

            assertThat(result.allPassed()).isFalse();
        }

        @Test
        @DisplayName("returns false when there are both failures and skipped")
        void falseWhenFailuresAndSkipped() {
            var result = new SentinelResult(5, 2, 2, 1, List.of(), Duration.ofSeconds(10));

            assertThat(result.allPassed()).isFalse();
        }

        @Test
        @DisplayName("returns false for zero total tests — nothing ran")
        void falseForEmptyRun() {
            var result = new SentinelResult(0, 0, 0, 0, List.of(), Duration.ZERO);

            assertThat(result.allPassed()).isFalse();
            assertThat(result.noneExecuted()).isTrue();
        }
    }

    @Nested
    @DisplayName("record accessors")
    class RecordAccessors {

        @Test
        @DisplayName("exposes all fields correctly")
        void exposesAllFields() {
            ProbabilisticTestVerdict verdict = new ProbabilisticTestVerdictBuilder()
                    .identity("TestClass", "testMethod", "serviceContract1")
                    .execution(100, 100, 95, 5, 0.9, 0.95, 1000)
                    .junitPassed(true)
                    .criterionVerdict(Verdict.PASS)
                    .build();
            var verdicts = List.of(verdict);
            var duration = Duration.ofMillis(1500);

            var result = new SentinelResult(3, 2, 1, 0, verdicts, duration);

            assertThat(result.totalTests()).isEqualTo(3);
            assertThat(result.passed()).isEqualTo(2);
            assertThat(result.failed()).isEqualTo(1);
            assertThat(result.skipped()).isEqualTo(0);
            assertThat(result.verdicts()).hasSize(1);
            assertThat(result.totalDuration()).isEqualTo(duration);
        }
    }
}
