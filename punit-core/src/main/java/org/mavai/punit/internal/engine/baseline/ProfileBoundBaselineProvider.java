package org.mavai.punit.internal.engine.baseline;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.mavai.punit.api.spec.ProbabilisticTest;
import org.mavai.punit.api.FactorBundle;
import org.mavai.punit.api.covariate.Covariate;
import org.mavai.punit.api.covariate.CovariateProfile;
import org.mavai.punit.api.spec.BaselineLookup;
import org.mavai.punit.api.spec.BaselineProvider;
import org.mavai.punit.api.spec.BaselineStatistics;

/**
 * Wraps a {@link BaselineProvider} with a captured covariate profile
 * and declarations so that the convenience overloads (no
 * profile/declarations arguments) become covariate-aware lookups
 * against the captured values.
 *
 * <p>The framework constructs one of these per probabilistic-test run
 * after resolving the run's covariate profile from the service contract, so
 * that {@link ProbabilisticTest#dispatch}
 * implementations can call the convenience overloads on
 * {@code BaselineProvider} and still exercise covariate-aware
 * selection.
 *
 * <p>The covariate-aware overloads pass through with whatever profile
 * / declarations the caller supplies; only the convenience overloads
 * get the captured-profile substitution. This means a caller that
 * <em>already</em> has covariate state can still bypass the binding
 * and pass its own.
 */
public final class ProfileBoundBaselineProvider implements BaselineProvider {

    private final BaselineProvider delegate;
    private final CovariateProfile profile;
    private final List<Covariate> declarations;

    private ProfileBoundBaselineProvider(
            BaselineProvider delegate,
            CovariateProfile profile,
            List<Covariate> declarations) {
        this.delegate = delegate;
        this.profile = profile;
        this.declarations = declarations;
    }

    /**
     * @return a binding wrapper, or {@code delegate} unchanged when
     *         there is nothing to bind ({@code declarations} empty)
     */
    public static BaselineProvider bind(
            BaselineProvider delegate,
            CovariateProfile profile,
            List<Covariate> declarations) {
        Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(declarations, "declarations");
        if (declarations.isEmpty()) {
            // No covariates declared → no binding needed. Skipping
            // the wrapper keeps stack traces and toString honest for
            // non-covariate service contracts.
            return delegate;
        }
        return new ProfileBoundBaselineProvider(delegate,
                profile, List.copyOf(declarations));
    }

    @Override
    public <S extends BaselineStatistics> Optional<S> baselineFor(
            String serviceContractId,
            FactorBundle factors,
            String criterionName,
            Class<S> statisticsType,
            CovariateProfile currentProfile,
            List<Covariate> declarations) {
        return delegate.baselineFor(serviceContractId, factors, criterionName,
                statisticsType, currentProfile, declarations);
    }

    @Override
    public Optional<String> baselineInputsIdentityFor(
            String serviceContractId, FactorBundle factors,
            CovariateProfile currentProfile, List<Covariate> declarations) {
        return delegate.baselineInputsIdentityFor(
                serviceContractId, factors, currentProfile, declarations);
    }

    @Override
    public <S extends BaselineStatistics> Optional<S> baselineFor(
            String serviceContractId,
            FactorBundle factors,
            String criterionName,
            Class<S> statisticsType) {
        return delegate.baselineFor(serviceContractId, factors, criterionName,
                statisticsType, profile, declarations);
    }

    @Override
    public Optional<String> baselineInputsIdentityFor(
            String serviceContractId, FactorBundle factors) {
        return delegate.baselineInputsIdentityFor(serviceContractId, factors,
                profile, declarations);
    }

    @Override
    public <S extends BaselineStatistics> BaselineLookup<S> baselineLookup(
            String serviceContractId,
            FactorBundle factors,
            String criterionName,
            Class<S> statisticsType,
            CovariateProfile currentProfile,
            List<Covariate> declarations) {
        return delegate.baselineLookup(serviceContractId, factors, criterionName,
                statisticsType, currentProfile, declarations);
    }

    @Override
    public <S extends BaselineStatistics> BaselineLookup<S> baselineLookup(
            String serviceContractId,
            FactorBundle factors,
            String criterionName,
            Class<S> statisticsType) {
        return delegate.baselineLookup(serviceContractId, factors, criterionName,
                statisticsType, profile, declarations);
    }
}
