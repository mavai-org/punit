package org.mavai.punit.lm.architecture;

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
 * Architecture tests for punit-lm's module boundaries.
 *
 * <p>punit-lm is a plug-in behind punit-decl's SPI: it depends on
 * punit-decl (and through it punit-core) only, is JUnit-free, adds no
 * statistics, and exports exactly one package — {@code lm.api}, the
 * thin programmatic surface — beside its two ServiceLoader
 * registrations. Providers, wire shapes, and validation stay
 * unexported.
 */
@DisplayName("punit-lm Architecture Rules")
class LmArchitectureTest {

    private static JavaClasses lmClasses;

    @BeforeAll
    static void importClasses() {
        lmClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("org.mavai.punit.lm");
    }

    @Test
    @DisplayName("the module exports exactly the api package — internals stay sealed")
    void moduleExportsExactlyTheApiPackage() throws Exception {
        java.nio.file.Path classes = java.nio.file.Paths.get("build", "classes", "java", "main");
        var descriptor = java.lang.module.ModuleFinder.of(classes).findAll().stream()
                .map(reference -> reference.descriptor())
                .filter(module -> module.name().equals("org.mavai.punit.lm"))
                .findFirst()
                .orElseThrow();
        org.assertj.core.api.Assertions.assertThat(descriptor.exports())
                .as("packages exported by org.mavai.punit.lm — the thin programmatic "
                        + "surface and nothing else")
                .extracting(java.lang.module.ModuleDescriptor.Exports::source)
                .containsExactly("org.mavai.punit.lm.api");
        org.assertj.core.api.Assertions.assertThat(descriptor.provides())
                .as("the two ServiceLoader registrations")
                .hasSize(2);
    }

    @Test
    @DisplayName("lm classes must not depend on JUnit")
    void lmMustNotDependOnJUnit() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("org.mavai.punit.lm..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.junit..")
                .because("punit-lm serves the declarative surface, which inherits the runtime "
                        + "package's sentinel-deployability — JUnit must never appear on its "
                        + "dependency graph");

        rule.check(lmClasses);
    }

    @Test
    @DisplayName("lm classes depend only on punit-decl, punit-core, the JDK, and Jackson")
    void lmDependsOnlyOnSanctionedPackages() {
        ArchRule rule = classes()
                .that().resideInAPackage("org.mavai.punit.lm..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "org.mavai.punit.lm..",
                        "org.mavai.punit.decl..",
                        "org.mavai.punit.api..",
                        "org.mavai.outcome..",
                        "java..",
                        "com.fasterxml.jackson..")
                .because("punit-lm is a plug-in behind punit-decl's SPI — its dependency "
                        + "surface is the SPI, the stepper contract, and its own HTTP/JSON "
                        + "plumbing, nothing else");

        rule.check(lmClasses);
    }

    @Test
    @DisplayName("lm classes compute no statistics")
    void lmTouchesNoStatistics() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("org.mavai.punit.lm..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.mavai.punit.statistics..")
                .because("provider adapters carry requests, never computations — every "
                        + "statistical calculation lives in punit-core's statistics package");

        rule.check(lmClasses);
    }
}
