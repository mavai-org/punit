package org.mavai.punit.decl.model;

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
 */
public record CriterionDeclaration(
        String name,
        List<FormDeclaration> forms,
        Double threshold,
        String thresholdOrigin,
        String contractRef,
        Double tolerate,
        Double confidence) {

    public CriterionDeclaration {
        forms = List.copyOf(forms);
    }
}
