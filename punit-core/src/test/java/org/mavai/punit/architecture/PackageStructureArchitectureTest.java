package org.mavai.punit.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Architecture rules expressing punit-core's <strong>target</strong> package
 * structure — the destination of the cleanup arc described in the orchestrator
 * directives {@code DIR-PACKAGE-DRIFT-FIX-punit} and
 * {@code DIR-INTERNAL-NAMESPACE-punit}.
 *
 * <p>Each rule encodes one architectural decision documented in the per-project
 * domain ontology ({@code punit/docs/DOMAIN-ONTOLOGY.md}) and the developer-API
 * spec ({@code punit/docs/DEVELOPER-API.md}). The rules target the
 * <strong>post-cleanup</strong> state, not the current one. Until each rule's
 * underlying refactor lands, current violations are captured in
 * {@code archunit_store/} via
 * {@link com.tngtech.archunit.library.freeze.FreezingArchRule}, so the build
 * stays green while the cleanup proceeds.
 *
 * <h2>Workflow — TDD-style architectural validation</h2>
 *
 * <ol>
 *   <li>This class authors rules that describe the destination, not the
 *       journey. On {@code main} today, several rules name packages or
 *       coupling that still exist.</li>
 *   <li>On the first {@code ./gradlew test} run after this class lands,
 *       ArchUnit captures every current violation into a store file under
 *       {@code archunit_store/} (one file per frozen rule). Commit those store
 *       files alongside the test class — they are the published "frozen
 *       exception list."</li>
 *   <li>Each cleanup commit per the directives resolves some violations.
 *       ArchUnit silently removes resolved violations from the store and fails
 *       only on <em>new</em> violations. Commit the updated store file(s) with
 *       the refactor commit so the published exception list matches the
 *       working tree.</li>
 *   <li>When a store file becomes empty (every violation resolved), the
 *       corresponding rule has reached its target state. The cleanup arc is
 *       complete when every store file is empty. The frozen wrapper can then
 *       be removed; the rules become permanent regression guards.</li>
 * </ol>
 *
 * <h2>Re-baselining a rule</h2>
 *
 * <p>If a rule's expression changes (not because new violations were
 * introduced, but because the rule itself was refined), set the system
 * property {@code archunit.freeze.refreeze=true} on the next test run. The
 * store file is rewritten to reflect the current state; commit the result.
 *
 * <h2>Relation to {@link CoreArchitectureTest}</h2>
 *
 * <p>{@code CoreArchitectureTest} carries the framework's stable architectural
 * invariants (JUnit independence, statistics isolation, api-vs-extensions).
 * This class carries the cleanup-arc rules — rules that describe the
 * envisaged structure punit is moving toward. After the cleanup completes,
 * either:
 * <ul>
 *   <li>This class's surviving rules migrate into {@code CoreArchitectureTest}
 *       as ordinary regression guards (with the freeze wrapper removed); or</li>
 *   <li>This class stays as the dedicated home for package-structure rules,
 *       distinct from invariants that govern dependency layering and
 *       framework deployability. Editorial decision at completion.</li>
 * </ul>
 */
@DisplayName("punit-core Package Structure")
class PackageStructureArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .withImportOption(location ->
                        !location.contains("punit-report")
                                && !location.contains("punit-sentinel"))
                .importPackages("org.mavai.punit");
    }

    // ─────────────────────────────────────────────────────────────────────
    //   Drift fix — packages scheduled for removal or relocation
    //   (DIR-PACKAGE-DRIFT-FIX-punit)
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Drift fix — packages scheduled for removal")
    class DriftFix {

        @Test
        @DisplayName("no class may reside in org.mavai.punit.model")
        void noTopLevelModelPackage() {
            ArchRule rule = noClasses()
                    .should().resideInAPackage("org.mavai.punit.model..")
                    .because("the generic model/ catch-all is dispersed across api/, "
                            + "api/covariate/, and verdict/ under "
                            + "DIR-PACKAGE-DRIFT-FIX-punit step 3");

            freeze(rule).check(classes);
        }

        @Test
        @DisplayName("no class may reside in org.mavai.punit.power")
        void noPowerPackage() {
            ArchRule rule = noClasses()
                    .should().resideInAPackage("org.mavai.punit.power..")
                    .because("power/ is a single-class package; its contents fold into "
                            + "statistics/ under DIR-PACKAGE-DRIFT-FIX-punit step 1");

            freeze(rule).check(classes);
        }

        @Test
        @DisplayName("no class may reside in org.mavai.punit.controls")
        void noControlsPackage() {
            ArchRule rule = noClasses()
                    .should().resideInAPackage("org.mavai.punit.controls..")
                    .because("controls/ collapses; budget and pacing move under engine/ "
                            + "(then internal.engine/) under "
                            + "DIR-PACKAGE-DRIFT-FIX-punit step 4");

            freeze(rule).check(classes);
        }

        @Test
        @DisplayName("no class may reside in the top-level spec/ runtime tree")
        void noTopLevelSpecRuntimePackages() {
            // api/spec/ is the public typed-spec surface and stays.
            // After the move: internal.engine.spec/ replaces top-level spec/.
            // This rule forbids the old runtime-side homes only.
            ArchRule rule = noClasses()
                    .should().resideInAnyPackage(
                            "org.mavai.punit.spec",
                            "org.mavai.punit.spec.registry..",
                            "org.mavai.punit.spec.expiration..",
                            "org.mavai.punit.spec.model..",
                            "org.mavai.punit.spec.criteria..")
                    .because("runtime spec materialisation relocates under engine/spec/ "
                            + "under DIR-PACKAGE-DRIFT-FIX-punit step 5; api.spec/ is "
                            + "the public typed-spec surface and is unaffected");

            freeze(rule).check(classes);
        }

        @Test
        @DisplayName("no class may reside in org.mavai.punit.engine.output")
        void noEngineOutputPackage() {
            ArchRule rule = noClasses()
                    .should().resideInAPackage("org.mavai.punit.engine.output..")
                    .because("engine/output/ helpers fold next to their primary callers "
                            + "(or into a unified engine/emit/) under "
                            + "DIR-PACKAGE-DRIFT-FIX-punit step 6");

            freeze(rule).check(classes);
        }

        @Test
        @DisplayName("no class may reside in org.mavai.punit.contract")
        void noContractPackage() {
            ArchRule rule = noClasses()
                    .should().resideInAPackage("org.mavai.punit.contract..")
                    .because("the parallel and unused contract/ stack is deleted in full "
                            + "under DIR-CONTRACT-PACKAGE-REMOVAL-punit");

            freeze(rule).check(classes);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //   Internal namespace — structural public/internal split
    //   (DIR-INTERNAL-NAMESPACE-punit)
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Internal namespace — structural public/internal split")
    class InternalNamespace {

        @Test
        @DisplayName("engine packages relocate under internal.engine")
        void engineUnderInternal() {
            ArchRule rule = noClasses()
                    .should().resideInAPackage("org.mavai.punit.engine..")
                    .because("engine/ relocates under internal.engine/ under "
                            + "DIR-INTERNAL-NAMESPACE-punit step 1. The public api.spec "
                            + "interfaces (Criterion, BaselineProvider, etc.) stay in "
                            + "api.spec; only the engine implementations move.");

            freeze(rule).check(classes);
        }

        @Test
        @DisplayName("reporting package relocates under internal.reporting")
        void reportingUnderInternal() {
            ArchRule rule = noClasses()
                    .should().resideInAPackage("org.mavai.punit.reporting..")
                    .because("reporting/ (sink implementations and renderers) relocates "
                            + "under internal.reporting/ under "
                            + "DIR-INTERNAL-NAMESPACE-punit step 2. The VerdictSink "
                            + "interface stays in verdict/ — only implementations move.");

            freeze(rule).check(classes);
        }

        @Test
        @DisplayName("util package relocates under internal.util")
        void utilUnderInternal() {
            ArchRule rule = noClasses()
                    .should().resideInAPackage("org.mavai.punit.util..")
                    .because("util/ relocates under internal.util/ under "
                            + "DIR-INTERNAL-NAMESPACE-punit step 3");

            freeze(rule).check(classes);
        }

        @Test
        @DisplayName("the runtime package's only top-level class is PUnit")
        void runtimeContainsOnlyPUnit() {
            // Public surface in runtime/ is exactly PUnit. The emitters
            // (BaselineEmitter, ExploreEmitter, OptimizeEmitter), the
            // resolvers (BaselineProviderResolver, TestIdentityResolver),
            // and the helper EmpiricalTestComposer all relocate under
            // internal.runtime/ — they are not part of the public surface.
            ArchRule rule = classes()
                    .that().resideInAPackage("org.mavai.punit.runtime")
                    .and().areTopLevelClasses()
                    .should().haveSimpleName("PUnit")
                    .because("runtime/ is part of the public surface; only PUnit is public "
                            + "there. Emitters and resolvers move to internal.runtime/ "
                            + "under DIR-INTERNAL-NAMESPACE-punit step 4");

            freeze(rule).check(classes);
        }

        @Test
        @DisplayName("no non-internal package may import from org.mavai.punit.internal")
        void publicMustNotImportInternal() {
            // The structural enforcement of the public/internal split.
            // Post-namespace move (steps 1–4), the rule is live as a
            // regression guard: any new cross-boundary dependency from a
            // public-named package into internal/* fails.
            //
            // The freeze store is not empty at the moment the rule first
            // goes live. The residue is intra-module access from public
            // packages into internal packages — which JPMS does not
            // restrict (export visibility is a cross-module concern only).
            // The ArchUnit rule is the stricter check; the freeze captures
            // the legitimate cases:
            //   1. runtime/PUnit drives the engine — by design. PUnit is
            //      the public entry point that orchestrates internals;
            //      its dependencies on internal.engine/, internal.runtime/,
            //      internal.reporting/, internal.util/ are intentional and
            //      JPMS-compatible (intra-module).
            //   2. api.covariate.CovariateDeclaration reaches into
            //      internal.util.HashUtils for sha256/truncateHash. Small
            //      util reach; intra-module so JPMS-fine. Could be inlined
            //      or have HashUtils promoted — neither is blocking.
            //
            // The earlier verdict-types-embed-engine-config category was
            // resolved by promoting TokenMode (→ verdict/) and
            // PacingConfiguration (→ api/) to their natural public
            // locations; those entries are no longer in the freeze.
            ArchRule rule = noClasses()
                    .that().resideOutsideOfPackage("org.mavai.punit.internal..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("org.mavai.punit.internal..")
                    .because("the internal/ namespace is the structural marker for "
                            + "framework internals; consumers must not reach in. Live "
                            + "regression guard after DIR-INTERNAL-NAMESPACE-punit; "
                            + "frozen residue tracks remaining cross-boundary cleanup.");

            freeze(rule).check(classes);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //   API root — abstraction-layer invariants
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("API root — abstraction-layer invariants")
    class ApiRoot {

        @Test
        @DisplayName("api root depends only on the JDK and other api code")
        void apiRootIsStdlibOnly() {
            // The api root carries the abstraction layer (Contract,
            // ServiceContract, Sampling, …). Concrete implementations and
            // their third-party dependencies belong in api subpackages
            // (api.spec, api.covariate, api.criterion, …). Allowed companions
            // for the root: the org.mavai.outcome library (a sibling
            // abstraction the api leans on by design) and
            // org.junit.jupiter.api (meta-annotation targets only, already
            // governed by CoreArchitectureTest.apiPackageMustNotDependOnJUnitExtensions).
            ArchRule rule = noClasses()
                    .that().resideInAPackage("org.mavai.punit.api")
                    .should().dependOnClassesThat()
                    .resideOutsideOfPackages(
                            "java..",
                            "javax..",
                            "org.mavai.punit.api..",
                            "org.mavai.outcome..",
                            "org.junit.jupiter.api..")
                    .because("the api root carries the abstraction layer; concrete "
                            + "implementations and their third-party dependencies live "
                            + "in api subpackages (api.spec, api.covariate, api.criterion, …)");

            freeze(rule).check(classes);
        }
    }
}
