package org.mavai.punit.api.spec;

import java.time.Duration;
import java.util.Objects;

import org.mavai.punit.api.ServiceContractOutcome;

/**
 * One per-sample observation: the input that drove the call, the
 * outcome the service contract returned (or that the engine synthesised
 * under {@link ExceptionPolicy#FAIL_SAMPLE}), the wall-clock
 * duration of the invocation, and the position of the input in
 * the spec's inputs list.
 *
 * <p>{@link SampleSummary#trials()} exposes the ordered sequence of
 * trials so history-sensitive criteria (collision rate, Shewhart
 * runs-rules, autocorrelation) can read the outcome stream rather
 * than only the aggregate counts a Bernoulli criterion is content
 * with.
 *
 * <p>{@link #inputIndex} is the zero-based position of {@link #input}
 * in the spec's configured inputs list. The engine cycles inputs
 * round-robin across samples, so for a run with N samples and M
 * inputs the trial at sample index i has
 * {@code inputIndex == (cycleStart + i) mod M}. Stored explicitly so
 * downstream artefacts can correlate a sample to its driver without
 * re-deriving the cycling formula.
 *
 * @param input      the per-sample input that drove this trial
 * @param outcome    the outcome the service contract returned (success or fail)
 * @param duration   wall-clock duration of the invocation
 * @param inputIndex zero-based position of {@code input} in the
 *                   spec's inputs list
 * @param <IT>       the per-sample input type
 * @param <OT>       the per-sample outcome value type
 */
public record Trial<IT, OT>(
        IT input,
        ServiceContractOutcome<IT, OT> outcome,
        Duration duration,
        int inputIndex) {

    public Trial {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(duration, "duration");
        if (inputIndex < 0) {
            throw new IllegalArgumentException(
                    "inputIndex must be non-negative, got " + inputIndex);
        }
    }

    /**
     * Backward-compatible 3-arg constructor that defaults
     * {@link #inputIndex} to {@code 0}. Used by test fixtures and
     * call sites that haven't yet migrated to the canonical 4-field
     * shape; the engine constructs trials via the canonical
     * constructor with the correct cycled input position.
     */
    public Trial(IT input, ServiceContractOutcome<IT, OT> outcome, Duration duration) {
        this(input, outcome, duration, 0);
    }
}
