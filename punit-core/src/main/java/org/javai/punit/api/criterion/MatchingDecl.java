package org.javai.punit.api.criterion;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.javai.punit.api.Postcondition;

/**
 * A terminal criterion decl whose verdict is determined by a single
 * {@link ValueMatcher} comparing an expected to an actual value of
 * the same type.
 *
 * <p>Produced by {@link CriterionDecl#matchedBy(Supplier)} and
 * {@link CriterionDecl#matchedByEquality()}. A {@code MatchingDecl}
 * carries the criterion's posture, name, and matcher supplier; it
 * offers only {@link #name(String)} as further configuration (so the
 * criterion's identifier in the verdict path can be set after the
 * matcher is supplied). It does <em>not</em> expose further
 * {@code .satisfies} / {@code .where} / {@code .matchedBy} chaining
 * — a matching criterion is purposefully <em>just</em> an
 * equivalence judgement. To pair expected-value matching with an
 * intrinsic check, declare two criteria via
 * {@link Criteria#of(Decl[])}.
 *
 * <p>The matcher supplier is invoked once per runtime criterion at
 * {@link #toRuntime(String)} time; matchers that carry per-sampling
 * state can return a fresh instance each call. Stateless matchers
 * may share a single instance.
 *
 * @param <O> the contract's per-sample output value type
 */
public final class MatchingDecl<O> implements Decl<O> {

    private final CriterionPosture posture;
    private final Supplier<? extends ValueMatcher<O>> matcher;
    private final Optional<String> name;

    MatchingDecl(CriterionPosture posture,
            Supplier<? extends ValueMatcher<O>> matcher,
            Optional<String> name) {
        this.posture = Objects.requireNonNull(posture, "posture");
        this.matcher = Objects.requireNonNull(matcher, "matcher");
        this.name = Objects.requireNonNull(name, "name");
    }

    @Override
    public Optional<String> name() {
        return name;
    }

    /**
     * Set the criterion's name — used by baseline storage,
     * diagnostics, and override targeting. Doubles as the
     * postcondition description in the verdict's
     * failures-by-postcondition histogram, since a matching criterion
     * carries exactly one postcondition.
     *
     * @throws IllegalStateException if {@code .name(...)} has already
     *         been called on this decl
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public MatchingDecl<O> name(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException(".name(...) requires a non-blank name");
        }
        if (this.name.isPresent()) {
            throw new IllegalStateException(
                    ".name(...) already supplied as '" + this.name.get()
                            + "'; cannot reassign to '" + name + "'");
        }
        return new MatchingDecl<>(posture, matcher, Optional.of(name));
    }

    @Override
    public List<Criterion<O>> asList() {
        return List.of(toRuntime(name.orElse(Criteria.DEFAULT_CRITERION_ID)));
    }

    @Override
    public Criterion<O> toRuntime(String id) {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        String description = name.orElse(id);
        Postcondition<O> postcondition = new Postcondition.Matching<>(description, matcher.get());
        return new DirectCriterion<>(id, List.of(postcondition)).withPosture(posture);
    }
}
