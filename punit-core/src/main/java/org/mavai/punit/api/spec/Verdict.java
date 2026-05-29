package org.mavai.punit.api.spec;

import java.util.List;
import java.util.Objects;

/**
 * Three-state statistical verdict returned by a
 * {@link ProbabilisticTest}'s {@link Spec#conclude() conclude()}
 * call.
 */
public enum Verdict {

    /** Observed pass rate meets or exceeds the threshold under the configured confidence. */
    PASS,

    /** Observed pass rate is below the threshold under the configured confidence. */
    FAIL,

    /** Covariate misalignment or statistical ambiguity prevents a confident verdict. */
    INCONCLUSIVE;

    /**
     * Compose an ordered list of evaluated criteria into a single
     * verdict. {@link CriterionRole#REPORT_ONLY REPORT_ONLY} entries
     * are filtered out; the contributing entries are combined by
     * three-valued logic:
     *
     * <ul>
     *   <li>Empty contributing list → {@link #PASS} (a spec with zero
     *       required criteria is trivially satisfied).</li>
     *   <li>Any contributing entry is {@link #INCONCLUSIVE} →
     *       {@link #INCONCLUSIVE}.</li>
     *   <li>Any contributing entry is {@link #FAIL} → {@link #FAIL}.</li>
     *   <li>Otherwise → {@link #PASS}.</li>
     * </ul>
     *
     * Pure function: same inputs, same output; order-independent.
     */
    /**
     * Aggregate a list of three-valued verdicts under the methodology's
     * worst-case rule for the composite over per-criterion verdicts:
     *
     * <ul>
     *   <li>Empty list → {@link #PASS}.</li>
     *   <li>Any entry is {@link #FAIL} → {@link #FAIL}.</li>
     *   <li>Else any entry is {@link #INCONCLUSIVE} → {@link #INCONCLUSIVE}.</li>
     *   <li>Else → {@link #PASS}.</li>
     * </ul>
     *
     * <p>This is the composite-verdict rule per the methodology's
     * §1.4.6: a failing criterion dominates an inconclusive one, on
     * the principle that an inconclusive criterion may yet pass with
     * more evidence while a failing criterion has demonstrated
     * incompatibility with the threshold. The rule differs from
     * {@link #compose(List)}, which dominates with INCONCLUSIVE — that
     * method covers spec-layer verdict-component composition where an
     * inconclusive component blocks any verdict claim. Both rules
     * coexist; per-criterion composition over methodology-level
     * partition units uses {@code aggregate}, spec-layer
     * verdict-component composition continues to use {@code compose}.
     *
     * <p>Pure function: same inputs, same output; order-independent.
     */
    public static Verdict aggregate(List<Verdict> verdicts) {
        Objects.requireNonNull(verdicts, "verdicts");
        boolean sawInconclusive = false;
        for (Verdict v : verdicts) {
            Objects.requireNonNull(v, "verdict");
            if (v == FAIL) {
                return FAIL;
            }
            if (v == INCONCLUSIVE) {
                sawInconclusive = true;
            }
        }
        return sawInconclusive ? INCONCLUSIVE : PASS;
    }

    public static Verdict compose(List<EvaluatedCriterion> evaluated) {
        Objects.requireNonNull(evaluated, "evaluated");
        boolean sawFail = false;
        boolean sawContributing = false;
        for (EvaluatedCriterion entry : evaluated) {
            Objects.requireNonNull(entry, "evaluated entry");
            if (entry.role() != CriterionRole.REQUIRED) {
                continue;
            }
            sawContributing = true;
            Verdict v = entry.result().verdict();
            if (v == INCONCLUSIVE) {
                return INCONCLUSIVE;
            }
            if (v == FAIL) {
                sawFail = true;
            }
        }
        if (!sawContributing) {
            return PASS;
        }
        return sawFail ? FAIL : PASS;
    }
}
