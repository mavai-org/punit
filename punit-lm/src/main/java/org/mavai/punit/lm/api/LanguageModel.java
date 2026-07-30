package org.mavai.punit.lm.api;

import java.util.Map;
import org.mavai.outcome.Outcome;

/**
 * A configured language model, ready to invoke — punit-lm's thin
 * programmatic surface over the same machinery the declarative
 * {@code type: language-model} route uses.
 *
 * <p>The semantics are the declarative route's, verbatim: one request
 * per invocation — no retries, no caching, no client-side
 * sophistication (a silently retried failure is a resampled trial);
 * a provider <em>rejection</em> (HTTP 4xx — bad schema, unknown
 * model, expired credential) is a defect and throws; a failed
 * <em>delivery</em> (5xx, unreachable endpoint, an off-shape reply)
 * travels back as an {@link Outcome} failure with its cause as the
 * reason — the expected-failure channel the criteria judge.
 */
public interface LanguageModel {

    /**
     * Invokes the model once with the given input — a prompt string,
     * or the declarative surface's message-part shapes.
     *
     * @return the usage-bearing reply, or an {@code Outcome} failure
     *     for a failed delivery
     */
    Outcome<LmReply> invoke(Object input);

    /**
     * The resolved configuration as covariate values — every parameter
     * as actually used, fit for run and baseline provenance.
     */
    Map<String, String> configurationCovariates();
}
