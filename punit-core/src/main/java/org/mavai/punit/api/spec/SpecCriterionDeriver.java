package org.mavai.punit.api.spec;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

import org.mavai.punit.api.criterion.CriterionPosture;

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
     * Map every contract criterion's posture to its spec-level
     * evaluator, one evaluator per kind. One pass-rate evaluator judges
     * every methodology criterion, each by its own posture; it is derived
     * from an empirical posture where the contract declares one, so that
     * the baseline is resolved whenever any criterion is baseline-derived
     * (a requirement and a baseline on the same postconditions are two
     * criteria of one contract).
     *
     * @param postures the contract criteria's postures, in declaration order
     * @param <O> the contract's output value type
     * @return the spec-side criteria, at most one per evaluator class
     */
    default <O> List<Criterion<O, ?>> deriveAll(List<CriterionPosture> postures) {
        List<CriterionPosture> ordered = new ArrayList<>(postures.size());
        for (CriterionPosture p : postures) {
            if (p.kind() == CriterionPosture.Kind.STATISTICAL_EMPIRICAL) {
                ordered.add(p);
            }
        }
        for (CriterionPosture p : postures) {
            if (p.kind() != CriterionPosture.Kind.STATISTICAL_EMPIRICAL) {
                ordered.add(p);
            }
        }
        List<Criterion<O, ?>> out = new ArrayList<>();
        for (CriterionPosture p : ordered) {
            this.<O>derive(p).ifPresent(c -> {
                if (out.stream().noneMatch(existing -> existing.getClass() == c.getClass())) {
                    out.add(c);
                }
            });
        }
        return out;
    }

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
