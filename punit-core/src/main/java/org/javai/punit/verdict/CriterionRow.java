package org.javai.punit.verdict;

import java.util.Objects;

/**
 * One per-criterion row inside a {@link PerCriterionStructure}:
 * the criterion's identifier, its three-valued aggregate verdict,
 * the per-outcome sample counts, the observed pass-rate, and the
 * threshold the verdict was judged against.
 *
 * <p>Persistence-layer cousin of
 * {@code org.javai.punit.api.spec.PerCriterionVerdict}; the two
 * carry the same data but live in different abstraction layers
 * (spec vs. verdict record) so renderers and persistence stay
 * decoupled from api-package types.
 *
 * @param criterionId   stable identifier for the criterion
 * @param verdict       PASS / FAIL / INCONCLUSIVE under the
 *                      contract-inherited threshold
 * @param pass          count of PASS samples
 * @param fail          count of FAIL samples (condition failures plus
 *                      transform / no-value failures)
 * @param inconclusive  retained per-trial inconclusive count for the
 *                      cross-framework verdict-XML schema; always
 *                      zero since a per-trial inconclusive is now a
 *                      FAIL
 * @param observedRate  observed marginal pass-rate ({@code pass / total})
 *                      or {@link Double#NaN} when {@code total} is zero
 * @param threshold     the contract-inherited threshold the verdict
 *                      was judged against or {@link Double#NaN} when
 *                      no threshold was resolved (e.g. the run gated
 *                      INCONCLUSIVE before threshold derivation)
 */
public record CriterionRow(
        String criterionId,
        org.javai.punit.api.spec.Verdict verdict,
        int pass,
        int fail,
        int inconclusive,
        double observedRate,
        double threshold) {

    public CriterionRow {
        Objects.requireNonNull(criterionId, "criterionId");
        Objects.requireNonNull(verdict, "verdict");
        if (pass < 0 || fail < 0 || inconclusive < 0) {
            throw new IllegalArgumentException(
                    "counts must be non-negative; got pass=" + pass
                            + ", fail=" + fail
                            + ", inconclusive=" + inconclusive);
        }
    }

    /** Sum of all per-criterion sample outcomes — the marginal denominator. */
    public int total() {
        return pass + fail + inconclusive;
    }
}
