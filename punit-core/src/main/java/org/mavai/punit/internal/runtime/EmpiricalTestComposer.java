package org.mavai.punit.internal.runtime;

import java.util.Iterator;

import org.mavai.punit.api.TestIntent;
import org.mavai.punit.api.InputSupplier;
import org.mavai.punit.api.Sampling;
import org.mavai.punit.api.spec.Configuration;
import org.mavai.punit.api.spec.Criterion;
import org.mavai.punit.api.spec.Experiment;
import org.mavai.punit.api.spec.ProbabilisticTest;
import org.mavai.punit.api.spec.Spec;
import org.mavai.punit.api.spec.TypedSpec;

/**
 * Translates a baseline {@link Experiment} value plus a test-side
 * sample count and criterion into a {@link ProbabilisticTest} spec
 * whose {@link Sampling} matches the baseline's by content (same
 * use-case factory, inputs, and governors) but with a different
 * sample count.
 *
 * <p>This is the bridge that lets
 * {@link PUnit#testing(java.util.function.Supplier)} accept just a
 * baseline supplier — every other parameter is implicit, derived
 * from the baseline by reference at compose time. The integrity
 * guarantee follows by construction: the test's {@link Sampling}
 * cannot drift from the baseline's on factors, service contract, inputs, or
 * governors.
 *
 * <p>Uses the {@link Spec.Dispatcher} wildcard-capture pattern to
 * thread the baseline's {@code <FT, IT, OT>} through long enough to
 * construct a typed test spec without unchecked casts on the spec
 * machinery.
 */
public final class EmpiricalTestComposer {

    private EmpiricalTestComposer() { }

    public static ProbabilisticTest compose(
            Experiment baseline, int testSamples,
            Criterion<?, ?> criterion, TestIntent intent) {
        if (baseline.kind() != Experiment.Kind.MEASURE) {
            throw new IllegalArgumentException(
                    "empirical probabilistic test must be paired with a MEASURE-flavour "
                            + "Experiment, got " + baseline.kind()
                            + ". Pass a method reference to a method whose body returns "
                            + "PUnit.measuring(...).build().");
        }
        return baseline.dispatch(new Spec.Dispatcher<>() {
            @Override
            public <FT, IT, OT> ProbabilisticTest apply(TypedSpec<FT, IT, OT> typedBaseline) {
                return composeTyped(typedBaseline, testSamples, criterion, intent);
            }
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <FT, IT, OT> ProbabilisticTest composeTyped(
            TypedSpec<FT, IT, OT> typedBaseline, int testSamples,
            Criterion<?, ?> criterion, TestIntent intent) {
        Iterator<Configuration<FT, IT, OT>> configs = typedBaseline.configurations();
        if (!configs.hasNext()) {
            throw new IllegalStateException(
                    "MEASURE baseline produced no configuration — typed spec is malformed");
        }
        Configuration<FT, IT, OT> cfg = configs.next();
        FT factors = cfg.factors();

        // Reconstruct the baseline's Sampling with the test's sample count;
        // every other parameter (use-case factory, inputs, governors) comes
        // from the baseline so the comparison is content-coherent. Optional
        // governors (timeBudget, tokenBudget) carry over only if declared.
        Sampling.Builder<FT, IT, OT> samplingBuilder = Sampling.<FT, IT, OT>builder()
                .serviceContractFactory(typedBaseline.serviceContractFactory())
                .inputs(InputSupplier.from(cfg::inputs))
                .samples(testSamples)
                .tokenCharge(typedBaseline.tokenCharge())
                .onBudgetExhausted(typedBaseline.budgetPolicy())
                .onException(typedBaseline.exceptionPolicy())
                .maxExampleFailures(typedBaseline.maxExampleFailures());
        typedBaseline.timeBudget().ifPresent(samplingBuilder::timeBudget);
        typedBaseline.tokenBudget().ifPresent(samplingBuilder::tokenBudget);
        Sampling<FT, IT, OT> testSampling = samplingBuilder.build();

        ProbabilisticTest.Builder<FT, IT, OT> builder =
                ProbabilisticTest.testing(testSampling, factors).intent(intent);
        if (criterion != null) {
            Criterion<OT, ?> typedCriterion = (Criterion<OT, ?>) criterion;
            builder.criterion(typedCriterion);
        }
        return builder.build();
    }
}
