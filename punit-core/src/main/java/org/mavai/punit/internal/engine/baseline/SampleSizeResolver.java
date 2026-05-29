package org.mavai.punit.internal.engine.baseline;

import java.util.List;
import java.util.Optional;

import org.mavai.punit.api.FactorBundle;
import org.mavai.punit.api.ServiceContract;
import org.mavai.punit.api.covariate.Covariate;
import org.mavai.punit.api.covariate.CovariateProfile;
import org.mavai.punit.api.criterion.Criterion;
import org.mavai.punit.api.criterion.CriterionPosture;
import org.mavai.punit.api.spec.BaselineProvider;
import org.mavai.punit.api.spec.PassRateStatistics;
import org.mavai.punit.api.spec.PerCriterionPassRateStatistics;
import org.mavai.punit.internal.engine.covariate.CovariateResolver;
import org.mavai.punit.statistics.SampleSizeCalculator;

/**
 * Resolves the *effective* sample count for a run by composing the
 * author-declared sample count with any confidence-first criterion's
 * power-analysis floor. The directive's silent-uplift rule:
 *
 * <pre>
 * N_effective = max(
 *     declared,
 *     max over confidence-first criteria of PowerAnalysis(baseline, mde, power)
 * )
 * </pre>
 *
 * <p>Called by the engine before sampling starts. The resolution is
 * captured on the returned {@link Resolution} record so the verdict
 * path can report which criterion drove any uplift.
 */
public final class SampleSizeResolver {

    private static final double DEFAULT_CONFIDENCE = 0.95;
    private static final SampleSizeCalculator CALCULATOR = new SampleSizeCalculator();

    private SampleSizeResolver() { }

    /**
     * The result of {@link #resolve}: the effective sample count plus
     * provenance.
     *
     * @param declared the author's {@code Sampling.samples(N)} value
     * @param effective the post-uplift sample count that will actually run
     * @param drivenBy when uplifted: the id of the criterion whose
     *                 PowerAnalysis floor pinned {@code effective};
     *                 empty when {@code effective == declared}
     */
    public record Resolution(int declared, int effective, Optional<String> drivenBy) {

        /** Whether the framework silently uplifted the declared count. */
        public boolean wasUplifted() {
            return effective > declared && drivenBy.isPresent();
        }
    }

    /**
     * Compute the effective sample count for a configuration.
     *
     * @param contract       the service contract instance for this configuration
     *                       (already constructed via the spec's factory)
     * @param factors        the configuration's factor bundle
     * @param baselineProvider the framework's baseline provider
     * @param declaredSamples the author's declared sample count
     *                        (typically {@code Configuration.samples()})
     * @return the {@link Resolution}: effective count + provenance
     */
    public static <FT, IT, OT> Resolution resolve(
            ServiceContract<FT, IT, OT> contract,
            FactorBundle factors,
            BaselineProvider baselineProvider,
            int declaredSamples) {

        List<? extends Criterion<OT>> criteria = contract.effectiveCriteria();
        List<? extends Criterion<OT>> confidenceFirst = criteria.stream()
                .filter(c -> c.posture().isConfidenceFirst())
                .toList();
        if (confidenceFirst.isEmpty()) {
            return new Resolution(declaredSamples, declaredSamples, Optional.empty());
        }

        // Resolve the baseline once for the contract's id + factor
        // bundle. PerCriterionPassRateStatistics is the K-aware shape;
        // we read the per-criterion rate when present.
        String serviceContractId = contract.id();
        List<Covariate> declarations = contract.covariates();
        CovariateProfile profile = declarations.isEmpty()
                ? CovariateProfile.empty()
                : CovariateResolver.defaults().resolve(
                        declarations, contract.customCovariateResolvers());
        Optional<PerCriterionPassRateStatistics> baseline = baselineProvider.baselineFor(
                serviceContractId, factors, "bernoulli-pass-rate",
                PerCriterionPassRateStatistics.class, profile, declarations);
        if (baseline.isEmpty()) {
            // No baseline yet — the criterion's empirical evaluation
            // will produce a no-baseline INCONCLUSIVE at conclude
            // time. Don't uplift; let the verdict path handle the
            // missing baseline as its own diagnostic.
            return new Resolution(declaredSamples, declaredSamples, Optional.empty());
        }

        int maxRequired = declaredSamples;
        String drivenBy = null;
        for (Criterion<OT> c : confidenceFirst) {
            CriterionPosture posture = c.posture();
            double mde = posture.mde().getAsDouble();
            double power = posture.power().getAsDouble();
            double confidence = posture.confidenceFloor().orElse(DEFAULT_CONFIDENCE);
            PassRateStatistics stats = baseline.get().byCriterion().get(c.id());
            if (stats == null && baseline.get().byCriterion().size() == 1) {
                // K=1 isomorphism: a single-entry baseline matches the
                // criterion even when the ids disagree (the legacy
                // "contract" id versus the auto-derived class name).
                stats = baseline.get().byCriterion().values().iterator().next();
            }
            if (stats == null) {
                continue;
            }
            double baselineRate = stats.observedPassRate();
            if (baselineRate - mde <= 0.0) {
                // Defer to the verdict path's own diagnostic on
                // alternative-hypothesis-rate ≤ 0.
                continue;
            }
            int required = CALCULATOR
                    .calculateForPower(baselineRate, mde, confidence, power)
                    .requiredSamples();
            if (required > maxRequired) {
                maxRequired = required;
                drivenBy = c.id();
            }
        }
        if (drivenBy == null) {
            return new Resolution(declaredSamples, declaredSamples, Optional.empty());
        }
        return new Resolution(declaredSamples, maxRequired, Optional.of(drivenBy));
    }
}
