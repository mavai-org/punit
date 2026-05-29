package org.mavai.punit.api.spec;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Produces the next factors for an {@link Experiment} iteration
 * given the current factors and the history of scored iterations
 * so far. The returned factors are passed to the service contract factory
 * to construct the next iteration's service contract instance.
 *
 * <p>An optimize search walks through the factor space; each
 * invocation is one step in that walk. Implementations encode the
 * search strategy — gradient descent, hill climbing, random walk,
 * genetic-algorithm variation, etc.
 *
 * @param <FT> the factor record type
 */
@FunctionalInterface
public interface FactorsStepper<FT> {

    /**
     * @param current the last factors evaluated
     * @param history the full iteration history in execution order
     * @return {@link NextFactor#next(Object)} carrying the next
     *         factors to evaluate, or {@link NextFactor#stop()} to
     *         signal "no more candidates" (the optimiser will stop)
     */
    NextFactor<FT> next(FT current, List<IterationResult<FT>> history);

    /**
     * One entry in the optimize history fed to {@link #next}.
     *
     * <p>{@link #failuresByPostcondition()} carries the per-clause
     * failure counts and bounded exemplars from the iteration's
     * {@link SampleSummary}, so a meta-LLM driving the search can
     * compose a meaningful improvement prompt that says <em>which</em>
     * postconditions tripped and <em>what inputs</em> tripped them.
     *
     * <p>{@link #failureExemplars()} is a bounded flattened view of
     * the same data — the union of all per-clause exemplars across
     * all clauses, useful when the search wants to see "concrete
     * failed samples" without bucketing.
     *
     * @param factors the factors evaluated in that iteration
     * @param score the score produced by the {@link Scorer}
     * @param failuresByPostcondition per-clause failure counts +
     *                                bounded exemplars
     * @param failureExemplars        flattened bounded view across
     *                                all clauses
     * @param successes               raw count of samples whose
     *                                outcome was {@code Ok}
     * @param failures                raw count of samples whose
     *                                outcome was {@code Fail}
     * @param samplesExecuted         total samples executed in the
     *                                iteration (successes + failures,
     *                                matching the iteration's
     *                                {@link SampleSummary#total()})
     * @param <FT> the factor record type
     */
    record IterationResult<FT>(
            FT factors,
            double score,
            Map<String, FailureCount> failuresByPostcondition,
            List<FailureExemplar> failureExemplars,
            int successes,
            int failures,
            int samplesExecuted) {

        public IterationResult {
            Objects.requireNonNull(failuresByPostcondition, "failuresByPostcondition");
            Objects.requireNonNull(failureExemplars, "failureExemplars");
            failuresByPostcondition = Map.copyOf(failuresByPostcondition);
            failureExemplars = List.copyOf(failureExemplars);
        }

        /**
         * Backward-compatible 4-field constructor. Defaults the raw
         * count fields ({@code successes}, {@code failures},
         * {@code samplesExecuted}) to {@code 0}; call sites that
         * have access to the iteration's {@link SampleSummary}
         * should use the canonical 7-field constructor so the
         * counts reach the optimize-experiment output artefact.
         */
        public IterationResult(
                FT factors,
                double score,
                Map<String, FailureCount> failuresByPostcondition,
                List<FailureExemplar> failureExemplars) {
            this(factors, score, failuresByPostcondition, failureExemplars, 0, 0, 0);
        }

        /**
         * Backward-compatible 2-field constructor. Defaults the
         * histogram and exemplar list to empty and the raw counts
         * to {@code 0}. Used by call sites that haven't yet migrated
         * to the canonical 7-field shape; the engine constructs
         * results via the canonical constructor with populated
         * histograms and counts.
         */
        public IterationResult(FT factors, double score) {
            this(factors, score, Map.of(), List.of(), 0, 0, 0);
        }
    }
}
