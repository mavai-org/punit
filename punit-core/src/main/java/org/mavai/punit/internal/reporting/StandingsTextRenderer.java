package org.mavai.punit.internal.reporting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.mavai.punit.api.spec.PostconditionStandings;

/**
 * Text rendering of the postcondition standings — the descriptive
 * per-{@code (input, check)} tally every run surfaces. Descriptive
 * vocabulary only: counts and observed fractions, never an interval, a
 * threshold, or a per-check verdict — the criterion stays the only
 * judged unit, and this renderer computes nothing (the observed
 * fraction is the row's own statement).
 */
public final class StandingsTextRenderer {

    private StandingsTextRenderer() { }

    /**
     * The standings as indent-ready lines; empty when nothing is
     * stated. One heading per criterion (with its declared
     * optional-slack budget, verbatim, when one is declared), one line
     * per {@code (input, check)} row.
     */
    public static List<String> render(PostconditionStandings standings) {
        List<String> lines = new ArrayList<>();
        for (PostconditionStandings.CriterionStandings criterion : standings.criteria()) {
            if (criterion.rows().isEmpty()) {
                continue;
            }
            if (lines.isEmpty()) {
                lines.add("Postcondition standings (descriptive):");
            }
            lines.add("  criterion '" + criterion.criterionId() + "'"
                    + criterion.optionalSlack()
                            .map(slack -> " (optional-slack " + slack + ")")
                            .orElse("")
                    + ":");
            for (PostconditionStandings.Row row : criterion.rows()) {
                StringBuilder line = new StringBuilder("    input ")
                        .append(row.inputIndex())
                        .append(" · ")
                        .append(row.check());
                if (row.optional()) {
                    line.append(" [optional]");
                }
                line.append(": ")
                        .append(row.passed()).append('/').append(row.trials())
                        .append(" passed");
                if (row.skipped() > 0) {
                    line.append(", ").append(row.skipped()).append(" skipped");
                }
                line.append(String.format(Locale.ROOT, " (%.2f)", row.observedFraction()));
                lines.add(line.toString());
            }
        }
        return lines;
    }
}
