package org.mavai.punit.verdict;

import java.util.List;
import java.util.Objects;

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
 * <p>The composite has been the contract's verdict authority since
 * the step-4 cutover. {@link ProbabilisticTestVerdict#punitVerdict()}
 * and this {@link #composite()} carry the same value on every run
 * where this structure is populated; the structural redundancy is a
 * clarity feature — consumers reading the methodology-level
 * decomposition read {@code composite()}, consumers reading the
 * harness-level signal read {@code punitVerdict()}.
 *
 * @param criteria   per-criterion rows in contract declaration order
 * @param composite  the composite verdict over the rows
 */
public record PerCriterionStructure(
        List<CriterionRow> criteria,
        Verdict composite) {

    public PerCriterionStructure {
        Objects.requireNonNull(criteria, "criteria");
        Objects.requireNonNull(composite, "composite");
        criteria = List.copyOf(criteria);
    }
}
