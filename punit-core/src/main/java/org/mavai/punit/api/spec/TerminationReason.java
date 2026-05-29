package org.mavai.punit.api.spec;

/**
 * Why a configuration's sample loop stopped.
 *
 * <p>Attached to {@link SampleSummary} so the spec's
 * {@link Spec#consume(Configuration, SampleSummary)} callback and the
 * downstream reporters can distinguish a naturally-completed run from
 * one that exhausted a budget early.
 */
public enum TerminationReason {

    /** All requested samples completed. */
    COMPLETED,

    /** Wall-clock time budget reached before all samples completed. */
    TIME_BUDGET,

    /** Token budget reached before all samples completed. */
    TOKEN_BUDGET,

    /**
     * Threshold became mathematically unreachable: even if every remaining
     * sample passes, the observed pass rate cannot meet the declared
     * threshold. Verdict is FAIL.
     */
    IMPOSSIBILITY,

    /**
     * Threshold is mathematically guaranteed: enough samples have already
     * passed that the threshold holds regardless of the remaining outcomes,
     * and the run has cleared the statistical-validity floor. Verdict is
     * PASS.
     */
    SUCCESS_GUARANTEED
}
