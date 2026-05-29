package org.mavai.punit.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Architecture tests enforcing abstraction-level discipline across
 * punit-core: evaluators / resolvers / deciders must not depend on
 * reporting; renderers must not perform statistical computation.
 */
@DisplayName("Abstraction-level Architecture Rules")
class AbstractionLevelArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("org.mavai.punit");
    }

    @Nested
    @DisplayName("Abstraction Level Enforcement")
    class AbstractionLevelEnforcement {

        /**
         * Evaluators, resolvers, and deciders are computation / decision
         * classes. They must not depend on reporting infrastructure
         * (formatting, rendering). Formatting belongs in dedicated
         * formatter / renderer / messages classes.
         */
        @Test
        @DisplayName("Evaluators, resolvers, and deciders must not depend on reporting")
        void evaluatorsResolversDecidersMustNotDependOnReporting() {
            ArchRule rule = noClasses()
                    .that().haveSimpleNameEndingWith("Evaluator")
                    .or().haveSimpleNameEndingWith("Resolver")
                    .or().haveSimpleNameEndingWith("Decider")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..reporting..")
                    .because("evaluators/resolvers/deciders compute decisions; "
                            + "formatting belongs in dedicated formatter/renderer classes");

            rule.check(classes);
        }

        /**
         * Renderers format pre-computed data for display. They must not
         * perform statistical computation themselves — that belongs in
         * estimators and derivers.
         */
        @Test
        @DisplayName("Renderers must not depend on statistical computation classes")
        void renderersMustNotDependOnStatisticalComputation() {
            ArchRule rule = noClasses()
                    .that().haveSimpleNameEndingWith("Renderer")
                    .should().dependOnClassesThat()
                    .haveSimpleNameEndingWith("Estimator")
                    .orShould().dependOnClassesThat()
                    .haveSimpleNameEndingWith("Deriver")
                    .because("renderers format pre-computed data; "
                            + "statistical computation belongs in estimator/deriver classes");

            rule.check(classes);
        }
    }
}
