package org.mavai.punit.report.optimize;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

@DisplayName("Optimization comparison report generator")
class ReportGeneratorTest {

    private final ReportGenerator generator = new ReportGenerator();

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("generate")
    class Generate {

        @Test
        @DisplayName("names the service, experiment, and objective and lists iterations in run order")
        void listsIterationsInRunOrder() throws IOException {
            Path service = optimizationsRoot().resolve("shopping-basket");
            writeRun(service, "prompt-tune-v1.yaml", run("shopping-basket", "prompt-tune-v1", "MAXIMIZE",
                    List.of(
                            iter(0, "p0", 0.0, 0, 5, "COMPLETED", new long[]{}),
                            iter(1, "p1", 1.0, 5, 0, "COMPLETED", new long[]{1200, 1300, 1400})),
                    convergence(2, 1, 1.0, "MAX_ITERATIONS")));

            String html = generate();

            assertThat(html).contains("shopping-basket");
            assertThat(html).contains("prompt-tune-v1");
            assertThat(html).contains("MAXIMIZE");
            // Rows follow run order regardless of score: iteration 0 before iteration 1.
            assertThat(html.indexOf("<summary>iteration 0"))
                    .isLessThan(html.indexOf("<summary>iteration 1"));
            // The score rank is carried in a column, not by row order: under
            // MAXIMIZE the higher-scoring iteration 1 is rank 1, so the rank-2
            // cell (iteration 0's row, first chronologically) precedes rank 1.
            assertThat(html.indexOf("<td class=\"rank\">2</td>"))
                    .isLessThan(html.indexOf("<td class=\"rank\">1</td>"));
        }

        @Test
        @DisplayName("assigns rank 1 to the lowest score under a MINIMIZE objective")
        void minimizeRanksAscending() throws IOException {
            Path service = optimizationsRoot().resolve("svc");
            writeRun(service, "min.yaml", run("svc", "min-exp", "MINIMIZE",
                    List.of(
                            iter(0, "p0", 0.2, 5, 0, "COMPLETED", new long[]{1000, 1100, 1200}),
                            iter(1, "p1", 0.8, 5, 0, "COMPLETED", new long[]{1000, 1100, 1200})),
                    convergence(2, 0, 0.2, "NO_IMPROVEMENT")));

            String html = generate();

            // Rows are chronological (iteration 0 first); MINIMIZE makes the
            // lower score (iteration 0) rank 1, so rank cell "1" precedes "2".
            assertThat(html.indexOf("<td class=\"rank\">1</td>"))
                    .isLessThan(html.indexOf("<td class=\"rank\">2</td>"));
        }

        @Test
        @DisplayName("reveals each iteration's factor bundle in a collapsed details")
        void revealsFactors() throws IOException {
            Path service = optimizationsRoot().resolve("svc");
            writeRun(service, "exp.yaml", run("svc", "exp", "MAXIMIZE",
                    List.of(iter(0, "the-system-prompt", 1.0, 5, 0, "COMPLETED", new long[]{1000, 1100, 1200})),
                    convergence(1, 0, 1.0, "MAX_ITERATIONS")));

            String html = generate();

            assertThat(html).contains("<details>");
            assertThat(html).contains("systemPrompt");
            assertThat(html).contains("the-system-prompt");
            assertThat(html).contains("model");
        }

        @Test
        @DisplayName("marks the convergence-chosen iteration as best")
        void marksBestIteration() throws IOException {
            Path service = optimizationsRoot().resolve("svc");
            writeRun(service, "exp.yaml", run("svc", "exp", "MAXIMIZE",
                    List.of(
                            iter(0, "p0", 0.0, 0, 5, "COMPLETED", new long[]{}),
                            iter(1, "p1", 1.0, 5, 0, "COMPLETED", new long[]{1200, 1300, 1400})),
                    convergence(2, 1, 1.0, "MAX_ITERATIONS")));

            String html = generate();

            assertThat(html).contains("badge ok\">best");
        }

        @Test
        @DisplayName("flags iterations with near-equal scores as too close to call")
        void scoreNearTieFlagged() throws IOException {
            Path service = optimizationsRoot().resolve("svc");
            writeRun(service, "exp.yaml", run("svc", "exp", "MAXIMIZE",
                    List.of(
                            iter(1, "p1", 1.0, 5, 0, "COMPLETED", new long[]{1200, 1250, 1300}),
                            iter(2, "p2", 1.0, 5, 0, "COMPLETED", new long[]{1400, 1450, 1500})),
                    convergence(2, 1, 1.0, "MAX_ITERATIONS")));

            String html = generate();

            assertThat(html).contains("class=\"tie-mark\"");
            assertThat(html).contains("too close to call");
        }

        @Test
        @DisplayName("does not flag a near-tie when scores differ materially")
        void noNearTieWhenScoresDiffer() throws IOException {
            Path service = optimizationsRoot().resolve("svc");
            writeRun(service, "exp.yaml", run("svc", "exp", "MAXIMIZE",
                    List.of(
                            iter(0, "p0", 0.0, 0, 5, "COMPLETED", new long[]{}),
                            iter(1, "p1", 1.0, 5, 0, "COMPLETED", new long[]{1200, 1300, 1400})),
                    convergence(2, 1, 1.0, "MAX_ITERATIONS")));

            String html = generate();

            assertThat(html).doesNotContain("class=\"tie-mark\"");
        }

        @Test
        @DisplayName("renders percentiles as dashes for an iteration with no passing samples")
        void dashesForNoLatencyIteration() throws IOException {
            Path service = optimizationsRoot().resolve("svc");
            writeRun(service, "exp.yaml", run("svc", "exp", "MAXIMIZE",
                    List.of(iter(0, "p0", 0.0, 0, 5, "COMPLETED", new long[]{})),
                    convergence(1, 0, 0.0, "MAX_ITERATIONS")));

            String html = generate();

            assertThat(html).contains("<span class=\"muted\">-</span>");
            assertThat(html).contains("0/5");
        }

        @Test
        @DisplayName("flags a non-completed termination reason")
        void flagsNonCompletedTermination() throws IOException {
            Path service = optimizationsRoot().resolve("svc");
            writeRun(service, "exp.yaml", run("svc", "exp", "MAXIMIZE",
                    List.of(iter(0, "p0", 0.8, 4, 1, "TIME_BUDGET", new long[]{1000, 1100})),
                    convergence(1, 0, 0.8, "TIME_BUDGET")));

            String html = generate();

            assertThat(html).contains("badge warn");
            assertThat(html).contains("TIME_BUDGET");
        }

        @Test
        @DisplayName("builds a per-criterion matrix over the union of criteria")
        void perCriterionMatrix() throws IOException {
            Path service = optimizationsRoot().resolve("svc");
            Map<String, Object> i0 = iter(0, "p0", 0.0, 0, 5, "COMPLETED", new long[]{});
            putCriteria(i0, Map.of("valid-json", crit(0.0, 0, 5)));
            Map<String, Object> i1 = iter(1, "p1", 1.0, 5, 0, "COMPLETED", new long[]{1200, 1300, 1400});
            putCriteria(i1, Map.of(
                    "valid-json", crit(1.0, 5, 0),
                    "response-not-empty", crit(1.0, 5, 0)));
            writeRun(service, "exp.yaml", run("svc", "exp", "MAXIMIZE",
                    List.of(i0, i1), convergence(2, 1, 1.0, "MAX_ITERATIONS")));

            String html = generate();

            assertThat(html).contains("valid-json");
            assertThat(html).contains("response-not-empty");
            assertThat(html).contains("n/a");
        }

        @Test
        @DisplayName("renders a score trajectory")
        void scoreTrajectory() throws IOException {
            Path service = optimizationsRoot().resolve("svc");
            writeRun(service, "exp.yaml", run("svc", "exp", "MAXIMIZE",
                    List.of(
                            iter(0, "p0", 0.0, 0, 5, "COMPLETED", new long[]{}),
                            iter(1, "p1", 1.0, 5, 0, "COMPLETED", new long[]{1200, 1300, 1400})),
                    convergence(2, 1, 1.0, "MAX_ITERATIONS")));

            String html = generate();

            assertThat(html).contains("trajectory-svg");
            assertThat(html).contains("<polyline");
        }

        @Test
        @DisplayName("renders one section per optimize file")
        void sectionPerRun() throws IOException {
            Path service = optimizationsRoot().resolve("shopping-basket");
            writeRun(service, "a.yaml", run("shopping-basket", "exp-a", "MAXIMIZE",
                    List.of(iter(0, "p0", 1.0, 5, 0, "COMPLETED", new long[]{1000})),
                    convergence(1, 0, 1.0, "MAX_ITERATIONS")));
            writeRun(service, "b.yaml", run("shopping-basket", "exp-b", "MAXIMIZE",
                    List.of(iter(0, "p0", 1.0, 5, 0, "COMPLETED", new long[]{1000})),
                    convergence(1, 0, 1.0, "MAX_ITERATIONS")));

            String html = generate();

            assertThat(html).contains("exp-a");
            assertThat(html).contains("exp-b");
            assertThat(html).contains("shopping-basket &middot; exp-a");
            assertThat(html).contains("shopping-basket &middot; exp-b");
        }

        @Test
        @DisplayName("writes a no-optimizations page for an absent root")
        void emptyPageForAbsentRoot() throws IOException {
            generator.generate(tempDir.resolve("nope"), tempDir.resolve("html"));
            String html = Files.readString(tempDir.resolve("html").resolve("index.html"));
            assertThat(html).contains("No optimizations found");
        }

        @Test
        @DisplayName("emits a single self-contained file with no external requests")
        void selfContained() throws IOException {
            Path service = optimizationsRoot().resolve("svc");
            writeRun(service, "exp.yaml", run("svc", "exp", "MAXIMIZE",
                    List.of(iter(0, "p0", 1.0, 5, 0, "COMPLETED", new long[]{1000, 1100})),
                    convergence(1, 0, 1.0, "MAX_ITERATIONS")));

            String html = generate();

            assertThat(html).contains("<style>");
            assertThat(html).doesNotContain("<script");
            assertThat(html).doesNotContain("src=");
            assertThat(html).doesNotContain("http://");
            assertThat(html).doesNotContain("https://");
        }

        @Test
        @DisplayName("reuses the test report's base stylesheet for visual parity")
        void reusesBaseStylesheet() throws IOException {
            Path service = optimizationsRoot().resolve("svc");
            writeRun(service, "exp.yaml", run("svc", "exp", "MAXIMIZE",
                    List.of(iter(0, "p0", 1.0, 5, 0, "COMPLETED", new long[]{1000})),
                    convergence(1, 0, 1.0, "MAX_ITERATIONS")));

            String html = generate();

            assertThat(html).contains("--pass-color: #2e7d32");
            assertThat(html).contains("--inconclusive-color: #6a1b9a");
        }
    }

    // ── Fixture helpers ──────────────────────────────────────────────────────

    private Path optimizationsRoot() {
        return tempDir.resolve("optimizations");
    }

    private String generate() throws IOException {
        Path htmlDir = tempDir.resolve("html");
        generator.generate(optimizationsRoot(), htmlDir);
        Path indexHtml = htmlDir.resolve("index.html");
        assertThat(indexHtml).exists();
        return Files.readString(indexHtml);
    }

    private void writeRun(Path serviceDir, String filename, Map<String, Object> root) throws IOException {
        Files.createDirectories(serviceDir);
        Files.writeString(serviceDir.resolve(filename), new Yaml().dump(root));
    }

    private static Map<String, Object> run(String service, String experimentId, String objective,
            List<Map<String, Object>> iterations, Map<String, Object> convergence) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", "punit-spec-1");
        root.put("useCaseId", service);
        root.put("experimentId", experimentId);
        root.put("objective", objective);
        root.put("iterations", iterations);
        root.put("convergence", convergence);
        return root;
    }

    private static Map<String, Object> iter(int index, String systemPrompt, double score,
            int successes, int failures, String termination, long[] latencies) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("iteration", index);

        Map<String, Object> factors = new LinkedHashMap<>();
        factors.put("model", "gpt-4o-mini");
        factors.put("temperature", 0.3);
        factors.put("systemPrompt", systemPrompt);
        entry.put("factors", factors);

        entry.put("score", score);

        Map<String, Object> execution = new LinkedHashMap<>();
        execution.put("samplesExecuted", successes + failures);
        execution.put("terminationReason", termination);
        entry.put("execution", execution);

        Map<String, Object> statistics = new LinkedHashMap<>();
        int total = successes + failures;
        statistics.put("observed", total == 0 ? 0.0 : (double) successes / total);
        statistics.put("successes", successes);
        statistics.put("failures", failures);
        entry.put("statistics", statistics);

        Map<String, Object> cost = new LinkedHashMap<>();
        cost.put("totalTimeMs", 1500L * Math.max(1, total));
        cost.put("avgTimePerSampleMs", 1500L);
        entry.put("cost", cost);

        if (latencies.length > 0) {
            Map<String, Object> latency = new LinkedHashMap<>();
            latency.put("basis", "passing-samples");
            latency.put("contributingSamples", latencies.length);
            latency.put("totalSamples", total);
            List<Long> sorted = new ArrayList<>();
            for (long l : latencies) {
                sorted.add(l);
            }
            latency.put("sortedPassingLatenciesMs", sorted);
            entry.put("latency", latency);
        }
        return entry;
    }

    private static Map<String, Object> convergence(int total, int bestIteration,
            double bestScore, String termination) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("totalIterations", total);
        c.put("bestIteration", bestIteration);
        c.put("bestScore", bestScore);
        c.put("terminationReason", termination);
        return c;
    }

    @SuppressWarnings("unchecked")
    private static void putCriteria(Map<String, Object> iteration, Map<String, Map<String, Object>> criteria) {
        Map<String, Object> statistics = (Map<String, Object>) iteration.get("statistics");
        statistics.put("criteria", new LinkedHashMap<>(criteria));
    }

    private static Map<String, Object> crit(double passRate, int pass, int fail) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("observedPassRate", passRate);
        row.put("pass", pass);
        row.put("fail", fail);
        row.put("conditionFail", 0);
        row.put("transformFail", 0);
        return row;
    }
}
