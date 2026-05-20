package org.javai.punit.api.criterion;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The contract's verdict-producing strategy. Given a sample summary,
 * a {@code Criteria<O>} resolves to a contract verdict via the
 * §1.4.6 composite-verdict procedure of the Statistical Companion
 * (FAIL-dominant aggregation over per-criterion verdicts).
 *
 * <p>Two concrete shapes:
 *
 * <ul>
 *   <li>{@link CriterionDecl} — a single-criterion strategy. The
 *       K=1 case: posture, optional name, optional postconditions,
 *       optional refinements (contractRef, confidence floor,
 *       MDE/power). Returned directly from {@code meeting().passRate(...)}
 *       and friends; an author with one criterion returns the decl
 *       directly from {@code Contract.criteria()}.</li>
 *   <li>{@link CompositeCriteria} — a multi-criterion strategy
 *       constructed via {@link #of(Decl[]) Criteria.of(...)}. Holds an
 *       ordered list of named decls and resolves the contract verdict
 *       per §1.4.6.</li>
 * </ul>
 *
 * <p>A single {@code CriterionDecl} <em>is</em> a {@code Criteria}
 * directly — the contract's K=1 strategy is the criterion itself.
 * The framework treats it as a one-entry composite at lowering time,
 * with the default criterion id {@link #DEFAULT_CRITERION_ID} when
 * the author did not supply an explicit name via {@code .name(...)}.
 *
 * <p>{@link #asList()} is the framework's lowering hook: it returns
 * the runtime {@link Criterion} list the engine, baseline-resolution
 * path, and verdict path consume. Authors do not call it.
 *
 * @param <O> the contract's per-sample output value type
 */
public sealed interface Criteria<O>
        permits Decl, CompositeCriteria, EmptyCriteria {

    /**
     * The default criterion name used at lowering time when a K=1
     * {@link Decl} carries no explicit {@code .name(...)}.
     */
    String DEFAULT_CRITERION_ID = "default";

    /**
     * Lower this declaration to the runtime {@link Criterion} list
     * the framework consumes. Framework-internal; authors do not
     * call this directly.
     */
    List<Criterion<O>> asList();

    /**
     * Whether this is the empty-criteria sentinel — used by the
     * framework's effective-criteria resolution to distinguish
     * "author has not declared via the value-form" from "author has
     * declared an explicit (possibly empty) criteria list."
     *
     * <p>Default implementation: never empty. Only
     * {@link Criteria#empty()} is empty.
     */
    default boolean isEmpty() {
        return false;
    }

    /**
     * The empty criteria value — used by the default
     * {@link org.javai.punit.api.Contract#criteria()} implementation
     * when no criteria are declared via the value-form. The
     * framework's lowering treats this as "no explicit criteria;
     * fall back to the postcondition-derived K=1 default with
     * implicit zero-failures" — same as the empty value-form case
     * today.
     */
    static <O> Criteria<O> empty() {
        return EmptyCriteria.instance();
    }

    /**
     * Construct a multi-criterion bundle from one or more named
     * {@link Decl} values. The canonical authoring surface for K>1
     * contracts:
     *
     * <pre>{@code
     * import static org.javai.punit.api.criterion.Criteria.empirical;
     * import static org.javai.punit.api.criterion.Criteria.meeting;
     * import static org.javai.punit.api.criterion.Criteria.of;
     * import static org.javai.punit.api.ThresholdOrigin.SLA;
     *
     * @Override public Criteria<Receipt> criteria() {
     *     return of(
     *         meeting().<Receipt>passRate(0.9999)
     *             .contractRef(SLA, "Payment Provider SLA v2.3 §4.1")
     *             .name("payment-completes")
     *             .satisfies("Authorisation returned APPROVED", ...),
     *         empirical().<Receipt>passRate()
     *             .name("structure-valid")
     *             .satisfies("Receipt is parseable", ...));
     * }
     * }</pre>
     *
     * <h4>Naming rules</h4>
     *
     * <ul>
     *   <li>K=1 (one Decl) — the bundle is constructed; the single
     *       decl's name (if supplied) or {@link #DEFAULT_CRITERION_ID}
     *       (otherwise) is used at lowering time. Authors who supply
     *       one decl typically return it bare from
     *       {@code criteria()}; calling {@code of(oneDecl)} is
     *       supported for symmetry.</li>
     *   <li>K>1 (two or more Decls) — every decl <strong>must</strong>
     *       supply a name via {@code .name(String)}. A missing name
     *       throws {@link IllegalArgumentException}. Names must be
     *       unique within the bundle; duplicates throw
     *       {@link IllegalArgumentException}.</li>
     * </ul>
     *
     * @param decls one or more declarations; must be non-empty
     * @return a {@code Criteria<O>} bundling the supplied decls
     * @throws IllegalArgumentException if {@code decls} is empty, if
     *         any decl in a K>1 bundle is unnamed, or if two decls
     *         share a name
     */
    /**
     * Open a contractual-mode kind-selector chain. The author calls
     * one of the kind-selector methods on the returned
     * {@link ContractualDecl} to declare a pass-rate, zero-failures,
     * or latency criterion.
     *
     * <p>Origin defaults to
     * {@link org.javai.punit.api.ThresholdOrigin#UNSPECIFIED} and is
     * overridden when the author later calls
     * {@code .contractRef(origin, ref)} on the kind-specific decl.
     *
     * <p>The factory is non-generic — the type parameter
     * {@code <O>} enters the chain at the kind-selector step
     * ({@code .passRate(...)}, {@code .zeroFailures()}), or not at
     * all in the latency case. Witness placement at the call site:
     *
     * <pre>{@code
     * // K=1 — target-type inference works through a single chain.
     * @Override public Criteria<String> criteria() {
     *     return meeting().passRate(0.85).where("parseable", v -> isJson(v));
     * }
     *
     * // K>1 — explicit witness on the kind-selector method.
     * @Override public Criteria<Response> criteria() {
     *     return of(
     *         meeting().<Response>passRate(0.99).name("body").where(...),
     *         meeting().<Response>zeroFailures().name("pii").where(...));
     * }
     *
     * // Latency — no witness anywhere; .atMost(...) returns
     * // LatencyCriterion which has no type parameter.
     * @Override public LatencyCriterion latency() {
     *     return meeting().atMost(P95, ofSeconds(1));
     * }
     * }</pre>
     */
    static ContractualDecl meeting() {
        return ContractualDeclImpl.INSTANCE;
    }

    /**
     * Open an empirical-mode kind-selector chain. The author calls
     * one of the kind-selector methods on the returned
     * {@link EmpiricalDecl} to declare an empirical pass-rate or
     * empirical latency criterion.
     *
     * <p>No origin parameter applies — the baseline IS the source
     * of the threshold.
     *
     * <p>The factory is non-generic for the same reason as
     * {@link #meeting()}.
     */
    static EmpiricalDecl empirical() {
        return EmpiricalDeclImpl.INSTANCE;
    }

    @SafeVarargs
    static <O> Criteria<O> of(Decl<O>... decls) {
        Objects.requireNonNull(decls, "decls");
        if (decls.length == 0) {
            throw new IllegalArgumentException(
                    "Criteria.of(...) requires at least one Decl");
        }
        List<Decl<O>> list = Arrays.asList(decls);
        for (Decl<O> d : list) {
            Objects.requireNonNull(d, "decl");
        }
        if (list.size() == 1) {
            // K=1: pass through the bare Decl as-is. Its name (if any)
            // or the default id is resolved at lowering time.
            return list.get(0);
        }
        rejectUnnamedOrDuplicateNames(list);
        return new CompositeCriteria<>(list);
    }

    private static <O> void rejectUnnamedOrDuplicateNames(List<Decl<O>> decls) {
        Set<String> seen = new HashSet<>(decls.size() * 2);
        for (int i = 0; i < decls.size(); i++) {
            Decl<O> d = decls.get(i);
            String name = d.name().orElseThrow(() -> new IllegalArgumentException(
                    "Decl at index " + decls.indexOf(d) + " has no name; in a "
                            + "multi-criterion bundle every Decl must call "
                            + ".name(...) before Criteria.of(...)"));
            if (!seen.add(name)) {
                throw new IllegalArgumentException(
                        "duplicate criterion name '" + name + "' in Criteria.of(...)");
            }
        }
    }
}
