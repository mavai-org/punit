package org.mavai.punit.report.explore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Public API entry point for the Explore-experiment comparison report.
 *
 * <p>Walks an explorations root directory laid out as
 * {@code <root>/<service>/*.yaml} — one sub-directory per service
 * contract, one YAML file per evaluated variant — and writes a single,
 * self-contained {@code index.html} comparing the variants of each
 * service against one another.
 *
 * <p>The method shape mirrors the test report's
 * {@code ReportGenerator.generate(Path, Path)} so the Gradle plugin can
 * invoke it reflectively the same way. The report re-presents values
 * already in the YAML; it computes no statistics the exploration producer
 * did not.
 */
// javai-ref: JVI-ZFQ8WNQ — do not remove (resolves in mavai-orchestrator)
public final class ReportGenerator {

    private final YamlReader reader = new YamlReader();

    /**
     * Reads exploration YAML under {@code explorationsRoot} and writes
     * {@code index.html} to {@code htmlDir}.
     *
     * <p>An absent or empty root is not an error: a valid page stating
     * that no explorations were found is written instead, mirroring how
     * the test-report task tolerates an empty report directory.
     *
     * @param explorationsRoot directory containing one sub-directory per service
     * @param htmlDir          output directory for the HTML report
     */
    public void generate(Path explorationsRoot, Path htmlDir) {
        List<ServiceComparison> services = readServices(explorationsRoot);
        String html = HtmlWriter.generate(services);
        writeReport(htmlDir, html);
    }

    private List<ServiceComparison> readServices(Path explorationsRoot) {
        if (explorationsRoot == null || !Files.isDirectory(explorationsRoot)) {
            return List.of();
        }
        Map<String, List<Variant>> byService = new LinkedHashMap<>();
        try (Stream<Path> serviceDirs = Files.list(explorationsRoot)) {
            serviceDirs.filter(Files::isDirectory)
                    .sorted()
                    .forEach(dir -> readVariants(dir).forEach(parsed ->
                            byService.computeIfAbsent(parsed.service(), k -> new ArrayList<>())
                                    .add(parsed.variant())));
        } catch (IOException e) {
            throw new ReadException("Failed to scan explorations directory: " + explorationsRoot, e);
        }
        return byService.entrySet().stream()
                .map(e -> new ServiceComparison(e.getKey(), labelVariants(e.getValue())))
                .collect(Collectors.toList());
    }

    private List<YamlReader.ParsedVariant> readVariants(Path serviceDir) {
        List<YamlReader.ParsedVariant> parsed = new ArrayList<>();
        try (Stream<Path> files = Files.list(serviceDir)) {
            files.filter(p -> p.toString().endsWith(".yaml"))
                    .sorted()
                    .forEach(path -> {
                        try (InputStream in = Files.newInputStream(path)) {
                            reader.read(in)
                                    .filter(v -> v.service() != null)
                                    .ifPresent(parsed::add);
                        } catch (IOException e) {
                            throw new ReadException("Failed to read: " + path, e);
                        }
                    });
        } catch (IOException e) {
            throw new ReadException("Failed to scan service directory: " + serviceDir, e);
        }
        return parsed;
    }

    /**
     * Labels each variant by the factors that actually differ across the
     * set. When every variant shares the same factor map (a degenerate
     * single-variant or all-identical set), each variant falls back to a
     * label built from its full factor map.
     */
    private static List<Variant> labelVariants(List<Variant> variants) {
        Set<String> discriminating = discriminatingFactors(variants);
        List<Variant> labelled = new ArrayList<>(variants.size());
        for (Variant variant : variants) {
            Set<String> keys = discriminating.isEmpty() ? variant.factors().keySet() : discriminating;
            labelled.add(variant.withLabel(labelFrom(variant.factors(), keys)));
        }
        return labelled;
    }

    /** The union of factor keys whose value is not identical across all variants. */
    private static Set<String> discriminatingFactors(List<Variant> variants) {
        Set<String> allKeys = new LinkedHashSet<>();
        variants.forEach(v -> allKeys.addAll(v.factors().keySet()));
        Set<String> discriminating = new LinkedHashSet<>();
        for (String key : allKeys) {
            Object first = variants.get(0).factors().get(key);
            boolean varies = variants.stream()
                    .anyMatch(v -> !Objects.equals(first, v.factors().get(key)));
            if (varies) {
                discriminating.add(key);
            }
        }
        return discriminating;
    }

    private static String labelFrom(Map<String, Object> factors, Set<String> keys) {
        String label = keys.stream()
                .filter(factors::containsKey)
                .map(key -> String.valueOf(factors.get(key)))
                .collect(Collectors.joining(", "));
        return label.isBlank() ? "(default)" : label;
    }

    private void writeReport(Path htmlDir, String html) {
        try {
            Files.createDirectories(htmlDir);
            Files.writeString(htmlDir.resolve("index.html"), html);
        } catch (IOException e) {
            throw new ReadException("Failed to write exploration report", e);
        }
    }
}
