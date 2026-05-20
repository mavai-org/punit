package org.javai.punit.api;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.javai.outcome.Outcome;
import org.javai.outcome.Outcome.Ok;
import org.javai.punit.api.criterion.Criteria;
import org.javai.punit.api.criterion.Criterion;
import org.javai.punit.api.criterion.CriterionSampleResult;
import org.javai.punit.api.criterion.Decl;
import org.javai.punit.api.criterion.LatencyCriterion;

/**
 * The operational layer of a service contract: how to invoke the service for
 * one sample, and what counts as success when the service returns.
 *
 * <p>{@link ServiceContract} extends this interface, so an author writing
 * {@code class MyServiceContract implements ServiceContract<F, I, O>} satisfies both
 * surfaces with one {@code implements} clause. The author overrides
 * exactly two methods:
 *
 * <ul>
 *   <li>{@link #invoke(Object, TokenTracker) invoke} — the service
 *       call. Returns {@link Outcome.Ok} for a successful invocation
 *       or {@link Outcome.Fail} for an expected business-level
 *       failure. Records cost via the supplied
 *       {@link TokenTracker}.</li>
 *   <li>{@link #criteria() criteria} — declares the contract's
 *       acceptance criteria as a {@link Criteria} value.</li>
 * </ul>
 *
 * <p>The {@code apply} method is a concrete default the framework
 * dispatches to per sample. Authors do not override it. It wraps
 * {@code invoke} with timing, cost diff, and postcondition
 * evaluation.
 *
 * @param <I> the per-sample input type
 * @param <O> the per-sample output value type
 */
public interface Contract<I, O> {

    /**
     * Invoke the service for one sample.
     *
     * <h4>How the framework treats the return value</h4>
     *
     * <ul>
     *   <li>{@link Outcome.Ok} — the framework treats the sample as a
     *       candidate for postcondition evaluation. The carried value is
     *       what each {@code ensure} clause sees.</li>
     *   <li>{@link Outcome.Fail} — the framework treats the sample as an
     *       apply-level failure. Postconditions are not evaluated (no
     *       result was produced). The full {@link org.javai.outcome.Failure}
     *       — symbolic name, message, optional cause — is preserved on
     *       the {@link ServiceContractOutcome} for diagnostics.</li>
     * </ul>
     *
     * <h4>How the framework treats a thrown exception</h4>
     *
     * <p>The framework does not catch exceptions thrown from this
     * method. A thrown exception propagates out of the sample runner,
     * out of the engine, and aborts the run. How an author handles
     * exception-prone code inside this method body is their decision;
     * the framework neither encourages nor discourages any particular
     * pattern.
     *
     * <h4>Cost tracking</h4>
     *
     * <p>Call {@code tracker.recordTokens(n)} to record cost incurred
     * during this invocation. The framework derives the per-sample
     * token count by diffing {@link TokenTracker#totalTokens()} before
     * and after the call. Authors with no cost to track simply omit
     * the call.
     *
     * @param input   the per-sample input
     * @param tracker the per-run cost channel
     * @return the wrapped outcome
     */
    Outcome<O> invoke(I input, TokenTracker tracker);

    /**
     * Author-facing, value-form criteria declaration — returns a
     * {@link Criteria} value describing the contract's
     * verdict-producing strategy.
     *
     * <h4>K=1 — return a bare {@link Decl}</h4>
     *
     * <pre>{@code
     * import static org.javai.punit.api.criterion.Criteria.meeting;
     * import static org.javai.punit.api.ThresholdOrigin.SLA;
     *
     * @Override public Criteria<Receipt> criteria() {
     *     return meeting().<Receipt>passRate(0.9999)
     *             .contractRef(SLA, "Payment Provider SLA v2.3, §4.1")
     *             .satisfies("Authorisation returned APPROVED", ...);
     * }
     * }</pre>
     *
     * <p>The decl's name (via {@code .name(String)}) is optional in
     * the K=1 case; missing names default to
     * {@link Criteria#DEFAULT_CRITERION_ID} at lowering time.
     *
     * <h4>K&gt;1 — bundle via {@link Criteria#of(Decl[])}</h4>
     *
     * <pre>{@code
     * import static org.javai.punit.api.criterion.Criteria.meeting;
     * import static org.javai.punit.api.criterion.Criteria.empirical;
     * import static org.javai.punit.api.criterion.Criteria.of;
     * import static org.javai.punit.api.ThresholdOrigin.SLA;
     *
     * @Override public Criteria<Receipt> criteria() {
     *     return of(
     *         meeting().<Receipt>passRate(0.9999)
     *             .contractRef(SLA, "Payment Provider SLA v2.3, §4.1")
     *             .name("payment-completes")
     *             .satisfies("Authorisation returned APPROVED", ...),
     *         empirical().<Receipt>passRate()
     *             .name("structure-valid")
     *             .satisfies("Receipt is parseable", ...));
     * }
     * }</pre>
     *
     * <p>Every decl in a K&gt;1 bundle <strong>must</strong> supply a
     * name via {@code .name(String)}; names must be unique within
     * the bundle. Both rules are enforced by
     * {@link Criteria#of(Decl[])}.
     *
     * <p>The default returns {@link Criteria#empty()}; a contract
     * that ships with the default must override this method to
     * declare at least one criterion before
     * {@link #effectiveCriteria()} is consulted.
     *
     * <p>Latency commitments live on the sibling {@link #latency()}
     * method, not on this bundle — the structural 0..1 cardinality
     * of latency is captured by that sibling's singular return type
     * rather than by a runtime check inside this bundle.
     */
    default Criteria<O> criteria() {
        return Criteria.empty();
    }

    /**
     * The contract's per-percentile latency commitment, when one is
     * declared. Singular: at most one latency criterion per contract.
     *
     * <p>Two authoring shapes via the {@link Criteria#meeting()} /
     * {@link Criteria#empirical()} factories:
     *
     * <pre>{@code
     * import static org.javai.punit.api.PercentileKey.P95;
     * import static org.javai.punit.api.PercentileKey.P99;
     * import static org.javai.punit.api.ThresholdOrigin.SLA;
     * import static org.javai.punit.api.criterion.Criteria.meeting;
     * import static org.javai.punit.api.criterion.Criteria.empirical;
     * import static java.time.Duration.ofMillis;
     *
     * // empirical — thresholds derived from the resolved baseline
     * @Override public LatencyCriterion latency() {
     *     return empirical().atMost(P95);
     * }
     *
     * // contractual — fixed per-percentile ceilings
     * @Override public LatencyCriterion latency() {
     *     return meeting().atMost(P95, ofMillis(500))
     *                     .atMost(P99, ofMillis(1500))
     *                     .contractRef(SLA, "Acme Payment SLA v3.2 §4.2");
     * }
     * }</pre>
     *
     * <p>The framework merges the present latency criterion into
     * {@link #effectiveCriteria()} under the fixed id
     * {@link LatencyCriterion#ID}. No name is authored at the call
     * site — the singular cardinality makes the id a constant.
     *
     * <p>The default returns {@link LatencyCriterion#none()}: no
     * latency commitment is asserted.
     */
    default LatencyCriterion latency() {
        return LatencyCriterion.none();
    }

    /**
     * Resolves the contract's criteria to an immutable runtime
     * list. The framework hook; do not override.
     *
     * <p>The contract's {@link #criteria()} must return a non-empty
     * {@link Criteria}; its {@link Criteria#asList() asList()} forms
     * the functional portion of the result, to which any present
     * {@link #latency() latency} criterion is appended.
     *
     * @throws IllegalStateException if {@link #criteria()} returns
     *         {@link Criteria#empty()} (no criteria declared).
     */
    default List<Criterion<O>> effectiveCriteria() {
        Criteria<O> declared = criteria();
        if (declared.isEmpty()) {
            throw new IllegalStateException(
                    "Contract " + getClass().getName()
                            + " declares no criteria. Override criteria()"
                            + " to return a non-empty Criteria<O>.");
        }
        List<Criterion<O>> functional = declared.asList();
        LatencyCriterion latency = latency();
        if (!latency.isPresent()) {
            return functional;
        }
        List<Criterion<O>> merged = new ArrayList<>(functional.size() + 1);
        merged.addAll(functional);
        merged.add(latency.<O>toRuntime());
        return List.copyOf(merged);
    }

    /**
     * Run one sample. Postconditions are evaluated against the produced
     * value on apply-level success; skipped on apply-level failure.
     *
     * <p>Concrete default; the framework dispatches to this form per
     * sample. Authors implement {@link #invoke}; they do not override
     * this method.
     */
    default ServiceContractOutcome<I, O> apply(I input, TokenTracker tracker) {
        long startTokens = tracker.totalTokens();
        long start = System.nanoTime();
        Outcome<O> result = invoke(input, tracker);
        Duration duration = Duration.ofNanos(System.nanoTime() - start);
        long sampleTokens = Math.max(0L, tracker.totalTokens() - startTokens);

        Optional<O> expected = expectedFrom(input);
        if (expected.isEmpty()) {
            requireMatchingCompatible(input);
        }
        ClauseEvaluation clauseEvaluation = result instanceof Ok<O> ok
                ? evaluateClauses(ok.value(), expected)
                : ClauseEvaluation.empty();   // postcondition evaluation skipped on apply-level failure

        return new ServiceContractOutcome<>(
                result,
                this,
                clauseEvaluation.postconditionResults(),
                sampleTokens,
                duration,
                clauseEvaluation.criterionSampleResults());
    }

    /**
     * Read the sample's known-expected output if the input declares
     * one via {@link Expected}. The unchecked cast on the return value
     * relies on the author having parameterised {@code Expected<OT>}
     * with the contract's output type {@code O}; a mismatch would
     * surface as a {@link ClassCastException} at the matcher call site.
     */
    @SuppressWarnings("unchecked")
    private Optional<O> expectedFrom(I input) {
        if (input instanceof Expected<?> e) {
            return Optional.ofNullable((O) e.expected());
        }
        return Optional.empty();
    }

    /**
     * Fail fast at the first sample when a criterion declares a
     * matching postcondition (via {@code .matchedBy(...)} or
     * {@code .matchedByEquality()}) but the sampling's input type
     * does not implement {@link Expected}.
     */
    private void requireMatchingCompatible(I input) {
        for (Criterion<O> criterion : effectiveCriteria()) {
            if (criterion.requiresExpected()) {
                throw new IllegalStateException(
                        "Criterion '" + criterion.id() + "' declares .matchedBy(...), "
                                + "which requires the sampling's input type to implement "
                                + "org.javai.punit.api.Expected. The current input is of "
                                + "type " + input.getClass().getName() + ", which does "
                                + "not implement Expected. Either implement Expected<...> "
                                + "on that input type, or replace .matchedBy(...) with "
                                + ".satisfies(...) on the criterion.");
            }
        }
    }

    private ClauseEvaluation evaluateClauses(O value, Optional<O> expected) {
        // Walk criteria via per-sample evaluate(value). For each criterion
        // we get a three-valued CriterionSampleResult; the verdict path
        // (which consumes a flat List<PostconditionResult>) is fed the
        // per-postcondition results on PASS / FAIL, and a single synthetic
        // failed PostconditionResult on INCONCLUSIVE — preserving the
        // reason's name and message for diagnostics. The
        // synthetic-result-on-INCONCLUSIVE mapping is the step-2 default
        // for the denominator policy; a configurable policy is the subject
        // of a later step. The per-criterion sample results are retained
        // verbatim alongside the flat list so downstream consumers — the
        // per-criterion accumulator, the per-criterion verdict computation
        // — can read the methodology-level partition unit's behaviour
        // without re-walking the criteria.
        List<PostconditionResult> flat = new ArrayList<>();
        List<CriterionSampleResult> perCriterion = new ArrayList<>();
        for (Criterion<O> criterion : effectiveCriteria()) {
            CriterionSampleResult result = criterion.evaluate(value, expected);
            perCriterion.add(result);
            switch (result.outcome()) {
                case PASS, FAIL -> flat.addAll(result.postconditionResults());
                case INCONCLUSIVE -> {
                    Outcome.Fail<?> reason = result.reason().orElseThrow();
                    flat.add(PostconditionResult.failed(
                            criterion.id(),
                            reason));
                }
            }
        }
        return new ClauseEvaluation(flat, perCriterion);
    }

    record ClauseEvaluation(
            List<PostconditionResult> postconditionResults,
            List<CriterionSampleResult> criterionSampleResults) {
        static ClauseEvaluation empty() {
            return new ClauseEvaluation(List.of(), List.of());
        }
    }
}
