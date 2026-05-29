package org.mavai.punit.api.spec;

import java.util.List;
import java.util.Optional;

import org.mavai.punit.api.FactorBundle;
import org.mavai.punit.api.covariate.Covariate;
import org.mavai.punit.api.covariate.CovariateProfile;

/**
 * The framework's read-side adapter for resolving baseline statistics
 * at criterion-evaluation time.
 *
 * <p>Conceptually a small functional interface (one query method per
 * concern); kept as a regular interface so the {@link #EMPTY} constant
 * has a stable place to live. Implementations live in the engine
 * layer (e.g. punit-core's {@code engine.baseline.YamlBaselineProvider});
 * the {@code api package} module declares only the contract.
 *
 * <p>The framework consults a {@code BaselineProvider} once per
 * empirical {@link Criterion} during {@link Spec#dispatch dispatch} of
 * a {@link ProbabilisticTest}'s {@code conclude} call, populating the
 * matching slot on the {@link EvaluationContext}.
 *
 * <h2>Lookup contract</h2>
 *
 * <p>The covariate-aware overloads ({@link #baselineFor(String, FactorBundle,
 * String, Class, CovariateProfile, List)} and the matching identity
 * lookup) are the primary contract. Implementations supply them.
 * The convenience overloads without {@code currentProfile} and
 * {@code declarations} default-delegate with {@link CovariateProfile#empty()}
 * and {@link List#of()}; covariate-aware implementations treat those
 * defaults as the "service contract declares no covariates" path.
 *
 * <p>The framework wraps the underlying provider in a profile-bound
 * decorator before handing it to {@link Spec#dispatch} so that
 * spec implementations can call the convenience overloads and still
 * exercise covariate-aware lookup with the run's resolved profile.
 *
 * @see CovariateProfile
 * @see Covariate
 */
public interface BaselineProvider {

    /**
     * @param currentProfile the resolved covariate profile for the
     *                       current run; {@link CovariateProfile#empty()}
     *                       when the service contract declared no covariates
     * @param declarations   the service contract's covariate declarations,
     *                       in declaration order; {@link List#of()}
     *                       when the service contract declared no covariates
     * @return the baseline statistics of the requested kind for the
     *         best-matching baseline, or {@link Optional#empty()} when
     *         none is selectable
     */
    <S extends BaselineStatistics> Optional<S> baselineFor(
            String serviceContractId,
            FactorBundle factors,
            String criterionName,
            Class<S> statisticsType,
            CovariateProfile currentProfile,
            List<Covariate> declarations);

    /**
     * Covariate-aware identity lookup. Used for the cross-process
     * {@link EmpiricalChecks#inputsIdentityMatch} integrity check.
     */
    Optional<String> baselineInputsIdentityFor(
            String serviceContractId,
            FactorBundle factors,
            CovariateProfile currentProfile,
            List<Covariate> declarations);

    /**
     * Convenience overload for callers that don't carry covariate
     * state. Equivalent to the covariate-aware overload with empty
     * profile + empty declarations — restricts selection to
     * baselines whose own profile is empty.
     */
    default <S extends BaselineStatistics> Optional<S> baselineFor(
            String serviceContractId,
            FactorBundle factors,
            String criterionName,
            Class<S> statisticsType) {
        return baselineFor(serviceContractId, factors, criterionName, statisticsType,
                CovariateProfile.empty(), List.of());
    }

    /**
     * Convenience overload for callers that don't carry covariate
     * state. Restricts selection to baselines whose own profile is
     * empty.
     */
    default Optional<String> baselineInputsIdentityFor(
            String serviceContractId, FactorBundle factors) {
        return baselineInputsIdentityFor(serviceContractId, factors,
                CovariateProfile.empty(), List.of());
    }

    /**
     * Lookup that returns both the selected statistics and a list of
     * human-readable notes describing rejected candidates and
     * fallback decisions — for surfacing through
     * {@link ProbabilisticTestResult#warnings()} so an INCONCLUSIVE
     * verdict can explain <em>why</em> the baseline didn't match.
     *
     * <p>Default implementation delegates to {@link #baselineFor}
     * and produces no notes. Concrete providers override to populate
     * the notes from the selection algorithm.
     */
    default <S extends BaselineStatistics> BaselineLookup<S> baselineLookup(
            String serviceContractId,
            FactorBundle factors,
            String criterionName,
            Class<S> statisticsType,
            CovariateProfile currentProfile,
            List<Covariate> declarations) {
        return BaselineLookup.of(baselineFor(
                serviceContractId, factors, criterionName, statisticsType,
                currentProfile, declarations));
    }

    /**
     * Convenience overload of {@link #baselineLookup} for callers
     * (typically the typed-spec dispatch path) that don't carry
     * covariate state directly. Delegates with empty profile + empty
     * declarations; the framework's profile-bound decorator
     * overrides this to substitute the run's captured covariate
     * state, so a bare call from {@code conclude()} still exercises
     * covariate-aware lookup.
     */
    default <S extends BaselineStatistics> BaselineLookup<S> baselineLookup(
            String serviceContractId,
            FactorBundle factors,
            String criterionName,
            Class<S> statisticsType) {
        return baselineLookup(serviceContractId, factors, criterionName, statisticsType,
                CovariateProfile.empty(), List.of());
    }

    /**
     * The empty provider — always returns {@link Optional#empty()}.
     * Used by the engine's no-arg / unconfigured paths and by tests
     * that don't exercise empirical resolution.
     */
    BaselineProvider EMPTY = new BaselineProvider() {
        @Override
        public <S extends BaselineStatistics> Optional<S> baselineFor(
                String serviceContractId,
                FactorBundle factors,
                String criterionName,
                Class<S> statisticsType,
                CovariateProfile currentProfile,
                List<Covariate> declarations) {
            return Optional.empty();
        }

        @Override
        public Optional<String> baselineInputsIdentityFor(
                String serviceContractId, FactorBundle factors,
                CovariateProfile currentProfile, List<Covariate> declarations) {
            return Optional.empty();
        }
    };
}
