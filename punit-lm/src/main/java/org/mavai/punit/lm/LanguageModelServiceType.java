package org.mavai.punit.lm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.mavai.punit.decl.spi.ConfiguredService;
import org.mavai.punit.decl.spi.ServiceType;

/**
 * The built-in {@code language-model} service type — registered
 * through punit-decl's ServiceLoader seam; adding punit-lm to the test
 * runtime is what enables {@code type: language-model}.
 *
 * <p>{@link #configure} is the strict tier of the family's capability
 * rule: a declared key the resolved adapter cannot honour is refused
 * at load, because silently dropping it would change what is being
 * measured. {@link #explorePoint} is the lenient tier: an explore grid
 * may span providers with differing support, so a point drops what its
 * provider cannot honour and the run announces it — never silently.
 */
public final class LanguageModelServiceType implements ServiceType {

    /** Public no-argument constructor for {@link java.util.ServiceLoader}. */
    public LanguageModelServiceType() {}

    @Override
    public String name() {
        return "language-model";
    }

    @Override
    public ConfiguredService configure(String serviceName, Map<String, Object> configuration) {
        LanguageModelParameters parameters =
                ParameterValidation.validated(serviceName, configuration);
        LmProvider provider = Providers.resolve(parameters.provider());
        refuseUnhonoured(serviceName, provider, parameters);
        veto(serviceName, provider, parameters);
        return ConfiguredLanguageModel.of(serviceName, parameters);
    }

    @Override
    public ExplorePoint explorePoint(String serviceName, Map<String, Object> configuration) {
        LanguageModelParameters parameters =
                ParameterValidation.validated(serviceName, configuration);
        LmProvider provider = Providers.resolve(parameters.provider());
        List<String> notes = new ArrayList<>();
        if (parameters.responseSchema() != null
                && !Providers.honours(provider, parameters.capabilities(), "response-schema")) {
            parameters = parameters.withoutResponseSchema();
            notes.add("provider '" + provider.name() + "' has no structured-output support in "
                    + "this reader — the response-schema is not sent for this configuration; "
                    + "carry the output shape in the system prompt if the comparison should "
                    + "stay fair");
        }
        if (Boolean.TRUE.equals(parameters.promptCaching())
                && !Providers.honours(provider, parameters.capabilities(), "prompt-caching")) {
            parameters = parameters.withoutPromptCaching();
            notes.add("provider '" + provider.name() + "' has no prompt-caching support in "
                    + "this reader — `prompt-caching:` is not sent for this configuration; its "
                    + "latency is measured uncached");
        }
        if (parameters.adaptiveThinking()
                && !Providers.honours(provider, parameters.capabilities(), "thinking")) {
            parameters = parameters.withoutThinking();
            notes.add("provider '" + provider.name() + "' has no thinking support in this "
                    + "reader — `thinking:` is not sent for this configuration; its responses "
                    + "are sampled without deliberation");
        }
        veto(serviceName, provider, parameters);
        return new ExplorePoint(ConfiguredLanguageModel.of(serviceName, parameters),
                notes.isEmpty() ? null : String.join("; ", notes));
    }

    /**
     * The strict tier: a declared key the adapter cannot honour is a
     * load-time refusal — never a silent drop, which would change what
     * is being measured while claiming the same configuration.
     */
    private void refuseUnhonoured(
            String serviceName, LmProvider provider, LanguageModelParameters parameters) {
        if (parameters.responseSchema() != null
                && !Providers.honours(provider, parameters.capabilities(), "response-schema")) {
            throw ParameterValidation.fail(serviceName,
                    "provider '" + provider.name() + "' has no structured-output support in "
                            + "this reader: a declared `response-schema:` cannot be honoured, "
                            + "and silently dropping it would change what is being measured. "
                            + "Remove the schema, declare the capability if the endpoint "
                            + "honours it, or choose a provider that supports it.");
        }
        if (Boolean.TRUE.equals(parameters.promptCaching())
                && !Providers.honours(provider, parameters.capabilities(), "prompt-caching")) {
            throw ParameterValidation.fail(serviceName,
                    "provider '" + provider.name() + "' has no prompt-caching support in this "
                            + "reader: `prompt-caching: true` cannot be honoured, and silently "
                            + "dropping it would change what is being measured. Remove the key, "
                            + "declare the capability if the endpoint honours it, or choose a "
                            + "provider that supports it.");
        }
        if (parameters.adaptiveThinking()
                && !Providers.honours(provider, parameters.capabilities(), "thinking")) {
            throw ParameterValidation.fail(serviceName,
                    "provider '" + provider.name() + "' has no thinking support in this "
                            + "reader: `thinking: adaptive` cannot be honoured, and silently "
                            + "dropping it would change what is being measured. Remove the key, "
                            + "declare the capability if the endpoint honours it, or choose a "
                            + "provider that supports it.");
        }
    }

    /** The vendor's own veto over an otherwise-valid combination, at load. */
    private void veto(String serviceName, LmProvider provider, LanguageModelParameters parameters) {
        String refusal = provider.constraint().apply(parameters);
        if (refusal != null) {
            throw ParameterValidation.fail(serviceName,
                    "provider '" + provider.name() + "': " + refusal);
        }
    }
}
