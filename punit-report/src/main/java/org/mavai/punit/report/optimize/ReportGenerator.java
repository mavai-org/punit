package org.mavai.punit.report.optimize;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Public API entry point for the OPTIMIZE-experiment comparison report.
 *
 * <p>Walks an optimizations root directory laid out as
 * {@code <root>/<service>/<experimentId>.yaml} — one sub-directory per
 * service contract, one YAML file per optimize run — and writes a single,
 * self-contained {@code index.html} summarising each run: which iteration
 * scored best, how every iteration fared, and the convergence outcome.
 *
 * <p>The method shape mirrors the exploration report's
 * {@code ReportGenerator.generate(Path, Path)} so the Gradle plugin can
 * invoke it reflectively the same way. The report re-presents values
 * already in the YAML; it computes no statistics the optimize producer did
 * not.
 */
public final class ReportGenerator {

    private final YamlReader reader = new YamlReader();

    /**
     * Reads optimization YAML under {@code optimizationsRoot} and writes
     * {@code index.html} to {@code htmlDir}.
     *
     * <p>An absent or empty root is not an error: a valid page stating that
     * no optimizations were found is written instead.
     *
     * @param optimizationsRoot directory containing one sub-directory per service
     * @param htmlDir           output directory for the HTML report
     */
    public void generate(Path optimizationsRoot, Path htmlDir) {
        List<OptimizationRun> runs = readRuns(optimizationsRoot);
        String html = HtmlWriter.generate(runs);
        writeReport(htmlDir, html);
    }

    private List<OptimizationRun> readRuns(Path optimizationsRoot) {
        if (optimizationsRoot == null || !Files.isDirectory(optimizationsRoot)) {
            return List.of();
        }
        List<OptimizationRun> runs = new ArrayList<>();
        try (Stream<Path> serviceDirs = Files.list(optimizationsRoot)) {
            serviceDirs.filter(Files::isDirectory)
                    .sorted()
                    .forEach(dir -> runs.addAll(readRunsIn(dir)));
        } catch (IOException e) {
            throw new ReadException("Failed to scan optimizations directory: " + optimizationsRoot, e);
        }
        return runs;
    }

    private List<OptimizationRun> readRunsIn(Path serviceDir) {
        List<OptimizationRun> runs = new ArrayList<>();
        try (Stream<Path> files = Files.list(serviceDir)) {
            files.filter(p -> p.toString().endsWith(".yaml"))
                    .sorted()
                    .forEach(path -> {
                        try (InputStream in = Files.newInputStream(path)) {
                            reader.read(in)
                                    .filter(run -> run.service() != null)
                                    .ifPresent(runs::add);
                        } catch (IOException e) {
                            throw new ReadException("Failed to read: " + path, e);
                        }
                    });
        } catch (IOException e) {
            throw new ReadException("Failed to scan service directory: " + serviceDir, e);
        }
        return runs;
    }

    private void writeReport(Path htmlDir, String html) {
        try {
            Files.createDirectories(htmlDir);
            Files.writeString(htmlDir.resolve("index.html"), html);
        } catch (IOException e) {
            throw new ReadException("Failed to write optimization report", e);
        }
    }
}
