package org.mavai.punit.lm.providers;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.mavai.punit.decl.ContractConfigurationException;
import org.mavai.punit.decl.spi.MediaKind;
import org.mavai.punit.lm.providers.anthropic.Anthropic;
import org.mavai.punit.lm.providers.apertus.Apertus;
import org.mavai.punit.lm.providers.litellm.LiteLlm;
import org.mavai.punit.lm.providers.mistral.Mistral;
import org.mavai.punit.lm.providers.ollama.Ollama;
import org.mavai.punit.lm.providers.openai.OpenAi;

/**
 * The named provider registry — one vendor per sub-package, at
 * baseltest parity; adding a provider is one new package plus one
 * entry here — and the capability-support logic every tier of the
 * family's capability rule consults.
 *
 * <p>Deliberately absent from every adapter, as a rule and not an
 * omission: retries, backoff, client-side response caching, streaming,
 * tool use. A silently retried failure is a resampled trial and biases
 * the observed rate — sampling independence outranks API convenience.
 */
public final class Providers {

    /** The generic OpenAI-compatible adapter, used when {@code provider:} is omitted. */
    public static final LmProvider GENERIC = new LmProvider(
            "openai-compatible", null, null, false,
            true, false, false,
            Set.of(), OpenAiCompatible.MEDIA_KINDS,
            parameters -> null,
            OpenAiCompatible::body,
            OpenAiCompatible::bearerHeaders,
            OpenAiCompatible::extract);

    private static final Map<String, LmProvider> PROVIDERS = registry();

    private Providers() {}

    /** The adapter for a declared provider name, or the generic one. */
    public static LmProvider resolve(String name) {
        if (name == null) {
            return GENERIC;
        }
        LmProvider provider = PROVIDERS.get(name);
        if (provider == null) {
            throw new ContractConfigurationException(
                    "unknown `provider: " + name + "` — supported: "
                            + String.join(", ", new TreeMap<>(PROVIDERS).keySet())
                            + " (or omit `provider:` for a generic OpenAI-compatible endpoint)");
        }
        return provider;
    }

    /** The capabilities the adapter honours without any author declaration. */
    public static Set<String> staticallySupported(LmProvider provider) {
        Set<String> supported = new LinkedHashSet<>();
        if (provider.supportsResponseSchema()) {
            supported.add("response-schema");
        }
        if (provider.supportsPromptCaching()) {
            supported.add("prompt-caching");
        }
        if (provider.supportsThinking()) {
            supported.add("thinking");
        }
        return supported;
    }

    /**
     * The capabilities an author may assert via {@code capabilities:} —
     * the statically supported set, widened by what the adapter can
     * encode on demand, widened by the media token for each modality
     * the adapter's protocol carries. An adapter that deliberately
     * withholds a capability leaves it out, so declaring it there is
     * refused rather than silently overriding the caution.
     */
    public static Set<String> declarableCapabilities(LmProvider provider) {
        Set<String> declarable = new LinkedHashSet<>(staticallySupported(provider));
        declarable.addAll(provider.extraDeclarableCapabilities());
        for (MediaKind kind : provider.mediaKinds()) {
            declarable.add(Media.CAPABILITY_FOR.get(kind));
        }
        return declarable;
    }

    /**
     * Effective support: honoured by default, or turned on by the
     * author — the single question both the refuse-at-load
     * (test/measure) and the degrade-with-note (explore) paths ask, so
     * they stay consistent.
     */
    public static boolean honours(LmProvider provider, Set<String> declared, String capability) {
        if (staticallySupported(provider).contains(capability)) {
            return true;
        }
        return declared != null && declared.contains(capability);
    }

    private static Map<String, LmProvider> registry() {
        Map<String, LmProvider> providers = new LinkedHashMap<>();
        for (LmProvider provider : List.of(OpenAi.PROVIDER, Anthropic.PROVIDER, Ollama.PROVIDER,
                Mistral.PROVIDER, Apertus.PROVIDER, LiteLlm.PROVIDER)) {
            providers.put(provider.name(), provider);
        }
        return providers;
    }
}
