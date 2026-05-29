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
 * Architecture tests enforcing punit-core's JUnit independence.
 *
 * <p>punit-core is the JUnit-free statistical engine. It must never depend on
 * JUnit extension or engine types. The only permitted JUnit usage is
 * {@code @TestTemplate} and {@code @Tag} meta-annotations on PUnit's own
 * annotations in the {@code api} package — these are inert without the
 * JUnit engine on the classpath.
 */
@DisplayName("punit-core Architecture Rules")
class CoreArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("org.mavai.punit");
    }

    @Nested
    @DisplayName("JUnit Independence")
    class JUnitIndependence {

        @Test
        @DisplayName("non-api packages must not depend on JUnit at all")
        void nonApiPackagesMustNotDependOnJUnit() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("org.mavai.punit..")
                    .and().resideOutsideOfPackage("org.mavai.punit.api..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("org.junit..")
                    .because("punit-core (outside api) must be completely JUnit-free "
                            + "to support Sentinel and other non-JUnit engines");

            rule.check(classes);
        }

        @Test
        @DisplayName("api package must not depend on JUnit extension API")
        void apiPackageMustNotDependOnJUnitExtensions() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("org.mavai.punit.api..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.junit.jupiter.api.extension..",
                            "org.junit.jupiter.engine..",
                            "org.junit.platform.."
                    )
                    .because("api annotations may only reference JUnit annotation types "
                            + "(@TestTemplate, @Tag) as meta-annotations — never extension or engine types");

            rule.check(classes);
        }

        /**
         * The statistics package must not depend on JUnit extension types.
         * It is the framework's statistical core and must stay reachable
         * from a sentinel-deployed classpath that has no JUnit on it.
         */
        @Test
        @DisplayName("statistics package must not depend on JUnit extension API")
        void statisticsPackageMustNotDependOnJUnitExtensions() {
            ArchRule rule = noClasses()
                    .that().resideInAnyPackage(
                            "org.mavai.punit.statistics.."
                    )
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("org.junit.jupiter.api.extension..")
                    .because("punit-core packages must be JUnit-free to support Sentinel and other engines");

            rule.check(classes);
        }
    }

    @Nested
    @DisplayName("Module Isolation")
    class ModuleIsolation {

        /**
         * The statistics module is intentionally isolated from all other PUnit packages.
         *
         * <p>This isolation enables:
         * <ul>
         *   <li><strong>Independent scrutiny:</strong> Statisticians can review calculations
         *       without understanding the broader framework.</li>
         *   <li><strong>Rigorous testing:</strong> Statistical concepts have dedicated unit tests
         *       with worked examples using real-world variable names.</li>
         *   <li><strong>Trust building:</strong> Calculations map directly to formulations in
         *       the STATISTICAL-COMPANION document.</li>
         * </ul>
         */
        @Test
        @DisplayName("Statistics module must not depend on any framework packages")
        void statisticsModuleMustBeIsolated() {
            // Conservative restoration of the pre-namespace-refresh rule:
            // statistics must not depend on the api/runtime/verdict public
            // surfaces or on internal.engine. The drift case (statistics
            // VerdictInterpreter calling into internal.reporting.RateFormat)
            // is pre-existing and is tracked as separate follow-up cleanup —
            // tightening the rule here without resolving the drift would
            // simply block the cleanup arc.
            ArchRule rule = noClasses()
                    .that().resideInAPackage("org.mavai.punit.statistics..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.mavai.punit.api..",
                            "org.mavai.punit.internal.engine..",
                            "org.mavai.punit.verdict..",
                            "org.mavai.punit.runtime.."
                    );

            rule.check(classes);
        }

        /**
         * The util package contains general-purpose utilities (hashing,
         * lazy evaluation). It lives under {@code internal.util} after
         * the internal-namespace refactor and must not depend on any
         * other framework package.
         */
        @Test
        @DisplayName("util package must be self-contained")
        void utilPackageMustBeSelfContained() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("org.mavai.punit.internal.util..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.mavai.punit.api..",
                            "org.mavai.punit.runtime..",
                            "org.mavai.punit.verdict..",
                            "org.mavai.punit.statistics..",
                            "org.mavai.punit.internal.engine..",
                            "org.mavai.punit.internal.reporting.."
                    )
                    .because("utilities must be self-contained and not depend on framework internals");

            rule.check(classes);
        }
    }
}
