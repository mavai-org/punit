package org.mavai.punit.api.spec;

import java.util.Objects;

/**
 * One concrete failed-sample example: the input that produced it
 * (rendered to a string for diagnostic display) and the failure
 * reason. Carried per-postcondition on
 * {@link FailureCount#exemplars()} so reporters and the optimize
 * meta-prompt can show authors not just <em>that</em> a clause
 * failed but <em>what input</em> tripped it.
 *
 * <p>The input is rendered to {@code String} at the call site that
 * builds the exemplar — typically {@code String.valueOf(input)} —
 * so the exemplar carries no live reference to the per-sample
 * input. This keeps the type small and serialisable.
 *
 * @param input the input rendered to its diagnostic string form;
 *              never null
 * @param reason the failure's reason — typically
 *               {@code PostconditionResult.failureReason()}; never null
 */
public record FailureExemplar(String input, String reason) {

    public FailureExemplar {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(reason, "reason");
    }
}
