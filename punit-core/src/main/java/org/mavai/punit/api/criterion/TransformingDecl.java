package org.mavai.punit.api.criterion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import org.mavai.outcome.Outcome;
import org.mavai.punit.api.Postcondition;
import org.mavai.punit.api.PostconditionCheck;

/**
 * A value-form criterion decl whose postcondition chain is evaluated
 * against a transformed value, not against the contract's raw output.
 * Returned by {@link CriterionDecl#transforming(Function)}.
 *
 * <p>The criterion's posture lives on the parent {@link CriterionDecl}
 * (the type-witness {@code <O>} is the contract's output type).
 * The criterion's postconditions live here (the type-witness {@code <T>}
 * is the transformed value type the postconditions see).
 *
 * <p>Transform failure ({@link Outcome.Fail}) or a thrown exception
 * classifies the criterion's per-sample outcome as a
 * {@link CriterionSampleOutcome#FAIL FAIL} carrying the failing
 * reason. The postcondition chain is not evaluated; the parse /
 * projection failure flows through to the per-sample record with its
 * symbolic name and message preserved, distinguishing it from a
 * condition failure.
 *
 * @param <O> the contract's per-sample output value type
 * @param <T> the transformed value type the postconditions evaluate
 *            against
 */
// mavai-ref: JVI-Q8BDYMS — do not remove (resolves in mavai-orchestrator)
public final class TransformingDecl<O, T> implements Decl<O> {

    private final CriterionPosture posture;
    private final Function<O, Outcome<T>> transform;
    private final List<NamedPostcondition<T>> postconditions;
    private final Optional<String> name;
    private final Optional<OptionalSlack> optionalSlack;

    TransformingDecl(
            CriterionPosture posture,
            Function<O, Outcome<T>> transform,
            List<NamedPostcondition<T>> postconditions) {
        this(posture, transform, postconditions, Optional.empty(), Optional.empty());
    }

    TransformingDecl(
            CriterionPosture posture,
            Function<O, Outcome<T>> transform,
            List<NamedPostcondition<T>> postconditions,
            Optional<String> name) {
        this(posture, transform, postconditions, name, Optional.empty());
    }

    TransformingDecl(
            CriterionPosture posture,
            Function<O, Outcome<T>> transform,
            List<NamedPostcondition<T>> postconditions,
            Optional<String> name,
            Optional<OptionalSlack> optionalSlack) {
        this.posture = Objects.requireNonNull(posture, "posture");
        this.transform = Objects.requireNonNull(transform, "transform");
        this.postconditions = List.copyOf(postconditions);
        this.name = Objects.requireNonNull(name, "name");
        this.optionalSlack = Objects.requireNonNull(optionalSlack, "optionalSlack");
    }

    @Override
    public Optional<String> name() {
        return name;
    }

    /**
     * Set the criterion's name — used by baseline storage,
     * diagnostics, and (under {@code DIR-CRITERIA-OVERRIDE-punit})
     * test-side override targeting. Optional for K=1 contracts;
     * required for K>1.
     *
     * @throws IllegalStateException if {@code .name(...)} has already
     *         been called
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public TransformingDecl<O, T> name(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException(".name(...) requires a non-blank name");
        }
        if (this.name.isPresent()) {
            throw new IllegalStateException(
                    ".name(...) already supplied as '" + this.name.get()
                            + "'; cannot reassign to '" + name + "'");
        }
        return new TransformingDecl<>(posture, transform, postconditions, Optional.of(name), optionalSlack);
    }

    /** The posture inherited from the parent {@link CriterionDecl}. */
    public CriterionPosture posture() {
        return posture;
    }

    /** Named post-transform postconditions in declaration order. May be empty. */
    public List<NamedPostcondition<T>> postconditions() {
        return postconditions;
    }

    /** The declared optional-check failure budget, when one is declared. */
    public Optional<OptionalSlack> optionalSlack() {
        return optionalSlack;
    }

    /**
     * Mark the most recently declared postcondition optional —
     * relaxable within this criterion's optional-slack budget; see
     * {@link CriterionDecl#optional()} for the double-opt-in rule.
     *
     * @throws IllegalStateException when no postcondition has been
     *     declared yet — the mark follows the check it relaxes
     */
    public TransformingDecl<O, T> optional() {
        if (postconditions.isEmpty()) {
            throw new IllegalStateException(
                    ".optional() marks the postcondition declared before it — declare a "
                            + ".where(...) / .satisfies(...) check first");
        }
        List<NamedPostcondition<T>> next = new ArrayList<>(postconditions);
        next.set(next.size() - 1, next.get(next.size() - 1).asOptional());
        return new TransformingDecl<>(posture, transform, next, name, optionalSlack);
    }

    /** Declare the optional-check failure budget as an absolute count. */
    public TransformingDecl<O, T> optionalSlack(int count) {
        return new TransformingDecl<>(posture, transform, postconditions, name,
                Optional.of(OptionalSlack.count(count)));
    }

    /**
     * Declare the optional-check failure budget as an explicit
     * percentage ({@code "20%"}), resolved by floor.
     */
    public TransformingDecl<O, T> optionalSlack(String percentage) {
        return new TransformingDecl<>(posture, transform, postconditions, name,
                Optional.of(OptionalSlack.percent(percentage)));
    }

    /** Declare the optional-check failure budget from a constructed value. */
    public TransformingDecl<O, T> optionalSlack(OptionalSlack slack) {
        Objects.requireNonNull(slack, "slack");
        return new TransformingDecl<>(posture, transform, postconditions, name, Optional.of(slack));
    }

    /**
     * Add a named postcondition over the transformed value. The
     * predicate returns {@code true} for pass; the framework
     * synthesises the failure message when it returns {@code false}.
     *
     * <p>For richer failure messages use
     * {@link #satisfies(String, Function)}.
     */
    public TransformingDecl<O, T> where(String name, Predicate<T> predicate) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException(".where(name, ...) requires a non-blank name");
        }
        Objects.requireNonNull(predicate, "predicate");
        PostconditionCheck<T> wrapped = v -> predicate.test(v)
                ? Outcome.ok()
                : Outcome.fail(name,
                        "postcondition '" + name + "' returned false for value: " + v);
        return appendPostcondition(name, wrapped);
    }

    /**
     * Add a named postcondition over the transformed value that
     * returns its own {@link Outcome}. Use this when the failure
     * message benefits from diagnostic detail.
     */
    public TransformingDecl<O, T> satisfies(String name, Function<T, Outcome<?>> check) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException(".satisfies(name, ...) requires a non-blank name");
        }
        Objects.requireNonNull(check, "check");
        PostconditionCheck<T> adapted = v -> {
            Outcome<?> result = check.apply(v);
            return switch (result) {
                case Outcome.Ok<?> ok -> Outcome.ok();
                case Outcome.Fail<?> fail -> Outcome.fail(fail.failure());
            };
        };
        return appendPostcondition(name, adapted);
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
        List<Postcondition<T>> clauses = new ArrayList<>(postconditions.size());
        for (NamedPostcondition<T> p : postconditions) {
            clauses.add(new Postcondition.Leaf<>(p.name(), p.check(), p.required()));
        }
        return new TransformingCriterion<>(id, transform, clauses, optionalSlack)
                .withPosture(posture);
    }

    private TransformingDecl<O, T> appendPostcondition(String postconditionName, PostconditionCheck<T> check) {
        List<NamedPostcondition<T>> next = new ArrayList<>(postconditions.size() + 1);
        next.addAll(postconditions);
        next.add(new NamedPostcondition<>(postconditionName, check));
        return new TransformingDecl<>(posture, transform, next, name, optionalSlack);
    }
}
