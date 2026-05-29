package org.mavai.punit.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ReportConfiguration")
class ReportConfigurationTest {

    @Nested
    @DisplayName("of")
    class OfFactory {

        @Test
        @DisplayName("returns configured output directory")
        void returnsConfiguredDirectory() {
            ReportConfiguration config = ReportConfiguration.of(Path.of("/tmp/reports"), true);

            assertThat(config.outputDirectory()).isEqualTo(Path.of("/tmp/reports"));
        }

        @Test
        @DisplayName("returns enabled state")
        void returnsEnabledState() {
            ReportConfiguration enabled = ReportConfiguration.of(Path.of("/tmp"), true);
            ReportConfiguration disabled = ReportConfiguration.of(Path.of("/tmp"), false);

            assertThat(enabled.enabled()).isTrue();
            assertThat(disabled.enabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("xmlFilePath")
    class XmlFilePath {

        @Test
        @DisplayName("generates correct filename from class and method names")
        void generatesCorrectFilename() {
            ReportConfiguration config = ReportConfiguration.of(Path.of("build/reports/punit/xml"), true);

            Path path = config.xmlFilePath("com.example.MyTest", "shouldPass");

            assertThat(path).isEqualTo(Path.of("build/reports/punit/xml/com.example.MyTest.shouldPass.xml"));
        }
    }
}
