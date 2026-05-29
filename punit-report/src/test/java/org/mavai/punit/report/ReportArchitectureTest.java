package org.mavai.punit.report;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ReportArchitectureTest")
class ReportArchitectureTest {

    private final JavaClasses reportClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("org.mavai.punit.report");

    @Test
    @DisplayName("punit-report has no JUnit dependencies")
    void noJunitDependencies() {
        noClasses()
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.junit..")
                .check(reportClasses);
    }
}
