package org.mavai.punit.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.mavai.punit.api.TestIntent;
import org.mavai.punit.verdict.TokenMode;
import org.mavai.punit.verdict.TerminationReason;
import org.mavai.punit.api.ServiceContractAttributes;
import org.mavai.punit.verdict.ProbabilisticTestVerdict;
import org.mavai.punit.verdict.ProbabilisticTestVerdict.*;
import org.mavai.punit.verdict.PUnitVerdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("ReportGenerator")
class ReportGeneratorTest {

    private final ReportGenerator generator = new ReportGenerator();
    private final VerdictXmlWriter xmlWriter = new VerdictXmlWriter();

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("generate")
    class Generate {

        @Test
        @DisplayName("produces index.html from XML verdict files")
        void producesIndexHtml() throws Exception {
            Path xmlDir = tempDir.resolve("xml");
            Path htmlDir = tempDir.resolve("html");
            Files.createDirectories(xmlDir);

            writeXmlVerdict(xmlDir, passingVerdict());

            generator.generate(xmlDir, htmlDir);

            Path indexHtml = htmlDir.resolve("index.html");
            assertThat(indexHtml).exists();
            String html = Files.readString(indexHtml);
            assertThat(html).contains("PUnit Test Report");
            assertThat(html).contains("shouldPass");
        }

        @Test
        @DisplayName("handles multiple verdict files")
        void handlesMultipleVerdicts() throws Exception {
            Path xmlDir = tempDir.resolve("xml");
            Path htmlDir = tempDir.resolve("html");
            Files.createDirectories(xmlDir);

            writeXmlVerdict(xmlDir, passingVerdict());
            writeXmlVerdict(xmlDir, failingVerdict());

            generator.generate(xmlDir, htmlDir);

            String html = Files.readString(htmlDir.resolve("index.html"));
            assertThat(html).contains("shouldPass");
            assertThat(html).contains("shouldFail");
            assertThat(html).contains("Pass: 1");
            assertThat(html).contains("Fail: 1");
        }

        @Test
        @DisplayName("creates output directory if needed")
        void createsOutputDirectory() throws Exception {
            Path xmlDir = tempDir.resolve("xml");
            Path htmlDir = tempDir.resolve("nested/output/html");
            Files.createDirectories(xmlDir);

            writeXmlVerdict(xmlDir, passingVerdict());

            generator.generate(xmlDir, htmlDir);

            assertThat(htmlDir.resolve("index.html")).exists();
        }

        @Test
        @DisplayName("handles empty XML directory")
        void handlesEmptyXmlDir() throws Exception {
            Path xmlDir = tempDir.resolve("xml");
            Path htmlDir = tempDir.resolve("html");
            Files.createDirectories(xmlDir);

            generator.generate(xmlDir, htmlDir);

            String html = Files.readString(htmlDir.resolve("index.html"));
            assertThat(html).contains("PUnit Test Report");
            assertThat(html).contains("Total: 0");
        }

        @Test
        @DisplayName("handles non-existent XML directory")
        void handlesNonExistentXmlDir() throws Exception {
            Path xmlDir = tempDir.resolve("does-not-exist");
            Path htmlDir = tempDir.resolve("html");

            generator.generate(xmlDir, htmlDir);

            String html = Files.readString(htmlDir.resolve("index.html"));
            assertThat(html).contains("Total: 0");
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private void writeXmlVerdict(Path xmlDir, ProbabilisticTestVerdict verdict) throws Exception {
        String filename = verdict.identity().className() + "." + verdict.identity().methodName() + ".xml";
        Path xmlFile = xmlDir.resolve(filename);
        try (OutputStream out = Files.newOutputStream(xmlFile)) {
            xmlWriter.write(verdict, out);
        }
    }

    private ProbabilisticTestVerdict passingVerdict() {
        return new ProbabilisticTestVerdict(
                "v:pass01",
                Instant.parse("2026-03-11T14:30:00Z"),
                new TestIdentity("com.example.MyTest", "shouldPass", Optional.empty()),
                new ExecutionSummary(100, 100, 95, 5, 0.9, 0.95, 150,
                        Optional.empty(), TestIntent.VERIFICATION, 0.95, ServiceContractAttributes.DEFAULT),
                Optional.empty(), Optional.empty(),
                new StatisticalAnalysis(0.95, 0.0218, 0.8948,
                        Optional.of(2.29), Optional.of(0.011),
                        Optional.empty(), Optional.empty(), List.of()),
                CovariateStatus.allAligned(),
                new CostSummary(0, 0, 0, TokenMode.NONE, Optional.empty(), Optional.empty()),
                Optional.empty(), Optional.empty(),
                new Termination(TerminationReason.COMPLETED, Optional.empty()),
                Map.of(), true, PUnitVerdict.PASS,
                "0.9500 >= 0.9000"
        );
    }

    private ProbabilisticTestVerdict failingVerdict() {
        return new ProbabilisticTestVerdict(
                "v:fail01",
                Instant.parse("2026-03-11T14:31:00Z"),
                new TestIdentity("com.example.MyTest", "shouldFail", Optional.empty()),
                new ExecutionSummary(100, 100, 80, 20, 0.9, 0.8, 200,
                        Optional.empty(), TestIntent.VERIFICATION, 0.95, ServiceContractAttributes.DEFAULT),
                Optional.empty(), Optional.empty(),
                new StatisticalAnalysis(0.95, 0.04, 0.72,
                        Optional.of(-2.5), Optional.of(0.006),
                        Optional.empty(), Optional.empty(), List.of()),
                CovariateStatus.allAligned(),
                new CostSummary(0, 0, 0, TokenMode.NONE, Optional.empty(), Optional.empty()),
                Optional.empty(), Optional.empty(),
                new Termination(TerminationReason.COMPLETED, Optional.empty()),
                Map.of(), false, PUnitVerdict.FAIL,
                "0.8000 < 0.9000"
        );
    }
}
