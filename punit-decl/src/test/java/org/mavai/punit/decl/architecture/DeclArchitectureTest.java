package org.mavai.punit.decl.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Architecture tests for punit-decl's module boundaries.
 *
 * <p>punit-decl is a front-end over punit-core: it depends on punit-core
 * only (never the reverse — punit-core has no compile path to this
 * module, which the Gradle dependency direction already guarantees) and
 * is JUnit-free like the runtime package whose sentinel-deployability
 * the declarative entrypoint inherits. It also adds no statistics: the
 * only statistics-package dependency permitted is reading the shared
 * defaults, never a computation.
 */
@DisplayName("punit-decl Architecture Rules")
class DeclArchitectureTest {

    private static JavaClasses declClasses;

    @BeforeAll
    static void importClasses() {
        declClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("org.mavai.punit.decl");
    }

    @Test
    @DisplayName("the public surface is exactly the author annotations and the refusal exception")
    void publicSurfaceIsExactlyTheAuthorSurface() {
        // The engine enabling the declarative approach — parser, model,
        // locator, compiler, sizing — is a black box, not an API: it
        // lives under internal.* and is subject to change without
        // notice. The author-facing surface is the YAML formats plus
        // this short allowlist.
        ArchRule rule = classes()
                .that().resideInAPackage("org.mavai.punit.decl")
                .and().areTopLevelClasses()
                .should().haveSimpleNameEndingWith("Binding")
                .orShould().haveSimpleName("BindingFactory")
                .orShould().haveSimpleName("Transform")
                .orShould().haveSimpleName("Check")
                .orShould().haveSimpleName("Covariates")
                .orShould().haveSimpleName("Stepper")
                .orShould().haveSimpleName("Scorer")
                .orShould().haveSimpleName("ContractConfigurationException")
                .because("everything beyond the author surface is internal enablement — "
                        + "new public types belong under internal.* unless they are "
                        + "deliberately part of the authoring API");

        rule.check(declClasses);
    }

    @Test
    @DisplayName("the module exports only the author surface package")
    void moduleExportsOnlyTheAuthorSurface() throws Exception {
        // JPMS is the second fence: consumers on the module path cannot
        // reach the engine at all. This pins the exports list so a new
        // exports clause is a deliberate, reviewed decision.
        java.nio.file.Path classes = java.nio.file.Paths.get("build", "classes", "java", "main");
        var descriptor = java.lang.module.ModuleFinder.of(classes).findAll().stream()
                .map(reference -> reference.descriptor())
                .filter(module -> module.name().equals("org.mavai.punit.decl"))
                .findFirst()
                .orElseThrow();
        var exported = descriptor.exports().stream()
                .map(java.lang.module.ModuleDescriptor.Exports::source)
                .collect(java.util.stream.Collectors.toSet());
        org.assertj.core.api.Assertions.assertThat(exported)
                .as("packages exported by org.mavai.punit.decl")
                .containsExactlyInAnyOrder("org.mavai.punit.decl", "org.mavai.punit.decl.spi");
    }

    @Test
    @DisplayName("decl classes must not depend on JUnit")
    void declMustNotDependOnJUnit() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("org.mavai.punit.decl..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.junit..")
                .because("the declarative surface inherits the runtime package's "
                        + "sentinel-deployability — JUnit must never appear on its "
                        + "dependency graph");

        rule.check(declClasses);
    }

    @Test
    @DisplayName("decl classes depend only on punit, the JDK, and the YAML parser")
    void declDependsOnlyOnSanctionedPackages() {
        ArchRule rule = classes()
                .that().resideInAPackage("org.mavai.punit.decl..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "org.mavai.punit..",
                        "org.mavai.outcome..",
                        "java..",
                        "javax.xml..",
                        "org.w3c.dom..",
                        "org.xml.sax..",
                        "com.fasterxml.jackson..",
                        "org.snakeyaml.engine..",
                        "org.opentest4j..")
                .because("punit-decl is a front-end over punit-core — its dependency "
                        + "surface is punit plus its own YAML parser, nothing else");

        rule.check(declClasses);
    }

    @Test
    @DisplayName("decl classes compute no statistics — the statistics package is defaults-only here")
    void declTouchesOnlyStatisticalDefaults() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("org.mavai.punit.decl..")
                .should().dependOnClassesThat(com.tngtech.archunit.base.DescribedPredicate.describe(
                        "are statistics classes other than the shared defaults",
                        javaClass -> javaClass.getPackageName().startsWith("org.mavai.punit.statistics")
                                && !javaClass.getSimpleName().equals("StatisticalDefaults")
                                && !javaClass.getFullName().contains("VerificationFeasibilityEvaluator")))
                .because("punit-decl adds no statistics and calls none directly — it builds "
                        + "declarations the existing engine evaluates; only the single-sourced "
                        + "defaults are readable");

        rule.check(declClasses);
    }
}
