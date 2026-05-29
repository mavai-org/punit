package org.mavai.punit.internal.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.ArrayList;
import java.util.List;
import org.mavai.punit.api.spec.Verdict;
import org.mavai.punit.verdict.ProbabilisticTestVerdict;
import org.mavai.punit.verdict.ProbabilisticTestVerdictBuilder;
import org.mavai.punit.verdict.VerdictSink;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CompositeVerdictSinkTest {

    private static ProbabilisticTestVerdict sampleVerdict() {
        return new ProbabilisticTestVerdictBuilder()
                .identity("TestClass", "testMethod", "serviceContract1")
                .execution(100, 100, 95, 5, 0.9, 0.95, 1000)
                .junitPassed(true)
                .criterionVerdict(Verdict.PASS)
                .build();
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("rejects null sinks list")
        void rejectsNullSinks() {
            assertThatThrownBy(() -> new CompositeVerdictSink(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("reports correct size")
        void reportsCorrectSize() {
            VerdictSink s1 = verdict -> {};
            VerdictSink s2 = verdict -> {};
            var composite = new CompositeVerdictSink(List.of(s1, s2));

            assertThat(composite.size()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("dispatch")
    class Dispatch {

        @Test
        @DisplayName("dispatches verdict to all sinks in order")
        void dispatchesToAllSinksInOrder() {
            List<String> callOrder = new ArrayList<>();
            VerdictSink first = verdict -> callOrder.add("first");
            VerdictSink second = verdict -> callOrder.add("second");
            VerdictSink third = verdict -> callOrder.add("third");

            var composite = new CompositeVerdictSink(List.of(first, second, third));
            composite.accept(sampleVerdict());

            assertThat(callOrder).containsExactly("first", "second", "third");
        }

        @Test
        @DisplayName("passes the same verdict to each sink")
        void passesSameVerdictToEachSink() {
            List<ProbabilisticTestVerdict> received = new ArrayList<>();
            VerdictSink collector = received::add;

            var composite = new CompositeVerdictSink(List.of(collector, collector));
            var verdict = sampleVerdict();
            composite.accept(verdict);

            assertThat(received).hasSize(2);
            assertThat(received).allSatisfy(v -> assertThat(v).isSameAs(verdict));
        }
    }

    @Nested
    @DisplayName("exception isolation")
    class ExceptionIsolation {

        @Test
        @DisplayName("continues dispatching when a sink throws")
        void continuesWhenSinkThrows() {
            List<String> callOrder = new ArrayList<>();
            VerdictSink first = verdict -> callOrder.add("first");
            VerdictSink failing = verdict -> { throw new RuntimeException("boom"); };
            VerdictSink third = verdict -> callOrder.add("third");

            var composite = new CompositeVerdictSink(List.of(first, failing, third));
            composite.accept(sampleVerdict());

            assertThat(callOrder).containsExactly("first", "third");
        }

        @Test
        @DisplayName("isolates exceptions from multiple failing sinks")
        void isolatesMultipleFailures() {
            List<String> callOrder = new ArrayList<>();
            VerdictSink failing1 = verdict -> { throw new RuntimeException("fail-1"); };
            VerdictSink healthy = verdict -> callOrder.add("healthy");
            VerdictSink failing2 = verdict -> { throw new RuntimeException("fail-2"); };

            var composite = new CompositeVerdictSink(List.of(failing1, healthy, failing2));
            composite.accept(sampleVerdict());

            assertThat(callOrder).containsExactly("healthy");
        }
    }
}
