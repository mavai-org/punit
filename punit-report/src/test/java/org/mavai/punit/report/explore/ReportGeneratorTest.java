package org.mavai.punit.report.explore;

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

@DisplayName("Exploration comparison report generator")
class ReportGeneratorTest {

    private final ReportGenerator generator = new ReportGenerator();

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("generate")
    class Generate {

        @Test
        @DisplayName("produces a ranked leaderboard from a directory of variants")
        void producesRankedLeaderboard() throws IOException {
            Path root = tempDir.resolve("explorations");
            Path service = root.resolve("shopping-basket");
            // gpt-4o is faster (lower p50) than gpt-4o-mini; both all-pass.
            writeVariant(service, "fast.yaml", allPassVariant("shopping-basket", "gpt-4o",
                    new long[]{1000, 1100, 1200}, 1308));
            writeVariant(service, "slow.yaml", allPassVariant("shopping-basket", "gpt-4o-mini",
                    new long[]{2000, 2100, 2200}, 2100));

            String html = generate(root);

            assertThat(html).contains("shopping-basket");
            assertThat(html).contains("gpt-4o");
            assertThat(html).contains("gpt-4o-mini");
            // All-pass, so the faster median ranks first in the leaderboard.
            assertThat(html.indexOf(">gpt-4o</summary>"))
                    .isLessThan(html.indexOf(">gpt-4o-mini</summary>"));
        }

        @Test
        @DisplayName("renders one section per service directory")
        void rendersSectionPerService() throws IOException {
            Path root = tempDir.resolve("explorations");
            writeVariant(root.resolve("shopping-basket"), "a.yaml",
                    allPassVariant("shopping-basket", "gpt-4o", new long[]{1000}, 1000));
            writeVariant(root.resolve("payment-gateway"), "b.yaml",
                    allPassVariant("payment-gateway", "gpt-4o", new long[]{1500}, 1500));

            String html = generate(root);

            assertThat(html).contains("shopping-basket");
            assertThat(html).contains("payment-gateway");
            assertThat(html).containsOnlyOnce("<h2>shopping-basket</h2>");
            assertThat(html).containsOnlyOnce("<h2>payment-gateway</h2>");
        }

        @Test
        @DisplayName("ranks a partial-pass variant below an all-pass one and colours it")
        void partialPassRankedBelow() throws IOException {
            Path service = tempDir.resolve("explorations").resolve("svc");
            writeVariant(service, "perfect.yaml",
                    allPassVariant("svc", "good-model", new long[]{1000, 1100, 1200}, 1100));
            Map<String, Object> partial = variant("svc", factors("flaky-model"),
                    0.6, 3, 2, "COMPLETED", new long[]{900, 950, 1000}, 950);
            writeVariant(service, "partial.yaml", partial);

            String html = generate(tempDir.resolve("explorations"));

            // good-model (1.0) ranks above flaky-model (0.6) despite slower latency.
            assertThat(html.indexOf(">good-model</summary>"))
                    .isLessThan(html.indexOf(">flaky-model</summary>"));
            assertThat(html).contains("60.0%");
            assertThat(html).contains("punit-fail");
        }

        @Test
        @DisplayName("flags a non-completed termination reason")
        void flagsNonCompletedTermination() throws IOException {
            Path service = tempDir.resolve("explorations").resolve("svc");
            writeVariant(service, "budget.yaml", variant("svc", factors("gpt-4o"),
                    0.8, 4, 1, "BUDGET_EXHAUSTED", new long[]{1000, 1100}, 1050));

            String html = generate(tempDir.resolve("explorations"));

            assertThat(html).contains("badge warn");
            assertThat(html).contains("BUDGET_EXHAUSTED");
        }

        @Test
        @DisplayName("renders percentiles as dashes when too few passing samples")
        void dashesForUnavailablePercentiles() throws IOException {
            Path service = tempDir.resolve("explorations").resolve("svc");
            // 5 passing samples: p50 available, p95 (needs 20) / p99 (needs 100) are not.
            writeVariant(service, "few.yaml", allPassVariant("svc", "gpt-4o",
                    new long[]{1000, 1100, 1200, 1300, 1400}, 1200));

            String html = generate(tempDir.resolve("explorations"));

            // p50 is shown; the p95 column shows a dash.
            assertThat(html).contains("1200ms");
            assertThat(html).contains("<span class=\"muted\">-</span>");
        }

        @Test
        @DisplayName("computes p95 once there are enough passing samples")
        void computesP95WithEnoughSamples() throws IOException {
            Path service = tempDir.resolve("explorations").resolve("svc");
            long[] latencies = new long[20];
            for (int i = 0; i < 20; i++) {
                latencies[i] = 1000 + i * 100; // 1000..2900
            }
            writeVariant(service, "many.yaml", allPassVariant("svc", "gpt-4o", latencies, 1500));

            String html = generate(tempDir.resolve("explorations"));

            // nearest-rank p95 over 20 points: ceil(0.95*20)-1 = 18 -> 1000+18*100 = 2800.
            assertThat(html).contains("2800ms");
        }

        @Test
        @DisplayName("handles a no-samples variant as inconclusive without crashing")
        void handlesNoSamplesVariant() throws IOException {
            Path service = tempDir.resolve("explorations").resolve("svc");
            // 0/0: no samples, no latency block, no criteria.
            writeVariant(service, "empty.yaml", variant("svc", factors("dead-model"),
                    0.0, 0, 0, "COMPLETED", new long[]{}, 0));

            String html = generate(tempDir.resolve("explorations"));

            assertThat(html).contains("punit-inconclusive");
            assertThat(html).contains("0/0");
        }

        @Test
        @DisplayName("unions differing factor keys and criterion sets")
        void unionsDifferingFactorsAndCriteria() throws IOException {
            Path service = tempDir.resolve("explorations").resolve("svc");

            Map<String, Object> a = variant("svc", factors("gpt-4o"),
                    1.0, 5, 0, "COMPLETED", new long[]{1000, 1100, 1200}, 1100);
            putCriteria(a, Map.of("valid-json", crit(1.0, 5, 0)));

            Map<String, Object> b = variant("svc", orderedFactors("model", "claude", "temperature", 0.2),
                    1.0, 5, 0, "COMPLETED", new long[]{900, 950, 1000}, 950);
            putCriteria(b, Map.of(
                    "valid-json", crit(1.0, 5, 0),
                    "response-not-empty", crit(0.8, 4, 1)));

            writeVariant(service, "a.yaml", a);
            writeVariant(service, "b.yaml", b);

            String html = generate(tempDir.resolve("explorations"));

            // Matrix carries the union of both variants' criteria.
            assertThat(html).contains("valid-json");
            assertThat(html).contains("response-not-empty");
            // The variant lacking response-not-empty shows n/a in that row.
            assertThat(html).contains("n/a");
        }

        @Test
        @DisplayName("per-criterion matrix lists variants as rows and criteria as columns")
        void criterionMatrixHasVariantsAsRowsAndCriteriaAsColumns() throws IOException {
            Path service = tempDir.resolve("explorations").resolve("svc");

            Map<String, Object> a = variant("svc", factors("gpt-4o"),
                    1.0, 5, 0, "COMPLETED", new long[]{1000, 1100, 1200}, 1100);
            putCriteria(a, Map.of("valid-json", crit(1.0, 5, 0)));

            Map<String, Object> b = variant("svc", factors("gpt-4o-mini"),
                    1.0, 5, 0, "COMPLETED", new long[]{900, 950, 1000}, 950);
            putCriteria(b, Map.of("valid-json", crit(1.0, 5, 0)));

            writeVariant(service, "a.yaml", a);
            writeVariant(service, "b.yaml", b);

            String html = generate(tempDir.resolve("explorations"));

            // Header row: variant label column, then one column per criterion.
            assertThat(html).contains("<tr><th>Variant</th><th>valid-json</th></tr>");
            // Each data row's first cell is the variant label, not the criterion name.
            assertThat(html).contains("<td class=\"criterion-name\">gpt-4o</td>");
            assertThat(html).contains("<td class=\"criterion-name\">gpt-4o-mini</td>");
            assertThat(html).doesNotContain("<td class=\"criterion-name\">valid-json</td>");
        }

        @Test
        @DisplayName("marks equally-reliable variants within the latency margin as too close to call")
        void nearTieMarksAdjacentVariants() throws IOException {
            Path service = tempDir.resolve("explorations").resolve("svc");
            // Both all-pass; p50 1010 vs 1040 -> ~2.9% apart, within the 5% margin.
            writeVariant(service, "a.yaml",
                    allPassVariant("svc", "model-a", new long[]{1000, 1010, 1020}, 1010));
            writeVariant(service, "b.yaml",
                    allPassVariant("svc", "model-b", new long[]{1030, 1040, 1050}, 1040));

            String html = generate(tempDir.resolve("explorations"));

            assertThat(html).contains("class=\"tie-mark\"");
            assertThat(html).contains("too close to call");
            // The overview cell joins both leading variants.
            assertThat(html).contains("model-a");
            assertThat(html).contains("model-b");
        }

        @Test
        @DisplayName("does not mark a near-tie when medians differ beyond the margin")
        void noNearTieWhenLatencyGapExceedsThreshold() throws IOException {
            Path service = tempDir.resolve("explorations").resolve("svc");
            // p50 1010 vs 1510 -> ~33% apart, well beyond 5%.
            writeVariant(service, "a.yaml",
                    allPassVariant("svc", "model-a", new long[]{1000, 1010, 1020}, 1010));
            writeVariant(service, "c.yaml",
                    allPassVariant("svc", "model-c", new long[]{1500, 1510, 1520}, 1510));

            String html = generate(tempDir.resolve("explorations"));

            assertThat(html).doesNotContain("class=\"tie-mark\"");
        }

        @Test
        @DisplayName("never softens a difference in pass rate into a near-tie")
        void noNearTieWhenPassRateDiffers() throws IOException {
            Path service = tempDir.resolve("explorations").resolve("svc");
            // Close medians, but different pass rates: not a near-tie (no proportion test).
            writeVariant(service, "a.yaml",
                    allPassVariant("svc", "model-a", new long[]{1000, 1010, 1020}, 1010));
            writeVariant(service, "d.yaml", variant("svc", factors("model-d"),
                    0.667, 2, 1, "COMPLETED", new long[]{1005, 1015, 1025}, 1015));

            String html = generate(tempDir.resolve("explorations"));

            assertThat(html).doesNotContain("class=\"tie-mark\"");
        }

        @Test
        @DisplayName("writes a no-explorations page for an absent root")
        void writesEmptyPageForAbsentRoot() throws IOException {
            String html = generate(tempDir.resolve("does-not-exist"));
            assertThat(html).contains("No explorations found");
        }

        @Test
        @DisplayName("emits a single self-contained file with no external requests")
        void selfContained() throws IOException {
            Path service = tempDir.resolve("explorations").resolve("svc");
            writeVariant(service, "a.yaml",
                    allPassVariant("svc", "gpt-4o", new long[]{1000, 1100}, 1050));

            String html = generate(tempDir.resolve("explorations"));

            assertThat(html).contains("<style>");
            assertThat(html).doesNotContain("<script");
            assertThat(html).doesNotContain("src=");
            assertThat(html).doesNotContain("http://");
            assertThat(html).doesNotContain("https://");
        }

        @Test
        @DisplayName("reuses the test report's base stylesheet for visual parity")
        void reusesBaseStylesheet() throws IOException {
            Path service = tempDir.resolve("explorations").resolve("svc");
            writeVariant(service, "a.yaml",
                    allPassVariant("svc", "gpt-4o", new long[]{1000}, 1000));

            String html = generate(tempDir.resolve("explorations"));

            // The base CSS variables come straight from ReportHtml.appendBaseCss.
            assertThat(html).contains("--pass-color: #2e7d32");
            assertThat(html).contains("--inconclusive-color: #6a1b9a");
        }
    }

    // ── Fixture helpers ──────────────────────────────────────────────────────

    private String generate(Path root) throws IOException {
        Path htmlDir = tempDir.resolve("html-" + Math.abs(root.hashCode()));
        generator.generate(root, htmlDir);
        Path indexHtml = htmlDir.resolve("index.html");
        assertThat(indexHtml).exists();
        return Files.readString(indexHtml);
    }

    private void writeVariant(Path serviceDir, String filename, Map<String, Object> root) throws IOException {
        Files.createDirectories(serviceDir);
        Files.writeString(serviceDir.resolve(filename), new Yaml().dump(root));
    }

    private static Map<String, Object> allPassVariant(String service, String model,
            long[] latencies, long avgMs) {
        int n = latencies.length;
        Map<String, Object> root = variant(service, factors(model), 1.0, n, 0, "COMPLETED", latencies, avgMs);
        putCriteria(root, Map.of("valid-json", crit(1.0, n, 0)));
        return root;
    }

    private static Map<String, Object> variant(String service, Map<String, Object> factors,
            double observed, int successes, int failures, String termination,
            long[] latencies, long avgMs) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", "punit-spec-1");
        root.put("useCaseId", service);
        root.put("factors", factors);

        Map<String, Object> execution = new LinkedHashMap<>();
        execution.put("samplesPlanned", successes + failures);
        execution.put("samplesExecuted", successes + failures);
        execution.put("terminationReason", termination);
        root.put("execution", execution);

        Map<String, Object> statistics = new LinkedHashMap<>();
        statistics.put("observed", observed);
        statistics.put("successes", successes);
        statistics.put("failures", failures);
        root.put("statistics", statistics);

        Map<String, Object> cost = new LinkedHashMap<>();
        cost.put("totalTimeMs", avgMs * Math.max(1, successes + failures));
        cost.put("avgTimePerSampleMs", avgMs);
        root.put("cost", cost);

        if (latencies.length > 0) {
            Map<String, Object> latency = new LinkedHashMap<>();
            latency.put("basis", "passing-samples");
            latency.put("contributingSamples", latencies.length);
            latency.put("totalSamples", successes + failures);
            List<Long> sorted = new ArrayList<>();
            for (long l : latencies) {
                sorted.add(l);
            }
            latency.put("sortedPassingLatenciesMs", sorted);
            root.put("latency", latency);
        }
        return root;
    }

    private static Map<String, Object> factors(String model) {
        Map<String, Object> factors = new LinkedHashMap<>();
        factors.put("model", model);
        return factors;
    }

    private static Map<String, Object> orderedFactors(Object... pairs) {
        Map<String, Object> factors = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            factors.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return factors;
    }

    @SuppressWarnings("unchecked")
    private static void putCriteria(Map<String, Object> root, Map<String, Map<String, Object>> criteria) {
        Map<String, Object> statistics = (Map<String, Object>) root.get("statistics");
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
