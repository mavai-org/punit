/**
 * The punit-lm module.
 *
 * <p>First-class language-model support for the declarative surface:
 * registers the {@code language-model} service type (provider adapters
 * per the mavai service-definition format) and the
 * {@code prompt-engineer} built-in stepper through punit-decl's
 * ServiceLoader seams. Adding this module to the test runtime is what
 * enables {@code type: language-model} — punit-core and punit-decl
 * carry no LLM assumptions.
 *
 * <p>Exports exactly one package — {@code org.mavai.punit.lm.api},
 * the thin programmatic surface (a configured {@code LanguageModel},
 * its usage-bearing {@code LmReply}, and the config-map factory) over
 * the same machinery the declarative route uses. Providers, wire
 * shapes, and validation stay internal.
 */
module org.mavai.punit.lm {

    exports org.mavai.punit.lm.api;

    // ── Required modules ──────────────────────────────────────
    requires transitive org.mavai.punit.decl;
    requires java.net.http;
    requires com.fasterxml.jackson.databind;
    // Explicitly NO requires for JUnit.

    // ── Services ──────────────────────────────────────────────
    provides org.mavai.punit.decl.spi.ServiceType
            with org.mavai.punit.lm.LanguageModelServiceType;
    provides org.mavai.punit.decl.spi.StepperProvider
            with org.mavai.punit.lm.PromptEngineer;
}
