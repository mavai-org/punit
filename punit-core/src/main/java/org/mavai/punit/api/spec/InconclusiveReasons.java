package org.mavai.punit.api.spec;

/**
 * The vocabulary for the verdict-reason field on an INCONCLUSIVE
 * verdict.
 *
 * <p>Six reasons are distinguished. Two are detected at the run
 * level (covariate alignment and budget exhaustion); the other four
 * arise inside a criterion's {@link Criterion#evaluate} and are
 * surfaced via the criterion's {@link CriterionResult#detail()}
 * map under {@link #DETAIL_KEY}. The verdict builder reads the
 * detail key when synthesising the verdict reason, falling back to
 * {@link #INSUFFICIENT_EVIDENCE} when no discriminant is present.
 *
 * <p>Lives in the {@code api.spec} package because both the
 * shared empirical-check helpers (here, alongside
 * {@link EmpiricalChecks}) and the concrete criteria in
 * {@code engine.criteria} stamp the discriminant — and the verdict
 * builder reads it. {@code api.spec} is the lowest common ancestor
 * package; placing the vocabulary anywhere else would force one
 * direction of the dependency to go upward through the layering.
 *
 * <p>This class is the single point of change for the vocabulary.
 */
public final class InconclusiveReasons {

    private InconclusiveReasons() { }

    /**
     * Detail-map key under which a criterion stamps its
     * INCONCLUSIVE-reason discriminant. Value is one of the
     * criterion-level constants below.
     */
    public static final String DETAIL_KEY = "inconclusiveReason";

    // ── Criterion-level reasons (set on CriterionResult.detail) ──

    /**
     * Empirical mode but no baseline could be resolved for the use
     * case + factors + covariates. Operator's next step: run a
     * measure experiment to produce one.
     */
    public static final String NO_BASELINE_AVAILABLE = "no baseline available";

    /**
     * The test's inputs identity differs from the resolved
     * baseline's recorded inputs identity. Operator's next step:
     * re-baseline with the current inputs.
     */
    public static final String BASELINE_INPUTS_MISMATCH = "baseline inputs mismatch";

    /**
     * The test executed more samples than the resolved baseline
     * supports. Operator's next step: re-baseline at a sample
     * count ≥ the test's.
     */
    public static final String BASELINE_SAMPLE_SIZE_EXCEEDED = "baseline sample size exceeded";

    /**
     * Too few samples relative to the threshold for a statistical
     * determination — the residual catch-all when no more specific
     * cause was named on the criterion's detail map. Operator's
     * next step: increase the sample count.
     */
    public static final String INSUFFICIENT_EVIDENCE = "insufficient evidence";

    // ── Run-level reasons (set by the builder, not by criteria) ──

    /**
     * Test conditions do not match the baseline. Detected at the
     * run level from {@link org.mavai.punit.api.covariate.CovariateAlignment}.
     * Operator's next step: re-baseline or check environment.
     */
    public static final String COVARIATE_MISALIGNMENT = "covariate misalignment";

    /**
     * Time or token budget expired before sufficient samples.
     * Detected at the run level from the engine's termination
     * reason. Operator's next step: raise the budget or accept
     * the partial run.
     */
    public static final String BUDGET_EXHAUSTED = "budget exhausted";
}
