package org.mavai.punit.internal.engine.emit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.mavai.outcome.Outcome;
import org.mavai.punit.api.spec.Trial;

/**
 * Builds the {@code failureDistribution:} block of the canonical
 * exploration and optimize interchange schemas — a <em>sequence</em>
 * of {@code {condition, count}} entries, never a mapping keyed by
 * free-text identity (the interchange area's key discipline).
 *
 * <p>Each failed trial is attributed to its first failing condition
 * — the same precedence {@link org.mavai.punit.api.ServiceContractOutcome#value()}
 * applies when deriving the sample outcome: an apply-level failure's
 * declared failure name short-circuits, otherwise the first failed
 * postcondition's description. Entry counts therefore sum exactly to
 * the enclosing {@code failures} total.
 *
 * <p>The {@code condition} field carries the condition's declared
 * identity, bounded per {@link EmittedKeys#bound(String)} — never
 * input or response content. punit's condition identities are the
 * contract-declared descriptions (per-input attribution travels
 * structurally as {@code inputIndex} in the result projection), so
 * entries aggregate by condition alone.
 */
public final class FailureDistributions {

    private FailureDistributions() { }

    /**
     * Aggregate {@code trials} into the sequence-of-entries failure
     * distribution. Entries appear in first-occurrence order; an
     * all-passing run yields an empty list.
     */
    public static List<Map<String, Object>> fromTrials(List<? extends Trial<?, ?>> trials) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Trial<?, ?> trial : trials) {
            if (trial.outcome().value() instanceof Outcome.Fail<?> fail) {
                String condition = EmittedKeys.bound(fail.failure().id().name());
                counts.merge(condition, 1, Integer::sum);
            }
        }
        List<Map<String, Object>> entries = new ArrayList<>(counts.size());
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("condition", e.getKey());
            entry.put("count", e.getValue());
            entries.add(entry);
        }
        return entries;
    }
}
