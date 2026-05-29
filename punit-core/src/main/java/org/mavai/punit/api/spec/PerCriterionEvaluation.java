package org.mavai.punit.api.spec;

import java.util.List;
import java.util.Objects;

/**
 * The per-criterion evaluation bundle attached to a
 * {@link ProbabilisticTestResult}: one verdict per methodology-level
 * criterion the contract declares, plus the composite verdict over
 * those per-criterion verdicts under the methodology's worst-case
 * rule ({@link Verdict#aggregate(List)}).
 *
 * <p>The composite is computed and surfaced but is <em>not</em> the
 * contract's overall verdict authority in this step. When the
 * composite disagrees with the legacy
 * {@link ProbabilisticTestResult#verdict() flat-aggregation verdict},
 * the transparent-stats output flags the disagreement explicitly —
 * that surfaced disagreement is the empirical evidence the cutover
 * step relies on.
 *
 * @param perCriterionVerdicts the verdict per declared criterion, in
 *                             contract declaration order; empty for
 *                             apply-level-failed runs that produced
 *                             no per-criterion sample evaluations
 * @param compositeVerdict     {@link Verdict#aggregate(List)} over the
 *                             per-criterion verdicts; defaults to
 *                             {@link Verdict#PASS} on an empty list
 *                             per the rule's empty-case semantics
 */
public record PerCriterionEvaluation(
        List<PerCriterionVerdict> perCriterionVerdicts,
        Verdict compositeVerdict) {

    public PerCriterionEvaluation {
        Objects.requireNonNull(perCriterionVerdicts, "perCriterionVerdicts");
        Objects.requireNonNull(compositeVerdict, "compositeVerdict");
        perCriterionVerdicts = List.copyOf(perCriterionVerdicts);
    }

    /** Empty evaluation — no per-criterion data; composite defaults to PASS. */
    public static PerCriterionEvaluation empty() {
        return new PerCriterionEvaluation(List.of(), Verdict.PASS);
    }
}
