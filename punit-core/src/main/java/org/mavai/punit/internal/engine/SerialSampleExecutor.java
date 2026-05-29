package org.mavai.punit.internal.engine;

import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.mavai.punit.api.spec.ExceptionPolicy;
import org.mavai.punit.api.Pacing;
import org.mavai.punit.api.TokenTracker;
import org.mavai.punit.api.ServiceContract;
import org.mavai.punit.api.ServiceContractOutcome;
import org.mavai.punit.api.spec.SampleExecutor;
import org.mavai.punit.api.spec.SampleObserver;

/**
 * One-at-a-time sample executor. Invokes
 * {@code serviceContract.apply(input, tracker)} on the calling thread and
 * forwards outcomes along with the wall-clock time taken. Consults
 * the caller's early-stop predicate between samples so the engine
 * can enforce wall-clock and token budgets, and honours the use
 * case's declared {@link Pacing} between samples.
 *
 * <p>Constructs one {@link TokenTracker} per {@code runSamples} call
 * — that tracker spans every sample in the call, accumulating cost
 * across the whole run. The contract's {@code apply} default diffs
 * the tracker's total before and after each {@code invoke} to derive
 * the per-sample cost recorded on the outcome.
 *
 * <h2>Pacing behaviour</h2>
 *
 * <p>Pacing is read from {@link ServiceContract#pacing()} once per call —
 * pacing is a service-level property, so a single {@code runSamples}
 * call sees a consistent limit. The pacing delay is inserted
 * <em>between</em> samples, so the first sample dispatches immediately
 * and subsequent samples wait
 * {@link Pacing#effectiveMinDelayMillis()} millis after the previous
 * sample started before dispatching. The sleep occurs on the calling
 * thread and therefore counts against any wall-clock budget the
 * engine is enforcing.
 *
 * <p>{@code maxConcurrent} is informational here — a serial executor
 * always runs at concurrency 1, which is &le; any positive cap.
 *
 * <p>Business-level failures are signalled by the service contract returning
 * a {@link ServiceContractOutcome} whose inner {@link org.mavai.outcome.Outcome}
 * is a {@code Fail}; the executor just forwards those. Defects —
 * exceptions thrown from {@code apply} — are routed through the
 * observer's {@code onDefect} channel, which the engine's aggregator
 * uses to apply the spec's
 * {@link ExceptionPolicy}. The
 * executor itself makes no policy decisions.
 */
public final class SerialSampleExecutor implements SampleExecutor {

    @Override
    public <FT, IT, OT> void runSamples(
            ServiceContract<FT, IT, OT> serviceContract,
            List<IT> inputs,
            int sampleCount,
            int cycleStart,
            SampleObserver<OT> observer,
            BooleanSupplier stopRequested) {

        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("inputs must be non-empty");
        }
        if (sampleCount < 0) {
            throw new IllegalArgumentException("sampleCount must be non-negative");
        }

        Pacing pacing = serviceContract.pacing();
        long minDelayMillis = pacing.effectiveMinDelayMillis();
        TokenTracker tracker = new InMemoryTokenTracker();

        for (int i = 0; i < sampleCount; i++) {
            if (stopRequested.getAsBoolean()) {
                return;
            }
            if (i > 0 && minDelayMillis > 0) {
                sleep(minDelayMillis);
                if (stopRequested.getAsBoolean()) {
                    return;
                }
            }
            int inputIndex = (cycleStart + i) % inputs.size();
            IT input = inputs.get(inputIndex);
            long t0 = System.nanoTime();
            ServiceContractOutcome<IT, OT> outcome;
            try {
                outcome = serviceContract.apply(input, tracker);
            } catch (Throwable t) {
                Duration elapsed = Duration.ofNanos(System.nanoTime() - t0);
                observer.onDefect(i, t, elapsed);
                emitProgress(i + 1, sampleCount);
                continue;
            }
            Duration elapsed = Duration.ofNanos(System.nanoTime() - t0);
            observer.onSample(i, outcome, elapsed);
            emitProgress(i + 1, sampleCount);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("pacing sleep interrupted", ie);
        }
    }

    /**
     * Token prefixed to every progress emission so the
     * punit-gradle-plugin can pattern-match the chunks on the
     * receiving side of Gradle's test-stdout pipe and reformat
     * them into clean in-place updates on the build's terminal.
     *
     * <p><b>Cross-module invariant:</b> this string must stay
     * byte-identical with the matcher in
     * {@code punit-gradle-plugin/.../PUnitPlugin.kt} (constant
     * {@code SAMPLE_PROGRESS_MARKER}). The two live in different
     * Gradle projects with separate classpaths — the plugin runs
     * in the Gradle build process, this code runs in the test
     * JVM — so a shared Java/Kotlin import is impractical. A
     * change here requires a matching change there.
     *
     * <p>The token is written verbatim and contains no characters
     * that need shell escaping, so a stray emission visible in a
     * raw log is human-readable rather than mojibake.
     */
    public static final String SAMPLE_PROGRESS_MARKER = "[PUNIT-PROGRESS]";

    /**
     * Emit a per-sample progress counter to {@code System.out} and
     * flush. The format is the {@link #SAMPLE_PROGRESS_MARKER}
     * token followed by width-padded {@code "completed/total"} and
     * a trailing newline (e.g. {@code "[PUNIT-PROGRESS]  12/100\n"}
     * for total = 100). The newline ensures Gradle's test-stdout
     * pipe delivers the line as one event; the marker lets the
     * punit-gradle-plugin's {@link
     * org.gradle.api.tasks.testing.TestOutputListener} recognise
     * the chunk, strip the decoration, and replay the counter as
     * an in-place {@code \r}-prefixed update on the build's
     * terminal.
     *
     * <p>Outside Gradle's test task — IntelliJ direct-JVM, Maven
     * Surefire, plain {@code java} invocation — the marker is
     * harmless visual noise, and the counter still scrolls
     * line-by-line as a live progress signal. The in-place update
     * is a Gradle-only nicety that the plugin layer provides.
     */
    private static void emitProgress(int completed, int total) {
        int width = String.valueOf(total).length();
        System.out.println(SAMPLE_PROGRESS_MARKER
                + String.format("%" + width + "d/%d", completed, total));
        System.out.flush();
    }

}
