package org.mavai.punit.internal.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;

class PUnitReporterTest {

    @Test
    void headerDividerIncludesTitleOnLeftAndPUnitOnRight() {
        PUnitReporter reporter = new PUnitReporter(60);
        String divider = reporter.headerDivider("TEST TITLE");

        // Format: ═ {TITLE} ═══...═══ PUnit ═
        assertThat(divider)
                .startsWith("═ TEST TITLE ")
                .endsWith(" PUnit ═")
                .hasSize(60);
    }

    @Test
    void headerDividerHandlesLongTitle() {
        PUnitReporter reporter = new PUnitReporter(30);
        String divider = reporter.headerDivider("VERY LONG TITLE HERE");

        // Title is too long for suffix, so suffix is sacrificed
        assertThat(divider)
                .startsWith("═ VERY LONG TITLE HERE")
                .endsWith("═");
    }

    @Test
    void headerDividerTruncatesExtremelyLongTitle() {
        PUnitReporter reporter = new PUnitReporter(40);
        String divider = reporter.headerDivider(
                "This is an extremely long title that definitely will not fit in the header");

        // Title should be truncated with ellipsis, PUnit suffix sacrificed
        assertThat(divider)
                .startsWith("═ This is an extremely long title")
                .contains("...")
                .endsWith(" ═")
                .hasSize(40);
    }

    @Test
    void footerDividerIsPlainLine() {
        PUnitReporter reporter = new PUnitReporter(40);
        String footer = reporter.footerDivider();

        assertThat(footer)
                .isEqualTo("═".repeat(40))
                .hasSize(40);
    }

    @Test
    void reportInfoEmitsFormattedOutputToStdout() {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream capture = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capture));

        try {
            PUnitReporter reporter = new PUnitReporter(40);
            reporter.reportInfo("TEST TITLE", "hello");

            String output = capture.toString();
            String expected = reporter.headerDivider("TEST TITLE") + "\n\n" + "  hello" + "\n";
            assertThat(output.trim()).isEqualTo(expected.trim());
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    void reportWarnEmitsToStderr() {
        PrintStream originalErr = System.err;
        ByteArrayOutputStream capture = new ByteArrayOutputStream();
        System.setErr(new PrintStream(capture));

        try {
            PUnitReporter reporter = new PUnitReporter(40);
            reporter.reportWarn("WARNING", "something is off");

            String output = capture.toString();
            assertThat(output).contains("═ WARNING");
            assertThat(output).contains("something is off");
        } finally {
            System.setErr(originalErr);
        }
    }
}
