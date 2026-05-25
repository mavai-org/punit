package org.javai.punit.sentinel;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

import org.javai.punit.verdict.ProbabilisticTestVerdict;
import org.javai.punit.internal.reporting.VerdictSinkBus;
import org.javai.punit.verdict.VerdictSink;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.TestAbortedException;

/**
 * Executes a single {@code @ProbabilisticTest} or {@code @Experiment}
 * method on a registered class, capturing the verdict the method's
 * body emits through {@code PUnit.assertPasses()} or
 * {@code PUnit.run()}.
 *
 * <p>Sentinel-side execution is structurally simpler than JUnit-side
 * execution: the framework already separates verdict emission
 * ({@code VerdictSinkBus.dispatch}) from JUnit-style translation
 * ({@code AssertionFailedError} / {@code TestAbortedException}
 * throws). The executor:
 *
 * <ol>
 *   <li>Replaces the bus's sinks with a single capturing sink — the
 *       framework's default XML / HTML / log sinks do not fire under
 *       Sentinel; Sentinel routes captured verdicts through its own
 *       configured sinks (see {@link SentinelConfiguration#verdictSink()}).</li>
 *   <li>Instantiates the registered class via its no-arg constructor.</li>
 *   <li>Invokes the method via reflection.</li>
 *   <li>Catches {@link AssertionFailedError} (FAIL verdict
 *       translation) and {@link TestAbortedException} (INCONCLUSIVE)
 *       as expected outcomes — the verdict was captured in step 1
 *       before the throw.</li>
 *   <li>Lets every other throwable propagate as a defect — a programming
 *       mistake or framework invariant violation, not a sample failure.</li>
 * </ol>
 *
 * <p>Each invocation resets the sink bus, so verdicts do not bleed
 * between methods. The executor itself is stateless and safe to reuse
 * across calls; orchestration over a registry of classes lives in
 * {@link SentinelOrchestrator}.
 */
// javai-ref: JVI-C4CJAN~ — do not remove (resolves in javai-orchestrator)
public class SentinelExecutor {

    /**
     * Executes one method on one registered class.
     *
     * @param sentinelClass the registered class; must declare a public
     *                      no-arg constructor
     * @param method        the method to invoke; must be a
     *                      {@code @ProbabilisticTest} or
     *                      {@code @Experiment} method (zero parameters,
     *                      returning {@code void})
     * @return the captured outcome — verdict (if emitted) and / or
     *         defect (if the method threw something other than the
     *         expected pass/fail/inconclusive translations)
     */
    public Outcome execute(Class<?> sentinelClass, Method method) {
        Capturer capturer = new Capturer();
        VerdictSinkBus.replaceAll(capturer);
        Throwable defect = null;
        try {
            Object instance = sentinelClass.getDeclaredConstructor().newInstance();
            method.setAccessible(true);
            method.invoke(instance);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (!(cause instanceof AssertionFailedError) && !(cause instanceof TestAbortedException)) {
                defect = cause != null ? cause : e;
            }
        } catch (ReflectiveOperationException e) {
            defect = e;
        }
        return new Outcome(sentinelClass, method, capturer.captured(), defect);
    }

    /**
     * The result of executing one method.
     *
     * <p>{@code verdict} is empty only when the method threw before
     * reaching the verdict-emission step (i.e. a defect prevented the
     * run from completing). On a normal run — including FAIL and
     * INCONCLUSIVE — the verdict is present.
     *
     * @param sentinelClass the class the method was invoked on
     * @param method        the method that was invoked
     * @param verdict       captured verdict, or empty on defect-before-emit
     * @param defect        unexpected throwable, or {@code null} on a
     *                      normal run (PASS, FAIL, or INCONCLUSIVE)
     */
    public record Outcome(
            Class<?> sentinelClass,
            Method method,
            Optional<ProbabilisticTestVerdict> verdict,
            Throwable defect) {

        public boolean isDefect() {
            return defect != null;
        }

        public boolean executed() {
            return verdict.isPresent();
        }
    }

    private static final class Capturer implements VerdictSink {

        private ProbabilisticTestVerdict captured;

        @Override
        public void accept(ProbabilisticTestVerdict verdict) {
            this.captured = verdict;
        }

        Optional<ProbabilisticTestVerdict> captured() {
            return Optional.ofNullable(captured);
        }
    }
}
