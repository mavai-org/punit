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
 * <p>Deliberately exports nothing: the module is declarative-only, its
 * adapters serve the declarative surface and expose no supported
 * programmatic API. Builder-style authors bring their own clients.
 */
module org.mavai.punit.lm {

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
