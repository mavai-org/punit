package org.mavai.punit.sentinel.testsubjects;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.mavai.punit.api.Experiment;
import org.mavai.punit.api.ProbabilisticTest;
import org.mavai.punit.api.TestIntent;
import org.mavai.punit.verdict.TokenMode;
import org.mavai.punit.verdict.TerminationReason;
import org.mavai.punit.api.ServiceContractAttributes;
import org.mavai.punit.verdict.ProbabilisticTestVerdict;
import org.mavai.punit.verdict.PUnitVerdict;
import org.mavai.punit.internal.reporting.VerdictSinkBus;
import org.opentest4j.AssertionFailedError;

/**
 * Test subjects for {@link org.mavai.punit.sentinel.SentinelMainTest}
 * and {@link org.mavai.punit.sentinel.SentinelOrchestratorTest}.
 *
 * <p>Each nested class declares an annotated method whose body
 * dispatches a stubbed
 * {@link ProbabilisticTestVerdict} directly to
 * {@link VerdictSinkBus} — bypassing the real engine. This
 * keeps the sentinel runtime tests focused on the framework's
 * verdict-capture and orchestration mechanics; engine-level
 * behaviour is exercised in {@code punit-core}.
 *
 * <p>Lives under {@code testsubjects/} so the package convention
 * excludes these from JUnit's normal test discovery — the
 * {@code @ProbabilisticTest} meta-annotation would otherwise make
 * JUnit try to run them as plain tests, which would fire the
 * {@link VerdictSinkBus} side-effect outside the sentinel
 * harness.
 */
public final class SentinelTestSubjects {

    private SentinelTestSubjects() { }

    public static class PassingSubject {

        public PassingSubject() { }

        @ProbabilisticTest
        public void emitsPass() {
            VerdictSinkBus.dispatch(stubVerdict(true));
        }
    }

    public static class FailingSubject {

        public FailingSubject() { }

        @ProbabilisticTest
        public void emitsFailAndThrows() {
            VerdictSinkBus.dispatch(stubVerdict(false));
            throw new AssertionFailedError("simulated FAIL");
        }
    }

    public static class MeasuringSubject {

        public MeasuringSubject() { }

        @Experiment
        public void emitsMeasurement() {
            VerdictSinkBus.dispatch(stubVerdict(true));
        }
    }

    /**
     * Builds a minimal {@link ProbabilisticTestVerdict} suitable for
     * testing. Every nested-record optional is empty; numeric values
     * are placeholders. Callers that need specific shapes (covariate
     * misalignment, latency, cost) build their own verdict.
     */
    public static ProbabilisticTestVerdict stubVerdict(boolean junitPassed) {
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
