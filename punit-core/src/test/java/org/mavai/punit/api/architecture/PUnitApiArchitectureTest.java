package org.mavai.punit.api.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Architecture rules for the {@code org.mavai.punit.api} package.
 *
 * <p>The api package is the public authoring surface. Two rules
 * remain meaningful at package level (the third — "no JUnit at all"
 * — is replaced by the narrower
 * {@code apiPackageMustNotDependOnJUnitExtensions} rule in
 * {@link org.mavai.punit.architecture.CoreArchitectureTest}, which
 * permits {@code @Tag} / {@code @Test} meta-annotations on the
 * package's own annotation types):
 *
 * <ul>
 *   <li>statistics-stays-out (no Apache Commons leakage), and</li>
 *   <li>retired-type-name protections.</li>
 * </ul>
 */
@DisplayName("api package Architecture Rules")
class PUnitApiArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("org.mavai.punit.api");
    }

    @Test
    @DisplayName("api classes must not depend on Apache Commons (statistics stays out of the API)")
    void mustNotDependOnApacheCommons() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("org.mavai.punit.api..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.apache.commons..")
                .because("commons-statistics belongs in the engine layer; keeping it off the "
                        + "author-facing classpath stops a transitive dependency leaking to "
                        + "downstream consumers");
        rule.check(classes);
    }

    @Test
    @DisplayName("retired Stage-3 type names must not be re-introduced under typed..")
    void mustNotReintroduceRetiredTypes() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("org.mavai.punit.api..")
                .should().haveSimpleNameStartingWith("VerdictDimension")
                .orShould().haveSimpleNameStartingWith("LatencyVerdict")
                .orShould().haveSimpleNameStartingWith("BernoulliFunctionalVerdict")
                .because("the Stage-3 two-dimensional verdict apparatus retired in Stage 3.5; "
                        + "criterion roles + Verdict.compose replace it, and the names should not return");
        rule.check(classes);
    }

    @Test
    @DisplayName("the four specs only expose verb-form static factories — no builder() entry remains")
    void specsExposeOnlyVerbFormFactories() {
        ArchRule rule = noMethods()
                .that().areDeclaredInClassesThat()
                .haveFullyQualifiedName("org.mavai.punit.api.spec.Experiment")
                .or().areDeclaredInClassesThat()
                .haveFullyQualifiedName("org.mavai.punit.api.spec.Experiment")
                .or().areDeclaredInClassesThat()
                .haveFullyQualifiedName("org.mavai.punit.api.spec.Experiment")
                .or().areDeclaredInClassesThat()
                .haveFullyQualifiedName("org.mavai.punit.api.spec.ProbabilisticTest")
                .should().haveName("builder")
                .because("the compositional refactor replaces .builder() with the verb-form factories "
                        + "measuring / exploring / optimizing / testing — re-introducing builder() "
                        + "splits the entry surface and dilutes the production/consumption seam");
    rule.check(classes);
    }

    @Test
    @DisplayName("each spec exposes its verb-form factory")
    void specsHaveVerbFormFactory() {
        verbFormFactory("Experiment", "measuring");
        verbFormFactory("Experiment", "exploring");
        verbFormFactory("Experiment", "optimizing");
        verbFormFactory("ProbabilisticTest", "testing");
    }

    private static void verbFormFactory(String specSimpleName, String verb) {
        ArchRule rule = methods()
                .that().areDeclaredInClassesThat()
                .haveFullyQualifiedName("org.mavai.punit.api.spec." + specSimpleName)
                .and().haveName(verb)
                .should().beStatic()
                .andShould().bePublic()
                .because(specSimpleName + " is composed via " + verb + "(...) — "
                        + "the static factory is the spec's only entry point");
        rule.check(classes);
    }
}
