package org.mavai.punit.lm.api;

import java.util.Map;
import org.mavai.punit.lm.ConfiguredLanguageModel;
import org.mavai.punit.lm.LanguageModelServiceType;

/**
 * The factory: a configured {@link LanguageModel} from a configuration
 * map — the services file's {@code configuration:} block, verbatim.
 *
 * <p>The map takes exactly the declarative keys ({@code system-prompt},
 * {@code provider}, {@code model}, {@code temperature}, {@code top-p},
 * {@code thinking}, {@code prompt-caching}, {@code response-schema},
 * {@code max-tokens}, {@code capabilities}) and goes through the same
 * validation catalogue, the same strict capability gate, the same
 * provider vetoes, and the same environment tier
 * ({@code mavai.llm.*} system properties, then {@code MAVAI_LLM_*}
 * variables, then each vendor's conventional credential fallback) as a
 * {@code type: language-model} service definition — one vocabulary,
 * one set of refusals, no second schema. A misfit configuration throws
 * at configure time, before any request.
 */
// mavai-ref: JVI-AT423AB — do not remove (resolves in mavai-orchestrator)
public final class LanguageModels {

    private LanguageModels() {}

    /**
     * Configures a language model from the declarative configuration
     * map.
     *
     * @throws org.mavai.punit.decl.ContractConfigurationException on
     *     any configuration misfit, with the declarative route's own
     *     refusal message
     */
    public static LanguageModel configure(Map<String, Object> configuration) {
        // The whole strict path, reused: validation, capability
        // allowance, provider resolution and veto — parity with the
        // services file by construction, not by parallel code.
        ConfiguredLanguageModel configured = (ConfiguredLanguageModel)
                new LanguageModelServiceType().configure("language-model", configuration);
        return new LanguageModel() {
            @Override
            public org.mavai.outcome.Outcome<LmReply> invoke(Object input) {
                return configured.exchange(input);
            }

            @Override
            public Map<String, String> configurationCovariates() {
                return configured.configurationCovariates();
            }
        };
    }
}
