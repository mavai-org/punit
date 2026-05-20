package org.javai.punit.api.criterion;

import java.util.Optional;

/**
 * A single-criterion declaration — direct or transforming — that can
 * be lowered to one runtime {@link Criterion} given an id.
 *
 * <p>The sealed supertype shared by {@link CriterionDecl} (direct,
 * postconditions over the contract's output type {@code O}) and
 * {@link TransformingDecl} (postconditions over a derived type
 * {@code T} after a transform). It is the parameter type accepted by
 * {@link Criteria#of(Decl[]) Criteria.of(...)} so a multi-criterion
 * bundle may mix direct and transforming criteria.
 *
 * <p>Latency criteria do not flow through this hierarchy — they
 * live on the sibling {@link org.javai.punit.api.Contract#latency()}
 * method and are merged into {@code effectiveCriteria()} at run
 * time.
 *
 * <p>Authors rarely reference this type by name — call-site idiom is
 * {@code Criteria.of(meeting(0.99, SLA).name("a"),
 * empirical().transforming(parse).where(...).name("b"))} and inference
 * picks {@code Decl<O>} as the unified type.
 *
 * @param <O> the contract's per-sample output value type
 */
public sealed interface Decl<O> extends Criteria<O>
        permits CriterionDecl, TransformingDecl, MatchingDecl {

    /**
     * The criterion's name as declared via {@code .name(String)}, or
     * empty when the author did not supply one.
     *
     * <p>For K=1 contracts, missing names default to
     * {@link Criteria#DEFAULT_CRITERION_ID} at lowering time. For K>1
     * contracts, missing names are rejected by
     * {@link Criteria#of(Decl[])} with a diagnostic.
     */
    Optional<String> name();

    /**
     * Lower this declaration to a runtime {@link Criterion} with the
     * given id. Framework-internal; authors do not call this directly.
     */
    Criterion<O> toRuntime(String id);
}
