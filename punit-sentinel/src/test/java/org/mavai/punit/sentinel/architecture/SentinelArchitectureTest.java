package org.mavai.punit.sentinel.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Architecture tests enforcing punit-sentinel's JUnit independence.
 *
 * <p>punit-sentinel is a standalone runtime that must never depend on JUnit.
 * It depends only on punit-core (which is itself JUnit-free outside of
 * inert meta-annotations in the {@code api} package).
 *
 * <p>This test scans both {@code org.mavai.punit.sentinel} (sentinel's own classes)
 * and {@code org.mavai.punit} (transitive punit-core classes on the classpath)
 * to ensure the entire dependency graph is JUnit-free.
 */
@DisplayName("punit-sentinel Architecture Rules")
class SentinelArchitectureTest {

    private static JavaClasses sentinelClasses;

    @BeforeAll
    static void importClasses() {
        sentinelClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("org.mavai.punit.sentinel");
    }

    @Test
    @DisplayName("sentinel classes must not depend on JUnit")
    void sentinelMustNotDependOnJUnit() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("org.mavai.punit.sentinel..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.junit..")
                .because("punit-sentinel is a standalone runtime — "
                        + "JUnit must never appear on its dependency graph");

        rule.check(sentinelClasses);
    }
}
