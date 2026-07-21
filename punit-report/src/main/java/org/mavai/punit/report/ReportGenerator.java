package org.mavai.punit.report;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.mavai.punit.verdict.ProbabilisticTestVerdict;

/**
 * Public API entry point for HTML report generation.
 *
 * <p>Reads XML verdict files from a directory, transforms them into
 * {@link ProbabilisticTestVerdict} instances, and writes a standalone
 * HTML report.
 */
// mavai-ref: JVI-PNR8C3F — do not remove (resolves in mavai-orchestrator)
public final class ReportGenerator {

    private final VerdictXmlReader reader = new VerdictXmlReader();

    /**
     * Reads XML verdicts from {@code xmlDir} and writes {@code index.html} to {@code htmlDir}.
     *
     * @param xmlDir directory containing {@code *.xml} verdict files
     * @param htmlDir output directory for the HTML report
     */
    public void generate(Path xmlDir, Path htmlDir) {
        List<ProbabilisticTestVerdict> verdicts = readVerdicts(xmlDir);
        String html = HtmlReportWriter.generate(verdicts);
        writeReport(htmlDir, html);
    }

    private List<ProbabilisticTestVerdict> readVerdicts(Path xmlDir) {
        if (!Files.isDirectory(xmlDir)) {
            return List.of();
        }
        List<ProbabilisticTestVerdict> verdicts = new ArrayList<>();
        try (Stream<Path> files = Files.list(xmlDir)) {
            files.filter(p -> p.toString().endsWith(".xml"))
                    .sorted()
                    .forEach(path -> {
                        try (InputStream in = Files.newInputStream(path)) {
                            verdicts.add(reader.read(in));
                        } catch (IOException e) {
                            throw new XmlReadException("Failed to read: " + path, e);
                        }
                    });
        } catch (IOException e) {
            throw new XmlReadException("Failed to scan XML directory: " + xmlDir, e);
        }
        return verdicts;
    }

    private void writeReport(Path htmlDir, String html) {
        try {
            Files.createDirectories(htmlDir);
            Files.writeString(htmlDir.resolve("index.html"), html);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write HTML report", e);
        }
    }
}
