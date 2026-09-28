package org.mavai.punit.internal.engine.baseline;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

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
import org.mavai.punit.statistics.Methodology;
import org.mavai.punit.statistics.RegressionSizing;
import org.mavai.punit.statistics.StatisticalDefaults;

/**
 * Resolves the <em>effective</em> sample count for a run by composing
 * the author-declared sample count with the sizing criteria's computed
 * requirements. The silent-uplift rule:
 *
 * <pre>
 * N_effective = max(
 *     declared,
 *     max over sizing criteria of their required size
 * )
 * </pre>
 *
 * <p>A sizing criterion declares the <em>design alternative rate</em> —
 * the true rate at which the test must reach its target power — either
 * absolutely ({@code .tolerating(rate)}) or as a detectable drop below
 * the baseline ({@code .detectingMde(m)}). The test is sized by
 * <b>resolved sizing</b> against the measured baseline (Statistical
 * Companion §5.4.1): its observed count fixes the
 * {@code regression/fisher} cutoff at every candidate size, and the
 * required size is the smallest from which the resolved power stays at
 * the target up to the baseline's size. A design no test up to the
 * baseline's size can power is refused ({@code BASELINE_TOO_SMALL}): the
 * baseline must be measured larger.
 *
 * <p>Called by the engine before sampling starts. The resolution is
 * captured on the returned {@link Resolution} record so the verdict path
 * can report which criterion drove any uplift.
 */
public final class SampleSizeResolver {

    private SampleSizeResolver() { }

    /**
     * The result of {@link #resolve}: the effective sample count plus
     * provenance.
     *
     * @param declared the author's {@code Sampling.samples(N)} value
     * @param effective the post-uplift sample count that will actually run
     * @param drivenBy when uplifted: the id of the criterion whose required
     *                 size pinned {@code effective}; empty when
     *                 {@code effective == declared}
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
     * @param contract         the service contract instance for this configuration
     * @param factors          the configuration's factor bundle
     * @param baselineProvider the framework's baseline provider
     * @param declaredSamples  the author's declared sample count
     * @return the {@link Resolution}: effective count + provenance
     */
    public static <FT, IT, OT> Resolution resolve(
            ServiceContract<FT, IT, OT> contract,
            FactorBundle factors,
            BaselineProvider baselineProvider,
            int declaredSamples) {
        List<? extends Criterion<OT>> sizing = contract.effectiveCriteria().stream()
                .filter(c -> c.posture().isConfidenceFirst() || c.posture().isRiskDriven())
                .toList();
        if (sizing.isEmpty()) {
            return new Resolution(declaredSamples, declaredSamples, Optional.empty());
        }
        List<Covariate> declarations = contract.covariates();
        CovariateProfile profile = declarations.isEmpty()
                ? CovariateProfile.empty()
                : CovariateResolver.defaults().resolve(
                        declarations, contract.customCovariateResolvers());
        Optional<PerCriterionPassRateStatistics> baseline = baselineProvider.baselineFor(
                contract.id(), factors, "bernoulli-pass-rate",
                PerCriterionPassRateStatistics.class, profile, declarations);
        if (baseline.isEmpty()) {
            // No baseline yet — the criterion's empirical evaluation will
            // produce a no-baseline INCONCLUSIVE at conclude time. Resolved
            // sizing needs the baseline it resolves against.
            return new Resolution(declaredSamples, declaredSamples, Optional.empty());
        }
        int maxRequired = declaredSamples;
        String drivenBy = null;
        for (Criterion<OT> c : sizing) {
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
            int required = requiredSamples(c.id(), c.posture(), stats);
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

    /**
     * The resolved-sizing requirement of one criterion, with the sizing
     * refusals stated in sizing terms.
     */
    private static int requiredSamples(
            String criterionId, CriterionPosture posture, PassRateStatistics stats) {
        int baselineTrials = stats.sampleCount();
        int baselineSuccesses = (int) Math.round(stats.observedPassRate() * baselineTrials);
        double baselineRate = stats.observedPassRate();
        double designRate = posture.toleratedRate().isPresent()
                ? posture.toleratedRate().getAsDouble()
                : baselineRate - posture.mde().getAsDouble();
        double confidence = posture.confidenceFloor().orElse(StatisticalDefaults.DEFAULT_CONFIDENCE);
        double power = posture.power().orElse(StatisticalDefaults.DEFAULT_TARGET_POWER);
        Optional<RegressionSizing.Refusal> refusal = RegressionSizing.checkDomain(
                baselineRate, baselineTrials, OptionalDouble.of(designRate), OptionalInt.empty());
        if (refusal.isPresent()) {
            throw new IllegalStateException(refusalMessage(
                    refusal.get(), criterionId, baselineRate, designRate, baselineTrials));
        }
        return RegressionSizing.resolvedSizing(
                        baselineSuccesses, baselineTrials, designRate,
                        Methodology.alphaFromConfidence(confidence), power)
                .map(RegressionSizing.Sized::requiredSamples)
                .orElseThrow(() -> new IllegalStateException(refusalMessage(
                        RegressionSizing.Refusal.BASELINE_TOO_SMALL, criterionId,
                        baselineRate, designRate, baselineTrials)));
    }

    private static String refusalMessage(
            RegressionSizing.Refusal refusal, String criterionId,
            double baselineRate, double designRate, int baselineTrials) {
        return switch (refusal) {
            case ZERO_BASELINE -> String.format(
                    "sizing refused for criterion '%s' (ZERO_BASELINE): the baseline observed no "
                            + "successes, so there is no rate below it to detect. Measure a "
                            + "baseline before sizing against it.", criterionId);
            case ALTERNATIVE_NOT_BELOW_BASELINE -> String.format(
                    "sizing refused for criterion '%s' (ALTERNATIVE_NOT_BELOW_BASELINE): the "
                            + "design alternative rate (%s) must sit strictly below the "
                            + "baseline rate (%s) — there is no degradation to detect. "
                            + "Re-measure the baseline rather than raising the rate.",
                    criterionId, designRate, baselineRate);
            case TEST_LARGER_THAN_BASELINE, BASELINE_TOO_SMALL -> String.format(
                    "sizing refused for criterion '%s' (BASELINE_TOO_SMALL): no test of at most "
                            + "%d samples — the baseline's size — reaches and holds the target "
                            + "power at the design alternative rate %s. Measure a larger "
                            + "baseline, or declare a lower design alternative rate.",
                    criterionId, baselineTrials, designRate);
        };
    }
}
