package org.mavai.punit.statistics.conformance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The manifest-driven coverage obligation: every
 * {@code (suite, case, binding-field)} triple demanded by the oracle's
 * family-mandatory tier plus punit's committed scope
 * ({@code conformance-scope.json}) must actually be asserted by the
 * conformance catalog. A binding field that is loaded but never asserted
 * is a gap, not a pass — the failure mode that let the empirical
 * decision-rule deviation ship undetected in the family.
 *
 * <p>This test <em>re-executes</em> the catalog's checks with its own
 * collecting recorder rather than reading a ledger populated by the
 * other test classes. That makes its verdict deterministic under any
 * test ordering, {@code --tests} filtering, or Gradle fork/parallel
 * configuration: there is no shared mutable state to lose. Assertion
 * failures inside re-run checks are tolerated here (the per-suite
 * display tests report them); a check records each triple before
 * asserting it, so an attempted-and-failed assertion still counts as
 * addressed while fields after the failure point in the same case
 * surface as gaps until the case is green.
 */
@DisplayName("Conformance coverage (mavai-R manifest)")
class ConformanceCoverageTest {

    private static final Path REPORT_PATH = Path.of("build", "conformance-report.json");

    @Test
    @DisplayName("Fetched fixture files match the manifest's content hashes")
    void fixtureFilesMatchManifestHashes() {
        ConformanceLedger ledger = ConformanceLedger.load();
        for (String suite : ledger.inScopeSuites()) {
            assertThat(ledger.fetchedMd5(suite))
                    .as("suite %s: fetched fixture must be byte-identical to the file the "
                            + "manifest describes — fetch/vendor drift is a conformance failure",
                            suite)
                    .isEqualTo(ledger.manifestMd5(suite));
        }
    }

    @Test
    @DisplayName("Every binding assertion the manifest and scope demand is made")
    void coverageMeetsManifestObligations() {
        ConformanceLedger ledger = ConformanceLedger.load();
        int failedChecks = 0;
        for (ConformanceCatalog.CaseCheck check : ConformanceCatalog.all()) {
            try {
                check.check().run(ledger::record);
            } catch (AssertionError e) {
                // Red checks are reported by the per-suite display tests;
                // coverage accounts for what was *attempted*. Triples
                // recorded before the failure point count as addressed.
                failedChecks++;
            } catch (Exception e) {
                throw new IllegalStateException(
                        "conformance check crashed (not an assertion failure): "
                                + check.suite() + "/" + check.caseName(), e);
            }
        }

        ledger.writeReport(REPORT_PATH);
        System.out.println(ledger.standing());
        if (failedChecks > 0) {
            System.out.println("conformance coverage: " + failedChecks
                    + " re-run check(s) currently red — see the conformance suite tests");
        }

        assertThat(ledger.gaps())
                .as("binding assertions required by the manifest (family-mandatory tier + "
                        + "committed scope) that were never made — report written to %s",
                        REPORT_PATH)
                .isEmpty();
    }
}
