package org.javai.punit.api;

import java.util.List;
import java.util.Objects;

import org.javai.outcome.Outcome;
import org.javai.punit.api.criterion.ValueMatcher;

/**
 * A named predicate over a single sample's produced value. A
 * postcondition decides pass or fail for one observable property;
 * it performs no transformation and carries no statistical
 * configuration. The {@link Criterion}-level transform (when
 * present) does any pre-postcondition shaping; the postcondition's
 * job is strictly the predicate.
 *
 * <h2>Authoring</h2>
 *
 * <p>The {@code ensure} factory is a nod to Eiffel's
 * design-by-contract vocabulary, not a strict replication of it. Each
 * call declares one postcondition that delivers an unopinionated
 * evaluation of an aspect of the value under test. The check
 * function returns {@link Outcome.Ok} when the aspect holds, or
 * {@link Outcome.Fail} carrying the specific reason — what was
 * observed, which element tripped the check, the diagnostic detail a
 * downstream report or optimize-feedback histogram needs.
 *
 * <p>The verdict is not formed at the postcondition site. Each
 * {@code ensure} produces a {@link PostconditionResult}; the
 * framework collects all of them per sample, and the opinion (pass /
 * fail) is taken later when the test or experiment asks for it. A
 * postcondition's {@code Outcome.Fail} only manifests as a
 * JUnit-style assertion failure when the test's
 * {@code assertPasses()} (or equivalent) is invoked.
 *
 * <pre>{@code
 * import static org.javai.punit.api.Postcondition.ensure;
 *
 * ensure("Response has actions", t -> t.actions().isEmpty()
 *         ? Outcome.fail("empty", "actions list was empty")
 *         : Outcome.ok())
 *
 * ensure("All actions known", t -> {
 *     var unknown = unknownActions(t);
 *     return unknown.isEmpty()
 *             ? Outcome.ok()
 *             : Outcome.fail("unknown-action", "found: " + unknown);
 * })
 * }</pre>
 *
 * <h2>Transforming the value before the predicate</h2>
 *
 * <p>To inspect a derived view of the produced value (parsed JSON,
 * normalised text, an extracted sub-record), declare a transforming
 * criterion via {@code decl.transforming(transform)} on the value-form
 * authoring surface — see
 * {@link org.javai.punit.api.criterion.CriterionDecl#transforming(
 * java.util.function.Function)}. The transform is owned by the
 * criterion; the postcondition's job remains strictly that of a
 * predicate over whatever value reaches it.
 *
 * @param <T> the type the postcondition evaluates
 */
public sealed interface Postcondition<T> permits Postcondition.Leaf, Postcondition.Matching {

    /** Human-readable description; non-blank. */
    String description();

    /**
     * Evaluate this postcondition and return one summary result.
     *
     * <p>This signature is well-defined for the {@link Leaf} variant,
     * whose predicate sees the produced value alone. The {@link Matching}
     * variant requires both an expected and an actual value, and so
     * throws on this method — callers in the engine pattern-match on
     * the variant and route to {@link Matching#match(Object, Object)}
     * directly.
     */
    PostconditionResult evaluate(T value);

    /**
     * Evaluate this postcondition and return every contributing
     * result. For a {@link Leaf} this is a singleton list. For a
     * {@link Matching} this is undefined without an expected value
     * — see {@link #evaluate(Object)}.
     */
    List<PostconditionResult> evaluateAll(T value);

    // ── Authoring entry point ───────────────────────────────────────

    /**
     * Declare a postcondition. The check function evaluates one
     * aspect of the value and returns an {@link Outcome.Ok} if the
     * aspect holds or an {@link Outcome.Fail} carrying the specific
     * reason if it does not. The framework collects every
     * postcondition's result per sample; whether a failure becomes
     * an assertion failure is decided later by the test, not here.
     */
    static <T> Postcondition<T> ensure(String description, PostconditionCheck<T> check) {
        return new Leaf<>(description, check);
    }

    // ── Variant ─────────────────────────────────────────────────────

    /** A direct postcondition: one description, one check. */
    record Leaf<T>(String description, PostconditionCheck<T> check)
            implements Postcondition<T> {

        public Leaf {
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(check, "check");
            if (description.isBlank()) {
                throw new IllegalArgumentException("description must not be blank");
            }
        }

        @Override
        public PostconditionResult evaluate(T value) {
            Outcome<Void> result;
            try {
                result = check.check(value);
            } catch (RuntimeException e) {
                String reason = e.getMessage() != null
                        ? e.getMessage()
                        : e.getClass().getSimpleName();
                return PostconditionResult.failed(description, reason);
            }
            return switch (result) {
                case Outcome.Ok<?> ignored -> PostconditionResult.passed(description);
                case Outcome.Fail<?> f -> PostconditionResult.failed(description, f);
            };
        }

        @Override
        public List<PostconditionResult> evaluateAll(T value) {
            return List.of(evaluate(value));
        }
    }

    /**
     * A reference-matching postcondition: one description and a
     * {@link ValueMatcher} that judges an actual value against an
     * expected value of the same type. Produced by
     * {@link org.javai.punit.api.criterion.CriterionDecl#matchedBy(
     * java.util.function.Supplier)} and
     * {@link org.javai.punit.api.criterion.CriterionDecl#matchedByEquality()}
     * on the criterion builder; the expected value is sourced at
     * sample time from the input's {@link Expected#expected()}.
     *
     * <p>This variant cannot be evaluated through the value-only
     * {@link #evaluate(Object)} method on the sealed interface — the
     * engine pattern-matches on the variant and calls
     * {@link #match(Object, Object)} directly. The interface-method
     * implementation throws to surface engine-side misuse.
     */
    record Matching<T>(String description, ValueMatcher<T> matcher)
            implements Postcondition<T> {

        public Matching {
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(matcher, "matcher");
            if (description.isBlank()) {
                throw new IllegalArgumentException("description must not be blank");
            }
        }

        /**
         * Apply the matcher to the {@code (expected, actual)} pair and
         * fold the resulting {@link Outcome} into a
         * {@link PostconditionResult} carrying this postcondition's
         * description.
         *
         * <p>A {@link RuntimeException} thrown from the matcher is a
         * defect under the framework's {@code Outcome}-vs-exception
         * convention and is folded into a failed
         * {@link PostconditionResult} carrying the exception's message
         * as the reason — same treatment as {@link Leaf}.
         */
        public PostconditionResult match(T expected, T actual) {
            Outcome<Void> result;
            try {
                result = matcher.match(expected, actual);
            } catch (RuntimeException e) {
                String reason = e.getMessage() != null
                        ? e.getMessage()
                        : e.getClass().getSimpleName();
                return PostconditionResult.failed(description, reason);
            }
            return switch (result) {
                case Outcome.Ok<?> ignored -> PostconditionResult.passed(description);
                case Outcome.Fail<?> f -> PostconditionResult.failed(description, f);
            };
        }

        @Override
        public PostconditionResult evaluate(T value) {
            throw new IllegalStateException(
                    "Matching postcondition '" + description
                            + "' requires an expected value; the engine must "
                            + "pattern-match on the Postcondition variant and "
                            + "route Matching through Matching.match(expected, actual). "
                            + "Reaching Postcondition.evaluate(value) on a Matching "
                            + "variant indicates an engine-side defect.");
        }

        @Override
        public List<PostconditionResult> evaluateAll(T value) {
            return List.of(evaluate(value));
        }
    }
}
