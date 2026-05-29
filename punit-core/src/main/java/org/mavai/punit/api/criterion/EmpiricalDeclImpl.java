package org.mavai.punit.api.criterion;

import java.util.List;
import java.util.Objects;

import org.mavai.punit.api.PercentileKey;

/**
 * Package-private singleton implementation of {@link EmpiricalDecl}.
 * Stateless — every method constructs and returns a fresh decl.
 */
final class EmpiricalDeclImpl implements EmpiricalDecl {

    static final EmpiricalDeclImpl INSTANCE = new EmpiricalDeclImpl();

    private EmpiricalDeclImpl() {
        /* singleton */
    }

    @Override
    public <O> CriterionDecl<O> passRate() {
        return new CriterionDecl<>(CriterionPosture.empirical(), List.of());
    }

    @Override
    public LatencyCriterion atMost(PercentileKey key) {
        Objects.requireNonNull(key, "key");
        return LatencyCriterion.empirical(key);
    }
}
