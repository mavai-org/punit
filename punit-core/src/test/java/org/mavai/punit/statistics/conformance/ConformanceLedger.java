package org.mavai.punit.statistics.conformance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.fail;

/**
 * Manifest-driven conformance coverage accounting.
 *
 * <p>The mavai-R oracle publishes {@code manifest.json} alongside its
 * fixture suites: per-suite case rosters, a binding-vs-informational
 * classification of every expected field, per-suite content hashes, and
 * a family-mandatory suite tier. The obligation on a consumer is the set
 * of {@code (suite, case, binding-field)} triples across the
 * family-mandatory tier plus this repository's committed scope file
 * ({@code conformance-scope.json}) — and the obligation is
 * <em>self-verified</em>: every conformance assertion records the triple
 * it asserted, and the coverage check diffs the recorded set against the
 * manifest. A binding field that is loaded but never asserted is a gap,
 * not a pass.
 */
final class ConformanceLedger {

    record Triple(String suite, String caseName, String field) implements Comparable<Triple> {
        @Override
        public int compareTo(Triple o) {
            int c = suite.compareTo(o.suite);
            if (c != 0) return c;
            c = caseName.compareTo(o.caseName);
            if (c != 0) return c;
            return field.compareTo(o.field);
        }

        @Override
        public String toString() {
            return suite + "/" + caseName + "/" + field;
        }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SCOPE_RESOURCE = "conformance-scope.json";

    private final JsonNode manifest;
    private final List<String> scopeSuites;
    private final List<String> mandatorySuites;
    private final Set<Triple> asserted = new LinkedHashSet<>();

    private ConformanceLedger(JsonNode manifest, List<String> scopeSuites) {
        this.manifest = manifest;
        this.scopeSuites = List.copyOf(scopeSuites);
        List<String> mandatory = new ArrayList<>();
        manifest.get("familyMandatory").forEach(n -> mandatory.add(n.asText()));
        this.mandatorySuites = List.copyOf(mandatory);
    }

    /**
     * Loads the fetched manifest plus the committed scope file. Fails
     * (as a test assertion) with the corrective release diagnostic when
     * the fetched oracle release predates the manifest.
     */
    static ConformanceLedger load() {
        JsonNode manifest = ConformanceFixtures.load("manifest.json");
        try (InputStream is = ConformanceLedger.class.getResourceAsStream(SCOPE_RESOURCE)) {
            if (is == null) {
                return fail("committed scope file %s is missing next to the conformance tests",
                        SCOPE_RESOURCE);
            }
            JsonNode scope = MAPPER.readTree(is);
            List<String> scopeSuites = new ArrayList<>();
            scope.get("suites").forEach(n -> scopeSuites.add(n.asText()));
            return new ConformanceLedger(manifest, scopeSuites);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + SCOPE_RESOURCE, e);
        }
    }

    // ── recording ───────────────────────────────────────────────────

    void record(String suite, String caseName, String field) {
        asserted.add(new Triple(suite, caseName, field));
    }

    // ── the obligation ──────────────────────────────────────────────

    String fixtureVersion() {
        return manifest.get("fixtureVersion").asText();
    }

    List<String> mandatorySuites() {
        return mandatorySuites;
    }

    List<String> scopeSuites() {
        return scopeSuites;
    }

    /** Family-mandatory plus committed scope, deduplicated, manifest order. */
    List<String> inScopeSuites() {
        Set<String> wanted = new LinkedHashSet<>();
        wanted.addAll(mandatorySuites);
        wanted.addAll(scopeSuites);
        List<String> ordered = new ArrayList<>();
        manifest.get("suites").fieldNames().forEachRemaining(name -> {
            if (wanted.contains(name)) {
                ordered.add(name);
            }
        });
        return ordered;
    }

    /**
     * The manifest's binding-field roster for a suite. The oracle's
     * serialiser unboxes single-element vectors to scalars, so the
     * value may be a lone JSON string rather than an array.
     */
    Set<String> bindingFields(String suite) {
        JsonNode fields = suiteEntry(suite).get("bindingFields");
        Set<String> out = new LinkedHashSet<>();
        if (fields.isTextual()) {
            out.add(fields.asText());
        } else {
            fields.forEach(n -> out.add(n.asText()));
        }
        return out;
    }

    /**
     * Every {@code (suite, case, binding-field)} triple the given suites
     * demand. A case owes exactly the binding fields present in its own
     * {@code expected} block — suites whose case groups carry different
     * expected shapes (e.g. threshold_derivation's two approaches) owe
     * per-case, not the suite-wide union.
     */
    Set<Triple> obligations(List<String> suites) {
        Set<Triple> out = new LinkedHashSet<>();
        for (String suite : suites) {
            Set<String> binding = bindingFields(suite);
            JsonNode suiteFile = ConformanceFixtures.load(suiteEntry(suite).get("file").asText());
            for (JsonNode c : suiteFile.get("cases")) {
                String caseName = c.get("name").asText();
                c.get("expected").fieldNames().forEachRemaining(field -> {
                    if (binding.contains(field)) {
                        out.add(new Triple(suite, caseName, field));
                    }
                });
            }
        }
        return out;
    }

    Set<Triple> gaps() {
        Set<Triple> gaps = new TreeSet<>(obligations(inScopeSuites()));
        gaps.removeAll(asserted);
        return gaps;
    }

    /** Manifest suites outside scope, with case counts — reported, never silently skipped. */
    Map<String, Integer> unaddressedSuites() {
        Set<String> inScope = new LinkedHashSet<>(inScopeSuites());
        Map<String, Integer> out = new LinkedHashMap<>();
        manifest.get("suites").fieldNames().forEachRemaining(name -> {
            if (!inScope.contains(name)) {
                out.put(name, manifest.get("suites").get(name).get("caseCount").asInt());
            }
        });
        return out;
    }

    // ── fetch / vendor drift ────────────────────────────────────────

    String manifestMd5(String suite) {
        return suiteEntry(suite).get("md5").asText();
    }

    String fetchedMd5(String suite) {
        return ConformanceFixtures.md5(suiteEntry(suite).get("file").asText());
    }

    // ── the standing ────────────────────────────────────────────────

    /** The one-line summary every coverage run prints. */
    String standing() {
        Set<Triple> mandatory = obligations(mandatorySuites);
        Set<Triple> scoped = obligations(scopeSuites);
        long mandatoryHit = mandatory.stream().filter(asserted::contains).count();
        long scopedHit = scoped.stream().filter(asserted::contains).count();
        StringBuilder sb = new StringBuilder("conformance standing: ")
                .append("fixtures v").append(fixtureVersion())
                .append("; mandatory ").append(mandatoryHit).append('/').append(mandatory.size())
                .append(" binding assertions over ").append(mandatorySuites.size()).append(" suites")
                .append("; scope ").append(scopedHit).append('/').append(scoped.size())
                .append(" over ").append(scopeSuites.size()).append(" suites");
        Map<String, Integer> unaddressed = unaddressedSuites();
        if (!unaddressed.isEmpty()) {
            sb.append("; unaddressed: ");
            boolean first = true;
            for (var e : unaddressed.entrySet()) {
                if (!first) sb.append(", ");
                sb.append(e.getKey()).append(" (").append(e.getValue()).append(')');
                first = false;
            }
        }
        return sb.toString();
    }

    /** The machine-readable per-run report, for CI surfacing. */
    void writeReport(Path path) {
        ObjectNode report = MAPPER.createObjectNode();
        report.put("fixtureVersion", fixtureVersion());
        report.put("manifestVersion", manifest.get("manifestVersion").asInt());
        ArrayNode mandatory = report.putArray("mandatorySuites");
        mandatorySuites.forEach(mandatory::add);
        ArrayNode scope = report.putArray("scopeSuites");
        scopeSuites.forEach(scope::add);
        report.put("assertedTriples", asserted.size());
        report.put("obligedTriples", obligations(inScopeSuites()).size());
        ArrayNode gapsNode = report.putArray("gaps");
        for (Triple gap : gaps()) {
            ObjectNode g = gapsNode.addObject();
            g.put("suite", gap.suite());
            g.put("case", gap.caseName());
            g.put("field", gap.field());
        }
        ArrayNode unaddressed = report.putArray("unaddressedSuites");
        for (var e : unaddressedSuites().entrySet()) {
            ObjectNode u = unaddressed.addObject();
            u.put("suite", e.getKey());
            u.put("caseCount", e.getValue());
        }
        report.put("standing", standing());
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, report.toPrettyString() + System.lineSeparator());
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write conformance report to " + path, e);
        }
    }

    private JsonNode suiteEntry(String suite) {
        JsonNode entry = manifest.get("suites").get(suite);
        if (entry == null) {
            throw new IllegalStateException("suite '" + suite + "' is not in the manifest");
        }
        return entry;
    }
}
