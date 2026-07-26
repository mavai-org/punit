/**
 * The punit-decl module.
 *
 * <p>The declarative authoring front-end: parser, validating loader,
 * registries, and instantiation for the mavai family's declarative
 * formats ({@code mavai-contract/1}, {@code mavai-services/1}). A
 * front-end over punit-core's existing machinery — never a second
 * engine: it adds no statistics and calls none directly.
 *
 * <p>Explicitly omits {@code requires org.junit.jupiter.api}: the
 * declarative surface inherits the runtime package's
 * sentinel-deployability, and the compiler enforces the JUnit-free
 * invariant, complementing the module's architecture test.
 */
module org.mavai.punit.decl {

    // ── Public API surface ────────────────────────────────────
    // Exactly the author surface: the bindings annotations and the
    // refusal exception. Everything else — parser, model, engine —
    // is black-box enablement of the declarative approach, not an
    // API, and subject to change without notice.
    exports org.mavai.punit.decl;

    // ── Required modules ──────────────────────────────────────
    requires transitive org.mavai.punit.core;
    requires org.snakeyaml.engine.v2;
    requires com.fasterxml.jackson.databind;
    requires java.xml;
    // Explicitly NO requires for JUnit.

    // ── Services ──────────────────────────────────────────────
    provides org.mavai.punit.runtime.PUnit.DeclarativeFrontEnd
            with org.mavai.punit.decl.internal.DeclFrontEnd;
}
