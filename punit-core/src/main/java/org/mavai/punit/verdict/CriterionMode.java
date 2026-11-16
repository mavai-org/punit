package org.mavai.punit.verdict;

/**
 * How a methodology criterion is decided (Statistical Companion §1.4.5).
 *
 * <p>An <b>inferential</b> criterion estimates its pass rate and is decided
 * by a rule against a threshold. An <b>observational</b> criterion — a
 * zero-failures commitment — estimates no rate and has no threshold: it
 * passes iff no trial failed. An observational criterion is never an
 * inferential one at a threshold of 1.
 *
 * <p>This is the criterion's mode, not the enforcement mode of a dimension
 * ({@link org.mavai.punit.api.spec.EnforcementMode}).
 */
public enum CriterionMode {

    /** Decided by a rule against a threshold: the default. */
    INFERENTIAL("inferential"),

    /** A zero-failures commitment, decided by observation alone. */
    OBSERVATIONAL("observational");

    private final String label;

    CriterionMode(String label) {
        this.label = label;
    }

    /** The label the verdict record spells. */
    public String label() {
        return label;
    }
}
