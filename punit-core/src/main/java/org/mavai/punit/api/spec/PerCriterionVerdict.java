package org.mavai.punit.api.spec;

import java.util.Objects;

/**
 * The per-criterion three-valued verdict for one methodology-level
 * criterion across a run, plus the supporting counts, observed
 * pass-rate, and the threshold the verdict was judged against.
 *
 * <p>Per the step-3 design the threshold is contract-inherited: every
 * criterion is judged against the same threshold the contract's
 * legacy verdict path resolves. The {@link #threshold()} field is
 * therefore the same value for every per-criterion verdict in one
 * run — it is carried per-criterion so that downstream renderers can
 * surface it inline with each criterion's row without reaching back
 * across the result.
 *
 * @param criterionId the criterion's stable identifier
 * @param verdict     PASS / FAIL / INCONCLUSIVE under the
 *                    contract-inherited threshold
 * @param counts      per-sample-outcome counts that produced this verdict
 * @param observed    the criterion's observed pass-rate
 *                    ({@link CriterionSampleCounts#observedPassRate()})
 *                    or {@code NaN} when {@link CriterionSampleCounts#total()}
 *                    is zero
 * @param threshold   the contract-inherited threshold the verdict was
 *                    judged against
 */
public record PerCriterionVerdict(
        String criterionId,
        Verdict verdict,
        CriterionSampleCounts counts,
        double observed,
        double threshold) {

    public PerCriterionVerdict {
        Objects.requireNonNull(criterionId, "criterionId");
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(counts, "counts");
    }
}
