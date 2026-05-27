package org.javai.punit.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Requirement-code isolation rule.
 *
 * <p>The orchestrator catalog tracks features by short letter-prefix
 * codes (CT04, EX04, LT01, RP01, and similar). Those codes are
 * orchestrator-internal: they are not interpretable by a reader of
 * punit's open-source code, who has no orchestrator access. Per the
 * project family's convention, the codes must not leak into punit's
 * source - production OR test - in javadoc, inline comments, code,
 * string literals, or @DisplayName values. A feature gets referred to
 * by its domain name, not by its tracking code.
 *
 * <p>This test walks every Java source file under the project's
 * <code>src/main/java</code> AND <code>src/test/java</code> trees, plus
 * the published text resources (<code>.xsd</code> / <code>.xslt</code> /
 * <code>.xml</code>) under <code>src/main/resources</code> and
 * <code>src/test/resources</code>, and asserts no requirement-style code
 * appears anywhere - neither in code, comments, nor schema/resource
 * files. Earlier iterations only guarded production Java source; test
 * sources, then resource files (the verdict XSDs in particular), were
 * back doors through which codes kept leaking back in, defeating the
 * rule. The guard now scans Java and text resources, production and test.
 *
 * <p>The one exception: this file itself necessarily mentions the
 * prefixes (in its docstring above and in the regex below) so it can
 * <em>be</em> the regression guard. The scanner skips a file whose
 * filename matches the self-reference list below.
 *
 * <p>Wire-format constants (<code>"punit-baseline-2"</code>,
 * <code>"verdict-1.0"</code>, and similar) are not requirement codes:
 * they have a different shape (lowercase prefix, not two uppercase
 * letters) and the regex below does not match them.
 */
@DisplayName("Requirement-code isolation")
class RequirementCodeIsolationTest {

    /**
     * Matches the catalog's letter-prefix-plus-two-digit convention.
     * Letters cover every prefix currently in use across the
     * orchestrator catalog and design docs:
     * <ul>
     *   <li>CR - criterion decomposition</li>
     *   <li>CT - conformance testing</li>
     *   <li>EX - experiments</li>
     *   <li>LT - latency dimension</li>
     *   <li>PT - probabilistic tests</li>
     *   <li>RC - resource controls</li>
     *   <li>RP - reporting</li>
     *   <li>SC - statistics core</li>
     *   <li>SN - sentinel</li>
     *   <li>TH - test-harness integration</li>
     *   <li>UC - use-case model</li>
     *   <li>XM - examples</li>
     *   <li>DG - design guides (orchestrator docs/)</li>
     * </ul>
     *
     * <p>Two letters then exactly two digits, with word boundaries on
     * either side. Matches RP01, LT04, EX10; does not match
     * FT/IT/OT type-parameter conventions, nor lowercase-prefixed
     * wire-format constants.
     */
    private static final Pattern REQUIREMENT_CODE = Pattern.compile(
            "\\b(CR|CT|EX|LT|PT|RC|RP|SC|SN|TH|UC|XM|DG)\\d{2}\\b");

    /**
     * Filenames that legitimately mention the prefixes - this test
     * file documents and matches them. Anything else mentioning a code
     * is a leak.
     */
    private static final List<String> SELF_REFERENCING_FILES = List.of(
            "RequirementCodeIsolationTest.java");

    /**
     * Source roots, relative to the project root the Gradle test task
     * runs in (the per-module dir: this test lives in punit-core, so
     * its CWD is .../punit/punit-core). Walk up one level to reach the
     * project root, then descend per-module.
     *
     * <p>Both <code>src/main</code> and <code>src/test</code> are scanned,
     * for Java and for text resources. Neither test sources nor resource
     * files are exempt: an orchestrator code in a {@code @DisplayName}, a
     * javadoc, or an XSD comment surfaces in build reports, IDE test
     * panes, and the published repository, where an open-source reader
     * meets it without any catalog access. Earlier scopes (production
     * only, then Java only) let codes leak back in through test files and
     * the verdict XSDs; the rule is now uniform.
     */
    private static final List<Path> SOURCE_ROOTS = List.of(
            Paths.get("..", "punit-core", "src", "main", "java"),
            Paths.get("..", "punit-core", "src", "test", "java"),
            Paths.get("..", "punit-core", "src", "main", "resources"),
            Paths.get("..", "punit-core", "src", "test", "resources"),
            Paths.get("..", "punit-junit5", "src", "main", "java"),
            Paths.get("..", "punit-junit5", "src", "test", "java"),
            Paths.get("..", "punit-junit5", "src", "main", "resources"),
            Paths.get("..", "punit-junit5", "src", "test", "resources"),
            Paths.get("..", "punit-report", "src", "main", "java"),
            Paths.get("..", "punit-report", "src", "test", "java"),
            Paths.get("..", "punit-report", "src", "main", "resources"),
            Paths.get("..", "punit-report", "src", "test", "resources"),
            Paths.get("..", "punit-sentinel", "src", "main", "java"),
            Paths.get("..", "punit-sentinel", "src", "test", "java"),
            Paths.get("..", "punit-sentinel", "src", "main", "resources"),
            Paths.get("..", "punit-sentinel", "src", "test", "resources"));

    /**
     * File extensions scanned for leaked codes: Java source plus the
     * published text-resource formats that carry human-readable comments
     * (the verdict XSDs, the report XSLT, and any bundled XML). Binary
     * resources and data formats (YAML specs, {@code .properties},
     * service files) are not scanned - lowercase wire-format constants do
     * not match the two-uppercase-letter pattern anyway.
     */
    private static final List<String> SCANNED_EXTENSIONS =
            List.of(".java", ".xsd", ".xslt", ".xml");

    @Test
    @DisplayName("no orchestrator-internal requirement codes appear in production or test source")
    void noLeaksInAnySource() throws IOException {
        List<String> hits = new ArrayList<>();
        for (Path root : SOURCE_ROOTS) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(root)) {
                stream
                        .filter(RequirementCodeIsolationTest::isScannable)
                        .filter(p -> !isSelfReferencing(p))
                        .forEach(file -> scanFile(file, hits));
            }
        }
        assertThat(hits)
                .as("Source must be free of orchestrator requirement codes "
                        + "(per the project family's convention; see CLAUDE.md). "
                        + "Replace each match with a domain-language description "
                        + "of the feature, or drop the cross-reference if it adds "
                        + "no information beyond what the surrounding context already gives. "
                        + "This rule applies uniformly to production AND test source - "
                        + "earlier iterations only scanned production, and codes kept "
                        + "leaking back in through test files.")
                .isEmpty();
    }

    private static boolean isScannable(Path file) {
        String name = file.toString();
        return SCANNED_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private static boolean isSelfReferencing(Path file) {
        String name = file.getFileName().toString();
        return SELF_REFERENCING_FILES.contains(name);
    }

    private static void scanFile(Path file, List<String> hits) {
        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("Failed to read " + file, e);
        }
        Matcher matcher = REQUIREMENT_CODE.matcher(content);
        while (matcher.find()) {
            int line = lineNumberOf(content, matcher.start());
            hits.add(file.normalize() + ":" + line + " - " + matcher.group());
        }
    }

    private static int lineNumberOf(String content, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
