package org.mavai.punit.api.spec;

/**
 * Per-entry role marker distinguishing a criterion whose result
 * contributes to the spec's combined verdict from one that is merely
 * reported.
 *
 * <p>The role is stamped at the spec builder's {@code .criterion(c)}
 * (REQUIRED) / {@code .reportOnly(c)} (REPORT_ONLY) call site. No
 * criterion instance knows its own role.
 */
public enum CriterionRole {

    /** The criterion's result contributes to the combined verdict. */
    REQUIRED,

    /**
     * The criterion's result is evaluated and attached to the result
     * record but excluded from the combined-verdict composition.
     */
    REPORT_ONLY
}
