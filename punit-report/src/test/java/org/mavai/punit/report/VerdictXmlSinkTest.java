package org.mavai.punit.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
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

@DisplayName("VerdictXmlSink")
class VerdictXmlSinkTest {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("accept")
    class Accept {

        @Test
        @DisplayName("writes XML file to configured directory")
        void writesXmlFile() {
            ReportConfiguration config = ReportConfiguration.of(tempDir, true);
            VerdictXmlSink sink = new VerdictXmlSink(config);

            sink.accept(sampleVerdict());

            Path expected = tempDir.resolve("com.example.MyTest.shouldPass.xml");
            assertThat(expected).exists();
            assertThat(expected).isRegularFile();
        }

        @Test
        @DisplayName("written XML contains valid content")
        void writtenXmlContainsValidContent() throws IOException {
            ReportConfiguration config = ReportConfiguration.of(tempDir, true);
            VerdictXmlSink sink = new VerdictXmlSink(config);

            sink.accept(sampleVerdict());

            Path expected = tempDir.resolve("com.example.MyTest.shouldPass.xml");
            String content = Files.readString(expected);
            assertThat(content).contains("verdict-record");
            assertThat(content).contains("com.example.MyTest");
            assertThat(content).contains("shouldPass");
        }

        @Test
        @DisplayName("does not write when disabled")
        void doesNotWriteWhenDisabled() {
            ReportConfiguration config = ReportConfiguration.of(tempDir, false);
            VerdictXmlSink sink = new VerdictXmlSink(config);

            sink.accept(sampleVerdict());

            Path expected = tempDir.resolve("com.example.MyTest.shouldPass.xml");
            assertThat(expected).doesNotExist();
        }

        @Test
        @DisplayName("creates output directory if it does not exist")
        void createsOutputDirectory() {
            Path nested = tempDir.resolve("deep/nested/dir");
            ReportConfiguration config = ReportConfiguration.of(nested, true);
            VerdictXmlSink sink = new VerdictXmlSink(config);

            sink.accept(sampleVerdict());

            Path expected = nested.resolve("com.example.MyTest.shouldPass.xml");
            assertThat(expected).exists();
        }
    }

    // ── Helper ───────────────────────────────────────────────────────────

    private ProbabilisticTestVerdict sampleVerdict() {
        return new ProbabilisticTestVerdict(
                "v:test01",
                Instant.parse("2026-03-11T14:30:00Z"),
                new TestIdentity("com.example.MyTest", "shouldPass", Optional.empty()),
                new ExecutionSummary(100, 100, 95, 5, 0.9, 0.95, 150,
                        Optional.empty(), TestIntent.VERIFICATION, 0.95, ServiceContractAttributes.DEFAULT),
                Optional.empty(),
                Optional.empty(),
                new StatisticalAnalysis(0.95, 0.0218, 0.8948,
                        Optional.of(2.29), Optional.of(0.011),
                        Optional.empty(), Optional.empty(), List.of()),
                CovariateStatus.allAligned(),
                new CostSummary(0, 0, 0, TokenMode.NONE, Optional.empty(), Optional.empty()),
                Optional.empty(),
                Optional.empty(),
                new Termination(TerminationReason.COMPLETED, Optional.empty()),
                Map.of(),
                true,
                PUnitVerdict.PASS,
                "0.9500 >= 0.9000"
        );
    }
}
