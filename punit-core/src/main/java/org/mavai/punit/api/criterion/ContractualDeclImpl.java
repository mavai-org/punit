package org.mavai.punit.api.criterion;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.mavai.punit.api.PercentileKey;
import org.mavai.punit.api.ThresholdOrigin;

/**
 * Package-private singleton implementation of {@link ContractualDecl}.
 * Stateless — every method constructs and returns a fresh decl.
 */
final class ContractualDeclImpl implements ContractualDecl {

    static final ContractualDeclImpl INSTANCE = new ContractualDeclImpl();

    private ContractualDeclImpl() {
        /* singleton */
    }

    @Override
    public <O> CriterionDecl<O> passRate(double rate) {
        return new CriterionDecl<>(
                CriterionPosture.meeting(ThresholdOrigin.UNSPECIFIED, rate),
                List.of());
    }

    @Override
    public <O> CriterionDecl<O> zeroFailures() {
        return new CriterionDecl<>(
                CriterionPosture.zeroFailures(ThresholdOrigin.UNSPECIFIED),
                List.of());
    }

    @Override
    public LatencyCriterion atMost(PercentileKey key, Duration value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        return LatencyCriterion.meeting(ThresholdOrigin.UNSPECIFIED,
                LatencyCriterion.ceiling(key, value));
    }
}
