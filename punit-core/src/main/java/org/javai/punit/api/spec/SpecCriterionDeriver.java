package org.javai.punit.api.spec;

import java.util.Optional;
import java.util.ServiceLoader;

import org.javai.punit.api.criterion.CriterionPosture;

/**
 * Derives a spec-level {@link Criterion} from a contract criterion's
 * posture — the bridge between the contract's acceptance commitment
 * (e.g. {@code .meeting(0.95, SLA)}) and the spec-side evaluator that
 * computes the per-criterion verdict at run conclude time.
 *
 * <p>The interface lives in {@code api.spec} so the test-spec builder
 * can consult it without importing internals; the implementation
 * lives alongside the spec-criterion classes in the engine's criteria
 * package and is wired in via {@link ServiceLoader}. This SPI
 * indirection is what keeps the public {@code api.spec} package
 * boundary clean.
 *
 * <p>Two outcomes:
 * <ul>
 *   <li>A {@link CriterionPosture} that expresses a statistical
 *       commitment (e.g. {@code STATISTICAL_CONTRACTUAL} or
 *       {@code STATISTICAL_EMPIRICAL}) produces a non-empty
 *       result.</li>
 *   <li>A non-statistical posture (zero-failures, explicit or
 *       implicit) returns {@link Optional#empty()} — those criteria
 *       are evaluated by a different path (the SMOKE classifier wired
 *       in step 2 of the contract-thresholds directive).</li>
 * </ul>
 */
public interface SpecCriterionDeriver {

    /**
     * Map a contract criterion's posture to its spec-level evaluator.
     *
     * @param posture the contract criterion's acceptance commitment
     * @param <O> the contract's output value type
     * @return the spec-side criterion when one applies to this
     *         posture; empty otherwise
     */
    <O> Optional<Criterion<O, ?>> derive(CriterionPosture posture);

    /**
     * Locate the registered {@link SpecCriterionDeriver}
     * implementation via {@link ServiceLoader}. Throws if none is on
     * the classpath — that is a packaging bug (the implementation in
     * {@code internal.engine.criteria} ships with the framework).
     */
    static SpecCriterionDeriver lookup() {
        return ServiceLoader.load(SpecCriterionDeriver.class)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "no SpecCriterionDeriver implementation on the classpath — "
                                + "this is a punit packaging defect"));
    }
}
