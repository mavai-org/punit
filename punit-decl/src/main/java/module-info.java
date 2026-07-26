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
    exports org.mavai.punit.decl;
    exports org.mavai.punit.decl.model;
    exports org.mavai.punit.decl.parser;

    // ── Required modules ──────────────────────────────────────
    requires transitive org.mavai.punit.core;
    requires org.snakeyaml.engine.v2;
    // Explicitly NO requires for JUnit.
}
