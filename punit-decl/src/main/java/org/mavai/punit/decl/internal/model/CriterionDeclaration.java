package org.mavai.punit.decl.internal.model;

import java.util.List;

/**
 * One parsed {@code criteria:} entry — the unit its own statistics are
 * computed over, its own Bernoulli stream.
 *
 * @param name the criterion's name; derived from its first form when
 *     the entry declares none
 * @param forms the declared postcondition forms, in declaration order
 *     (a conjunction)
 * @param threshold the declared pass-rate threshold in (0, 1) — a
 *     normative stipulation — or {@code null} for an empirical
 *     criterion
 * @param thresholdOrigin the bar's provenance category ({@code sla} /
 *     {@code slo} / {@code policy}), or {@code null} for origin
 *     unspecified
 * @param contractRef the provenance document reference, or {@code null}
 * @param tolerate the empirical criterion's risk claim — the worst true
 *     rate the operator accepts — or {@code null}
 * @param confidence the empirical criterion's own confidence level,
 *     overriding the contract default, or {@code null}
 * @param optionalSlack the optional-check failure budget, or {@code null}
 * @param zeroFailures {@code true} for the observational zero-failures
 *     form ({@code zero-failures: true}, amendment 2026-11-03): no
 *     threshold, no confidence, no risk claim; judged PASS iff no trial
 *     failed
 */
public record CriterionDeclaration(
        String name,
        List<FormDeclaration> forms,
        Double threshold,
        String thresholdOrigin,
        String contractRef,
        Double tolerate,
        Double confidence,
        org.mavai.punit.api.criterion.OptionalSlack optionalSlack,
        boolean zeroFailures) {

    /** A criterion with no optional-check failure budget declared. */
    public CriterionDeclaration(String name, List<FormDeclaration> forms, Double threshold,
            String thresholdOrigin, String contractRef, Double tolerate, Double confidence) {
        this(name, forms, threshold, thresholdOrigin, contractRef, tolerate, confidence, null);
    }

    /** A rate-bearing (normative or empirical) criterion. */
    public CriterionDeclaration(String name, List<FormDeclaration> forms, Double threshold,
            String thresholdOrigin, String contractRef, Double tolerate, Double confidence,
            org.mavai.punit.api.criterion.OptionalSlack optionalSlack) {
        this(name, forms, threshold, thresholdOrigin, contractRef, tolerate, confidence,
                optionalSlack, false);
    }

    /**
     * An empirical criterion: no declared threshold and not the
     * zero-failures form — its bar comes from a measured baseline.
     */
    public boolean empirical() {
        return threshold == null && !zeroFailures;
    }

    public CriterionDeclaration {
        forms = List.copyOf(forms);
    }
}
