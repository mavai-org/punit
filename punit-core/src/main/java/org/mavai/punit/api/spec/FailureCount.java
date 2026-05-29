package org.mavai.punit.api.spec;

import java.util.List;
import java.util.Objects;

/**
 * Aggregate failure data for one postcondition clause across the
 * samples in a run. Carried as the value type in
 * {@link SampleSummary#failuresByPostcondition()
 * failuresByPostcondition()} (and the equivalent maps on
 * {@link ProbabilisticTestResult} and
 * {@link FactorsStepper.IterationResult}), keyed by the clause's
 * description.
 *
 * <p>{@link #count()} reports every failed evaluation; {@link
 * #exemplars()} carries up to a bounded number of concrete
 * examples (per-postcondition cap, configured at the spec level
 * — defaults to 3 in the typed pipeline). Once the cap is reached,
 * additional failures of the same clause increment {@link #count()}
 * but are not retained in {@link #exemplars()}.
 *
 * <p>The bounded retention keeps the in-memory cost of a long run
 * proportional to the number of distinct failure modes, not to the
 * number of failed samples. Authors who want a richer picture
 * (every failed input) can read the full per-sample
 * {@code postconditionResults} from {@link SampleSummary#trials()}.
 *
 * @param count    the total number of failed evaluations of this
 *                 clause in the run; non-negative
 * @param exemplars up to N concrete failures (input + reason);
 *                 length &le; count
 */
public record FailureCount(int count, List<FailureExemplar> exemplars) {

    public FailureCount {
        Objects.requireNonNull(exemplars, "exemplars");
        if (count < 0) {
            throw new IllegalArgumentException("count must be non-negative, got " + count);
        }
        if (exemplars.size() > count) {
            throw new IllegalArgumentException(
                    "exemplars (" + exemplars.size() + ") cannot exceed count (" + count + ")");
        }
        exemplars = List.copyOf(exemplars);
    }

    /** Convenience: an empty bucket — no failures, no exemplars. */
    public static FailureCount empty() {
        return new FailureCount(0, List.of());
    }
}
