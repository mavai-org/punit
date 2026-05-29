package org.mavai.punit.sentinel;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import org.mavai.punit.api.Experiment;
import org.mavai.punit.api.ProbabilisticTest;
import org.mavai.punit.verdict.ProbabilisticTestVerdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SentinelOrchestrator}.
 *
 * <p>The orchestrator is a thin reflective dispatcher; tests use a
 * recording fake {@link SentinelExecutor} so this test focuses on
 * discovery + iteration semantics, not execution mechanics (covered
 * in {@link SentinelExecutorTest}).
 */
@DisplayName("SentinelOrchestrator")
class SentinelOrchestratorTest {

    @Test
    @DisplayName("runTests dispatches every @ProbabilisticTest method on every registered class")
    void runTestsDispatchesAllProbabilisticTestMethods() {
        RecordingExecutor executor = new RecordingExecutor();
        SentinelOrchestrator orchestrator = new SentinelOrchestrator(executor);

        orchestrator.runTests(List.of(MixedSubject.class, ProbabilisticOnlySubject.class));

        assertThat(executor.invocations).extracting(i -> i.method.getName())
                .containsExactly("aProbabilisticTest", "bProbabilisticTest", "cProbabilisticTest");
    }

    @Test
    @DisplayName("runExperiments dispatches every @Experiment method on every registered class")
    void runExperimentsDispatchesAllExperimentMethods() {
        RecordingExecutor executor = new RecordingExecutor();
        SentinelOrchestrator orchestrator = new SentinelOrchestrator(executor);

        orchestrator.runExperiments(List.of(MixedSubject.class, ExperimentOnlySubject.class));

        assertThat(executor.invocations).extracting(i -> i.method.getName())
                .containsExactly("aExperiment", "bExperiment");
    }

    @Test
    @DisplayName("methods are iterated in alphabetical order — deterministic across runs")
    void methodsIteratedInDeclarationOrder() {
        RecordingExecutor executor = new RecordingExecutor();
        SentinelOrchestrator orchestrator = new SentinelOrchestrator(executor);

        orchestrator.runTests(List.of(UnorderedSubject.class));

        assertThat(executor.invocations).extracting(i -> i.method.getName())
                .containsExactly("alpha", "beta", "gamma");
    }

    @Test
    @DisplayName("classes with no annotated methods contribute nothing")
    void classesWithoutAnnotatedMethodsContributeNothing() {
        RecordingExecutor executor = new RecordingExecutor();
        SentinelOrchestrator orchestrator = new SentinelOrchestrator(executor);

        orchestrator.runTests(List.of(NoAnnotationsSubject.class));

        assertThat(executor.invocations).isEmpty();
    }

    @Test
    @DisplayName("inherited methods are not invoked — only methods declared on each class")
    void inheritedMethodsNotInvoked() {
        RecordingExecutor executor = new RecordingExecutor();
        SentinelOrchestrator orchestrator = new SentinelOrchestrator(executor);

        orchestrator.runTests(List.of(ChildSubject.class));

        // ChildSubject declares no @ProbabilisticTest methods of its own.
        // ParentSubject's methods are NOT inherited because we use
        // getDeclaredMethods, not getMethods.
        assertThat(executor.invocations).isEmpty();
    }

    @Test
    @DisplayName("empty registry produces an empty outcome list")
    void emptyRegistryProducesEmptyOutcomeList() {
        RecordingExecutor executor = new RecordingExecutor();
        SentinelOrchestrator orchestrator = new SentinelOrchestrator(executor);

        List<SentinelExecutor.Outcome> outcomes = orchestrator.runTests(List.of());

        assertThat(outcomes).isEmpty();
    }

    @Test
    @DisplayName("runTests does not invoke @Experiment methods, and vice versa")
    void filtersByAnnotationKind() {
        RecordingExecutor executor = new RecordingExecutor();
        SentinelOrchestrator orchestrator = new SentinelOrchestrator(executor);

        orchestrator.runTests(List.of(ExperimentOnlySubject.class));

        assertThat(executor.invocations).isEmpty();
    }

    // ── Subjects ─────────────────────────────────────────────────────────

    public static class MixedSubject {
        @ProbabilisticTest void aProbabilisticTest() { }
        @Experiment void aExperiment() { }
    }

    public static class ProbabilisticOnlySubject {
        @ProbabilisticTest void bProbabilisticTest() { }
        @ProbabilisticTest void cProbabilisticTest() { }
    }

    public static class ExperimentOnlySubject {
        @Experiment void bExperiment() { }
    }

    public static class UnorderedSubject {
        @ProbabilisticTest void gamma() { }
        @ProbabilisticTest void alpha() { }
        @ProbabilisticTest void beta() { }
    }

    public static class NoAnnotationsSubject {
        public void something() { }
        public void otherwise() { }
    }

    public static class ParentSubject {
        @ProbabilisticTest void inheritedTest() { }
    }

    public static class ChildSubject extends ParentSubject {
        // no methods of its own
    }

    // ── Recording fake ───────────────────────────────────────────────────

    private static final class RecordingExecutor extends SentinelExecutor {

        record Invocation(Class<?> sentinelClass, Method method) { }

        final List<Invocation> invocations = new java.util.ArrayList<>();

        @Override
        public Outcome execute(Class<?> sentinelClass, Method method) {
            invocations.add(new Invocation(sentinelClass, method));
            return new Outcome(sentinelClass, method, Optional.<ProbabilisticTestVerdict>empty(), null);
        }
    }
}
