package org.mavai.punit.api.spec;

import java.util.Objects;

/**
 * A {@link CriterionResult} together with its {@link CriterionRole} as
 * stamped by the spec builder at registration time.
 *
 * @param result the criterion's evaluated result
 * @param role   REQUIRED (contributes) or REPORT_ONLY (attached, not
 *               contributing)
 */
public record EvaluatedCriterion(CriterionResult result, CriterionRole role) {

    public EvaluatedCriterion {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(role, "role");
    }
}
