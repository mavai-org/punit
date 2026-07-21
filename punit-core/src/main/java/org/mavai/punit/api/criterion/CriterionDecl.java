package org.mavai.punit.api.criterion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.mavai.outcome.Outcome;
import org.mavai.punit.api.Postcondition;
import org.mavai.punit.api.PostconditionCheck;

/**
 * A single-criterion verdict-producing strategy — the value behind
 * one row in a contract's criteria declaration.
 *
 * <p>Carries:
 * <ul>
 *   <li>a {@link CriterionPosture} (required) — what counts as
 *       acceptable: a threshold-first {@code .meeting(...)}, the
 *       empirical Wilson-vs-baseline procedure, or zero-failures;</li>
 *   <li>an ordered list of named postconditions (optional) — the
 *       per-sample checks the criterion runs against the contract's
 *       output. Empty means apply-level {@code Outcome.ok / fail}
 *       drives the criterion's per-sample outcome.</li>
 * </ul>
 *
 * <p>Authors do not call {@code new CriterionDecl(...)} directly.
 * The starting point is a {@link Criteria#meeting()} /
 * {@link Criteria#empirical()} chain:
 * <pre>{@code
 * import static org.mavai.punit.api.criterion.Criteria.meeting;
 * import static org.mavai.punit.api.criterion.Criteria.empirical;
 * import static org.mavai.punit.api.ThresholdOrigin.SLA;
 *
 * meeting().passRate(0.9999)
 *         .contractRef(SLA, "Payment Provider SLA v2.3, §4.1");
 *
 * meeting().passRate(0.85)
 *         .where("parseable", v -> isJson(v));
 *
 * empirical().passRate()
 *         .detectingMde(0.02)
 *         .atPower(0.95);
 * }</pre>
 *
 * <p>Every chain method returns a new {@code CriterionDecl} —
 * declarations are values, not builders.
 *
 * <p>A bare {@code CriterionDecl<O>} <em>is</em> a {@link Criteria}.
 * For the K=1 default-id case the author returns the decl directly
 * from {@code Contract.criteria()}; the framework lowers it to a
 * one-entry runtime criteria list with the criterion id
 * {@value Criteria#DEFAULT_CRITERION_ID} when no explicit
 * {@code .name(...)} has been supplied.
 *
 * @param <O> the contract's per-sample output value type
 */
// mavai-ref: JVI-JGG2K8= — do not remove (resolves in mavai-orchestrator)
public final class CriterionDecl<O> implements Decl<O> {

    private final CriterionPosture posture;
    private final List<NamedPostcondition<O>> postconditions;
    private final Optional<String> name;

    CriterionDecl(CriterionPosture posture, List<NamedPostcondition<O>> postconditions) {
        this(posture, postconditions, Optional.empty());
    }

    CriterionDecl(CriterionPosture posture, List<NamedPostcondition<O>> postconditions,
            Optional<String> name) {
        this.posture = Objects.requireNonNull(posture, "posture");
        this.postconditions = List.copyOf(postconditions);
        this.name = Objects.requireNonNull(name, "name");
    }

    @Override
    public Optional<String> name() {
        return name;
    }

    /**
     * Set the criterion's name — used by baseline storage,
     * diagnostics, and (under {@code DIR-CRITERIA-OVERRIDE-punit})
     * test-side override targeting. The name is optional for K=1
     * contracts (defaults to {@link Criteria#DEFAULT_CRITERION_ID})
     * and required for K>1 contracts.
     *
     * @throws IllegalStateException if {@code .name(...)} has already
     *         been called on this decl
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public CriterionDecl<O> name(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException(".name(...) requires a non-blank name");
        }
        if (this.name.isPresent()) {
            throw new IllegalStateException(
                    ".name(...) already supplied as '" + this.name.get()
                            + "'; cannot reassign to '" + name + "'");
        }
        return new CriterionDecl<>(posture, postconditions, Optional.of(name));
    }

    /** The criterion's posture — the verdict-producing commitment. */
    public CriterionPosture posture() {
        return posture;
    }

    /** Named postconditions in declaration order. May be empty. */
    public List<NamedPostcondition<O>> postconditions() {
        return postconditions;
    }

    /**
     * Add a named postcondition. The predicate returns {@code true}
     * for pass; the framework synthesises the failure message when
     * it returns {@code false}.
     *
     * <p>For richer failure messages — author-supplied symbolic name
     * and message — use {@link #satisfies(String, Function)}.
     */
    public CriterionDecl<O> where(String name, Predicate<O> predicate) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException(".where(name, ...) requires a non-blank name");
        }
        Objects.requireNonNull(predicate, "predicate");
        PostconditionCheck<O> wrapped = v -> predicate.test(v)
                ? Outcome.ok()
                : Outcome.fail(name,
                        "postcondition '" + name + "' returned false for value: " + v);
        return appendPostcondition(name, wrapped);
    }

    /**
     * Add a named postcondition that returns its own {@link Outcome}.
     * Use this overload when the failure message benefits from
     * diagnostic detail (offending input, parse error, etc.) —
     * {@code Outcome.fail("symbolic-name", "message")} flows through to
     * the verdict's failure histogram unchanged.
     */
    public CriterionDecl<O> satisfies(String name, Function<O, Outcome<?>> check) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException(".satisfies(name, ...) requires a non-blank name");
        }
        Objects.requireNonNull(check, "check");
        PostconditionCheck<O> adapted = v -> {
            Outcome<?> result = check.apply(v);
            return switch (result) {
                case Outcome.Ok<?> ok -> Outcome.ok();
                case Outcome.Fail<?> fail -> Outcome.fail(fail.failure());
            };
        };
        return appendPostcondition(name, adapted);
    }

    /**
     * Attach a human-readable contract reference — the document and
     * clause that justify this criterion's commitment
     * (e.g. {@code "Payment Provider SLA v2.3, §4.1"}). Surfaced in
     * the verdict path so compliance reports cite the authority
     * alongside the verdict.
     */
    public CriterionDecl<O> contractRef(String ref) {
        return new CriterionDecl<>(posture.withContractRef(ref), postconditions, name);
    }

    /**
     * Attach a contract reference and update the origin in one call.
     * Designed for the new {@link Criteria#meeting()} authoring shape
     * where the chain opens with origin
     * {@link org.mavai.punit.api.ThresholdOrigin#UNSPECIFIED} and the
     * author later names both the source category and the document
     * reference on the same line:
     *
     * <pre>{@code
     * meeting().passRate(0.9999).contractRef(SLA, "Payment SLA v2.3 §4.1")
     * }</pre>
     *
     * <p>{@code origin} must be a non-empirical origin (SLA / SLO /
     * POLICY / UNSPECIFIED). Rejected on empirical-mode postures —
     * the baseline IS the source.
     */
    public CriterionDecl<O> contractRef(
            org.mavai.punit.api.ThresholdOrigin origin, String ref) {
        return new CriterionDecl<>(
                posture.withContractRef(origin, ref), postconditions, name);
    }

    /**
     * Set the per-criterion confidence floor — the run cannot
     * loosen it. Composes only with {@code empirical()}; rejected
     * by the posture machinery on a {@code .meeting(...)} or
     * zero-failures commitment.
     */
    public CriterionDecl<O> atConfidence(double confidence) {
        return new CriterionDecl<>(posture.withConfidenceFloor(confidence), postconditions, name);
    }

    /**
     * Declare the minimum detectable effect — the smallest regression
     * (in percentage points off the baseline rate) this criterion
     * commits to detecting. Composes only with {@code empirical()};
     * must be paired with {@link #atPower(double)}.
     */
    public CriterionDecl<O> detectingMde(double mde) {
        return new CriterionDecl<>(posture.withMde(mde), postconditions, name);
    }

    /**
     * Declare the statistical power — probability of detecting a true
     * regression of size MDE. Composes only with {@code empirical()};
     * must be paired with {@link #detectingMde(double)} or
     * {@link #tolerating(double)}.
     */
    public CriterionDecl<O> atPower(double power) {
        return new CriterionDecl<>(posture.withPower(power), postconditions, name);
    }

    /**
     * Declare the worst true rate this criterion tolerates — an
     * <em>absolute</em> bound, not a drop off the baseline. The
     * framework computes the sample count that catches a genuine
     * breach of the tolerance, priced self-consistently against the
     * acceptance floor the run derives at its own size; the author's
     * declared sample count remains a floor. Composes only with
     * {@code empirical()}; mutually exclusive with
     * {@link #detectingMde(double)}. Pair with
     * {@link #atPower(double)} and {@link #atConfidence(double)} to
     * override the framework defaults (0.80 and 0.95).
     */
    public CriterionDecl<O> tolerating(double rate) {
        return new CriterionDecl<>(posture.withToleratedRate(rate), postconditions, name);
    }

    /**
     * Judge the contract's output against the sample's known-expected
     * value via a {@link ValueMatcher}. Returns a terminal
     * {@link MatchingDecl} that carries the matcher and offers only
     * {@link MatchingDecl#name(String) .name(...)} for further
     * configuration.
     *
     * <p>The sampling's input type must implement
     * {@link org.mavai.punit.api.Expected Expected&lt;OT&gt;} — the
     * framework reads {@code input.expected()} once per sample and
     * routes the result alongside the contract's produced value to
     * the matcher. The requirement is enforced at sampling-construction
     * time; a sampling whose input type does not implement
     * {@code Expected} but is paired with a criteria carrying
     * {@code .matchedBy(...)} fails fast with a clear message.
     *
     * <p>{@code Supplier} rather than a direct {@link ValueMatcher}
     * lets matchers carry per-sampling state if needed. In the common
     * stateless case the call is {@code MyMatcher::new}.
     *
     * <p>A matching criterion is purposefully equivalence-only:
     * {@code .matchedBy} may not follow any {@code .where} /
     * {@code .satisfies} on the same decl, and {@link MatchingDecl}
     * does not offer further postcondition chaining. To pair an
     * equivalence check with an intrinsic check (e.g. non-blank
     * output), declare two criteria via
     * {@link Criteria#of(Decl[])}.
     *
     * @throws IllegalStateException if any {@code .where} /
     *         {@code .satisfies} postcondition has already been added
     *         to this decl
     */
    public MatchingDecl<O> matchedBy(Supplier<? extends ValueMatcher<O>> matcher) {
        Objects.requireNonNull(matcher, "matcher");
        if (!postconditions.isEmpty()) {
            throw new IllegalStateException(
                    ".matchedBy(...) cannot follow .where/.satisfies on the same"
                            + " criterion decl: a matching criterion is purposefully"
                            + " equivalence-only. Either drop the postconditions, or"
                            + " split into two criteria via Criteria.of(...).");
        }
        return new MatchingDecl<>(posture, matcher, name);
    }

    /**
     * Shorthand for {@code .matchedBy(ValueMatcher::equality)} — the
     * common case where the actual output is expected to be
     * {@link java.util.Objects#equals(Object, Object) Objects.equals}
     * to the sample's expected output. See
     * {@link ValueMatcher#equality()} for the matcher's behaviour and
     * the failure name / message it emits on mismatch.
     */
    public MatchingDecl<O> matchedByEquality() {
        return matchedBy(ValueMatcher::equality);
    }

    /**
     * Chain a transform — parse, project, derive — that produces a
     * value of a different type {@code T} the criterion's
     * postconditions will check. Returns a {@link TransformingDecl}
     * whose {@code .where(...)} and {@code .satisfies(...)} operate
     * on {@code T}, not on the contract's output {@code O}.
     *
     * <p>Transform semantics:
     * <ul>
     *   <li>Transform returns {@link Outcome.Ok Ok(t)} — the
     *       postcondition chain runs against {@code t}; criterion
     *       sample is PASS / FAIL based on the chain.</li>
     *   <li>Transform returns {@link Outcome.Fail Fail(...)} or throws
     *       — criterion sample is a
     *       {@link CriterionSampleOutcome#FAIL FAIL} carrying the
     *       failing reason; the postcondition chain is skipped. The
     *       failure's symbolic name and message flow through to the
     *       per-sample record for diagnostics.</li>
     * </ul>
     *
     * <p>Posture stays attached to the outer (this) decl; postconditions
     * already attached here are <em>not</em> carried forward to the
     * returned {@code TransformingDecl} — postconditions must be
     * stated either pre-transform (on this decl, via
     * {@code .where} / {@code .satisfies}) or post-transform
     * (on the returned decl), not both. Mixing pre- and
     * post-transform postconditions is a misuse and is rejected.
     *
     * @param <T> the derived value type the postcondition chain
     *            evaluates against on successful transform
     */
    public <T> TransformingDecl<O, T> transforming(Function<O, Outcome<T>> transform) {
        Objects.requireNonNull(transform, "transform");
        if (!postconditions.isEmpty()) {
            throw new IllegalStateException(
                    ".transforming(...) cannot follow .where/.satisfies on the same"
                            + " criterion decl: postconditions are evaluated either"
                            + " against the contract's output or against the"
                            + " transformed value, not both. Either drop the"
                            + " pre-transform postconditions, or split into two"
                            + " criteria via Criteria.of(...).");
        }
        return new TransformingDecl<>(posture, transform, List.of(), name);
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
        List<Postcondition<O>> clauses = new ArrayList<>(postconditions.size());
        for (NamedPostcondition<O> p : postconditions) {
            clauses.add(new Postcondition.Leaf<>(p.name(), p.check()));
        }
        return new DirectCriterion<>(id, clauses).withPosture(posture);
    }

    private CriterionDecl<O> appendPostcondition(String postconditionName, PostconditionCheck<O> check) {
        List<NamedPostcondition<O>> next = new ArrayList<>(postconditions.size() + 1);
        next.addAll(postconditions);
        next.add(new NamedPostcondition<>(postconditionName, check));
        return new CriterionDecl<>(posture, next, name);
    }
}
