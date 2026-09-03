package org.mavai.punit.internal.engine.emit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.mavai.outcome.Outcome;
import org.mavai.punit.api.spec.DeliveryCause;
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
 *
 * <p>Each entry also states its {@code kind}: whether these trials
 * were judged and found wanting, or never delivered a response to
 * judge. The counting rule is untouched — a failed delivery is a
 * failed trial exactly as before — but a reader can now tell a
 * configuration whose endpoint was down from one whose answers were
 * bad, which on a bare pass rate of zero are the same picture. A
 * delivery entry's {@code condition} is its cause token from
 * {@link DeliveryCause}: there is no declared condition to name when
 * nothing arrived to judge.
 */
public final class FailureDistributions {

    private FailureDistributions() { }

    /**
     * Aggregate {@code trials} into the sequence-of-entries failure
     * distribution. Entries appear in first-occurrence order; an
     * all-passing run yields an empty list.
     */
    public static List<Map<String, Object>> fromTrials(List<? extends Trial<?, ?>> trials) {
        Map<Attribution, Integer> counts = new LinkedHashMap<>();
        for (Trial<?, ?> trial : trials) {
            if (trial.outcome().value() instanceof Outcome.Fail<?> fail) {
                counts.merge(attribute(fail), 1, Integer::sum);
            }
        }
        List<Map<String, Object>> entries = new ArrayList<>(counts.size());
        for (Map.Entry<Attribution, Integer> e : counts.entrySet()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("condition", e.getKey().condition());
            entry.put("kind", e.getKey().kind());
            entry.put("count", e.getValue());
            entries.add(entry);
        }
        return entries;
    }

    /**
     * The entry identity: what failed, and what kind of failure it was.
     *
     * <p>Recognition is by the reserved failure-id namespace, never by
     * matching the name against the cause vocabulary. An author is free
     * to declare a contract condition called {@code server-error}, and
     * reading it as a transport fact would tell a reader the service
     * was never reached when in truth it answered and failed a check —
     * a stated cause that is not merely absent but wrong.
     */
    private static Attribution attribute(Outcome.Fail<?> fail) {
        if (DeliveryCause.NAMESPACE.equals(fail.failure().id().namespace())) {
            // The cause token is the identity. There is no declared
            // condition to name when nothing was delivered to judge.
            return new Attribution(EmittedKeys.bound(fail.failure().id().name()), "delivery");
        }
        return new Attribution(EmittedKeys.bound(fail.failure().id().name()), "evaluated");
    }

    /** One failure-distribution entry's identity, kind included. */
    private record Attribution(String condition, String kind) { }
}
