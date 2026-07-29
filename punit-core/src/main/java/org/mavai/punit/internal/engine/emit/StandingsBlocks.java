package org.mavai.punit.internal.engine.emit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.mavai.punit.api.spec.PostconditionStandings;

/**
 * The postcondition-standings block as artefact YAML maps — the family
 * interchange shape (camelCase keys; the optional {@code optionalSlack}
 * verbatim; one row per {@code (input, check)} with the tally and
 * observed fraction). Shared by the explore and optimize writers per
 * the identical-statistics-shape rule; descriptive only, exactly as
 * stated by the derivation.
 */
public final class StandingsBlocks {

    private StandingsBlocks() { }

    /**
     * Each criterion's standings block, keyed by its bounded criterion
     * id — criteria stating no rows are absent (binding when present,
     * never fabricated).
     */
    public static Map<String, Map<String, Object>> byCriterion(PostconditionStandings standings) {
        Map<String, Map<String, Object>> blocks = new LinkedHashMap<>();
        for (PostconditionStandings.CriterionStandings criterion : standings.criteria()) {
            if (criterion.rows().isEmpty()) {
                continue;
            }
            Map<String, Object> block = new LinkedHashMap<>();
            criterion.optionalSlack().ifPresent(slack -> block.put("optionalSlack", slack));
            List<Map<String, Object>> rows = new ArrayList<>();
            for (PostconditionStandings.Row row : criterion.rows()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("inputIndex", row.inputIndex());
                entry.put("check", EmittedKeys.bound(row.check()));
                entry.put("optional", row.optional());
                entry.put("passed", row.passed());
                entry.put("failed", row.failed());
                entry.put("skipped", row.skipped());
                entry.put("observedFraction", Double.parseDouble(
                        String.format(Locale.ROOT, "%.6f", row.observedFraction())));
                rows.add(entry);
            }
            block.put("rows", rows);
            blocks.put(EmittedKeys.bound(criterion.criterionId()), block);
        }
        return blocks;
    }
}
