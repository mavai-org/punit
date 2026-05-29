package org.mavai.punit.api.criterion;

/**
 * The two-valued outcome of evaluating one criterion against one
 * sample's produced value.
 *
 * <p>A criterion partitions the functional dimension into one
 * independently-evaluated statistical stream. For a single sample,
 * that evaluation lands in exactly one of:
 *
 * <ul>
 *   <li>{@link #PASS} — the criterion's postcondition chain ran to
 *       completion and every postcondition passed.</li>
 *   <li>{@link #FAIL} — the trial did not pass. Either the
 *       postcondition chain ran and at least one postcondition
 *       failed (a condition failure), or the criterion's transform
 *       (if any) returned {@code Outcome.Fail} or threw and so the
 *       postcondition chain never ran (a transform / no-value
 *       failure). Both are failures of the trial; a FAIL of the
 *       second kind carries the failing reason for diagnostics.</li>
 * </ul>
 *
 * <p>A failed trial — of either kind — counts in the criterion's
 * denominator as a non-pass. The per-trial indicator is 0; it is
 * never undefined.
 *
 * <p>The criterion-aggregate verdict (PASS / FAIL / INCONCLUSIVE for
 * the whole criterion across all samples) is a separate concept built
 * from a sequence of per-sample outcomes by an aggregation policy. The
 * verdict-level INCONCLUSIVE — the statistical procedure cannot render
 * PASS/FAIL (feasibility gate, zero samples, covariate misalignment) —
 * is unrelated to the per-trial outcome captured here. This enum
 * captures only the per-sample case.
 */
public enum CriterionSampleOutcome {
    PASS,
    FAIL
}
