package org.mavai.punit.api.spec;

/**
 * The user-facing strategy interface every spec kind implements.
 *
 * <p>Sealed over the two spec families — {@link Experiment} (which
 * itself unifies measure, explore, and optimize) and
 * {@link ProbabilisticTest}. The binary distinction maps the
 * authoring intent: experiments observe and produce artefacts; tests
 * assert and produce verdicts.
 *
 * <p>Carries <strong>no</strong> type parameters at the user-facing
 * level: the typed work happens inside each spec via an internal
 * {@link TypedSpec} delegate, reached through
 * {@link #dispatch(Dispatcher)}. The dispatch pattern lets the engine
 * recover the typed view without unchecked casts — Java captures the
 * wildcards on the internal delegate into the generic method on the
 * dispatcher.
 *
 * <p>The user touches this interface only as a return type from
 * {@code @PUnitExperiment} / {@code @PUnitTest} methods; the
 * dispatch mechanism is engine-internal.
 *
 * @see TypedSpec
 */
public sealed interface Spec
        permits Experiment, ProbabilisticTest {

    /**
     * Engine entry point — dispatches to a typed view of this spec.
     * Authors do not call this directly.
     */
    <R> R dispatch(Dispatcher<R> dispatcher);

    /**
     * Engine-facing visitor over the typed view of a spec. The
     * {@link #apply} method is generic over the spec's
     * {@code <FT, IT, OT>}, allowing implementations (the engine, in
     * practice) to remain fully type-safe.
     */
    interface Dispatcher<R> {
        <FT, IT, OT> R apply(TypedSpec<FT, IT, OT> typed);
    }
}
