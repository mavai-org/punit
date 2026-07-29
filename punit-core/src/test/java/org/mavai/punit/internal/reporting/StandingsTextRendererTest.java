package org.mavai.punit.internal.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mavai.punit.api.spec.PostconditionStandings;
import org.mavai.punit.api.spec.PostconditionStandings.CriterionStandings;
import org.mavai.punit.api.spec.PostconditionStandings.Row;

@DisplayName("Postcondition standings text renderer")
class StandingsTextRendererTest {

    private static PostconditionStandings standings() {
        return new PostconditionStandings(List.of(new CriterionStandings(
                "quality",
                Optional.of("1"),
                List.of(
                        new Row(0, "mentions greeting", false, 18, 2, 0),
                        new Row(0, "mentions farewell", true, 12, 6, 2)))));
    }

    @Test
    @DisplayName("renders one heading per criterion and one line per (input, check) row")
    void rendersHeadingAndRows() {
        List<String> lines = StandingsTextRenderer.render(standings());

        assertThat(lines).containsExactly(
                "Postcondition standings (descriptive):",
                "  criterion 'quality' (optional-slack 1):",
                "    input 0 · mentions greeting: 18/20 passed (0.90)",
                "    input 0 · mentions farewell [optional]: 12/20 passed, 2 skipped (0.60)");
    }

    @Test
    @DisplayName("empty standings render nothing")
    void emptyStandingsRenderNothing() {
        assertThat(StandingsTextRenderer.render(new PostconditionStandings(List.of()))).isEmpty();
        assertThat(StandingsTextRenderer.render(new PostconditionStandings(
                List.of(new CriterionStandings("quality", Optional.empty(), List.of())))))
                .isEmpty();
    }

    @Test
    @DisplayName("standings lines stay descriptive — no judgement vocabulary")
    void descriptiveVocabularyOnly() {
        String rendered = String.join("\n", StandingsTextRenderer.render(standings()))
                .toLowerCase(java.util.Locale.ROOT);

        // The criterion is the only judged unit; a standings line never
        // states an interval, a threshold, or a per-check verdict.
        assertThat(rendered)
                .doesNotContain("interval")
                .doesNotContain("threshold")
                .doesNotContain("verdict")
                .doesNotContain("confidence")
                .doesNotContain("required")
                .doesNotContain("pass rate");
    }
}
