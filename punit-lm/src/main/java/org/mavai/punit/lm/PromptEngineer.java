package org.mavai.punit.lm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.mavai.outcome.Outcome;
import org.mavai.punit.api.spec.FactorsStepper;
import org.mavai.punit.api.spec.FailureCount;
import org.mavai.punit.api.spec.NextFactor;
import org.mavai.punit.decl.ContractConfigurationException;
import org.mavai.punit.decl.spi.StepperProvider;

/**
 * The {@code prompt-engineer} built-in stepper: a meta-LLM tunes the
 * prompt. Each iteration sends the current prompt, its score, and the
 * per-criterion failure breakdown with exemplars to a meta model and
 * treats the response as the next prompt.
 *
 * <p>Schema (all keys optional): {@code provider} and {@code model}
 * default to the optimized service's own — read from the current
 * configuration at each step, so the credentials the service already
 * uses cover the meta model too and no vendor is silently pinned —
 * {@code temperature} (default 0.5), {@code system-prompt} (the meta
 * instruction), {@code target-key} (default {@code system-prompt}),
 * {@code max-exemplars} (default 2). The resolved meta identity is
 * announced as a run note (punit's stepper seam carries no per-proposal
 * provenance yet).
 */
public final class PromptEngineer implements StepperProvider {

    static final String META_PROMPT = """
            You are a prompt engineer. The user gives you a system prompt currently \
            used with an LLM-backed service under probabilistic test, the pass rate \
            that prompt achieved, and a breakdown of the criteria it failed with \
            example failures. Propose an improved version of the prompt that \
            addresses the most common failure modes for structured-output and \
            instruction-following tasks — vague output shape, missing required \
            fields, free-form commentary mixed into the answer. Output only the new \
            system prompt. No commentary, no preamble, no surrounding quotes.""";

    private static final Set<String> CONFIGURATION_KEYS = Set.of(
            "provider", "model", "temperature", "system-prompt", "target-key", "max-exemplars");

    /** Public no-argument constructor for {@link java.util.ServiceLoader}. */
    public PromptEngineer() {}

    @Override
    public String name() {
        return "prompt-engineer";
    }

    @Override
    public FactorsStepper<Map<String, Object>> create(Map<String, Object> stepperConfig) {
        for (String key : stepperConfig.keySet()) {
            if (!CONFIGURATION_KEYS.contains(key)) {
                throw new ContractConfigurationException(
                        "stepper 'prompt-engineer': unknown `stepper-config:` key `" + key
                                + ":` — the schema is: provider, model, temperature, "
                                + "system-prompt, target-key, max-exemplars (all optional)");
            }
        }
        String provider = string(stepperConfig, "provider");
        String model = string(stepperConfig, "model");
        double temperature = temperature(stepperConfig);
        String systemPrompt = stepperConfig.get("system-prompt") instanceof String prompt
                ? prompt : META_PROMPT;
        String targetKey = stepperConfig.get("target-key") instanceof String key
                ? key : "system-prompt";
        int maxExemplars = maxExemplars(stepperConfig);
        return new Stepper(provider, model, temperature, systemPrompt, targetKey, maxExemplars);
    }

    private static String string(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value != null && !(value instanceof String)) {
            throw new ContractConfigurationException(
                    "stepper 'prompt-engineer': `" + key + ":` must be a string");
        }
        return (String) value;
    }

    private static double temperature(Map<String, Object> config) {
        Object value = config.get("temperature");
        if (value == null) {
            return 0.5;
        }
        if (value instanceof Boolean || !(value instanceof Number number)) {
            throw new ContractConfigurationException(
                    "stepper 'prompt-engineer': `temperature:` must be a number");
        }
        return number.doubleValue();
    }

    private static int maxExemplars(Map<String, Object> config) {
        Object value = config.get("max-exemplars");
        if (value == null) {
            return 2;
        }
        if (value instanceof Boolean || !(value instanceof Integer count) || count < 0) {
            throw new ContractConfigurationException(
                    "stepper 'prompt-engineer': `max-exemplars:` must be at least 0, got "
                            + value);
        }
        return count;
    }

    /**
     * The meta-LLM as prompt engineer: the previous iteration's
     * failures drive the next prompt. Meta invokers are cached per
     * resolved (provider, model) identity, so a sweep over one meta
     * model builds one client.
     */
    private static final class Stepper implements FactorsStepper<Map<String, Object>> {

        private final String provider;
        private final String model;
        private final double temperature;
        private final String systemPrompt;
        private final String targetKey;
        private final int maxExemplars;
        private final Map<List<String>, ConfiguredLanguageModel> invokers = new LinkedHashMap<>();

        private Stepper(String provider, String model, double temperature, String systemPrompt,
                String targetKey, int maxExemplars) {
            this.provider = provider;
            this.model = model;
            this.temperature = temperature;
            this.systemPrompt = systemPrompt;
            this.targetKey = targetKey;
            this.maxExemplars = maxExemplars;
        }

        @Override
        public NextFactor<Map<String, Object>> next(
                Map<String, Object> current, List<IterationResult<Map<String, Object>>> history) {
            IterationResult<Map<String, Object>> last = history.get(history.size() - 1);
            String metaProvider = provider != null ? provider
                    : current.get("provider") instanceof String declared ? declared : null;
            String metaModel = model != null ? model
                    : current.get("model") instanceof String declared ? declared : null;
            ConfiguredLanguageModel meta = invokers.computeIfAbsent(
                    java.util.Arrays.asList(metaProvider, metaModel),
                    identity -> ConfiguredLanguageModel.of("prompt-engineer (meta)",
                            new LanguageModelParameters(systemPrompt, metaProvider, null,
                                    metaModel, temperature, null, null, null, null,
                                    LanguageModelParameters.DEFAULT_MAX_TOKENS)));
            System.out.println("[PUNIT] note: prompt-engineer meta model: provider "
                    + (metaProvider == null ? "openai-compatible" : metaProvider) + ", model "
                    + (metaModel == null ? "(environment default)" : metaModel)
                    + ", temperature " + temperature);
            String suggestion = switch (meta.invoke(metaMessage(last))) {
                case Outcome.Ok<String> ok -> ok.value().strip();
                case Outcome.Fail<String> fail -> throw new IllegalStateException(
                        "stepper 'prompt-engineer': the meta model failed to deliver — "
                                + fail.failure().message());
            };
            if (suggestion.isEmpty()) {
                // A meta model with nothing to propose stops the run.
                return NextFactor.stop();
            }
            Map<String, Object> next = new LinkedHashMap<>(current);
            next.put(targetKey, suggestion);
            return NextFactor.next(next);
        }

        /** The meta-LLM's user message: prompt, score, and the failure breakdown. */
        private String metaMessage(IterationResult<Map<String, Object>> last) {
            List<String> sections = new ArrayList<>();
            sections.add("Current system prompt:");
            sections.add(String.valueOf(last.factors().getOrDefault(targetKey, "")));
            sections.add("");
            int samples = last.samplesExecuted();
            double rate = samples > 0 ? (double) last.successes() / samples : last.score();
            sections.add(String.format(java.util.Locale.ROOT,
                    "Pass rate achieved: %.2f (%d of %d samples passed)",
                    rate, last.successes(), samples));
            List<String> breakdown = failureBreakdown(last.failuresByPostcondition());
            if (!breakdown.isEmpty()) {
                sections.add("");
                sections.add("Failure breakdown:");
                sections.addAll(breakdown);
            }
            sections.add("");
            sections.add("Suggest an improved version.");
            return String.join("\n", sections);
        }

        private List<String> failureBreakdown(Map<String, FailureCount> failures) {
            List<String> lines = new ArrayList<>();
            failures.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue().count(), a.getValue().count()))
                    .forEach(entry -> {
                        lines.add("- criterion \"" + entry.getKey() + "\" failed "
                                + entry.getValue().count() + " time(s).");
                        entry.getValue().exemplars().stream()
                                .limit(maxExemplars)
                                .forEach(exemplar -> lines.add("    - input \""
                                        + exemplar.input() + "\" → " + exemplar.reason()));
                    });
            return lines;
        }
    }
}
