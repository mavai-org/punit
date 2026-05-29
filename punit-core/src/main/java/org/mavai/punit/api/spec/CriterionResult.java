package org.mavai.punit.api.spec;

import java.util.Map;
import java.util.Objects;

/**
 * The outcome of a single {@link Criterion#evaluate(EvaluationContext)}
 * invocation.
 *
 * <p>{@link #detail()} carries the criterion's structured observations
 * (thresholds, observed values, breaches, baseline source, …) keyed by
 * a stable string. Each concrete criterion kind documents its detail
 * keys in its own javadoc; the same keys feed the verdict-XML
 * {@code <detail>} block without further translation.
 *
 * @param criterionName stable identifier (e.g. {@code "bernoulli-pass-rate"})
 * @param verdict       the criterion's contribution to the spec's composed verdict
 * @param explanation   a one-line human-readable summary
 * @param detail        structured detail keyed by a stable string
 */
public record CriterionResult(
        String criterionName,
        Verdict verdict,
        String explanation,
        Map<String, Object> detail) {

    public CriterionResult {
        Objects.requireNonNull(criterionName, "criterionName");
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(explanation, "explanation");
        Objects.requireNonNull(detail, "detail");
        if (criterionName.isBlank()) {
            throw new IllegalArgumentException("criterionName must not be blank");
        }
        detail = Map.copyOf(detail);
    }
}
