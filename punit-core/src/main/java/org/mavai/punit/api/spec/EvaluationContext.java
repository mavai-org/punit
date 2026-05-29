package org.mavai.punit.api.spec;

import java.util.Map;
import java.util.Optional;

import org.mavai.punit.api.FactorBundle;
import org.mavai.punit.api.TestIntent;
import org.mavai.punit.api.criterion.CriterionPosture;

/**
 * The typed view a {@link Criterion} receives at evaluate time.
 *
 * <p>The type parameter {@code S} pins the baseline statistics kind
 * the criterion declared via {@link Criterion#statisticsType()}. The
 * {@link #baseline()} accessor returns exactly that kind, or empty
 * when no matching baseline was resolvable.
 *
 * @param <OT> the outcome value type the sampling loop produced
 * @param <S>  the baseline statistics kind the criterion reads
 */
public interface EvaluationContext<OT, S extends BaselineStatistics> {

    /** The observed sample aggregate for this spec run. */
    SampleSummary<OT> summary();

    /**
     * The baseline statistics of the criterion's declared kind, when
     * one was resolvable. Empty when the framework could not resolve
     * a matching baseline. Always empty when {@code S = NoStatistics}.
     */
    Optional<S> baseline();

    /** The bound factor bundle the spec was evaluated against. */
    FactorBundle factors();

    /**
     * The test's inputs identity — the
     * {@code Sampling.inputsIdentity()} of the {@code Sampling} this
     * spec was built from. Used by
     * {@link EmpiricalChecks#inputsIdentityMatch(String, String, String, java.util.Map)}
     * to verify cross-process that the test and its baseline drew
     * from the same input population.
     */
    String testInputsIdentity();

    /**
     * The inputs identity recorded by the resolved baseline, when
     * one was resolvable. Empty when the framework could not resolve
     * a matching baseline (in which case {@link #baseline()} is also
     * empty by construction).
     */
    Optional<String> baselineInputsIdentity();

    /**
     * The contract's per-methodology-criterion postures, keyed by
     * criterion id. The framework derives this map from the
     * contract's {@code criteria()} declarations
     * (with {@link CriterionPosture#implicit()} for any criterion
     * that did not declare a posture explicitly).
     *
     * <p>Empty when no methodology criteria were declared (the
     * apply-level-failure path or a hand-built test fixture).
     *
     * <p>Default implementation returns an empty map for back-compat
     * with test fixtures that hand-build their context. Production
     * code paths in the framework always populate it.
     */
    default Map<String, CriterionPosture> criterionPostures() {
        return Map.of();
    }

    /**
     * The test's declared {@link TestIntent}. Defaults to
     * {@link TestIntent#VERIFICATION} for back-compat with hand-built
     * test fixtures; production code paths propagate the spec's
     * configured intent.
     */
    default TestIntent intent() {
        return TestIntent.VERIFICATION;
    }
}
