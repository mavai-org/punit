package org.javai.punit.api;

import java.time.Duration;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import org.javai.outcome.Outcome;
import org.javai.punit.api.criterion.Criteria;
import org.javai.punit.api.criterion.CriterionDecl;
import org.javai.punit.api.criterion.LatencyCriterion;

/**
 * Authoring entry point for an <em>inline contract</em> — an anonymous
 * {@link ServiceContract} declared at the test/experiment call site,
 * with no named contract class.
 *
 * <p>An inline contract carries only the operational layer (the service
 * call plus contractual acceptance criteria); it has no stable identity,
 * covariates, or factor record. It is therefore restricted to
 * <strong>contractual</strong> criteria — the builder exposes
 * pass-rate / zero-failures / latency thresholds (routed through
 * {@link Criteria#meeting()}) but deliberately offers no empirical path.
 * Empirical criteria need a baseline, and a baseline needs identity an
 * anonymous contract cannot provide; the absence of an empirical method
 * on this builder is that boundary made type-level.
 *
 * <p>The factor type {@code FT} is phantom — factors reach the body by
 * closure capture, never through a method — so {@link Builder#build()}
 * produces a {@code ServiceContract<FT, IT, OT>} for whatever {@code FT}
 * the call site demands.
 */
public final class InlineContract {

    private InlineContract() { }

    /** Open an inline-contract builder. Seeds {@code IT} / {@code OT}. */
    public static <IT, OT> Builder<IT, OT> of() {
        return new Builder<>();
    }

    public static final class Builder<IT, OT> {

        private BiFunction<IT, TokenTracker, Outcome<OT>> invoke;
        private CriterionDecl<OT> decl;
        private LatencyCriterion latency;

        private Builder() { }

        // ── the service call (pick one) ──────────────────────────────

        /** The service call, returning an {@link Outcome}. */
        public Builder<IT, OT> invoking(Function<IT, Outcome<OT>> call) {
            Objects.requireNonNull(call, "call");
            this.invoke = (in, tracker) -> call.apply(in);
            return this;
        }

        /** The service call, with the per-run cost channel. */
        public Builder<IT, OT> invoking(BiFunction<IT, TokenTracker, Outcome<OT>> call) {
            this.invoke = Objects.requireNonNull(call, "call");
            return this;
        }

        /** The service call, returning a bare value the framework wraps in {@code Outcome.ok}. */
        public Builder<IT, OT> returning(Function<IT, OT> call) {
            Objects.requireNonNull(call, "call");
            this.invoke = (in, tracker) -> Outcome.ok(call.apply(in));
            return this;
        }

        // ── contractual criteria (no empirical path by design) ───────

        public Builder<IT, OT> passRate(double rate) {
            this.decl = Criteria.meeting().<OT>passRate(rate);
            return this;
        }

        public Builder<IT, OT> zeroFailures() {
            this.decl = Criteria.meeting().<OT>zeroFailures();
            return this;
        }

        public Builder<IT, OT> satisfies(String name, Function<OT, Outcome<?>> check) {
            this.decl = requireDecl().satisfies(name, check);
            return this;
        }

        public Builder<IT, OT> where(String name, Predicate<OT> predicate) {
            this.decl = requireDecl().where(name, predicate);
            return this;
        }

        public Builder<IT, OT> contractRef(ThresholdOrigin origin, String ref) {
            this.decl = requireDecl().contractRef(origin, ref);
            return this;
        }

        public Builder<IT, OT> latencyAtMost(PercentileKey key, Duration max) {
            this.latency = Criteria.meeting().atMost(key, max);
            return this;
        }

        /** Build the anonymous contract for any {@code FT} (phantom). */
        public <FT> ServiceContract<FT, IT, OT> build() {
            if (invoke == null) {
                throw new IllegalStateException(
                        "an inline contract requires invoking(...) or returning(...)");
            }
            if (decl == null) {
                throw new IllegalStateException(
                        "an inline contract requires a criterion — call passRate(...) or zeroFailures()");
            }
            final BiFunction<IT, TokenTracker, Outcome<OT>> call = invoke;
            final CriterionDecl<OT> criteria = decl;
            final LatencyCriterion lat = latency;
            return new ServiceContract<FT, IT, OT>() {
                @Override
                public Outcome<OT> invoke(IT input, TokenTracker tracker) {
                    return call.apply(input, tracker);
                }

                @Override
                public Criteria<OT> criteria() {
                    return criteria;
                }

                @Override
                public LatencyCriterion latency() {
                    return lat != null ? lat : LatencyCriterion.none();
                }

                @Override
                public String id() {
                    return "inline";
                }
            };
        }

        private CriterionDecl<OT> requireDecl() {
            if (decl == null) {
                throw new IllegalStateException(
                        "declare the criterion kind first — call passRate(...) or zeroFailures() "
                                + "before satisfies/where/contractRef");
            }
            return decl;
        }
    }
}
