package org.mavai.punit.statistics;

/**
 * Judges a measure run's evidence against a stipulated (contractual)
 * pass-rate threshold — the experiment-time verdict for normative
 * criteria.
 *
 * <h2>Method</h2>
 * <p>Composes two existing constructs; it introduces no new
 * statistical machinery:
 *
 * <ol>
 *   <li><strong>Supportability gate</strong> — delegates to
 *       {@link VerificationFeasibilityEvaluator}: when even a perfect
 *       observation at the run's sample count could not place the
 *       Wilson one-sided lower confidence bound at or above the
 *       stipulated threshold, no judgement can be rendered at this
 *       sample size. The result is
 *       {@link State#UNSUPPORTABLE} with the feasible minimum sample
 *       count stated.</li>
 *   <li><strong>Judgement</strong> — the Wilson score one-sided lower
 *       confidence bound at the run's own sample count
 *       ({@link BinomialProportionEstimator#lowerBound}), at the
 *       criterion's confidence level, compared against the stipulated
 *       threshold: bound &ge; threshold is {@link State#MET},
 *       otherwise {@link State#FAILED}.</li>
 * </ol>
 *
 * <h2>Interpretation</h2>
 * <p>A {@link State#FAILED} judgement states one fact: this run's
 * evidence did not clear a stipulation in force at measure time. It
 * does <em>not</em> invalidate the service under measurement — an
 * aspirational bar measured mid-development, or a service measured
 * precisely because it is suspected to be below its bar, fails its
 * stipulation as a matter of course. Consumers of this result must
 * word it as a relation to the stipulation, never as a claim about
 * the service's validity.
 */
// mavai-ref: JVI-305FCX1 — do not remove (resolves in mavai-orchestrator)
public final class NormativeJudgementEvaluator {

    private static final BinomialProportionEstimator ESTIMATOR =
            new BinomialProportionEstimator();

    private NormativeJudgementEvaluator() {
        // utility class
    }

    /** The three possible judgement states. */
    public enum State {
        /** The run's lower confidence bound clears the stipulated threshold. */
        MET,
        /** The run's lower confidence bound does not clear the stipulated threshold. */
        FAILED,
        /**
         * The run's sample count cannot support the stipulated
         * threshold at the stated confidence — even a perfect
         * observation would leave the bound below the threshold. No
         * met/failed judgement is rendered.
         */
        UNSUPPORTABLE
    }

    /**
     * Result of judging a run against a stipulated threshold.
     *
     * @param state                  the judgement state
     * @param stipulatedThreshold    the threshold the run was judged against
     * @param confidence             the confidence level the bound was computed at
     * @param observedRate           the run's observed pass rate (successes / samples)
     * @param lowerBound             the Wilson one-sided lower confidence bound at the
     *                               run's sample count; {@code NaN} when the judgement
     *                               is {@link State#UNSUPPORTABLE} (no bound is asserted)
     * @param feasibleMinimumSamples the minimum sample count at which a judgement
     *                               against this threshold becomes supportable at
     *                               this confidence
     */
    public record Judgement(
            State state,
            double stipulatedThreshold,
            double confidence,
            double observedRate,
            double lowerBound,
            int feasibleMinimumSamples) {
    }

    /**
     * Judge a run's evidence against a stipulated threshold.
     *
     * @param successes           number of passing samples (k); must be in [0, samples]
     * @param samples             the run's sample count (N); must be &gt; 0
     * @param stipulatedThreshold the stipulated minimum acceptable rate (p₀); must be in [0, 1)
     * @param confidence          the confidence level (1 − α); must be in (0, 1) exclusive
     * @return the judgement with its full statistical context
     * @throws IllegalArgumentException if any parameter is out of range
     */
    public static Judgement judge(
            int successes, int samples, double stipulatedThreshold, double confidence) {
        validate(successes, samples, stipulatedThreshold, confidence);
        double observedRate = (double) successes / samples;
        if (stipulatedThreshold <= 0.0) {
            // A zero threshold is degenerate: any evidence clears it,
            // and the feasibility machinery (which requires a target in
            // (0, 1) exclusive) has nothing to gate. Trivially met.
            return new Judgement(State.MET, stipulatedThreshold, confidence,
                    observedRate, ESTIMATOR.lowerBound(successes, samples, confidence), 1);
        }
        VerificationFeasibilityEvaluator.FeasibilityResult feasibility =
                VerificationFeasibilityEvaluator.evaluate(
                        samples, stipulatedThreshold, confidence);
        if (!feasibility.feasible()) {
            return new Judgement(State.UNSUPPORTABLE, stipulatedThreshold, confidence,
                    observedRate, Double.NaN, feasibility.minimumSamples());
        }
        double lowerBound = ESTIMATOR.lowerBound(successes, samples, confidence);
        State state = lowerBound >= stipulatedThreshold ? State.MET : State.FAILED;
        return new Judgement(state, stipulatedThreshold, confidence,
                observedRate, lowerBound, feasibility.minimumSamples());
    }

    private static void validate(
            int successes, int samples, double stipulatedThreshold, double confidence) {
        if (samples <= 0) {
            throw new IllegalArgumentException("samples must be > 0, got: " + samples);
        }
        if (successes < 0 || successes > samples) {
            throw new IllegalArgumentException(
                    "successes must be in [0, " + samples + "], got: " + successes);
        }
        if (Double.isNaN(stipulatedThreshold)
                || stipulatedThreshold < 0.0 || stipulatedThreshold >= 1.0) {
            throw new IllegalArgumentException(
                    "stipulatedThreshold must be in [0, 1), got: " + stipulatedThreshold);
        }
        if (Double.isNaN(confidence) || confidence <= 0.0 || confidence >= 1.0) {
            throw new IllegalArgumentException(
                    "confidence must be in (0, 1) exclusive, got: " + confidence);
        }
    }
}
