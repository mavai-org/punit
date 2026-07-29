package org.mavai.punit.internal.engine.emit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mavai.punit.api.spec.PostconditionStandings;
import org.mavai.punit.api.spec.PostconditionStandings.CriterionStandings;
import org.mavai.punit.api.spec.PostconditionStandings.Row;

@DisplayName("Standings artefact blocks")
class StandingsBlocksTest {

    private static PostconditionStandings standings() {
        return new PostconditionStandings(List.of(new CriterionStandings(
                "quality",
                Optional.of("10%"),
                List.of(
                        new Row(0, "mentions greeting", false, 18, 2, 0),
                        new Row(1, "mentions farewell", true, 12, 6, 2)))));
    }

    @Test
    @DisplayName("emits one block per criterion, keyed by bounded criterion id")
    void emitsBlockPerCriterion() {
        Map<String, Map<String, Object>> blocks = StandingsBlocks.byCriterion(standings());

        assertThat(blocks).containsOnlyKeys("quality");
        Map<String, Object> block = blocks.get("quality");
        assertThat(block.get("optionalSlack")).isEqualTo("10%");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) block.get("rows");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsOnlyKeys(
                "inputIndex", "check", "optional", "passed", "failed", "skipped",
                "observedFraction");
        assertThat(rows.get(0).get("check")).isEqualTo("mentions greeting");
        assertThat(rows.get(0).get("passed")).isEqualTo(18);
        assertThat(rows.get(0).get("observedFraction")).isEqualTo(0.9);
        assertThat(rows.get(1).get("skipped")).isEqualTo(2);
        assertThat(rows.get(1).get("observedFraction")).isEqualTo(0.6);
    }

    @Test
    @DisplayName("criteria stating no rows are absent, never fabricated")
    void rowlessCriteriaAbsent() {
        PostconditionStandings empty = new PostconditionStandings(List.of(
                new CriterionStandings("quality", Optional.empty(), List.of())));

        assertThat(StandingsBlocks.byCriterion(empty)).isEmpty();
    }

    @Test
    @DisplayName("block keys stay descriptive — no judgement vocabulary")
    void descriptiveVocabularyOnly() {
        Map<String, Object> block = StandingsBlocks.byCriterion(standings()).get("quality");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) block.get("rows");
        List<String> keys = rows.stream().flatMap(r -> r.keySet().stream()).distinct().toList();

        // Counts and observed fractions only — never an interval, a
        // threshold, or a per-check verdict key.
        assertThat(keys).allSatisfy(key -> assertThat(key.toLowerCase(java.util.Locale.ROOT))
                .doesNotContain("interval")
                .doesNotContain("threshold")
                .doesNotContain("verdict")
                .doesNotContain("confidence")
                .doesNotContain("bound"));
    }
}
