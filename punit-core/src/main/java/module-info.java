/**
 * The punit-core module.
 *
 * <p>The framework's JUnit-free core: authoring API, statistical
 * engine, verdict pipeline, and the entry-point class {@code PUnit}.
 * Reachable from a sentinel-deployed classpath without JUnit on it
 * (JUnit annotation types are a compile-time-only dependency, via
 * {@code requires static}).
 *
 * <h2>Exported packages — the public surface</h2>
 *
 * <p>Every package listed via {@code exports X} is the public API
 * an author may import. The {@code internal.*} subtree is
 * intentionally not exported. Targeted-exports clauses
 * ({@code exports X to <module>}) below grant the sibling modules
 * ({@code punit-junit5}, {@code punit-report}, {@code punit-sentinel})
 * the minimum surface they need to compose with punit-core; external
 * consumers continue to see only the public packages.
 */
module org.mavai.punit.core {

    // ── Public API surface ────────────────────────────────────
    exports org.mavai.punit.api;
    exports org.mavai.punit.api.criterion;
    exports org.mavai.punit.api.spec;
    exports org.mavai.punit.api.covariate;
    exports org.mavai.punit.runtime;
    exports org.mavai.punit.verdict;
    exports org.mavai.punit.statistics;
    exports org.mavai.punit.statistics.transparent;

    // ── Targeted exports — internal types granted to sibling
    //    modules at their narrowest. Each grant is the minimum
    //    sufficient set surfaced by the sibling's compile errors;
    //    none of these are visible to external (unnamed-module)
    //    consumers.
    exports org.mavai.punit.internal.engine.emit
        to org.mavai.punit.report;
    exports org.mavai.punit.internal.reporting
        to org.mavai.punit.report,
           org.mavai.punit.sentinel;

    // ── Required modules ──────────────────────────────────────
    requires transitive org.mavai.outcome;
    requires transitive org.opentest4j;
    requires static org.junit.jupiter.api;
    requires java.xml;
    requires org.apache.commons.statistics.distribution;
    requires org.yaml.snakeyaml;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.dataformat.csv;
    requires org.apache.logging.log4j;

    // ── ServiceLoader ─────────────────────────────────────────
    uses org.mavai.punit.verdict.VerdictSink;
    uses org.mavai.punit.api.spec.SpecCriterionDeriver;
    uses org.mavai.punit.runtime.PUnit.DeclarativeFrontEnd;
    provides org.mavai.punit.api.spec.SpecCriterionDeriver
        with org.mavai.punit.internal.engine.criteria.PostureBasedSpecCriterionDeriver;
}
