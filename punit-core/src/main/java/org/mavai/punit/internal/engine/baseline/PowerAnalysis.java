package org.mavai.punit.internal.engine.baseline;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.mavai.punit.api.FactorBundle;
import org.mavai.punit.api.ServiceContract;
import org.mavai.punit.api.covariate.Covariate;
import org.mavai.punit.api.covariate.CovariateProfile;
import org.mavai.punit.api.spec.Configuration;
import org.mavai.punit.api.spec.Experiment;
import org.mavai.punit.api.spec.PassRateStatistics;
import org.mavai.punit.api.spec.PerCriterionPassRateStatistics;
import org.mavai.punit.api.spec.Spec;
import org.mavai.punit.api.spec.TypedSpec;
import org.mavai.punit.internal.engine.covariate.CovariateResolver;
import org.mavai.punit.statistics.SampleSizeCalculator;

/**
 * Authoring-time sample-size utility for the confidence-first
 * probabilistic-test pattern.
 *
 * <p>Given a baseline supplier and a desired minimum detectable
 * effect plus statistical power, computes the sample size at which
 * the one-proportion z-test achieves that power against the baseline
 * rate.
 *
 * <p>Authors call this at spec-construction time and stamp the
 * computed sample count onto a template sampling, then bind factors
 * at the spec entry point:
 *
 * <pre>{@code
 * int n = PowerAnalysis.sampleSize(BASELINES, this::shoppingBaseline, 0.02, 0.80);
 * var sampling = samplingTemplate().samples(n);
 * return ProbabilisticTest.testing(sampling, factors)
 *         .criterion(PassRate.empirical())
 *         .build();
 * }</pre>
 *
 * <p>The default confidence level is {@value #DEFAULT_CONFIDENCE} —
 * matching the empirical-criterion default.
 */
public final class PowerAnalysis {

    /** The default confidence (1 - α) used when none is specified. */
    public static final double DEFAULT_CONFIDENCE = 0.95;

    /** Criterion-name key the resolver looks under for the pass-rate baseline. */
    private static final String PASS_RATE_CRITERION = "bernoulli-pass-rate";

    /**
     * The actual sample-size formula lives in
     * {@link SampleSizeCalculator}, the dedicated home for statistical
     * calculations. This class is the bridge that resolves the
     * baseline rate; it does not duplicate the math.
     */
    private static final SampleSizeCalculator SAMPLE_SIZE_CALCULATOR = new SampleSizeCalculator();

    private PowerAnalysis() { }

    /**
     * Convenience overload that resolves the baseline directory the
     * same way the test pipeline does — via
     * {@link BaselineResolver#defaultDir()}, which honours the
     * {@code punit.baseline.dir} system property and falls back to
     * the project convention path.
     *
     * <p>This is the right call for tests using {@code PowerAnalysis}
     * inside an {@code @ProbabilisticTest} method body — the test
     * doesn't need to know where baselines live; the framework does.
     *
     * @see #sampleSize(Path, Supplier, double, double)
     */
    public static int sampleSize(
            Supplier<Experiment> baseline,
            double mde,
            double power) {
        return sampleSize(BaselineResolver.defaultDir(), baseline, mde, power);
    }

    /**
     * Single-argument overload that reads minimum detectable effect
     * and statistical power from the contract's *confidence-first*
     * criteria — those declared as
     * {@code .empirical().detectingMde(m).atPower(p)}. Returns the
     * maximum sample count any such criterion demands; the contract's
     * own commitment dictates the run's sample size without the
     * author re-stating MDE / power at the call site.
     *
     * <p>Resolves the baseline directory via
     * {@link BaselineResolver#defaultDir()} like the no-args
     * overload pair.
     *
     * @param baseline a supplier yielding the {@code MEASURE}
     *                 experiment whose baseline is the comparison
     *                 reference
     * @return the maximum required sample count across the contract's
     *         confidence-first criteria; ceiling-rounded
     * @throws IllegalStateException if the contract declares no
     *         confidence-first criterion (no MDE / power on any
     *         criterion's posture) — there is nothing for the
     *         single-arg call to size against
     */
    public static int sampleSize(Supplier<Experiment> baseline) {
        return sampleSize(BaselineResolver.defaultDir(), baseline);
    }

    /**
     * Single-argument-plus-baseline-dir variant of
     * {@link #sampleSize(Supplier)}. See that method for the contract-
     * driven semantics.
     */
    public static int sampleSize(Path baselineDir, Supplier<Experiment> baseline) {
        Objects.requireNonNull(baselineDir, "baselineDir");
        Objects.requireNonNull(baseline, "baseline");
        Experiment experiment = Objects.requireNonNull(
                baseline.get(), "baseline supplier returned null");
        if (experiment.kind() != Experiment.Kind.MEASURE) {
            throw new IllegalArgumentException(
                    "PowerAnalysis.sampleSize requires a MEASURE-flavour Experiment "
                            + "(one that produces a baseline); got " + experiment.kind()
                            + ". Pass a method reference to an @PUnitExperiment method "
                            + "whose body returns Experiment.measuring(...).build().");
        }
        BaselineLookup lookup = experiment.dispatch(LOOKUP_DISPATCHER);
        java.util.List<org.mavai.punit.api.criterion.Criterion<?>> confidenceFirst = lookup.contractCriteria().stream()
                .filter(c -> c.posture().isConfidenceFirst())
                .toList();
        if (confidenceFirst.isEmpty()) {
            throw new IllegalStateException(
                    "PowerAnalysis.sampleSize(baseline) requires the contract to declare "
                            + "at least one confidence-first criterion "
                            + "(.empirical().detectingMde(m).atPower(p)); none found. "
                            + "Either add MDE+power to a criterion or call the three-argument "
                            + "overload with explicit values.");
        }
        int maxRequired = 0;
        for (org.mavai.punit.api.criterion.Criterion<?> c : confidenceFirst) {
            org.mavai.punit.api.criterion.CriterionPosture p = c.posture();
            int n = sampleSize(baselineDir, baseline,
                    p.mde().getAsDouble(), p.power().getAsDouble());
            if (n > maxRequired) {
                maxRequired = n;
            }
        }
        return maxRequired;
    }

    /**
     * Computes the sample size required to detect a difference of at
     * least {@code mde} from the baseline's recorded pass rate at the
     * given statistical power, using the default confidence
     * ({@value #DEFAULT_CONFIDENCE}).
     *
     * <p>The supplier yields a {@code MEASURE}-flavour {@link Experiment};
     * the utility resolves the baseline file matching that experiment's
     * use-case identity and factors fingerprint under {@code baselineDir},
     * reads the recorded {@link PassRateStatistics}, and feeds the
     * recorded {@code observedPassRate} into the z-test sample-size
     * formula.
     *
     * @param baselineDir directory containing the baseline YAML files
     *                    the resolver searches
     * @param baseline    a supplier yielding the {@code MEASURE}
     *                    {@link Experiment} the sample size is being
     *                    planned against — typically a method
     *                    reference to an {@code @PUnitExperiment} method
     * @param mde         the minimum detectable effect, in (0, 1)
     * @param power       the required statistical power, in (0, 1)
     * @return the required sample count, ceiling-rounded
     * @throws NullPointerException     if any argument is null, or the
     *                                  supplier returns null
     * @throws IllegalArgumentException if {@code mde} ∉ (0, 1) or
     *                                  {@code power} ∉ (0, 1) or the
     *                                  supplier yields a non-MEASURE
     *                                  experiment, or the alternative-
     *                                  hypothesis rate {@code rate − mde}
     *                                  is not strictly positive
     * @throws IllegalStateException    if no matching baseline file is
     *                                  resolvable under {@code baselineDir}
     */
    public static int sampleSize(
            Path baselineDir,
            Supplier<Experiment> baseline,
            double mde,
            double power) {
        Objects.requireNonNull(baselineDir, "baselineDir");
        Objects.requireNonNull(baseline, "baseline");
        validate(mde, power);

        Experiment experiment = Objects.requireNonNull(
                baseline.get(), "baseline supplier returned null");
        if (experiment.kind() != Experiment.Kind.MEASURE) {
            throw new IllegalArgumentException(
                    "PowerAnalysis.sampleSize requires a MEASURE-flavour Experiment "
                            + "(one that produces a baseline); got " + experiment.kind()
                            + ". Pass a method reference to an @PUnitExperiment method "
                            + "whose body returns Experiment.measuring(...).build().");
        }

        BaselineLookup lookup = experiment.dispatch(LOOKUP_DISPATCHER);
        BaselineResolver resolver = new BaselineResolver(baselineDir);
        PerCriterionPassRateStatistics stats = resolver.resolve(
                lookup.serviceContractId(),
                lookup.factorsFingerprint(),
                PASS_RATE_CRITERION,
                PerCriterionPassRateStatistics.class,
                lookup.profile(),
                lookup.declarations())
                .orElseThrow(() -> new IllegalStateException(
                        "no baseline found for service contract '" + lookup.serviceContractId()
                                + "' (factors fingerprint " + lookup.factorsFingerprint()
                                + (lookup.profile().isEmpty() ? ""
                                        : ", covariates " + formatProfile(lookup.profile()))
                                + ") under " + baselineDir
                                + " — run the baseline measure before planning a test against it."));

        // K>1 worst-case: when the contract carries multiple
        // methodology criteria, the sample-size dictating criterion
        // is the one with the lowest baseline rate — its degradation
        // is hardest to detect, so its sample-size requirement
        // dominates. For K=1 this collapses to the lone criterion's
        // rate, preserving legacy behaviour.
        if (stats.byCriterion().isEmpty()) {
            throw new IllegalStateException(
                    "baseline carries no per-criterion pass-rate entries — "
                            + "the baseline file is malformed");
        }
        double observedRate = stats.byCriterion().values().stream()
                .mapToDouble(PassRateStatistics::observedPassRate)
                .min()
                .getAsDouble();
        // One-sided check: degradation testing only cares about the
        // lower side, so the alternative-hypothesis rate p1 = rate − mde
        // must be strictly positive. The upper side (rate + mde) is
        // never used by the formula and intentionally not bounded —
        // perfect baselines (rate = 1.0) are valid input here, with
        // σ0 = 0 collapsing the formula to n = (z_β · σ1)² / δ²
        // inside SampleSizeCalculator.
        if (observedRate - mde <= 0.0) {
            throw new IllegalArgumentException(
                    "baseline observed rate " + observedRate
                            + " is incompatible with mde=" + mde
                            + " — the alternative-hypothesis rate (rate − mde) "
                            + "must be > 0 for one-sided degradation detection.");
        }

        // The sample-size formula lives in SampleSizeCalculator — the
        // dedicated statistics-package home. This bridge resolves the
        // baseline rate and delegates the math.
        return SAMPLE_SIZE_CALCULATOR
                .calculateForPower(observedRate, mde, DEFAULT_CONFIDENCE, power)
                .requiredSamples();
    }

    private static void validate(double mde, double power) {
        if (Double.isNaN(mde) || mde <= 0.0 || mde >= 1.0) {
            throw new IllegalArgumentException(
                    "mde must be in (0, 1), got " + mde);
        }
        if (Double.isNaN(power) || power <= 0.0 || power >= 1.0) {
            throw new IllegalArgumentException(
                    "power must be in (0, 1), got " + power);
        }
    }

    /**
     * Carrier for the identity values and covariate context the
     * resolver needs. The covariate fields are required so the
     * authoring-time resolution path matches the JUnit-extension test
     * pipeline: a covariate-stamped baseline only matches when the
     * caller supplies the same profile + declarations the test would
     * supply at run time.
     */
    private record BaselineLookup(
            String serviceContractId,
            String factorsFingerprint,
            CovariateProfile profile,
            List<Covariate> declarations,
            List<org.mavai.punit.api.criterion.Criterion<?>> contractCriteria) { }

    /**
     * Dispatcher that captures the {@code <FT>} type parameter from the
     * spec's engine-facing view long enough to call
     * {@code serviceContractFactory.apply(factors).id()}, compute the factors
     * fingerprint, and resolve the service contract's covariate profile, then
     * collapses the result back to a non-generic carrier.
     */
    private static final Spec.Dispatcher<BaselineLookup> LOOKUP_DISPATCHER =
            new Spec.Dispatcher<>() {
                @Override
                public <FT, IT, OT> BaselineLookup apply(TypedSpec<FT, IT, OT> spec) {
                    Iterator<Configuration<FT, IT, OT>> iterator = spec.configurations();
                    if (!iterator.hasNext()) {
                        throw new IllegalStateException(
                                "MEASURE experiment produced no configurations — "
                                        + "the spec is malformed");
                    }
                    Configuration<FT, IT, OT> cfg = iterator.next();
                    FT factors = cfg.factors();
                    ServiceContract<FT, IT, OT> serviceContract = spec.serviceContractFactory().apply(factors);
                    String serviceContractId = serviceContract.id();
                    String fingerprint = FactorsFingerprint.of(FactorBundle.of(factors));
                    List<Covariate> declarations = serviceContract.covariates();
                    CovariateProfile profile = declarations.isEmpty()
                            ? CovariateProfile.empty()
                            : CovariateResolver.defaults().resolve(
                                    declarations, serviceContract.customCovariateResolvers());
                    List<org.mavai.punit.api.criterion.Criterion<?>> contractCriteria =
                            new java.util.ArrayList<>(serviceContract.effectiveCriteria());
                    return new BaselineLookup(serviceContractId, fingerprint, profile, declarations, contractCriteria);
                }
            };

    private static String formatProfile(CovariateProfile profile) {
        Map<String, String> values = profile.values();
        return values.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
    }
}
