package org.mavai.punit.api.spec;

import java.util.Objects;

/**
 * Per-criterion sample-outcome counts across a run, keyed by the
 * criterion's stable identifier. One record per
 * {@link org.mavai.punit.api.criterion.Criterion} the
 * {@link org.mavai.punit.api.Contract} declares.
 *
 * <p>The per-trial outcome is two-valued: a trial either passes or
 * fails. A failing trial carries a reason, and this record keeps the
 * reason breakdown so diagnostics can distinguish the two sources of
 * failure:
 * <ul>
 *   <li>{@link #conditionFail()} — the postcondition chain ran and at
 *       least one postcondition failed.</li>
 *   <li>{@link #transformFail()} — the criterion's transform failed
 *       (or produced no value), so the postcondition chain never
 *       ran.</li>
 * </ul>
 * Both kinds count in the denominator as a non-pass; {@link #fail()}
 * is their sum, together with the third kind below.
 *
 * <p>{@link #applyFail()} is the third: a trial that failed at apply —
 * a failed delivery, or any other {@code Outcome.fail} from the
 * contract — so that <em>no</em> criterion judged anything. Such a
 * trial counts as a failure against every declared criterion (owner
 * ruling, 2026-08-24, adopting the family's Python reader's counting).
 * It is a bucket of its own rather than a share of
 * {@link #transformFail()} because the diagnostic axes mean particular
 * things: a transform/no-value failure is one where a response
 * <em>arrived</em> and no testable value could be derived from it,
 * which is a fact about the contract's instrument. An apply failure is
 * a fact about the service or the transport, and folding the two
 * would misreport the axis a developer reads first.
 *
 * <p>The denominator is {@link #total()} = {@link #pass()} +
 * {@link #fail()}; the per-criterion observed pass rate is
 * {@link #pass()} / {@link #total()}. The methodology's "n_c = 0 →
 * verdict INCONCLUSIVE" rule applies only when {@link #total()} is
 * zero, which means the entire run had zero samples and the
 * feasibility gate has independently refused the test.
 *
 * @param criterionId  the criterion's stable identifier
 * @param pass         count of samples whose per-criterion outcome was
 *                     PASS
 * @param conditionFail count of FAIL samples where the postcondition
 *                     chain ran and a postcondition failed
 * @param transformFail count of FAIL samples where the transform
 *                     failed (or produced no value) and the chain
 *                     never ran
 * @param applyFail    count of samples that failed at apply, before
 *                     this criterion could judge anything
 */
public record CriterionSampleCounts(
        String criterionId,
        int pass,
        int conditionFail,
        int transformFail,
        int applyFail) {

    public CriterionSampleCounts {
        Objects.requireNonNull(criterionId, "criterionId");
        if (pass < 0 || conditionFail < 0 || transformFail < 0 || applyFail < 0) {
            throw new IllegalArgumentException(
                    "counts must be non-negative; got pass=" + pass
                            + ", conditionFail=" + conditionFail
                            + ", transformFail=" + transformFail
                            + ", applyFail=" + applyFail);
        }
    }

    /**
     * Total failing trials — condition failures, transform / no-value
     * failures, and trials that failed before this criterion could
     * judge anything. All three count in the denominator as a
     * non-pass.
     */
    public int fail() {
        return conditionFail + transformFail + applyFail;
    }

    /** Sum of all per-criterion sample outcomes — the denominator. */
    public int total() {
        return pass + fail();
    }

    /**
     * Observed pass rate: {@link #pass()} / {@link #total()}. Returns
     * {@code NaN} when {@link #total()} is zero — callers should treat
     * that as verdict INCONCLUSIVE per the methodology's $n_c = 0$
     * rule.
     */
    public double observedPassRate() {
        int t = total();
        return t == 0 ? Double.NaN : (double) pass / (double) t;
    }
}
