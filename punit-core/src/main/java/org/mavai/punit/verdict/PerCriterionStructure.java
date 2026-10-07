package org.mavai.punit.verdict;

import java.util.List;
import java.util.Objects;

import org.mavai.punit.api.spec.EnforcementMode;
import org.mavai.punit.api.spec.Verdict;

/**
 * The per-criterion structural decomposition attached to a
 * {@link ProbabilisticTestVerdict}: one row per methodology-level
 * criterion the contract declared, plus the composite verdict over
 * those rows under the FAIL-dominant rule (companion §1.4.6).
 *
 * <p>Persistence-layer cousin of
 * {@code org.mavai.punit.api.spec.PerCriterionEvaluation}; same
 * shape, different abstraction layer. The translation seam is
 * {@code VerdictAdapter}.
 *
 * <p>{@link ProbabilisticTestVerdict#punitVerdict()} is the test
 * verdict, which composes this composite with the latency dimension's
 * verdict over the enforced dimensions; the two agree only when the
 * functional dimension alone binds.
 *
 * <p>The composite is the functional dimension's verdict {@code V_rate}.
 * Its {@code mode} says whether it entered the test verdict: enforced,
 * or advisory when the run made the functional dimension advisory, so
 * that every row is still decided by its rule and the composite
 * reported, but neither binds (Statistical Companion §12.6).
 *
 * @param criteria   per-criterion rows in contract declaration order
 * @param composite  the composite verdict over the rows
 * @param mode       the functional dimension's mode
 */
public record PerCriterionStructure(
        List<CriterionRow> criteria,
        Verdict composite,
        EnforcementMode mode) {

    public PerCriterionStructure {
        Objects.requireNonNull(criteria, "criteria");
        Objects.requireNonNull(composite, "composite");
        Objects.requireNonNull(mode, "mode");
        criteria = List.copyOf(criteria);
    }

    /** An enforced functional dimension: the default. */
    public PerCriterionStructure(List<CriterionRow> criteria, Verdict composite) {
        this(criteria, composite, EnforcementMode.ENFORCED);
    }
}
