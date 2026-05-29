package org.mavai.punit.sentinel;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.mavai.punit.api.TestIntent;
import org.mavai.punit.verdict.TokenMode;
import org.mavai.punit.verdict.TerminationReason;
import org.mavai.punit.api.ServiceContractAttributes;
import org.mavai.punit.verdict.ProbabilisticTestVerdict;
import org.mavai.punit.verdict.PUnitVerdict;
import org.mavai.punit.internal.reporting.VerdictSinkBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.TestAbortedException;

/**
 * Tests for {@link SentinelExecutor}.
 *
 * <p>The subject classes are plain (no PUnit annotations) — the executor
 * only cares about reflective invocation mechanics and verdict capture.
 * Annotation-based filtering is the orchestrator's concern (see
 * {@code SentinelOrchestratorTest}).
 *
 * <p>Verdicts are stubbed by directly calling {@link VerdictSinkBus#dispatch}
 * inside the subject methods, so the test exercises the executor's
 * sink-replacement and capture path without standing up the real engine.
 */
@DisplayName("SentinelExecutor")
class SentinelExecutorTest {

    @BeforeEach
    void resetBus() {
        VerdictSinkBus.reset();
    }

    @AfterEach
    void cleanUp() {
        VerdictSinkBus.reset();
    }

    @Test
    @DisplayName("captures verdict from a method that dispatches and returns normally")
    void capturesVerdictOnNormalReturn() throws Exception {
        Method method = StubSubject.class.getDeclaredMethod("dispatchesPass");
        SentinelExecutor.Outcome outcome = new SentinelExecutor().execute(StubSubject.class, method);

        assertThat(outcome.executed()).isTrue();
        assertThat(outcome.verdict()).isPresent();
        assertThat(outcome.verdict().get().junitPassed()).isTrue();
        assertThat(outcome.isDefect()).isFalse();
    }

    @Test
    @DisplayName("treats AssertionFailedError as an expected FAIL outcome")
    void treatsAssertionFailedErrorAsExpected() throws Exception {
        Method method = StubSubject.class.getDeclaredMethod("dispatchesFailAndThrows");
        SentinelExecutor.Outcome outcome = new SentinelExecutor().execute(StubSubject.class, method);

        assertThat(outcome.verdict()).isPresent();
        assertThat(outcome.verdict().get().junitPassed()).isFalse();
        assertThat(outcome.isDefect()).isFalse();
    }

    @Test
    @DisplayName("treats TestAbortedException as an expected INCONCLUSIVE outcome")
    void treatsTestAbortedExceptionAsExpected() throws Exception {
        Method method = StubSubject.class.getDeclaredMethod("dispatchesInconclusiveAndAborts");
        SentinelExecutor.Outcome outcome = new SentinelExecutor().execute(StubSubject.class, method);

        assertThat(outcome.verdict()).isPresent();
        assertThat(outcome.isDefect()).isFalse();
    }

    @Test
    @DisplayName("treats other throwables as defects, with no verdict captured")
    void treatsUnexpectedThrowableAsDefect() throws Exception {
        Method method = StubSubject.class.getDeclaredMethod("throwsRuntimeException");
        SentinelExecutor.Outcome outcome = new SentinelExecutor().execute(StubSubject.class, method);

        assertThat(outcome.verdict()).isEmpty();
        assertThat(outcome.isDefect()).isTrue();
        assertThat(outcome.defect()).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("simulated defect");
    }

    @Test
    @DisplayName("when a defect occurs after emission, the verdict is preserved alongside the defect")
    void capturesBothVerdictAndDefect() throws Exception {
        Method method = StubSubject.class.getDeclaredMethod("dispatchesThenThrowsUnexpected");
        SentinelExecutor.Outcome outcome = new SentinelExecutor().execute(StubSubject.class, method);

        assertThat(outcome.verdict()).isPresent();
        assertThat(outcome.isDefect()).isTrue();
        assertThat(outcome.defect()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("invocations are independent — verdicts do not bleed between method runs")
    void invocationsAreIndependent() throws Exception {
        SentinelExecutor executor = new SentinelExecutor();

        SentinelExecutor.Outcome first = executor.execute(
                StubSubject.class, StubSubject.class.getDeclaredMethod("dispatchesPass"));
        assertThat(first.verdict()).isPresent();

        SentinelExecutor.Outcome second = executor.execute(
                StubSubject.class, StubSubject.class.getDeclaredMethod("noop"));
        assertThat(second.verdict()).isEmpty();
        assertThat(second.isDefect()).isFalse();
    }

    @Test
    @DisplayName("treats reflection failures (no constructor, etc.) as defects")
    void treatsReflectionFailuresAsDefect() throws Exception {
        Method method = NoNoArgConstructorSubject.class.getDeclaredMethod("doNothing");
        SentinelExecutor.Outcome outcome = new SentinelExecutor()
                .execute(NoNoArgConstructorSubject.class, method);

        assertThat(outcome.verdict()).isEmpty();
        assertThat(outcome.isDefect()).isTrue();
        assertThat(outcome.defect()).isInstanceOf(NoSuchMethodException.class);
    }

    // ── Subjects ─────────────────────────────────────────────────────────

    public static class StubSubject {

        public StubSubject() { }

        public void dispatchesPass() {
            VerdictSinkBus.dispatch(stubVerdict(true));
        }

        public void dispatchesFailAndThrows() {
            VerdictSinkBus.dispatch(stubVerdict(false));
            throw new AssertionFailedError("simulated FAIL");
        }

        public void dispatchesInconclusiveAndAborts() {
            VerdictSinkBus.dispatch(stubVerdict(false));
            throw new TestAbortedException("simulated INCONCLUSIVE");
        }

        public void throwsRuntimeException() {
            throw new RuntimeException("simulated defect");
        }

        public void dispatchesThenThrowsUnexpected() {
            VerdictSinkBus.dispatch(stubVerdict(true));
            throw new IllegalStateException("simulated framework invariant violation");
        }

        public void noop() { }
    }

    public static class NoNoArgConstructorSubject {

        public NoNoArgConstructorSubject(String required) { }

        public void doNothing() { }
    }

    // ── Stub verdict helper ──────────────────────────────────────────────

    private static ProbabilisticTestVerdict stubVerdict(boolean junitPassed) {
        return new ProbabilisticTestVerdict(
                "v:test01",
                Instant.parse("2026-04-30T00:00:00Z"),
                new ProbabilisticTestVerdict.TestIdentity(
                        "com.example.MyTest", "stub", Optional.empty()),
                new ProbabilisticTestVerdict.ExecutionSummary(
                        10, 10, 10, 0, 0.9, 1.0, 100,
                        Optional.empty(), TestIntent.VERIFICATION, 0.95,
                        ServiceContractAttributes.DEFAULT),
                Optional.empty(),
                Optional.empty(),
                new ProbabilisticTestVerdict.StatisticalAnalysis(
                        0.95, 0.0, 0.9,
                        Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty(), List.of()),
                ProbabilisticTestVerdict.CovariateStatus.allAligned(),
                new ProbabilisticTestVerdict.CostSummary(
                        0, 0, 0, TokenMode.NONE, Optional.empty(), Optional.empty()),
                Optional.empty(),
                Optional.empty(),
                new ProbabilisticTestVerdict.Termination(
                        TerminationReason.COMPLETED, Optional.empty()),
                Map.of(),
                junitPassed,
                junitPassed ? PUnitVerdict.PASS : PUnitVerdict.FAIL,
                "stub");
    }
}
