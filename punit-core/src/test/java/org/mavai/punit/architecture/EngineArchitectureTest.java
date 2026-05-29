package org.mavai.punit.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Architecture rules for the engine under
 * {@code org.mavai.punit.internal.engine}.
 *
 * <p>The core guarantee: the engine does not branch on spec subtype.
 * It reaches flavour-specific behaviour only through the strategy
 * methods on {@code org.mavai.punit.api.spec.Spec}.
 */
@DisplayName("engine architecture rules")
class EngineArchitectureTest {

    private static JavaClasses engineClasses;

    @BeforeAll
    static void importClasses() {
        engineClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("org.mavai.punit.internal.engine");
    }

    @Test
    @DisplayName("engine dispatcher must not import concrete Spec subtypes")
    void engineMustNotImportConcreteSpecs() {
        // Scope is the dispatcher itself — classes directly in
        // org.mavai.punit.internal.engine (Engine.java, BudgetTracker.java,
        // SerialSampleExecutor.java, …) — not the subpackages that
        // host genuinely Experiment-aware machinery (engine.criteria
        // holds PassRate, which references Experiment via
        // its empiricalFrom(Supplier<Experiment>) pinning API; that's
        // value-shaped reference, not subtype-discrimination).
        ArchRule rule = noClasses()
                .that().resideInAPackage("org.mavai.punit.internal.engine")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName(
                        "org.mavai.punit.api.spec.Experiment")
                .orShould().dependOnClassesThat()
                .haveFullyQualifiedName(
                        "org.mavai.punit.api.spec.ProbabilisticTest")
                .because("engine must dispatch through the Spec strategy interface, "
                        + "never instanceof / switch on subtype");
        rule.check(engineClasses);
    }

    @Test
    @DisplayName("engine must not depend on JUnit")
    void engineMustNotDependOnJUnit() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("org.mavai.punit.internal.engine..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.junit..");
        rule.check(engineClasses);
    }
}
